package com.example.lectormobile;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Helper class to copy files from Content URIs (shared files) to app storage.
 *
 * Imported books are stored in the app's private files directory so they
 * survive cache cleanups and remain available in the library.
 */
public class UriFileCopier {

    /**
     * Copy a file from a Content URI into the app's books directory.
     *
     * @param context The application context
     * @param uri The content URI to copy from
     * @return A File pointing to the copied file, or null if the copy failed
     */
    public static File copyFromUri(Context context, Uri uri) {
        InputStream in = null;
        FileOutputStream out = null;
        try {
            String fileName = sanitize(getFileName(context, uri));
            if (fileName == null || fileName.isEmpty()) {
                fileName = "libro_" + System.currentTimeMillis() + guessExtension(context, uri);
            }

            File booksDir = new File(context.getFilesDir(), "books");
            if (!booksDir.exists() && !booksDir.mkdirs()) {
                return null;
            }

            File destFile = uniqueFile(booksDir, fileName);

            in = context.getContentResolver().openInputStream(uri);
            if (in == null) return null;
            out = new FileOutputStream(destFile);

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();

            return destFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) { }
            }
            if (out != null) {
                try { out.close(); } catch (IOException ignored) { }
            }
        }
    }

    private static File uniqueFile(File dir, String fileName) {
        File file = new File(dir, fileName);
        if (!file.exists()) return file;

        String base = fileName;
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            base = fileName.substring(0, dot);
            ext = fileName.substring(dot);
        }
        int i = 1;
        while (file.exists()) {
            file = new File(dir, base + " (" + i + ")" + ext);
            i++;
        }
        return file;
    }

    private static String sanitize(String name) {
        if (name == null) return null;
        // Keep the display name but remove path separators that could escape the dir
        return name.replace('/', '_').replace('\\', '_').trim();
    }

    private static String guessExtension(Context context, Uri uri) {
        String mime = null;
        try {
            mime = context.getContentResolver().getType(uri);
        } catch (Exception ignored) { }
        if ("application/epub+zip".equals(mime)) return ".epub";
        return ".pdf";
    }

    /**
     * Get the display name from a Content URI.
     */
    private static String getFileName(Context context, Uri uri) {
        String fileName = null;
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex);
                }
            }
        } catch (Exception ignored) {
            // Fall through to the URI's last path segment
        } finally {
            if (cursor != null) cursor.close();
        }

        if (fileName == null && uri.getLastPathSegment() != null) {
            fileName = uri.getLastPathSegment();
        }
        return fileName;
    }
}
