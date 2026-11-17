package com.nawazishali.sonora;

import android.net.Uri;

/** One audio or video item from MediaStore (or a SAF folder). */
public class MediaItem {
    public long id;                 // MediaStore id (or hash for SAF items)
    public Uri uri;                 // playable content uri
    public String title = "";
    public String artist = "";      // audio artist / video folder hint
    public String album = "";
    public long albumId = -1;
    public long duration = 0;       // ms
    public long size = 0;           // bytes
    public long dateAdded = 0;      // seconds
    public boolean video;
    public String bucketId = "";
    public String bucketName = "";
    public String path = "";        // file path when known (may be empty on scoped storage)
    public String mime = "";

    public String key() { return (video ? "v:" : "a:") + id; }

    public String subtitle() {
        if (video) return Ui.fmtDuration(duration) + "  •  " + Ui.fmtSize(size);
        String a = artist == null ? "" : artist.trim();
        if (a.isEmpty() || a.equals("<unknown>")) a = album == null ? "" : album.trim();
        return (a.isEmpty() ? "Unknown artist" : a) + "  •  " + Ui.fmtDuration(duration);
    }
}
