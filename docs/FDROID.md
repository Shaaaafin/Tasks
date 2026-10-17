# Publishing on F-Droid

No personal server is required for inclusion in the official F-Droid repository. Publish the source in a public Git repository; F-Droid reviews the application, builds it, and hosts accepted releases. Task synchronization remains optional and uses each user's CalDAV account.

The source currently identifies the app as Tasks, package `dev.quiettasks`, version `0.2.0`, version code `2`. It includes the latest scrolling, press feedback, widget hierarchy, and shaded-theme changes. The application has not been submitted to or accepted by F-Droid.

## 1. Publish the complete source on GitHub

1. Use the public repository [Shaaaafin/Tasks](https://github.com/Shaaaafin/Tasks).
2. Extract the source ZIP. Its root should contain `README.md`, `LICENSE`, `app/`, `gradle/`, `.github/`, and the Gradle wrapper files.
3. Upload the extracted contents, including hidden files, rather than the ZIP itself or an extra enclosing folder. GitHub Desktop or Git is suitable when a browser upload omits hidden files. See [GITHUB.md](GITHUB.md).
4. Wait for the included Android workflow to run and review its result. Local validation does not establish that GitHub-hosted CI has passed.

Keep local SDKs, build output, keystores, passwords, and `.tools/` out of the repository. These are excluded from the prepared source ZIP.

## 2. Complete the store information

The Apache 2.0 license, attribution, current icon, descriptions, and version-code changelog are included. Choose the intended public display name before tagging the release if a change is planned. The existing name can also be retained.

Eight portrait feature cards are included in `fastlane/metadata/android/en-US/images/phoneScreenshots/`, along with a landscape feature graphic in the parent directory. They use actual native app renders with sample data and feature captions; they do not claim physical-device testing. Review [the gallery and provenance](store-assets/README.md). Device captures may be added or substituted using [SCREENSHOTS.md](SCREENSHOTS.md).

Keep the short description below 80 characters and each version-code changelog at or below 500 characters. Review component licenses and the current inclusion policy.

## 3. Tag the release source

After the source, metadata, screenshots, and checks are ready, create a GitHub release with the tag `v0.2.0`, targeting that exact commit. The current application version remains unchanged at `0.2.0` / code `2`. Future published updates need a higher version code.

Record the full commit SHA behind the tag. The F-Droid build recipe should pin that full SHA. Release tags must remain attached to the source they identify.

The signed APK and its checksum may be attached as release assets. Do not commit APKs or signing keys to the source tree.

## 4. Finalize and verify the F-Droid recipe

`packaging/fdroid/dev.quiettasks.yml.example` is a disabled template with the public repository URLs configured. Replace `RELEASE_COMMIT_SHA` with the full release commit SHA. Copy it into the F-Droid metadata repository as `metadata/dev.quiettasks.yml`.

The project uses JDK 21, Gradle 9.5.0, Android Gradle Plugin 9.3.0, Android SDK Platform 37, and Build Tools 36.0.0. The F-Droid build environment must provide compatible versions. The extracted public source built successfully on Windows without private signing files; see [the validation record](validation/fdroid-source-export-0.2.0.json). F-Droid's Linux build environment and scanner remain unverified.

In a fork of `fdroiddata`, use the current F-Droid tooling to format, lint, scan, and build the completed recipe. Remove the `disable` entry when the repository, pinned commit, and build configuration are ready for that verification. Enable tag-based update checking after confirming how the published tags are structured.

Typical metadata checks inside a prepared F-Droid environment are:

```sh
fdroid rewritemeta dev.quiettasks
fdroid lint dev.quiettasks
fdroid build dev.quiettasks
```

Do not add scanner exclusions merely to hide a failing dependency check; identify the cause and fix the build or dependency.

## 5. Choose signing and submit

Decide the signing arrangement before the first F-Droid release. F-Droid can normally sign its own build. Retaining the existing developer signature requires a successful reproducibility check against the published signed APK. Reproducibility has not been established for this project. APKs signed with different keys cannot normally update one another in place. Keep the existing private key backed up and never publish it.

Submit a tested recipe as a merge request to `fdroiddata`, or open a Request for Packaging and provide the public repository, tagged release, license, and build information. `packaging/fdroid/REQUEST_FOR_PACKAGING.md` contains an editable draft; adapt it to the current tracker template. Respond to reviewer questions. Inclusion and publication depend on F-Droid's review and build results.

## Included preparation files

| Path | Purpose |
| --- | --- |
| `LICENSE`, `NOTICE.md` | Project license and component attribution |
| `fastlane/metadata/android/en-US/` | Title, descriptions, changelogs, icon, and screenshot directory |
| `packaging/fdroid/dev.quiettasks.yml.example` | Repository-configured recipe awaiting a pinned release commit and F-Droid build verification |
| `packaging/fdroid/REQUEST_FOR_PACKAGING.md` | Draft information for the packaging request |
| `docs/VALIDATION.md` | Automated checks and remaining device/build-environment verification |

## Official references

- [Submission guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
- [Inclusion policy](https://f-droid.org/en/docs/Inclusion_Policy/)
- [Build metadata reference](https://f-droid.org/en/docs/Build_Metadata_Reference/)
- [Reproducible builds](https://f-droid.org/en/docs/Reproducible_Builds/)
- [Requests for Packaging](https://gitlab.com/fdroid/rfp/-/work_items)
- [F-Droid application metadata](https://gitlab.com/fdroid/fdroiddata)
