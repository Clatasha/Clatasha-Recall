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
        final String app, sender, text;
        final long time;
        Entry(String app, String sender, String text, long time) {
            this.app = app; this.sender = sender; this.text = text; this.time = time;
        }
    }

    RecallDatabase(Context context) { super(context, "recall.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, fingerprint TEXT NOT NULL UNIQUE, app TEXT NOT NULL, sender TEXT NOT NULL, body TEXT NOT NULL, event_time INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX entries_time ON entries(event_time DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Database migration required");
    }

    synchronized void save(String fingerprint, String app, String sender, String body, long time) {
        ContentValues row = new ContentValues();
        row.put("fingerprint", fingerprint); row.put("app", app); row.put("sender", sender);
        row.put("body", body); row.put("event_time", time);
        // Messaging apps can repost the same notification with a new post time.
        // Keep identical messages sent later, while collapsing immediate reposts.
        try (Cursor recent = getReadableDatabase().rawQuery(
                "SELECT 1 FROM entries WHERE app=? AND sender=? AND body=? "
                + "AND event_time BETWEEN ? AND ? LIMIT 1",
                new String[]{app, sender, body, Long.toString(time - 3000),
                        Long.toString(time + 3000)})) {
            if (recent.moveToFirst()) return;
        }
        getWritableDatabase().insertWithOnConflict("entries", null, row, SQLiteDatabase.CONFLICT_IGNORE);
    }

    synchronized List<Entry> search(String query) {
        List<Entry> entries = new ArrayList<>();
        String like = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String clause = query.isEmpty() ? null : "(app LIKE ? ESCAPE '\\' OR sender LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\')";
        String[] args = query.isEmpty() ? null : new String[]{like, like, like};
        try (Cursor cursor = getReadableDatabase().query("entries",
                new String[]{"app", "sender", "body", "event_time"}, clause, args,
                null, null, "app ASC, sender ASC, event_time DESC, id DESC", "500")) {
            while (cursor.moveToNext()) {
                entries.add(new Entry(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3)));
            }
        }
        return entries;
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
