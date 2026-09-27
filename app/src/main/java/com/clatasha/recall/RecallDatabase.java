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
        final boolean archived;

        Entry(long id, String fingerprint, String app, String sender, String text, long time, boolean archived) {
            this.id = id; this.fingerprint = fingerprint; this.app = app;
            this.sender = sender; this.text = text; this.time = time; this.archived = archived;
        }
    }

    RecallDatabase(Context context) { super(context, "recall.db", null, 2); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, fingerprint TEXT NOT NULL UNIQUE, "
                + "app TEXT NOT NULL, sender TEXT NOT NULL, body TEXT NOT NULL, event_time INTEGER NOT NULL, "
                + "archived INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX entries_time ON entries(event_time DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1 && newVersion >= 2) {
            db.execSQL("ALTER TABLE entries ADD COLUMN archived INTEGER NOT NULL DEFAULT 0");
        }
    }

    synchronized boolean save(String fingerprint, String app, String sender, String body, long time) {
        // A messaging app may repost a notification with a new timestamp.
        try (Cursor recent = getReadableDatabase().rawQuery(
                "SELECT 1 FROM entries WHERE app=? AND sender=? AND body=? "
                + "AND event_time BETWEEN ? AND ? LIMIT 1",
                new String[]{app, sender, body, Long.toString(time - 3000),
                        Long.toString(time + 3000)})) {
            if (recent.moveToFirst()) return false;
        }
        ContentValues row = values(fingerprint, app, sender, body, time, false);
        return getWritableDatabase().insertWithOnConflict(
                "entries", null, row, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    synchronized List<Entry> search(String query) {
        List<Entry> entries = new ArrayList<>();
        String like = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String clause = query.isEmpty() ? null : "(app LIKE ? ESCAPE '\\' OR sender LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\')";
        String[] args = query.isEmpty() ? null : new String[]{like, like, like};
        try (Cursor cursor = getReadableDatabase().query("entries",
                new String[]{"id", "fingerprint", "app", "sender", "body", "event_time", "archived"},
                clause, args, null, null, "archived ASC, event_time DESC, id DESC", "500")) {
            while (cursor.moveToNext()) {
                entries.add(new Entry(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getString(4), cursor.getLong(5), cursor.getInt(6) == 1));
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

    synchronized void restore(Entry entry) {
        ContentValues row = values(entry.fingerprint, entry.app, entry.sender,
                entry.text, entry.time, entry.archived);
        row.put("id", entry.id);
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

    synchronized void clear() { getWritableDatabase().delete("entries", null, null); }
}
