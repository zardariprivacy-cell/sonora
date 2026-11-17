package com.nawazishali.sonora;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Local persistence (SQLite — the spec's "Room or equivalent").
 * favorites / playlists / playlist_items / history / positions (resume) / settings / vault_meta.
 */
public class Db extends SQLiteOpenHelper {
    private static Db instance;

    public static synchronized Db get(Context ctx) {
        if (instance == null) instance = new Db(ctx.getApplicationContext());
        return instance;
    }

    private Db(Context ctx) { super(ctx, "sonora.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE favorites(key TEXT PRIMARY KEY, video INTEGER, title TEXT, ts INTEGER)");
        d.execSQL("CREATE TABLE playlists(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, ts INTEGER)");
        d.execSQL("CREATE TABLE playlist_items(playlist_id INTEGER, key TEXT, video INTEGER, title TEXT, ord INTEGER, PRIMARY KEY(playlist_id, key))");
        d.execSQL("CREATE TABLE history(key TEXT PRIMARY KEY, video INTEGER, title TEXT, count INTEGER, last_ts INTEGER)");
        d.execSQL("CREATE TABLE positions(key TEXT PRIMARY KEY, pos INTEGER, duration INTEGER, ts INTEGER)");
        d.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY, v TEXT)");
        d.execSQL("CREATE TABLE vault_meta(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, mime TEXT, size INTEGER, enc_file TEXT, video INTEGER, ts INTEGER)");
        d.execSQL("CREATE INDEX idx_history_ts ON history(last_ts)");
        d.execSQL("CREATE INDEX idx_positions_ts ON positions(ts)");
    }

    @Override public void onUpgrade(SQLiteDatabase d, int oldV, int newV) { /* v1 */ }

    // ---------- favorites ----------
    public boolean isFavorite(String key) {
        Cursor c = getReadableDatabase().query("favorites", new String[]{"key"}, "key=?", new String[]{key}, null, null, null);
        boolean r = c.moveToFirst(); c.close(); return r;
    }
    public void setFavorite(MediaItem it, boolean fav) {
        SQLiteDatabase d = getWritableDatabase();
        if (fav) {
            ContentValues v = new ContentValues();
            v.put("key", it.key()); v.put("video", it.video ? 1 : 0); v.put("title", it.title);
            v.put("ts", System.currentTimeMillis());
            d.insertWithOnConflict("favorites", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } else d.delete("favorites", "key=?", new String[]{it.key()});
    }
    public Set<String> favoriteKeys() {
        Set<String> s = new HashSet<>();
        Cursor c = getReadableDatabase().query("favorites", new String[]{"key"}, null, null, null, null, null);
        while (c.moveToNext()) s.add(c.getString(0));
        c.close(); return s;
    }

    // ---------- playlists ----------
    public long createPlaylist(String name) {
        ContentValues v = new ContentValues();
        v.put("name", name); v.put("ts", System.currentTimeMillis());
        return getWritableDatabase().insert("playlists", null, v);
    }
    public void renamePlaylist(long id, String name) {
        ContentValues v = new ContentValues(); v.put("name", name);
        getWritableDatabase().update("playlists", v, "id=?", new String[]{"" + id});
    }
    public void deletePlaylist(long id) {
        SQLiteDatabase d = getWritableDatabase();
        d.delete("playlist_items", "playlist_id=?", new String[]{"" + id});
        d.delete("playlists", "id=?", new String[]{"" + id});
    }
    public List<long[]> playlists() { // [id, count], parallel names via playlistNames()
        List<long[]> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT p.id, (SELECT COUNT(*) FROM playlist_items i WHERE i.playlist_id=p.id) FROM playlists p ORDER BY p.ts", null);
        while (c.moveToNext()) out.add(new long[]{c.getLong(0), c.getLong(1)});
        c.close(); return out;
    }
    public List<String> playlistNames() {
        List<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("playlists", new String[]{"name"}, null, null, null, null, "ts");
        while (c.moveToNext()) out.add(c.getString(0));
        c.close(); return out;
    }
    public void addToPlaylist(long pid, MediaItem it) {
        SQLiteDatabase d = getWritableDatabase();
        Cursor c = d.rawQuery("SELECT COALESCE(MAX(ord),0)+1 FROM playlist_items WHERE playlist_id=?", new String[]{"" + pid});
        long ord = c.moveToFirst() ? c.getLong(0) : 1; c.close();
        ContentValues v = new ContentValues();
        v.put("playlist_id", pid); v.put("key", it.key()); v.put("video", it.video ? 1 : 0);
        v.put("title", it.title); v.put("ord", ord);
        d.insertWithOnConflict("playlist_items", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }
    public void removeFromPlaylist(long pid, String key) {
        getWritableDatabase().delete("playlist_items", "playlist_id=? AND key=?", new String[]{"" + pid, key});
    }
    public List<String> playlistKeys(long pid) {
        List<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("playlist_items", new String[]{"key"}, "playlist_id=?",
                new String[]{"" + pid}, null, null, "ord");
        while (c.moveToNext()) out.add(c.getString(0));
        c.close(); return out;
    }

    // ---------- history / most played ----------
    public void recordPlay(MediaItem it) {
        SQLiteDatabase d = getWritableDatabase();
        Cursor c = d.query("history", new String[]{"count"}, "key=?", new String[]{it.key()}, null, null, null);
        long count = c.moveToFirst() ? c.getLong(0) : 0; c.close();
        ContentValues v = new ContentValues();
        v.put("key", it.key()); v.put("video", it.video ? 1 : 0); v.put("title", it.title);
        v.put("count", count + 1); v.put("last_ts", System.currentTimeMillis());
        d.insertWithOnConflict("history", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }
    /** Keys ordered by most recent play. */
    public List<String> recentKeys(boolean video, int limit) {
        List<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("history", new String[]{"key"}, "video=?",
                new String[]{video ? "1" : "0"}, null, null, "last_ts DESC", "" + limit);
        while (c.moveToNext()) out.add(c.getString(0));
        c.close(); return out;
    }
    /** Keys ordered by play count. */
    public List<String> mostPlayedKeys(int limit) {
        List<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("history", new String[]{"key"}, null, null, null, null,
                "count DESC, last_ts DESC", "" + limit);
        while (c.moveToNext()) out.add(c.getString(0));
        c.close(); return out;
    }

    // ---------- resume positions ----------
    public void savePosition(String key, long pos, long duration) {
        ContentValues v = new ContentValues();
        v.put("key", key); v.put("pos", pos); v.put("duration", duration);
        v.put("ts", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("positions", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }
    public long getPosition(String key) {
        Cursor c = getReadableDatabase().query("positions", new String[]{"pos"}, "key=?", new String[]{key}, null, null, null);
        long r = c.moveToFirst() ? c.getLong(0) : 0; c.close(); return r;
    }
    public void clearPosition(String key) { getWritableDatabase().delete("positions", "key=?", new String[]{key}); }
    /** Continue watching: video keys with a saved position >5s and not finished, newest first. */
    public List<String> continueWatchingKeys(int limit) {
        List<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT key FROM positions WHERE key LIKE 'v:%' AND pos>5000 AND (duration<=0 OR pos < duration-10000) ORDER BY ts DESC LIMIT " + limit, null);
        while (c.moveToNext()) out.add(c.getString(0));
        c.close(); return out;
    }

    // ---------- settings ----------
    public String getSetting(String k, String def) {
        Cursor c = getReadableDatabase().query("settings", new String[]{"v"}, "k=?", new String[]{k}, null, null, null);
        String r = c.moveToFirst() ? c.getString(0) : def; c.close(); return r == null ? def : r;
    }
    public void setSetting(String k, String v) {
        ContentValues cv = new ContentValues(); cv.put("k", k); cv.put("v", v);
        getWritableDatabase().insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ---------- vault ----------
    public long addVaultItem(String name, String mime, long size, String encFile, boolean video) {
        ContentValues v = new ContentValues();
        v.put("name", name); v.put("mime", mime); v.put("size", size); v.put("enc_file", encFile);
        v.put("video", video ? 1 : 0); v.put("ts", System.currentTimeMillis());
        return getWritableDatabase().insert("vault_meta", null, v);
    }
    public Cursor vaultCursor() {
        return getReadableDatabase().query("vault_meta",
            new String[]{"id", "name", "mime", "size", "enc_file", "video", "ts"}, null, null, null, null, "ts DESC");
    }
    public void deleteVaultItem(long id) { getWritableDatabase().delete("vault_meta", "id=?", new String[]{"" + id}); }
}
