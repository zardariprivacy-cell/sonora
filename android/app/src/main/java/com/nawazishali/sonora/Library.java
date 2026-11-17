package com.nawazishali.sonora;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Holds the scanned library and performs MediaStore scans on a background thread. */
public class Library {
    public interface Listener { void onLibraryChanged(); }

    public static final List<MediaItem> AUDIO = new ArrayList<>();
    public static final List<MediaItem> VIDEO = new ArrayList<>();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    public static volatile boolean scanning = false;
    public static volatile boolean scannedOnce = false;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void addListener(Listener l) { if (!LISTENERS.contains(l)) LISTENERS.add(l); }
    public static void removeListener(Listener l) { LISTENERS.remove(l); }
    private static void notifyChanged() { for (Listener l : new ArrayList<>(LISTENERS)) l.onLibraryChanged(); }

    public static boolean hasAudioPermission(Context c) { return hasPerm(c, false); }
    public static boolean hasVideoPermission(Context c) { return hasPerm(c, true); }
    public static boolean hasAnyPermission(Context c) { return hasAudioPermission(c) || hasVideoPermission(c); }

    private static boolean hasPerm(Context c, boolean video) {
        if (Build.VERSION.SDK_INT >= 33) {
            String p = video ? Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.READ_MEDIA_AUDIO;
            if (c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) return true;
            // Android 14 partial access grants visual media via this permission
            return Build.VERSION.SDK_INT >= 34 && video
                    && c.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
        }
        return c.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    /** Permissions to request at runtime for the current SDK. */
    public static String[] neededPermissions() {
        if (Build.VERSION.SDK_INT >= 33)
            return new String[]{Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO};
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    public static void scanAsync(final Context ctx) {
        if (scanning) return;
        scanning = true;
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            List<MediaItem> audio = new ArrayList<>(), video = new ArrayList<>();
            try { if (hasAudioPermission(app)) audio = queryAudio(app); } catch (Throwable ignored) { }
            try { if (hasVideoPermission(app)) video = queryVideo(app); } catch (Throwable ignored) { }
            synchronized (Library.class) {
                AUDIO.clear(); AUDIO.addAll(audio);
                VIDEO.clear(); VIDEO.addAll(video);
            }
            scanning = false; scannedOnce = true;
            MAIN.post(Library::notifyChanged);
        }, "sonora-scan").start();
    }

    private static List<MediaItem> queryAudio(Context ctx) {
        List<MediaItem> out = new ArrayList<>();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.BUCKET_ID, MediaStore.Audio.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.MIME_TYPE};
        try (Cursor c = ctx.getContentResolver().query(uri, proj,
                MediaStore.Audio.Media.IS_MUSIC + "!=0", null, MediaStore.Audio.Media.DATE_ADDED + " DESC")) {
            if (c == null) return out;
            while (c.moveToNext()) {
                MediaItem it = new MediaItem();
                it.id = c.getLong(0);
                it.uri = ContentUris.withAppendedId(uri, it.id);
                it.title = str(c, 1, "Unknown title");
                it.artist = str(c, 2, "");
                it.album = str(c, 3, "");
                it.albumId = c.getLong(4);
                it.duration = c.getLong(5);
                it.size = c.getLong(6);
                it.dateAdded = c.getLong(7);
                it.bucketId = str(c, 8, "");
                it.bucketName = str(c, 9, "Music");
                it.path = str(c, 10, "");
                it.mime = str(c, 11, "");
                it.video = false;
                out.add(it);
            }
        }
        return out;
    }

    private static List<MediaItem> queryVideo(Context ctx) {
        List<MediaItem> out = new ArrayList<>();
        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.BUCKET_ID, MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DATA, MediaStore.Video.Media.MIME_TYPE, MediaStore.Video.Media.DISPLAY_NAME};
        try (Cursor c = ctx.getContentResolver().query(uri, proj, null, null,
                MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (c == null) return out;
            while (c.moveToNext()) {
                MediaItem it = new MediaItem();
                it.id = c.getLong(0);
                it.uri = ContentUris.withAppendedId(uri, it.id);
                it.title = str(c, 1, "");
                if (it.title.isEmpty()) it.title = str(c, 9, "Video");
                it.duration = c.getLong(2);
                it.size = c.getLong(3);
                it.dateAdded = c.getLong(4);
                it.bucketId = str(c, 5, "");
                it.bucketName = str(c, 6, "Videos");
                it.path = str(c, 7, "");
                it.mime = str(c, 8, "");
                it.video = true;
                out.add(it);
            }
        }
        return out;
    }

    private static String str(Cursor c, int i, String def) {
        try { String s = c.getString(i); return s == null ? def : s; } catch (Throwable t) { return def; }
    }

    public static MediaItem findByKey(String key) {
        if (key == null) return null;
        boolean video = key.startsWith("v:");
        long id;
        try { id = Long.parseLong(key.substring(2)); } catch (Throwable t) { return null; }
        for (MediaItem it : (video ? VIDEO : AUDIO)) if (it.id == id) return it;
        return null;
    }

    public static List<MediaItem> resolveKeys(List<String> keys) {
        List<MediaItem> out = new ArrayList<>();
        for (String k : keys) { MediaItem it = findByKey(k); if (it != null) out.add(it); }
        return out;
    }

    // ---------- sorting ----------
    public static final int SORT_NAME = 0, SORT_DATE = 1, SORT_SIZE = 2, SORT_DURATION = 3;
    public static void sort(List<MediaItem> list, int mode) {
        Comparator<MediaItem> cmp;
        switch (mode) {
            case SORT_SIZE: cmp = (a, b) -> Long.compare(b.size, a.size); break;
            case SORT_DURATION: cmp = (a, b) -> Long.compare(b.duration, a.duration); break;
            case SORT_NAME: cmp = (a, b) -> a.title.compareToIgnoreCase(b.title); break;
            default: cmp = (a, b) -> Long.compare(b.dateAdded, a.dateAdded);
        }
        Collections.sort(list, cmp);
    }

    // ---------- folders / buckets ----------
    public static class Folder {
        public String id, name;
        public int audioCount, videoCount;
        public long audioSize, videoSize;
        public MediaItem cover;
        public long totalSize() { return audioSize + videoSize; }
        public int totalCount() { return audioCount + videoCount; }
    }

    public static List<Folder> folders() {
        Map<String, Folder> map = new HashMap<>();
        for (MediaItem it : AUDIO) addTo(map, it);
        for (MediaItem it : VIDEO) addTo(map, it);
        List<Folder> out = new ArrayList<>(map.values());
        Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    private static void addTo(Map<String, Folder> map, MediaItem it) {
        String k = (it.bucketId == null ? "" : it.bucketId) + "|" + it.bucketName;
        Folder f = map.get(k);
        if (f == null) {
            f = new Folder(); f.id = it.bucketId; f.name = it.bucketName == null || it.bucketName.isEmpty() ? "Folder" : it.bucketName;
            f.cover = it; map.put(k, f);
        }
        if (it.video) { f.videoCount++; f.videoSize += it.size; } else { f.audioCount++; f.audioSize += it.size; }
    }

    public static long totalAudioSize() { long s = 0; for (MediaItem it : AUDIO) s += it.size; return s; }
    public static long totalVideoSize() { long s = 0; for (MediaItem it : VIDEO) s += it.size; return s; }
}
