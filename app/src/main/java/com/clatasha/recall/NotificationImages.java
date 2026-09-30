package com.clatasha.recall;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

final class NotificationImages {
    static final int MAX_BYTES = 256 * 1024;
    static boolean enabled(Context context) {
        return context.getSharedPreferences("recall_settings", Context.MODE_PRIVATE)
                .getBoolean("save_images", true);
    }

    static byte[] capture(Context context, Notification notification) {
        if (!enabled(context)) return null;
        try {
            android.os.Parcelable[] messages = notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (messages != null && messages.length > 0
                    && messages[messages.length - 1] instanceof android.os.Bundle) {
                android.os.Bundle latest = (android.os.Bundle) messages[messages.length - 1];
                long latestTime = latest.getLong("time", -1);
                // Android stores a caption and image as separate messages sharing a timestamp.
                // Limit the search to that latest timestamp to avoid attaching an old photo.
                for (int i = messages.length - 1; i >= 0; i--) {
                    if (!(messages[i] instanceof android.os.Bundle)) continue;
                    android.os.Bundle message = (android.os.Bundle) messages[i];
                    if (i != messages.length - 1
                            && (latestTime < 0 || message.getLong("time", -2) != latestTime)) break;
                    String mime = message.getString("type");
                    Object value = message.getParcelable("uri");
                    if (mime != null && mime.startsWith("image/") && value instanceof Uri) {
                        Uri uri = (Uri) value;
                        if ("content".equals(uri.getScheme())) {
                            byte[] image = fromUri(context, uri);
                            if (image != null) return image;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        try {
            Object picture = notification.extras.getParcelable(Notification.EXTRA_PICTURE);
            if (picture instanceof Bitmap) return encode((Bitmap) picture);
            if (Build.VERSION.SDK_INT >= 31) {
                Object value = notification.extras.getParcelable(Notification.EXTRA_PICTURE_ICON);
                if (value instanceof Icon) {
                    Drawable drawable = ((Icon) value).loadDrawable(context);
                    if (drawable != null) {
                        int w = Math.max(1, drawable.getIntrinsicWidth());
                        int h = Math.max(1, drawable.getIntrinsicHeight());
                        float scale = Math.min(1f, 1024f / Math.max(w, h));
                        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, (int)(w * scale)),
                                Math.max(1, (int)(h * scale)), Bitmap.Config.ARGB_8888);
                        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                        drawable.draw(new Canvas(bitmap));
                        try { return encode(bitmap); } finally { bitmap.recycle(); }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static byte[] fromUri(Context context, Uri uri) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, options);
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 1024)
            options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(input, null, options);
        }
        if (bitmap == null) return null;
        try { return encode(bitmap); } finally { bitmap.recycle(); }
    }

    private static byte[] encode(Bitmap original) {
        float scale = Math.min(1f, 1024f / Math.max(original.getWidth(), original.getHeight()));
        Bitmap bitmap = Bitmap.createScaledBitmap(original, Math.max(1, (int)(original.getWidth() * scale)),
                Math.max(1, (int)(original.getHeight() * scale)), true);
        try {
            for (int quality : new int[]{85, 65, 45}) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output);
                if (output.size() <= MAX_BYTES) return output.toByteArray();
            }
            return null;
        } finally { if (bitmap != original) bitmap.recycle(); }
    }
}
