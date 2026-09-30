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
import java.util.List;

final class NotificationImages {
    static final int MAX_BYTES = 256 * 1024;
    static boolean enabled(Context context) {
        return context.getSharedPreferences("recall_settings", Context.MODE_PRIVATE)
                .getBoolean("save_images", true);
    }

    static byte[] capture(Context context, Notification notification) {
        if (!enabled(context)) return null;
        try {
            Notification.MessagingStyle style =
                    Notification.MessagingStyle.extractMessagingStyleFromNotification(notification);
            if (style != null) {
                List<Notification.MessagingStyle.Message> messages = style.getMessages();
                // Only inspect the latest message, so an old attachment isn't paired with new text.
                if (!messages.isEmpty()) {
                    Notification.MessagingStyle.Message message = messages.get(messages.size() - 1);
                    String mime = message.getDataMimeType();
                    Uri uri = message.getDataUri();
                    if (mime != null && mime.startsWith("image/") && uri != null
                            && "content".equals(uri.getScheme())) {
                        byte[] image = fromUri(context, uri);
                        if (image != null) return image;
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
