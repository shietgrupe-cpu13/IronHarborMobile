package org.vera20k;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Grants read-only access to generated diagnostic ZIPs and nothing else. */
public final class DiagnosticArchiveProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        try {
            resolve(uri);
            return "application/zip";
        } catch (FileNotFoundException ignored) {
            return null;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File archive;
        try {
            archive = resolve(uri);
        } catch (FileNotFoundException ignored) {
            return null;
        }
        String[] columns = projection == null
                ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] values = new Object[columns.length];
        for (int index = 0; index < columns.length; index++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[index])) {
                values[index] = archive.getName();
            } else if (OpenableColumns.SIZE.equals(columns[index])) {
                values[index] = archive.length();
            }
        }
        cursor.addRow(values);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Diagnostic archives are read-only");
        }
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Diagnostic archive provider is read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Diagnostic archive provider is read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Diagnostic archive provider is read-only");
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (uri.getPathSegments().size() != 2
                || !"diagnostics".equals(uri.getPathSegments().get(0))) {
            throw new FileNotFoundException("Invalid diagnostic archive URI");
        }
        if (getContext() == null) {
            throw new FileNotFoundException("Diagnostic archive context is unavailable");
        }
        return DiagnosticLogArchive.resolveShareableArchive(
                getContext().getCacheDir(), uri.getPathSegments().get(1));
    }
}
