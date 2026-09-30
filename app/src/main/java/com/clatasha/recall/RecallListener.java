package com.clatasha.recall;

import android.app.Notification;
import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RecallListener extends NotificationListenerService {
    static final String PREFS = "capture";
    static final String SELECTED = "selected_apps";
    static final String ACTION_HISTORY_CHANGED = "com.clatasha.recall.HISTORY_CHANGED";
    private final ExecutorService writer = Executors.newSingleThreadExecutor();

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        String app = sbn.getPackageName();
        if (getPackageName().equals(app)) return;
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> selected = preferences.getStringSet(SELECTED, java.util.Collections.emptySet());
        if (!selected.contains(app)) return;
        Notification notification = sbn.getNotification();
        if ((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0) return;
        Bundle extras = notification.extras;
        if (extras == null) return;
        String title = clean(extras.getCharSequence(Notification.EXTRA_TITLE));
        String body = clean(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (body.isEmpty()) body = clean(extras.getCharSequence(Notification.EXTRA_TEXT));

        long time = sbn.getPostTime();
        String sender = title.isEmpty() ? app : title;
        final String savedBody = body;
        writer.execute(() -> {
            byte[] image = NotificationImages.capture(getApplicationContext(), notification);
            if (savedBody.isEmpty() && image == null) return;
            String text = savedBody.isEmpty() ? "Image" : savedBody;
            String fingerprint = digest(app + "\u0000" + sender + "\u0000" + text + "\u0000"
                    + time + "\u0000" + java.util.Arrays.hashCode(image));
            try (RecallDatabase db = new RecallDatabase(getApplicationContext())) {
            if (db.save(fingerprint, app, sender, text, time, image)) {
                sendBroadcast(new Intent(ACTION_HISTORY_CHANGED).setPackage(getPackageName()));
            }
            }
        });
    }

    private static String clean(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public void onDestroy() {
        writer.shutdown();
        super.onDestroy();
    }
}
