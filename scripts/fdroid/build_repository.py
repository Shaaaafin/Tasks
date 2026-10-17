"""Generate the public Tasks repository from a verified GitHub release."""

import argparse
import base64
import datetime as dt
import hashlib
import html
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import urllib.parse
import urllib.request
import zipfile

import qrcode
import qrcode.image.svg
import yaml


ROOT = Path(__file__).resolve().parents[2]


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def download(url, limit=20_000_000, api=False):
    headers = {"User-Agent": "Tasks-repository-publisher", "Accept": "application/vnd.github+json"}
    if api and os.environ.get("GITHUB_TOKEN"):
        require(urllib.parse.urlsplit(url).netloc == "api.github.com", "Unexpected API host")
        headers["Authorization"] = "Bearer " + os.environ["GITHUB_TOKEN"]
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=90) as response:
        data = response.read(limit + 1)
    require(len(data) <= limit, "Download exceeds its size limit")
    return data


def api(repository, suffix):
    return json.loads(download(f"https://api.github.com/repos/{repository}/{suffix}", api=True))


def checked_output(command, **kwargs):
    return subprocess.run(command, check=True, capture_output=True, **kwargs).stdout


def copy_release_metadata(repository, commit, destination):
    archive = download(f"https://codeload.github.com/{repository}/zip/{commit}")
    count = 0
    with zipfile.ZipFile(io.BytesIO(archive)) as source:
        for member in source.infolist():
            parts = PurePosixPath(member.filename).parts
            if len(parts) < 6 or parts[1:5] != ("fastlane", "metadata", "android", "en-US"):
                continue
            relative = PurePosixPath(*parts[5:])
            require(".." not in relative.parts and not relative.is_absolute() and "\\" not in str(relative) and ":" not in str(relative), "Unsafe metadata path")
            if member.is_dir():
                continue
            require(member.file_size <= 5_000_000, "Metadata file is too large")
            require(relative.suffix.lower() in (".txt", ".png", ".jpg", ".jpeg"), "Unexpected metadata type")
            target = destination / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source.read(member))
            count += 1
    require(count >= 5, "Tagged release is missing its store metadata")
    for name in ("title.txt", "short_description.txt", "full_description.txt", "images/icon.png"):
        require((destination / name).is_file(), f"Missing metadata: {name}")
    screenshots = sorted((destination / "images/phoneScreenshots").glob("*.png"))
    require(bool(screenshots), "Tagged release has no screenshots")
    return screenshots


def inspect_apk(apk, tools, settings):
    signature = checked_output([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk)], text=True)
    matches = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)$", signature, re.M)
    require(len(matches) == 1 and matches[0].lower() == settings["apkCertificateSha256"].lower(), "APK signing certificate does not match")
    badging = checked_output([str(tools / "aapt2"), "dump", "badging", str(apk)], text=True)
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    require(package is not None and package[1] == settings["applicationId"], "Unexpected application ID")
    require("application-debuggable" not in badging, "Debug APKs cannot be published")
    manifest = checked_output([str(tools / "aapt2"), "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml"], text=True)
    for flag in ("debuggable", "testOnly"):
        require(not re.search(rf"android:{flag}[^\n]*0xffffffff", manifest), "Debug or test-only APK")
    return {"versionCode": int(package[2]), "versionName": package[3], "certificateSha256": matches[0].lower()}


def verify_index(repo, settings, expected_apk_hash):
    from fdroidserver import common, index

    common.config = {"jarsigner": shutil.which("jarsigner")}
    require(common.config["jarsigner"] is not None, "jarsigner is required")
    entry, _, fingerprint = index.get_index_from_jar(str(repo / "entry.jar"), settings["repoCertificateSha256"])
    index_name = entry["index"]["name"].lstrip("/")
    require(index_name == "index-v2.json", "Unexpected index name")
    data = (repo / index_name).read_bytes()
    require(sha256(data) == entry["index"]["sha256"], "Signed index hash does not match")
    v2 = json.loads(data)
    require(v2["repo"]["address"].rstrip("/") == settings["repoUrl"].rstrip("/"), "Repository address mismatch")
    require(set(v2["packages"]) == {settings["applicationId"]}, "Unexpected applications in index")
    package = v2["packages"][settings["applicationId"]]
    require(set(package["versions"]) == {expected_apk_hash}, "Published APK hash does not match")
    version = package["versions"][expected_apk_hash]
    filename = version["file"]["name"].lstrip("/")
    require(PurePosixPath(filename).name == filename, "Unsafe APK filename")
    require(sha256((repo / filename).read_bytes()) == expected_apk_hash, "APK bytes do not match index")
    signer = version["manifest"]["signer"]["sha256"]
    require(signer == [settings["apkCertificateSha256"].lower()], "Indexed APK signer mismatch")
    require(bool(package["metadata"].get("name", {}).get("en-US")), "App name missing from index")
    require(len(list((repo / settings["applicationId"] / "en-US/phoneScreenshots").glob("*.png"))) >= 1, "Screenshots were not published")
    index.get_index_from_jar(str(repo / "index-v1.jar"), fingerprint, allow_deprecated=True)
    return v2


