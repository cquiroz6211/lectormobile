package com.example.lectormobile;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * SQLite database helper for bookmarks and reading history.
 * Tables:
 *   - bookmarks (id, book_path, bookmark_value, timestamp, label)
 *   - reading_history (id, book_path, last_read_at, times_read)
 */
public class BookmarksDBHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "lectormobile.db";
    private static final int DATABASE_VERSION = 3;
    private static final String TABLE_BOOKMARKS = "bookmarks";
    private static final String TABLE_HISTORY = "reading_history";
    private static final String TABLE_POSITIONS = "reading_positions";

    private static final String COLUMN_ID = "id";
    private static final String COLUMN_BOOK_PATH = "book_path";
    private static final String COLUMN_BOOKMARK_VALUE = "bookmark_value";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_LABEL = "label";
    private static final String COLUMN_POSITION = "position";
    private static final String COLUMN_UPDATED_AT = "updated_at";
    private static final String COLUMN_LAST_READ = "last_read_at";
    private static final String COLUMN_TIMES_READ = "times_read";

    private static final String CREATE_TABLE_BOOKMARKS =
        "CREATE TABLE IF NOT EXISTS " + TABLE_BOOKMARKS + " (" +
        COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COLUMN_BOOK_PATH + " TEXT NOT NULL, " +
        COLUMN_BOOKMARK_VALUE + " TEXT NOT NULL, " +
        COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
        COLUMN_LABEL + " TEXT)";

    private static final String CREATE_TABLE_HISTORY =
        "CREATE TABLE IF NOT EXISTS " + TABLE_HISTORY + " (" +
        COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COLUMN_BOOK_PATH + " TEXT UNIQUE NOT NULL, " +
        COLUMN_LAST_READ + " INTEGER NOT NULL, " +
        COLUMN_TIMES_READ + " INTEGER DEFAULT 1)";

    private static final String CREATE_TABLE_POSITIONS =
        "CREATE TABLE IF NOT EXISTS " + TABLE_POSITIONS + " (" +
        COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COLUMN_BOOK_PATH + " TEXT UNIQUE NOT NULL, " +
        COLUMN_POSITION + " TEXT NOT NULL, " +
        COLUMN_UPDATED_AT + " INTEGER NOT NULL)";

    public BookmarksDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_BOOKMARKS);
        db.execSQL(CREATE_TABLE_HISTORY);
        db.execSQL(CREATE_TABLE_POSITIONS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Additive migrations: never drop user data.
        db.execSQL(CREATE_TABLE_BOOKMARKS);
        db.execSQL(CREATE_TABLE_HISTORY);
        db.execSQL(CREATE_TABLE_POSITIONS);

        // Older builds stored the reading position as a fake bookmark.
        try {
            db.execSQL("DELETE FROM " + TABLE_BOOKMARKS + " WHERE " + COLUMN_LABEL + " = 'position'");
        } catch (Exception ignored) { }
    }

    // ─── Reading position ───

    public void saveReadingPosition(String bookPath, String position) {
        if (bookPath == null || position == null) return;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_BOOK_PATH, bookPath);
        values.put(COLUMN_POSITION, position);
        values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_POSITIONS, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    public String getReadingPosition(String bookPath) {
        if (bookPath == null) return null;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
            "SELECT " + COLUMN_POSITION + " FROM " + TABLE_POSITIONS +
            " WHERE " + COLUMN_BOOK_PATH + " = ?",
            new String[]{bookPath});
        String position = null;
        if (cursor.moveToFirst()) {
            position = cursor.getString(0);
        }
        cursor.close();
        db.close();
        return position;
    }

    public int deleteReadingPosition(String bookPath) {
        if (bookPath == null) return 0;
        SQLiteDatabase db = this.getWritableDatabase();
        int deleted = db.delete(TABLE_POSITIONS,
                COLUMN_BOOK_PATH + " = ?", new String[]{bookPath});
        db.close();
        return deleted;
    }

    // ─── Bookmark Methods ───

    public long insertBookmark(String bookPath, String bookmarkValue, String label) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_BOOK_PATH, bookPath);
        values.put(COLUMN_BOOKMARK_VALUE, bookmarkValue);
        values.put(COLUMN_TIMESTAMP, System.currentTimeMillis());
        values.put(COLUMN_LABEL, label);
        long id = db.insert(TABLE_BOOKMARKS, null, values);
        db.close();
        return id;
    }

    public Cursor getBookmarks(String bookPath) {
        SQLiteDatabase db = this.getReadableDatabase();
        if (bookPath == null) {
            return db.rawQuery(
                "SELECT * FROM " + TABLE_BOOKMARKS + " ORDER BY timestamp DESC", null);
        }
        return db.rawQuery(
            "SELECT * FROM " + TABLE_BOOKMARKS +
            " WHERE " + COLUMN_BOOK_PATH + " = ? ORDER BY timestamp DESC",
            new String[]{bookPath});
    }

    public Cursor getAllBookmarks() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery(
            "SELECT * FROM " + TABLE_BOOKMARKS + " ORDER BY timestamp DESC", null);
    }

    public Cursor getBookmark(String bookPath, String bookmarkValue) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery(
            "SELECT * FROM " + TABLE_BOOKMARKS +
            " WHERE " + COLUMN_BOOK_PATH + " = ? AND " + COLUMN_BOOKMARK_VALUE + " = ?",
            new String[]{bookPath, bookmarkValue});
    }

    public int deleteBookmark(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_BOOKMARKS, COLUMN_ID + " = ?",
            new String[]{String.valueOf(id)});
        db.close();
        return rows;
    }

    public int deleteAllBookmarks(String bookPath) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_BOOKMARKS, COLUMN_BOOK_PATH + " = ?",
            new String[]{bookPath});
        db.close();
        return rows;
    }

    public boolean hasBookmark(String bookPath, String bookmarkValue) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
            "SELECT 1 FROM " + TABLE_BOOKMARKS +
            " WHERE " + COLUMN_BOOK_PATH + " = ? AND " + COLUMN_BOOKMARK_VALUE + " = ?",
            new String[]{bookPath, bookmarkValue});
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        db.close();
        return exists;
    }

    public int deleteBookmarksForPath(String bookPath) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_BOOKMARKS, COLUMN_BOOK_PATH + " = ?",
            new String[]{bookPath});
        db.close();
        return rows;
    }

    // ─── Reading History Methods ───

    public void recordRead(String bookPath) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_BOOK_PATH, bookPath);
        values.put(COLUMN_LAST_READ, System.currentTimeMillis());
        values.put(COLUMN_TIMES_READ, 1);

        Cursor cursor = db.rawQuery(
            "SELECT " + COLUMN_ID + ", " + COLUMN_TIMES_READ +
            " FROM " + TABLE_HISTORY +
            " WHERE " + COLUMN_BOOK_PATH + " = ?",
            new String[]{bookPath});

        if (cursor.moveToFirst()) {
            int id = cursor.getInt(0);
            int timesRead = cursor.getInt(1);
            values.put(COLUMN_TIMES_READ, timesRead + 1);
            db.update(TABLE_HISTORY, values, COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)});
        } else {
            db.insert(TABLE_HISTORY, null, values);
        }

        cursor.close();
        db.close();
    }

    public Cursor getRecentBooks(int limit) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery(
            "SELECT * FROM " + TABLE_HISTORY +
            " ORDER BY " + COLUMN_LAST_READ + " DESC LIMIT ?",
            new String[]{String.valueOf(limit)});
    }

    public int deleteHistory(String bookPath) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_HISTORY, COLUMN_BOOK_PATH + " = ?",
            new String[]{bookPath});
        db.close();
        return rows;
    }

    public int clearHistory() {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_HISTORY, null, null);
        db.close();
        return rows;
    }
}