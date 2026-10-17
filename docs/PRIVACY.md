# Privacy policy

Last updated: 17 October 2026.

## Local information

Tasks, lists, tags, priorities, reminders, and application preferences are stored in the application's private storage on the device. The application does not include advertising, analytics, or a crash-reporting service. It does not require a Google account or Google Play Services.

CalDAV is optional. Local task management remains available without an account or network connection.

## Optional synchronization

When an account is configured, the application sends task content and calendar metadata to the HTTPS CalDAV server selected by the user. This includes task titles, notes, dates, completion, recurrence, hierarchy, tags, priority, and ordering information. Authentication uses the account credentials supplied by the user.

The selected server and its operator can access the information transmitted to that server. HTTPS protects transport; the application does not provide end-to-end encryption of CalDAV task content. The server operator's own privacy and retention policies apply.

Automatic synchronization follows the selected mode: while the application is visible, only while charging, or manually. The Wi-Fi restriction applies to synchronization when enabled. Selecting Sync now is an explicit request to synchronize while the app is visible.

The application does not send task data to an application-operated service. Its only configured application-level network destination is the selected CalDAV origin.

## Credentials

The account password or app password is encrypted in local storage using a key held by Android Keystore. Android's security model protects that key; this is not a guarantee against a compromised device.

## Permissions and notifications

| Permission | Purpose |
| --- | --- |
| Internet and network state | Connect to the selected CalDAV server and apply connectivity restrictions |
| Notifications | Display task reminders and deadlines when allowed |
| Receive boot completed | Restore reminder scheduling after a restart |
| Schedule exact alarms | Improve reminder timing when separately permitted by Android |

Task titles may appear in notifications and in widgets placed on the home screen. Android and launcher settings determine where that information is visible.

## Removal and retention

Deleting a synchronized task queues a deletion for the next successful synchronization. A conflict may retain a local copy to prevent data loss. Deleting a list removes its known tasks during synchronization but retains the empty server collection.

Disconnecting an account keeps local tasks and removes the configured credentials. Uninstalling the application removes its private local data. Server copies remain subject to the server's retention policy and must be removed through the server or a synchronized client when necessary.

Android cloud backup for application data is disabled. The application does not currently provide a separate backup and restore interface.

## Questions

Privacy questions can be directed to the repository maintainer. Use [SECURITY.md](../SECURITY.md) for reports involving sensitive information.