def make_landing(output, repo, settings, release, info, locale, index_verified=False):
    escape = html.escape
    url = settings["repoUrl"] + "?fingerprint=" + settings["repoCertificateSha256"]
    parsed = urllib.parse.urlsplit(url)
    intent = urllib.parse.urlunsplit(("fdroidrepos", parsed.netloc, parsed.path, parsed.query, ""))
    qrcode.make(url, image_factory=qrcode.image.svg.SvgPathImage).save(output / "add-repository.svg")
    shutil.copy2(locale / "images/icon.png", output / "icon.png")
    gallery = output / "screenshots"
    gallery.mkdir()
    for number in (1, 2, 7):
        source = locale / f"images/phoneScreenshots/{number}.png"
        if source.exists():
            shutil.copy2(source, gallery / source.name)
    title = (locale / "title.txt").read_text(encoding="utf-8").strip()
    description = (locale / "short_description.txt").read_text(encoding="utf-8").strip()
    report = {
        "repositoryUrl": settings["repoUrl"],
        "repositoryFingerprint": settings["repoCertificateSha256"],
        "applicationId": settings["applicationId"],
        "versionName": info["versionName"],
        "versionCode": info["versionCode"],
        "apkSha256": info["apkSha256"],
        "apkCertificateSha256": settings["apkCertificateSha256"],
        "releaseTag": release["tag_name"],
        "releaseUrl": release["html_url"],
        "metadataCommit": info["commit"],
        "signedIndexVerified": index_verified,
    }
    (output / "repository.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    (repo / "fingerprint.txt").write_text(settings["repoCertificateSha256"] + "\n", encoding="utf-8")
    (output / ".nojekyll").touch()
    screenshots = "".join(f'<img src="screenshots/{p.name}" alt="{escape(title)} feature screenshot" loading="lazy">' for p in sorted(gallery.glob("*.png")))
    document = f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="description" content="{escape(description)}"><title>{escape(title)} · Android app repository</title>
<link rel="icon" href="icon.png"><style>
:root{{color-scheme:light dark;--bg:#f4f1e9;--ink:#173a2d;--muted:#52695d;--card:#fffef9;--accent:#276f54;--border:#d2ddcf}}
*{{box-sizing:border-box}}body{{margin:0;background:var(--bg);color:var(--ink);font:17px/1.65 system-ui,sans-serif}}main{{max-width:1060px;margin:auto;padding:48px 24px 64px}}header{{display:flex;gap:14px;align-items:center;font-weight:650}}header img{{width:52px;height:52px;border-radius:15px}}h1{{font-size:clamp(42px,7vw,78px);line-height:1.08;letter-spacing:-.04em;max-width:760px;margin:52px 0 22px}}p{{max-width:690px;color:var(--muted)}}.label{{font-size:12px;letter-spacing:.14em;text-transform:uppercase;color:var(--accent)}}.actions{{display:flex;gap:12px;flex-wrap:wrap;margin:30px 0}}a{{color:var(--accent)}}.button,button{{display:inline-block;border:1px solid var(--border);border-radius:16px;padding:13px 20px;text-decoration:none;font:inherit;font-weight:650;background:var(--card);color:var(--ink);cursor:pointer}}.primary{{background:var(--accent);color:#fff;border-color:var(--accent)}}.setup{{display:grid;grid-template-columns:1fr 210px;gap:32px;align-items:center;background:var(--card);padding:30px;border:1px solid var(--border);border-radius:28px;margin:42px 0}}h2{{font-size:27px;margin:0 0 12px}}code{{font-size:13px;overflow-wrap:anywhere}}.url{{display:block;background:var(--bg);padding:14px;border-radius:12px;margin:14px 0}}.qr{{width:200px;height:200px;padding:8px;background:#fff;border-radius:18px}}.gallery{{display:grid;grid-template-columns:repeat(3,1fr);gap:18px;margin:38px 0}}.gallery img{{width:100%;border-radius:18px}}footer{{margin-top:42px;padding-top:24px;border-top:1px solid var(--border);font-size:14px;color:var(--muted)}}.notice{{font-size:14px}}.fp{{display:block;max-width:100%;font-size:12px}}@media(prefers-color-scheme:dark){{:root{{--bg:#14241d;--ink:#eef2e7;--muted:#bccdbd;--card:#20352b;--accent:#9dd4b6;--border:#3a5545}}.primary{{color:#14241d}}}}@media(max-width:640px){{main{{padding:28px 20px}}.setup{{grid-template-columns:1fr;padding:22px}}.qr{{justify-self:center}}.gallery{{gap:8px}}.gallery img{{border-radius:9px}}}}
</style></head><body><main>
<header><img src="icon.png" alt=""><span>{escape(title)}</span></header>
<h1>Make room for<br>what matters.</h1><p>{escape(description)}. Native Android, no ads or analytics, and no account required for offline use.</p>
<p class="label">Android 9+ · Apache 2.0 · Version {escape(info['versionName'])}</p>
<div class="actions"><a class="button primary" href="{escape(intent)}">Add to F-Droid</a><a class="button" href="{escape(release['html_url'])}">Download APK</a><a class="button" href="https://github.com/{escape(settings['githubRepository'])}">View source</a></div>
<section class="setup"><div><h2>Your Tasks repository</h2><p>Open F-Droid → Settings → Repositories → Add repository. Paste this link, or scan the QR code with F-Droid.</p>
<code class="url" id="repo-link">{escape(url)}</code><button type="button" id="copy">Copy repository link</button><p class="notice" id="copied" role="status" aria-live="polite"></p>
<p class="notice">Repository signing fingerprint:<code class="fp">{settings['repoCertificateSha256']}</code></p></div><img class="qr" src="add-repository.svg" alt="QR code containing the repository address and signing fingerprint"></section>
<div class="gallery">{screenshots}</div>
<p>Organize lists, subtasks, repeats, tags, and priorities. Choose light, shaded dark, or AMOLED. Sync with your own CalDAV server while using the app, while charging, or manually.</p>
<footer>This independent repository is maintained by Ahnaf Hasan Shafin. It is separate from the official f-droid.org catalog. The APK retains the developer's signing key. Feature images use native UI renders with sample data.<br><a href="https://github.com/{escape(settings['githubRepository'])}/blob/{info['commit']}/docs/PRIVACY.md">Privacy policy</a> · <a href="repository.json">Repository details</a></footer>
</main><script>document.getElementById('copy').addEventListener('click',async()=>{{try{{await navigator.clipboard.writeText(document.getElementById('repo-link').textContent);document.getElementById('copied').textContent='Repository link copied.';}}catch{{document.getElementById('copied').textContent='Select and copy the repository link above.';}}}});</script></body></html>'''
    (output / "index.html").write_text(document, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--work", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    require(not args.work.exists() and not args.output.exists(), "Use new work and output directories")
    require(not args.output.resolve().is_relative_to(args.work.resolve()), "Public output must be outside private work")
    require(not args.work.resolve().is_relative_to(args.output.resolve()), "Private work must be outside public output")
    settings = json.loads((ROOT / "packaging/repository/settings.json").read_text(encoding="utf-8"))
    repository = settings["githubRepository"]
    tag = os.environ.get("RELEASE_TAG", "").strip()
    endpoint = "releases/tags/" + urllib.parse.quote(tag, safe="") if tag else "releases/latest"
    release = api(repository, endpoint)
    require(not release["draft"] and not release["prerelease"], "Only published stable releases can be indexed")
    tag = release["tag_name"]
    require(re.fullmatch(r"v?[0-9][0-9A-Za-z.-]{0,63}", tag) is not None, "Unsafe release tag")
    asset_name = f"Tasks-{tag.removeprefix('v')}.apk"
    matches = [asset for asset in release["assets"] if asset["name"] == asset_name]
    require(len(matches) == 1, "Release must contain exactly one matching APK asset")
    asset = matches[0]
    require(asset["size"] <= 30_000_000, "APK exceeds repository size limit")
    url = urllib.parse.urlsplit(asset["browser_download_url"])
    require(url.scheme == "https" and url.netloc == "github.com" and url.path.startswith(f"/{repository}/releases/download/"), "Unexpected APK location")
    digest = asset.get("digest", "")
    require(re.fullmatch(r"sha256:[a-f0-9]{64}", digest) is not None, "GitHub asset must have a SHA-256 digest")
    commit = api(repository, "commits/" + urllib.parse.quote(tag, safe=""))["sha"]
    require(re.fullmatch(r"[a-f0-9]{40}", commit) is not None, "Invalid release commit")
    args.work.mkdir(parents=True, mode=0o700)
    repo = args.work / "repo"
    repo.mkdir()
    apk = repo / asset_name
    apk.write_bytes(download(asset["browser_download_url"], limit=30_000_000))
    require(sha256(apk.read_bytes()) == digest.split(":", 1)[1], "GitHub APK digest mismatch")
    require(apk.stat().st_size == asset["size"], "GitHub APK size mismatch")
    published = dt.datetime.fromisoformat(release["published_at"].replace("Z", "+00:00")).timestamp()
    os.utime(apk, (published, published))
    sdk = Path(os.environ["ANDROID_HOME"])
    tools = sdk / "build-tools/36.0.0"
    info = inspect_apk(apk, tools, settings)
    require(info["versionName"] == tag.removeprefix("v"), "Release tag does not match APK version")
    info.update(apkSha256=sha256(apk.read_bytes()), commit=commit)
    locale = args.work / "metadata" / settings["applicationId"] / "en-US"
    locale.mkdir(parents=True)
    copy_release_metadata(repository, commit, locale)
    shutil.copy2(locale / "images/icon.png", args.work / "repo-icon.png")
    app = {"Categories": ["Time"], "License": "Apache-2.0", "AuthorName": "Ahnaf Hasan Shafin", "SourceCode": f"https://github.com/{repository}", "IssueTracker": f"https://github.com/{repository}/issues", "AutoName": (locale / "title.txt").read_text(encoding="utf-8").strip(), "CurrentVersion": info["versionName"], "CurrentVersionCode": info["versionCode"], "AllowedAPKSigningKeys": [settings["apkCertificateSha256"]]}
    (args.work / "metadata" / f"{settings['applicationId']}.yml").write_text(yaml.safe_dump(app, sort_keys=False), encoding="utf-8")
    keystore = args.work / "repository-key.p12"
    key_data = base64.b64decode(os.environ["FDROID_REPO_KEYSTORE_BASE64"], validate=True)
    with keystore.open("xb") as stream:
        stream.write(key_data)
    keystore.chmod(0o600)
    certificate = checked_output(["keytool", "-exportcert", "-keystore", str(keystore.resolve()), "-alias", settings["repoKeyAlias"], "-storepass:env", "FDROID_REPO_PASSWORD"])
    require(sha256(certificate).upper() == settings["repoCertificateSha256"], "Repository key does not match pinned identity")
    config = {"sdk_path": str(sdk), "repo_url": settings["repoUrl"], "repo_name": settings["repoName"], "repo_description": settings["repoDescription"], "repo_icon": "repo-icon.png", "repo_keyalias": settings["repoKeyAlias"], "keystore": str(keystore.resolve()), "keystorepass": {"env": "FDROID_REPO_PASSWORD"}, "keypass": {"env": "FDROID_REPO_PASSWORD"}, "archive_older": 0, "repo_maxage": 0}
    config_file = args.work / "config.yml"
    config_file.write_text(yaml.safe_dump(config, sort_keys=False), encoding="utf-8")
    config_file.chmod(0o600)
    subprocess.run(["fdroid", "update", "--pretty"], cwd=args.work, check=True)
    verify_index(repo, settings, info["apkSha256"])
    args.output.mkdir(parents=True)
    public_repo = args.output / "fdroid/repo"
    shutil.copytree(repo, public_repo, copy_function=shutil.copy2)
    for file in args.output.rglob("*"):
        require(not file.is_symlink(), "Public output contains a symbolic link")
        require(file.name not in ("config.yml", "repository-key.p12"), "Private file in public output")
        require(file.suffix.lower() not in (".p12", ".jks", ".keystore", ".pem"), "Private key material in public output")
    make_landing(args.output, public_repo, settings, release, info, locale, index_verified=True)
    print(f"Verified signed repository: {settings['applicationId']} {info['versionName']} / code {info['versionCode']}")
    print("Repository fingerprint: " + settings["repoCertificateSha256"])
    print("Public repository URL: " + settings["repoUrl"])


if __name__ == "__main__":
    main()
