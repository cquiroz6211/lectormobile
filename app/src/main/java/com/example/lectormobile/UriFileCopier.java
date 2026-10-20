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
 * Helper class to copy files from Content URIs (shared files) to app internal storage.
 */
public class UriFileCopier {

    /**
     * Copy a file from a Content URI to the app's internal cache directory.
     *
     * @param context The application context
     * @param uri The content URI to copy from
     * @return A File object pointing to the copied file, or null if copy failed
     */
    public static File copyFromUri(Context context, Uri uri) {
        try {
            String fileName = getFileName(context, uri);
            if (fileName == null) {
                fileName = "temp_file";
            }

            File destFile = new File(context.getCacheDir(), fileName);
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(destFile)) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            return destFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get the file name from a Content URI.
     */
    private static String getFileName(Context context, Uri uri) {
        String fileName = "temp";

        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = contentResolver.query(uri, null, null, null, null);

        if (cursor != null) {
            try {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex);
                }
            } finally {
                cursor.close();
            }
        }

        return fileName;
    }
}