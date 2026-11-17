package com.nawazishali.sonora;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal framework-only replacement for AndroidX FileProvider: serves files from
 * app-private storage (files/cache, internal + external) under
 * content://com.nawazishali.sonora.fileprovider/... so they can be shared safely.
 */
public class ShareProvider extends ContentProvider {
    public static final String AUTHORITY = "com.nawazishali.sonora.fileprovider";

    public static Uri uriFor(File f) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath(f.getAbsolutePath()).build();
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null) throw new FileNotFoundException("no path");
        File f = new File(path);
        try {
            String canon = f.getCanonicalPath();
            for (File root : allowedRoots()) {
                if (root != null && canon.startsWith(root.getCanonicalPath())) return f;
            }
        } catch (Exception ignored) { }
        throw new FileNotFoundException("outside allowed roots: " + path);
    }

    private List<File> allowedRoots() {
        List<File> roots = new ArrayList<>();
        if (getContext() != null) {
            roots.add(getContext().getFilesDir());
            roots.add(getContext().getCacheDir());
            roots.add(getContext().getExternalFilesDir(null));
            roots.add(getContext().getExternalCacheDir());
        }
        return roots;
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolve(uri);
        int m = mode.contains("w") ? ParcelFileDescriptor.MODE_WRITE_ONLY
                : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(f, m);
    }

    @Override public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        String ext = name != null && name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
        return mime == null ? "application/octet-stream" : mime;
    }

    @Override public Cursor query(Uri uri, String[] proj, String sel, String[] args, String sort) {
        try {
            File f = resolve(uri);
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (Exception e) { return null; }
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
