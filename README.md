# Clatasha Recall

A private, on-device Android notification history app. Clatasha Recall saves notification content after the user grants notification access and organizes saved messages by app and conversation.

## Current status

A first, untested Android prototype is available. The [Android debug build](https://github.com/Clatasha/Clatasha-Recall/actions/workflows/android.yml) uploads an APK artifact after each successful build. This is for device testing, not a public release. There is no media recovery, deletion detection or ad network integration yet. The two bundled banners are local placement demos, and users can turn them off for free. Archiving here is a local saved section; it does not modify the source app.

## Prototype features

- Select installed apps to monitor, grant notification access and capture visible text from new notifications.
- Save text locally in app-private SQLite storage, with search and a delete-all control.
- Show compact app and sender cards that expand to individual notifications.
- Swipe right to archive or restore, and left to delete, with a short Undo action.
- Keep Archive collapsed below saved notifications until tapped.
- Search installed apps in the capture picker and refresh history while the app is open.
- Preview two bundled demo banners, rotating every 20 seconds, with a free Show ads toggle in Settings.
- Collapse immediate notification reposts and clean up existing matching duplicates.
- Use a rewind notification launcher icon.
- Optionally remove unarchived history after 7, 30, 90 or 365 days. Archive is exempt.
- Optionally lock the screen with the phone's biometric or device credential.
- Export all saved and archived entries to a password-encrypted `.recall` file through Android's file picker.

## Planned first release

- Capture new notifications from selected apps with Android's `NotificationListenerService`.
- Store text, sender/conversation, app, and time in a local database.
- Group entries by app and conversation; search and filter the history.
- Explain when notification access is disconnected and provide a test notification.
- Provide listener health and a test notification.

A saved notification is a historical copy, not proof that its original message was deleted. Content hidden by the source app or received before access was granted cannot be recovered. Media saving and deletion detection remain future work. App lock protects the user interface and task preview; the app-private database itself is not encrypted. The exported file is password-encrypted. [Offline decrypt instructions](docs/EXPORT.md) explain how to read it on a computer.

## Development approach

This is a new codebase inspired by [WhatsDeleted](https://github.com/TheCosmoNomad/WhatsDeleted). No WhatsDeleted source has been copied. WhatsDeleted targets Android 9 and uses older Android tooling and storage methods; its notification and media behavior will be studied as reference. If code is incorporated later, we will retain attribution and comply with its GPLv3 license.

See [the initial roadmap](docs/ROADMAP.md) for milestones and product decisions.
