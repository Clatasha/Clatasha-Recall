# Clatasha Recall roadmap

## Milestone 1 — Notification history

1. Set up a current Android Studio project with a unique `com.clatasha.recall` application ID.
2. Give a clear on-device privacy explanation and link to Android's notification access settings.
3. Capture new notification posts for explicitly selected apps. Read the notification title, text, big text and message-style entries when available.
4. Persist records in app-private storage; deduplicate notification updates without losing distinct messages.
5. Present an app list, conversation list and chronological history with search.
6. Provide delete, retention and listener health controls.
7. Test on recent Android versions and with WhatsApp, Signal, Telegram and a generic messaging app.

## Milestone 2 — Refinement

- Optional notification about *possible* message deletion, with conservative detection and clear uncertainty.
- Private export/import, app lock and accessibility review.
- Evaluate downloaded media support separately. Avoid promising media recovery until it works reliably on current Android releases.
- Ads only after capture and privacy behavior are verified. Notification text and sender names must not be sent to an ad SDK or used for ad targeting.

## Findings from WhatsDeleted

- Upstream repository: https://github.com/TheCosmoNomad/WhatsDeleted
- GPLv3; source reuse requires preserving the applicable license and attribution when distributed.
- Version 0.4.1 uses `compileSdkVersion 28`, `targetSdkVersion 28` and `kotlin-android-extensions`.
- Its README says the listener logs new messages and a media observer watches images; it cannot save messages received only in an open chat, and image saving depends on downloaded files.
- Open issues mention media observation, Android 11 file observation, log export, conversation selection and support for more apps.

## Product rules

- Store content locally by default. No account or server required.
- Ask users to select the apps whose notifications are retained.
- Never claim to restore old messages or infer deletion solely from notification removal.
- Make the retention and delete controls straightforward, because notification data can be sensitive.
