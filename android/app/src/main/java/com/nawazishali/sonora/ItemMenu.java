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

    public static void playVideoList(Context ctx, List<MediaItem> items, int index) {
        if (items == null || items.isEmpty()) return;
        VideoPlayerActivity.queue = new ArrayList<>(items);
        VideoPlayerActivity.queueIndex = index;
        Intent i = new Intent(ctx, VideoPlayerActivity.class);
        i.putExtra("index", index);
        ctx.startActivity(i);
    }

    private static List<MediaItem> single(MediaItem it) {
        List<MediaItem> l = new ArrayList<>(); l.add(it); return l;
    }

    /** Options dialog for one item. host is the Activity (needed for delete consent). */
    public static void show(final Activity act, final MediaItem it, final Runnable onChanged) {
        final Db db = Db.get(act);
        boolean fav = db.isFavorite(it.key());
        List<String> opts = new ArrayList<>();
        opts.add("Play");
        opts.add(fav ? "Remove from favorites" : "Add to favorites");
        if (!it.video) opts.add("Add to playlist");
        if (it.video) opts.add("Extract audio (M4A)");
        opts.add("Share");
        opts.add("Open with…");
        opts.add("Rename");
        opts.add("Details");
        opts.add("Delete");
        new AlertDialog.Builder(act)
                .setTitle(it.title)
                .setItems(opts.toArray(new String[0]), (d, which) -> {
                    String o = opts.get(which);
                    switch (o) {
                        case "Play":
                            if (it.video) playVideo(act, it); else playAudio(act, single(it), 0);
                            break;
                        case "Add to favorites": case "Remove from favorites":
                            db.setFavorite(it, !fav);
                            if (onChanged != null) onChanged.run();
                            break;
                        case "Add to playlist": playlistPicker(act, it); break;
                        case "Extract audio (M4A)":
                            Toast.makeText(act, "Extracting audio…", Toast.LENGTH_SHORT).show();
                            AudioExtractor.extract(act, it, (ok, msg) -> {
                                Toast.makeText(act, msg, Toast.LENGTH_LONG).show();
                                if (ok) Library.scanAsync(act);
                            });
                            break;
                        case "Share": MediaActions.share(act, it); break;
                        case "Open with…": MediaActions.openWith(act, it); break;
                        case "Rename": renameDialog(act, it, onChanged); break;
                        case "Details": detailsDialog(act, it); break;
                        case "Delete": deleteDialog(act, it, onChanged); break;
                    }
                })
                .show();
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

    private static void renameDialog(final Activity act, final MediaItem it, final Runnable onChanged) {
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

    private static void detailsDialog(Activity act, MediaItem it) {
        StringBuilder sb = new StringBuilder();
        sb.append("Type: ").append(it.video ? "Video" : "Audio").append('\n');
        if (!it.artist.isEmpty()) sb.append("Artist: ").append(it.artist).append('\n');
        if (!it.album.isEmpty()) sb.append("Album: ").append(it.album).append('\n');
        sb.append("Duration: ").append(Ui.fmtDuration(it.duration)).append('\n');
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

    private static void deleteDialog(final Activity act, final MediaItem it, final Runnable onChanged) {
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
