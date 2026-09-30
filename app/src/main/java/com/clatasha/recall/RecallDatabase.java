package com.clatasha.recall;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

final class RecallDatabase extends SQLiteOpenHelper {
    static final class Entry {
        final long id, time;
        final String fingerprint, app, sender, text;
        final boolean archived, hasImage;

        Entry(long id, String fingerprint, String app, String sender, String text, long time, boolean archived, boolean hasImage) {
            this.id = id; this.fingerprint = fingerprint; this.app = app;
            this.sender = sender; this.text = text; this.time = time; this.archived = archived; this.hasImage = hasImage;
        }
    }

    RecallDatabase(Context context) { super(context, "recall.db", null, 3); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, fingerprint TEXT NOT NULL UNIQUE, "
                + "app TEXT NOT NULL, sender TEXT NOT NULL, body TEXT NOT NULL, event_time INTEGER NOT NULL, "
                + "archived INTEGER NOT NULL DEFAULT 0, image BLOB)");
        db.execSQL("CREATE INDEX entries_time ON entries(event_time DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2 && newVersion >= 2) {
            db.execSQL("ALTER TABLE entries ADD COLUMN archived INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE entries ADD COLUMN image BLOB");
    }

    synchronized boolean save(String fingerprint, String app, String sender, String body, long time, byte[] image) {
        // A messaging app may repost a notification with a new timestamp.
        try (Cursor recent = getReadableDatabase().rawQuery(
                "SELECT id, image FROM entries WHERE app=? AND sender=? AND body=? "
                + "AND event_time BETWEEN ? AND ? ORDER BY id DESC",
                new String[]{app, sender, body, Long.toString(time - 3000),
                        Long.toString(time + 3000)})) {
            while (recent.moveToNext()) {
                byte[] previous = recent.isNull(1) ? null : recent.getBlob(1);
                if (java.util.Arrays.equals(previous, image) || image == null) return false;
                if (previous == null) {
                    ContentValues attachment = new ContentValues();
                    attachment.put("image", image);
                    getWritableDatabase().update("entries", attachment, "id=?",
                            new String[]{Long.toString(recent.getLong(0))});
                    return true;
                }
            }
        }
        ContentValues row = values(fingerprint, app, sender, body, time, false);
        row.put("image", image);
        return getWritableDatabase().insertWithOnConflict(
                "entries", null, row, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    synchronized List<Entry> search(String query) {
        List<Entry> entries = new ArrayList<>();
        String like = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String clause = query.isEmpty() ? null : "(app LIKE ? ESCAPE '\\' OR sender LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\')";
        String[] args = query.isEmpty() ? null : new String[]{like, like, like};
        try (Cursor cursor = getReadableDatabase().query("entries",
                new String[]{"id", "fingerprint", "app", "sender", "body", "event_time", "archived", "image IS NOT NULL"},
                clause, args, null, null, "archived ASC, event_time DESC, id DESC", "500")) {
            while (cursor.moveToNext()) {
                entries.add(new Entry(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getString(4), cursor.getLong(5), cursor.getInt(6) == 1, cursor.getInt(7) == 1));
            }
        }
        return entries;
    }

    synchronized void setArchived(long id, boolean archived) {
        ContentValues row = new ContentValues();
        row.put("archived", archived ? 1 : 0);
        getWritableDatabase().update("entries", row, "id=?", new String[]{Long.toString(id)});
    }

    synchronized void delete(long id) {
        getWritableDatabase().delete("entries", "id=?", new String[]{Long.toString(id)});
    }

    synchronized void restore(Entry entry, byte[] image) {
        ContentValues row = values(entry.fingerprint, entry.app, entry.sender,
                entry.text, entry.time, entry.archived);
        row.put("id", entry.id);
        row.put("image", image);
        getWritableDatabase().insertWithOnConflict("entries", null, row, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private ContentValues values(String fingerprint, String app, String sender,
                                 String body, long time, boolean archived) {
        ContentValues row = new ContentValues();
        row.put("fingerprint", fingerprint); row.put("app", app); row.put("sender", sender);
        row.put("body", body); row.put("event_time", time); row.put("archived", archived ? 1 : 0);
        return row;
    }

    synchronized void removeOldReposts() {
        getWritableDatabase().execSQL(
                "DELETE FROM entries WHERE EXISTS (SELECT 1 FROM entries AS previous "
                + "WHERE previous.id < entries.id AND previous.app=entries.app "
                + "AND previous.sender=entries.sender AND previous.body=entries.body "
                + "AND ABS(previous.event_time - entries.event_time) <= 3000)");
    }

    synchronized int cleanup(int days) {
        if (days <= 0) return 0;
        long cutoff = System.currentTimeMillis() - days * 86400000L;
        return getWritableDatabase().delete("entries", "archived=0 AND event_time<?",
                new String[]{Long.toString(cutoff)});
    }

    synchronized List<Entry> allForExport() {
        List<Entry> entries = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("entries",
                new String[]{"id", "fingerprint", "app", "sender", "body", "event_time", "archived", "image IS NOT NULL"},
                null, null, null, null, "event_time ASC, id ASC")) {
            while (cursor.moveToNext()) {
                entries.add(new Entry(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getString(4), cursor.getLong(5), cursor.getInt(6) == 1, cursor.getInt(7) == 1));
            }
        }
        return entries;
    }

    synchronized byte[] image(long id) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT image FROM entries WHERE id=?", new String[]{Long.toString(id)})) {
            return cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getBlob(0) : null;
        }
    }

    synchronized void clear() { getWritableDatabase().delete("entries", null, null); }
}
