# Tasks app repository

The author-maintained F-Droid repository is published with GitHub Pages:

- Website: https://Shaaaafin.github.io/Tasks/
- Repository: https://Shaaaafin.github.io/Tasks/fdroid/repo
- Repository certificate SHA-256: `88D8C8D601561E730FEE320964AED6CA56E71EC8A84FA2446ABE11F3183A1652`

This is an independent repository maintained by Ahnaf Hasan Shafin. It is separate from the official f-droid.org catalog and IzzyOnDroid. It serves the same developer-signed APK as the GitHub release; it does not re-sign the app with a different application key.

## Install and update

Open F-Droid, go to Settings → Repositories → Add repository, and paste:

```text
https://Shaaaafin.github.io/Tasks/fdroid/repo?fingerprint=88D8C8D601561E730FEE320964AED6CA56E71EC8A84FA2446ABE11F3183A1652
```

Alternatively, use the website's Add to F-Droid button or scan its QR code in F-Droid. Refresh repositories, search for Tasks, and install it. Updates can replace the existing developer-signed installation because the package identifier and application signing key are retained. Android still requires a higher version code for future updates.

## Publishing updates

1. Build and validate the intended release. Increase the application version code for every new public update.
2. Retain package `dev.quiettasks` and its original application signing key.
3. Commit the corresponding source and Fastlane metadata, then tag the release, such as `v0.3.0`.
4. Publish a stable GitHub release with an APK named `Tasks-0.3.0.apk`. Its version name must match the tag without the leading `v`.
5. The Publish F-Droid repository workflow downloads that release, imports its store metadata from the tagged commit, verifies the APK, generates and verifies the signed index, and deploys only the public site to Pages.

The workflow can also be started manually. Leave the release tag blank to publish the latest stable release. Prereleases and drafts are excluded. The simple feed publishes one stable APK at a time. Removing an old version from the feed does not uninstall it from devices. No periodic polling or additional background work is added to the Tasks app.

## Signing and private files

The application signing key and repository signing key have different purposes:

- The existing application key identifies APK updates. It remains local and is not used by this workflow.
- A separate RSA 4096-bit repository key signs the F-Droid index. Its certificate fingerprint is pinned in `packaging/repository/settings.json`.

The workflow reads two encrypted GitHub Actions secrets: `FDROID_REPO_KEYSTORE_BASE64` and `FDROID_REPO_PASSWORD`. Only the trusted publisher job receives them. The key and generated private configuration stay outside the Pages artifact. The publisher rejects a repository key that does not match the pinned fingerprint and an APK that does not match the original application certificate.

The initial private repository identity is backed up locally under the ignored `.tools/fdroid-repository/` directory as `repository-key.p12` and `repository-password.txt`. Keep a private backup of both files. Do not upload that directory or either file to Git, Releases, Pages, or a public support request. Do not regenerate the key when refreshing the index; changing it changes the repository's identity.

GitHub Pages deploys only the generated public directory. Keystores, passwords, private configuration, SDKs, and application build directories are excluded.

## Validation

Publisher tests cover metadata extraction boundaries, path traversal, executable metadata, incorrect package IDs or signing keys, and debug/test APK rejection. Every deployment also verifies the entry JAR signature against the pinned repository certificate, the v2 index digest, the APK digest and signer, and the v1 index signature. Public verification details are available at `repository.json` on the website.

Physical-device F-Droid installation is a separate check from serving and cryptographically verifying the repository. App behavior, Android 17/CalyxOS frame timing, real-server interoperability, and measured battery use retain the limitations documented in [VALIDATION.md](VALIDATION.md).

References: [F-Droid repository setup](https://f-droid.org/en/docs/Setup_an_F-Droid_App_Repo/) and [GitHub Pages workflows](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages).
