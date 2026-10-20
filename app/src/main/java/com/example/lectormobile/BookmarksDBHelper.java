package com.example.lectormobile;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashMap;

/**
 * SQLite database helper for bookmarks and reading history.
 * Tables:
 *   - bookmarks (id, book_path, bookmark_value, timestamp, label)
 *   - reading_history (id, book_path, last_read_at, times_read)
 *   - reading_positions (id, book_path, position, updated_at, percent)  [v4]
 */
public class BookmarksDBHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "lectormobile.db";
    private static final int DATABASE_VERSION = 5;
    private static final String TABLE_BOOKMARKS = "bookmarks";
    private static final String TABLE_HISTORY = "reading_history";
    private static final String TABLE_POSITIONS = "reading_positions";
    private static final String TABLE_ANNOTATIONS = "annotations";

    private static final String COLUMN_ID = "id";
    private static final String COLUMN_BOOK_PATH = "book_path";
    private static final String COLUMN_BOOKMARK_VALUE = "bookmark_value";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_LABEL = "label";
    private static final String COLUMN_POSITION = "position";
    private static final String COLUMN_UPDATED_AT = "updated_at";
    private static final String COLUMN_LAST_READ = "last_read_at";
    private static final String COLUMN_TIMES_READ = "times_read";
    private static final String COLUMN_PERCENT = "percent";
    private static final String COLUMN_CHAPTER = "chapter_index";
    private static final String COLUMN_START = "start_offset";
    private static final String COLUMN_END = "end_offset";
    private static final String COLUMN_TEXT = "text";
    private static final String COLUMN_NOTE = "note";

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
        COLUMN_UPDATED_AT + " INTEGER NOT NULL, " +
        COLUMN_PERCENT + " INTEGER NOT NULL DEFAULT 0)";

    private static final String CREATE_TABLE_ANNOTATIONS =
        "CREATE TABLE IF NOT EXISTS " + TABLE_ANNOTATIONS + " (" +
        COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COLUMN_BOOK_PATH + " TEXT NOT NULL, " +
        COLUMN_CHAPTER + " INTEGER NOT NULL, " +
        COLUMN_START + " INTEGER NOT NULL, " +
        COLUMN_END + " INTEGER NOT NULL, " +
        COLUMN_TEXT + " TEXT, " +
        COLUMN_NOTE + " TEXT, " +
        COLUMN_TIMESTAMP + " INTEGER NOT NULL)";

    public BookmarksDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_BOOKMARKS);
        db.execSQL(CREATE_TABLE_HISTORY);
        db.execSQL(CREATE_TABLE_POSITIONS);
        db.execSQL(CREATE_TABLE_ANNOTATIONS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Additive migrations: never drop user data.
        db.execSQL(CREATE_TABLE_BOOKMARKS);
        db.execSQL(CREATE_TABLE_HISTORY);
        db.execSQL(CREATE_TABLE_POSITIONS);
        db.execSQL(CREATE_TABLE_ANNOTATIONS); // v4 -> v5 (additive)

        // v3 -> v4: reading progress percent (0..100, 0 = not read yet).
        // Additive column; try/catch because CREATE TABLE IF NOT EXISTS above
        // may already have created the table with the column on fresh installs.
        try {
            db.execSQL("ALTER TABLE " + TABLE_POSITIONS + " ADD COLUMN " +
                COLUMN_PERCENT + " INTEGER NOT NULL DEFAULT 0");
        } catch (Exception ignored) { }

        // Older builds stored the reading position as a fake bookmark.
        try {
            db.execSQL("DELETE FROM " + TABLE_BOOKMARKS + " WHERE " + COLUMN_LABEL + " = 'position'");
        } catch (Exception ignored) { }
    }

    // ─── Reading position ───

    public void saveReadingPosition(String bookPath, String position) {
        saveReadingPosition(bookPath, position, -1);
    }

    /**
     * Save the reading position together with the book progress percent
     * (0..100). Pass a negative percent to keep the stored percent
     * (unknown total, spec S2) — implemented as an UPDATE so the REPLACE
     * path never resets an existing percent to its default.
     */
    public void saveReadingPosition(String bookPath, String position, int percent) {
        if (bookPath == null || position == null) return;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_POSITION, position);
        values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
        if (percent >= 0) {
            values.put(COLUMN_PERCENT, Math.min(100, percent));
        }
        int updated = db.update(TABLE_POSITIONS, values,
                COLUMN_BOOK_PATH + " = ?", new String[]{bookPath});
        if (updated == 0) {
            values.put(COLUMN_BOOK_PATH, bookPath);
            db.insertWithOnConflict(TABLE_POSITIONS, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        }
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

    /**
     * Reading progress percent per book for the library screen (spec S3).
     * Returns book_path -> percent (0..100). Books never opened are absent.
     */
    public HashMap<String, Integer> getAllProgress() {
        HashMap<String, Integer> progress = new HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
            "SELECT " + COLUMN_BOOK_PATH + ", " + COLUMN_PERCENT +
            " FROM " + TABLE_POSITIONS, null);
        while (cursor.moveToNext()) {
            progress.put(cursor.getString(0), cursor.getInt(1));
        }
        cursor.close();
        db.close();
        return progress;
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

    // ─── Annotations (v5) ───

    /** Insert an EPUB highlight/note anchored to the rendered chapter text. */
    public long insertAnnotation(String bookPath, int chapterIndex,
            int startOffset, int endOffset, String text, String note) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_BOOK_PATH, bookPath);
        values.put(COLUMN_CHAPTER, chapterIndex);
        values.put(COLUMN_START, startOffset);
        values.put(COLUMN_END, endOffset);
        values.put(COLUMN_TEXT, text);
        values.put(COLUMN_NOTE, note);
        values.put(COLUMN_TIMESTAMP, System.currentTimeMillis());
        long id = db.insert(TABLE_ANNOTATIONS, null, values);
        db.close();
        return id;
    }

    /** Annotations for a book chapter, ordered by position (spec S3). */
    public Cursor getAnnotations(String bookPath, int chapterIndex) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery(
            "SELECT * FROM " + TABLE_ANNOTATIONS +
            " WHERE " + COLUMN_BOOK_PATH + " = ? AND " + COLUMN_CHAPTER + " = ?" +
            " ORDER BY " + COLUMN_START + " ASC",
            new String[]{bookPath, String.valueOf(chapterIndex)});
    }

    /** All annotations, newest first; optionally filtered by book (spec S4). */
    public Cursor getAllAnnotations(String bookPath) {
        SQLiteDatabase db = this.getReadableDatabase();
        if (bookPath != null) {
            return db.rawQuery(
                "SELECT * FROM " + TABLE_ANNOTATIONS +
                " WHERE " + COLUMN_BOOK_PATH + " = ?" +
                " ORDER BY " + COLUMN_TIMESTAMP + " DESC",
                new String[]{bookPath});
        }
        return db.rawQuery(
            "SELECT * FROM " + TABLE_ANNOTATIONS +
            " ORDER BY " + COLUMN_TIMESTAMP + " DESC", null);
    }

    public int deleteAnnotation(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_ANNOTATIONS, COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)});
        db.close();
        return rows;
    }

    public int deleteAnnotationsForPath(String bookPath) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_ANNOTATIONS, COLUMN_BOOK_PATH + " = ?",
                new String[]{bookPath});
        db.close();
        return rows;
    }
}
