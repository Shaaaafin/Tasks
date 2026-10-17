# Contributing

Contributions should preserve the application's offline operation, explicit synchronization controls, and compatibility with Android installations without Google Play Services.

## Reporting an issue

Include the application version, Android version, device model, steps to reproduce, expected behavior, and actual behavior. For synchronization defects, identify the server software and its version if known. Remove account names, task content, server credentials, and other private information from logs and screenshots.

Use the security reporting process in [SECURITY.md](SECURITY.md) for vulnerabilities.

## Development

Install JDK 21, Android SDK Platform 37, and Build Tools 36.0.0. Use the checked-in Gradle wrapper. Configuration instructions are in [README.md](README.md).

Run the following checks before submitting a change:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-configuration-cache
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

Add tests for changes to synchronization, persistence, migrations, recurrence, reminders, or other behavior where a regression could affect user data. UI changes should be reviewed in both light and dark themes and with increased system font size.

## Style and scope

- Follow the Kotlin official code style and the repository's EditorConfig settings.
- Use concise, formal technical comments. Explain decisions and constraints rather than repeating the implementation.
- Keep pull requests focused and describe the resulting behavior and relevant validation.
- Preserve unknown iCalendar data when editing imported tasks.
- Do not introduce polling, persistent services, wake locks, telemetry, or proprietary runtime dependencies without an explicit design discussion.
- Keep credentials, local configuration, generated output, and third-party research images out of commits.
- Preserve existing third-party copyright and license notices.

Contributions are provided under the project's Apache License, Version 2.0, unless an alternative has been agreed in advance.
