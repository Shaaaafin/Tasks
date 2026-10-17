# Publishing the repository on GitHub

## Prepared source

Run the repository export utility from PowerShell:

```powershell
.\scripts\export-repository.ps1
```

The default outputs are `dist/github-ready/` and `dist/Tasks-github-ready.zip`. The exported directory contains the public source at its root. Local SDKs, signing keys, APKs, build caches, and downloaded reference images are excluded.

The utility refuses existing output paths. For another export, provide new paths with `-OutputDirectory` and `-ZipPath`, both inside `dist/`.

## Uploading

The public repository is [Shaaaafin/Tasks](https://github.com/Shaaaafin/Tasks). Update that existing repository so its history is retained.

For a browser upload, extract the ZIP and upload its contents rather than an enclosing directory or the ZIP file itself. Include the hidden `.github`, `.gitignore`, `.gitattributes`, and `.editorconfig` entries. Use a Git client if the browser does not expose those files reliably.

With GitHub Desktop, clone `Shaaaafin/Tasks`, copy the complete exported source into that checkout, review the changes, commit them, and push to `main`. Include folders as well as the top-level files. The repository root must contain `app/`, `gradle/`, `.github/`, `docs/`, `fastlane/`, and the other exported directories. Uploading the Gradle scripts alone does not provide the application source.

Using Git, clone the existing repository first:

```sh
git clone https://github.com/Shaaaafin/Tasks.git
cd Tasks
```

Copy the extracted source contents into this checkout, then run:

```sh
git add .
git update-index --chmod=+x gradlew
git commit -m "Publish complete application source and store assets"
git push -u origin main
```

Configure a Git author identity if Git requests one. Review the staged files before pushing; the clean export excludes local signing files and development SDKs.

## Repository settings

The included Android workflow runs on pushes, pull requests, and manual dispatch. It uses JDK 21 and installs the required Android platform. Its first execution on GitHub still needs to be reviewed; local verification does not execute GitHub-hosted CI.

Enable private vulnerability reporting if it is appropriate for the repository. The contribution, security, and issue-template documents provide the corresponding reporting guidance.

The prepared screenshots are documented in [SCREENSHOTS.md](SCREENSHOTS.md). Before F-Droid submission, pin the release commit in the repository-configured recipe template and complete [FDROID.md](FDROID.md).

The provisional name remains Tasks. The launcher now uses the supplied dot-and-bars logo; see [branding assets](BRANDING.md). The [Gemini branding brief](BRANDING_PROMPT.md) can be used to explore alternatives.
