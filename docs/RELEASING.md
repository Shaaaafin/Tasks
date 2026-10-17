# Releases

## Source preparation

1. Complete the intended changes. The current first-publication candidate remains `0.2.0` / code `2`. For subsequent public updates, increase `versionCode` and set the intended `versionName` in `app/build.gradle.kts`.
2. Update `CHANGELOG.md` and the store descriptions. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`; F-Droid limits this file to 500 characters.
3. Add the final icon and device screenshots using [SCREENSHOTS.md](SCREENSHOTS.md).
4. Run the build, automated tests, and lint checks described in the README. Review notification timing, widgets, synchronization, and both themes on the intended devices.
5. Commit the release source and create a version tag such as `v0.2.0` for this candidate. Tags must identify the exact source used for the APK.

The latest 0.2.0 source export includes repository preparation and the current application fixes. Finalize publication metadata and verify the release commit before tagging it. A GitHub release and an F-Droid listing have not yet been published.

## Unsigned build

On a checkout without local signing configuration:

```sh
./gradlew :app:assembleRelease --no-configuration-cache
```

The unsigned APK is written to `app/build/outputs/apk/release/`. CI does not require a signing key.

## Signing

The build optionally reads `.tools/local-signing.properties`. A template is provided in [build-support/local-signing.properties.example](../build-support/local-signing.properties.example). Keep the configured keystore and properties file outside version control and maintain a private backup.

For the original local installation, retain the existing package identifier `dev.quiettasks` and its existing signing key. Changing either prevents a normal in-place update. Changing the display name or launcher icon does not require changing the package identifier.

Build a signed release only after configuring the intended key:

```sh
./gradlew :app:assembleRelease --no-configuration-cache
```

Verify the output with Android Build Tools:

```sh
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

## GitHub publication

Publish the release tag and attach the signed APK and a SHA-256 checksum to the GitHub release. Keep binaries out of the source tree. Review the release notes against the actual build.

`scripts/export-repository.ps1` creates a clean source directory and ZIP for uploading. It excludes local SDKs, JDKs, signing files, build output, APKs, and downloaded reference images. Review the exported files before uploading.

## F-Droid

Follow [FDROID.md](FDROID.md). Decide on the distribution signing arrangement before the first published release. Different signing keys across channels affect whether users can install one channel's update over the other. Reproducible-build verification has not yet been established for this project.
