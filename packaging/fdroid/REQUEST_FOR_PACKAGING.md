# Request for Packaging draft

Resolve the release commit placeholder and use the current F-Droid tracker template when submitting. This file is a draft; it has not been sent to F-Droid.

- Application name: Tasks
- Application ID: `dev.quiettasks`
- Author: Ahnaf Hasan Shafin
- License: Apache-2.0
- Source: https://github.com/Shaaaafin/Tasks
- Issue tracker: https://github.com/Shaaaafin/Tasks/issues
- Release tag: `v0.2.0`
- Release commit: RELEASE_COMMIT_SHA
- Current version: `0.2.0`, version code `2`
- Category: Time

Tasks is a native Kotlin and Jetpack Compose task manager. It works offline and offers optional HTTPS CalDAV synchronization with a user-selected server. It supports lists, subtasks, recurrence, reminders, tags, priorities, My order, archive, light/shaded/AMOLED themes, and home-screen widgets. Synchronization can be limited to foreground use, charging, or manual requests. No Google account, Google Play Services, ads, or analytics are required.

The repository includes an Apache 2.0 license, component attribution, Fastlane store metadata, an original vector launcher icon, eight portrait feature cards, a landscape feature graphic, and a disabled F-Droid recipe template under `packaging/fdroid/`. The gallery combines actual native app renders with sample data and feature captions; it does not claim physical-device capture. Image provenance and editable sources are in `docs/store-assets/`.

Build requirements are JDK 21, Gradle 9.5.0, Android Gradle Plugin 9.3.0, Android SDK Platform 37, and Build Tools 36.0.0. A release build without private signing configuration is produced by:

```sh
chmod +x gradlew
./gradlew :app:assembleRelease --no-daemon --no-configuration-cache
```

The latest application changes passed 83 automated tests, Android lint with no errors, and the local signed release build. The exported public source also built successfully on Windows without private signing files, producing an unsigned release APK. Native UI checks use Robolectric API 35. Physical Android 17/CalyxOS testing, F-Droid build-environment verification, and reproducibility verification are still pending. See `docs/VALIDATION.md` for the detailed record.

The upstream author is requesting inclusion. Please review the source, dependency licenses, build recipe, and signing arrangement. No personal server is required for offline task management or app distribution through the official F-Droid repository.
