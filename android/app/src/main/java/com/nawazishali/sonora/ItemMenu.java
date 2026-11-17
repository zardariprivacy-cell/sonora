package com.nawazishali.sonora;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Shared playback launching + per-item options menu (used by every list in the app). */
public class ItemMenu {

    public static void playAudio(final Context ctx, final List<MediaItem> items, final int index) {
        if (items == null || items.isEmpty()) return;
        MusicService.ensureStarted(ctx);
        if (MusicService.instance != null) {
            MusicService.instance.setQueue(items, index);
            return;
        }
        // Service still starting: bind and play when connected.
        Intent i = new Intent(ctx, MusicService.class);
        ctx.getApplicationContext().bindService(i, new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName n, IBinder b) {
                ((MusicService.LocalBinder) b).service().setQueue(items, index);
                ctx.getApplicationContext().unbindService(this);
            }
            @Override public void onServiceDisconnected(ComponentName n) { }
        }, Context.BIND_AUTO_CREATE);
    }

    public static void playVideo(Context ctx, MediaItem item) {
        playVideoList(ctx, single(item), 0);
    }

    /** Play a single video and drop straight into Picture-in-Picture. */
    public static void playVideoPip(Context ctx, MediaItem item) {
        playVideoList(ctx, single(item), 0, true);
    }

    public static void playVideoList(final Context ctx, final List<MediaItem> items, final int index) {
        playVideoList(ctx, items, index, false);
    }

    public static void playVideoList(final Context ctx, final List<MediaItem> items, final int index,
                                     final boolean startInPip) {
        if (items == null || items.isEmpty()) return;
        Runnable go = () -> {
            VideoPlayerActivity.queue = new ArrayList<>(items);
            VideoPlayerActivity.queueIndex = index;
            Intent i = new Intent(ctx, VideoPlayerActivity.class);
            i.putExtra("index", index);
            if (startInPip) i.putExtra("startInPip", true);
            ctx.startActivity(i);
        };
        // Tasteful interstitial (v3.1): shown before the player opens, at most
        // once every 3 minutes, only if one is already loaded. If not, the
        // video opens instantly — navigation is never blocked by an ad.
        if (ctx instanceof Activity && Ads.showInterstitialBefore((Activity) ctx, go)) return;
        go.run();
    }

    /** Song: play after the current track (starts playback if nothing is playing). */
    public static void playNext(Context ctx, MediaItem it) {
        if (MusicService.instance != null && MusicService.instance.current() != null) {
            MusicService.instance.playNext(it);
            Toast.makeText(ctx, "Will play next: " + it.title, Toast.LENGTH_SHORT).show();
        } else {
            playAudio(ctx, single(it), 0);
        }
    }

    /** Song: append to the play queue (starts playback if nothing is playing). */
    public static void addToQueue(Context ctx, MediaItem it) {
        if (MusicService.instance != null && MusicService.instance.current() != null) {
            MusicService.instance.addToQueue(it);
            Toast.makeText(ctx, "Added to queue: " + it.title, Toast.LENGTH_SHORT).show();
        } else {
            playAudio(ctx, single(it), 0);
        }
    }

    /** Video: run the real M4A audio extraction (same flow as the old menu). */
    public static void extractAudio(final Activity act, final MediaItem it) {
        Toast.makeText(act, "Extracting audio…", Toast.LENGTH_SHORT).show();
        AudioExtractor.extract(act, it, (ok, msg) -> {
            Toast.makeText(act, msg, Toast.LENGTH_LONG).show();
            if (ok) Library.scanAsync(act);
        });
    }

    private static List<MediaItem> single(MediaItem it) {
        List<MediaItem> l = new ArrayList<>(); l.add(it); return l;
    }

    /**
     * Options for one item. v3.1: opens the raised "3D box" MediaSheet
     * (artwork header + icon action rows) instead of the old plain dialog.
     * Every existing caller (long-press and the new overflow button in
     * lists/rows) gets the sheet through this single entry point.
     */
    public static void show(final Activity act, final MediaItem it, final Runnable onChanged) {
        MediaSheet.show(act, it, onChanged);
    }

    public static void playlistPicker(final Activity act, final MediaItem it) {
        final Db db = Db.get(act);
        final List<long[]> pls = db.playlists();
        final List<String> names = db.playlistNames();
        List<String> opts = new ArrayList<>(names);
        opts.add("+ New playlist");
        new AlertDialog.Builder(act)
                .setTitle("Add to playlist")
                .setItems(opts.toArray(new String[0]), (d, w) -> {
                    if (w == opts.size() - 1) {
                        nameDialog(act, "New playlist", name -> {
                            long pid = db.createPlaylist(name);
                            db.addToPlaylist(pid, it);
                            Toast.makeText(act, "Added to " + name, Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        db.addToPlaylist(pls.get(w)[0], it);
                        Toast.makeText(act, "Added to " + names.get(w), Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    public interface NameCallback { void onName(String name); }

    public static void nameDialog(Activity act, String title, final NameCallback cb) {
        final EditText input = new EditText(act);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        new AlertDialog.Builder(act)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    String n = input.getText().toString().trim();
                    if (!n.isEmpty()) cb.onName(n);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    static void renameDialog(final Activity act, final MediaItem it, final Runnable onChanged) {
        final EditText input = new EditText(act);
        input.setText(it.title);
        input.setSingleLine(true);
        new AlertDialog.Builder(act)
                .setTitle("Rename")
                .setView(input)
                .setPositiveButton("Rename", (d, w) -> {
                    String n = input.getText().toString().trim();
                    if (!n.isEmpty()) {
                        // Keep the original extension when renaming the display name.
                        String ext = "";
                        String old = it.path;
                        if (old != null && old.contains(".")) ext = old.substring(old.lastIndexOf('.'));
                        String display = n.endsWith(ext) || ext.isEmpty() ? n : n + ext;
                        boolean ok = MediaActions.rename(act, it, display);
                        Toast.makeText(act, ok ? "Renamed" : "Rename not permitted for this file",
                                Toast.LENGTH_SHORT).show();
                        if (ok) { Library.scanAsync(act); if (onChanged != null) onChanged.run(); }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    static void detailsDialog(Activity act, MediaItem it) {
        StringBuilder sb = new StringBuilder();
        sb.append("Type: ").append(it.video ? "Video" : "Audio").append('\n');
        if (it.mime != null && !it.mime.isEmpty()) sb.append("Format: ").append(it.mime).append('\n');
        if (!it.artist.isEmpty()) sb.append("Artist: ").append(it.artist).append('\n');
        if (!it.album.isEmpty()) sb.append("Album: ").append(it.album).append('\n');
        sb.append("Duration: ").append(Ui.fmtDuration(it.duration)).append('\n');
        if (it.video) {
            // Resolution from the file itself (not stored in MediaStore scan)
            try {
                android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
                r.setDataSource(act, it.uri);
                String w = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                String h = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                if (w != null && h != null) sb.append("Resolution: ").append(w).append(" × ").append(h).append('\n');
                r.release();
            } catch (Throwable ignored) { }
        }
        sb.append("Size: ").append(Ui.fmtSize(it.size)).append('\n');
        sb.append("Added: ").append(Ui.fmtDate(it.dateAdded)).append('\n');
        sb.append("Folder: ").append(it.bucketName).append('\n');
        if (!it.path.isEmpty()) sb.append("Path: ").append(it.path);
        new AlertDialog.Builder(act)
                .setTitle(it.title)
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    static void deleteDialog(final Activity act, final MediaItem it, final Runnable onChanged) {
        new AlertDialog.Builder(act)
                .setTitle("Delete this file?")
                .setMessage("\"" + it.title + "\" will be permanently deleted from your device.")
                .setPositiveButton("Delete", (d, w) -> MediaActions.delete(act, it, ok -> {
                    if (ok) { Library.scanAsync(act); if (onChanged != null) onChanged.run(); }
                    else Toast.makeText(act, "Could not delete this file", Toast.LENGTH_SHORT).show();
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
