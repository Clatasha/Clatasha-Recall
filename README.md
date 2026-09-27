# Clatasha Recall

A private, on-device Android notification history app. Clatasha Recall saves notification content after the user grants notification access and organizes saved messages by app and conversation.

## Current status

Project setup and Android implementation are in progress. There is no installable APK yet.

## First release

- Capture new notifications from selected apps with Android's `NotificationListenerService`.
- Store text, sender/conversation, app, and time in a local database.
- Group entries by app and conversation; search and filter the history.
- Explain when notification access is disconnected and provide a test notification.
- Let the user delete entries and set an automatic retention period.

A saved notification is a historical copy, not proof that its original message was deleted. Content hidden by the source app or received before access was granted cannot be recovered. Media saving, export, app lock, and ads will be evaluated after reliable text capture.

## Development approach

This is a new codebase inspired by [WhatsDeleted](https://github.com/TheCosmoNomad/WhatsDeleted). No WhatsDeleted source has been copied. WhatsDeleted targets Android 9 and uses older Android tooling and storage methods; its notification and media behavior will be studied as reference. If code is incorporated later, we will retain attribution and comply with its GPLv3 license.

See [the initial roadmap](docs/ROADMAP.md) for milestones and product decisions.
