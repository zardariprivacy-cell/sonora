package com.nawazishali.sonora;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Share / open-with / rename / delete helpers for MediaStore items (with user consent flows). */
public class MediaActions {

    public static void share(Context ctx, MediaItem it) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(it.mime == null || it.mime.isEmpty() ? (it.video ? "video/*" : "audio/*") : it.mime);
        i.putExtra(Intent.EXTRA_STREAM, it.uri);
        i.putExtra(Intent.EXTRA_TITLE, it.title);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ctx.startActivity(Intent.createChooser(i, "Share " + it.title));
    }

    public static void openWith(Context ctx, MediaItem it) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(it.uri, it.mime == null || it.mime.isEmpty() ? (it.video ? "video/*" : "audio/*") : it.mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { ctx.startActivity(Intent.createChooser(i, "Open with")); }
        catch (Exception e) { Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show(); }
    }

    /** Rename via MediaStore DISPLAY_NAME update. Returns true on success. */
    public static boolean rename(Context ctx, MediaItem it, String newName) {
        try {
            ContentValues v = new ContentValues();
            String col = it.video ? MediaStore.Video.Media.DISPLAY_NAME : MediaStore.Audio.Media.DISPLAY_NAME;
            v.put(col, newName);
            int rows = ctx.getContentResolver().update(it.uri, v, null, null);
            if (rows > 0) {
                if (!it.path.isEmpty())
                    MediaScannerConnection.scanFile(ctx, new String[]{it.path}, null, null);
                return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    public interface DeleteCallback { void onDone(boolean ok); }

    /**
     * Delete with the platform consent flow: on API 30+ this launches the system
     * confirmation dialog via createDeleteRequest; the caller re-checks afterwards.
     * On older APIs it deletes directly. The UI must still show its own confirmation
     * before calling this.
     */
    public static void delete(Activity act, MediaItem it, DeleteCallback cb) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                List<Uri> uris = new ArrayList<>();
                uris.add(it.uri);
                PendingIntent pi = MediaStore.createDeleteRequest(act.getContentResolver(), uris);
                act.startIntentSenderForResult(pi.getIntentSender(), 4471, null, 0, 0, 0);
                // Result handled by the activity (onActivityResult 4471) which refreshes the library.
                if (cb != null) cb.onDone(true); // request launched; actual deletion confirmed by rescan
            } else {
                int rows = act.getContentResolver().delete(it.uri, null, null);
                if (cb != null) cb.onDone(rows > 0);
            }
        } catch (IntentSender.SendIntentException e) {
            if (cb != null) cb.onDone(false);
        } catch (Exception e) {
            if (cb != null) cb.onDone(false);
        }
    }
}
