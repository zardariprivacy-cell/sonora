package com.nawazishali.sonora;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Async thumbnail / album-art loader with an LruCache ("Coil or compatible" per plan:
 * built-in loader). Video thumbs come from MediaStore thumbnails / retriever frames;
 * audio art from embedded pictures / album-art URIs. Work happens off the main thread.
 */
public class ImageLoader {
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(24 * 1024) {
                @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount() / 1024; }
            };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void load(final Context ctx, final MediaItem item, final ImageView target) {
        final String key = item.key();
        target.setTag(key);
        Bitmap hit = CACHE.get(key);
        if (hit != null) { target.setImageBitmap(hit); return; }
        target.setImageBitmap(null);
        POOL.execute(() -> {
            Bitmap bmp = null;
            try { bmp = item.video ? videoThumb(ctx, item) : audioArt(ctx, item); }
            catch (Throwable ignored) { }
            // v3.3: no blank squares — fall back to a generated colourful placeholder.
            if (bmp == null) {
                try { bmp = Artwork.placeholder(ctx, item, 288); } catch (Throwable ignored) { }
            }
            if (bmp != null) CACHE.put(key, bmp);
            final Bitmap out = bmp;
            MAIN.post(() -> { if (key.equals(target.getTag()) && out != null) target.setImageBitmap(out); });
        });
    }

    /** Callback for {@link #loadRound}: reports whether real artwork was found. */
    public interface RoundCallback { void onResult(boolean hasArt); }

    /**
     * Load artwork circle-cropped (Now Playing disc / round avatars of songs).
     * The circular bitmap is cached separately from the rectangular one. When
     * no artwork exists the target is cleared and the callback reports false,
     * so the caller can show its gradient-disc placeholder.
     */
    public static void loadRound(final Context ctx, final MediaItem item, final ImageView target,
                                 final RoundCallback cb) {
        final String key = item.key() + "|round";
        target.setTag(key);
        Bitmap hit = CACHE.get(key);
        if (hit != null) {
            target.setImageBitmap(hit);
            if (cb != null) cb.onResult(true);
            return;
        }
        target.setImageBitmap(null);
        POOL.execute(() -> {
            Bitmap bmp = null;
            try {
                Bitmap raw = item.video ? videoThumb(ctx, item) : audioArt(ctx, item);
                if (raw == null) raw = Artwork.placeholder(ctx, item, 360); // v3.3 placeholder disc
                if (raw != null) bmp = Ui.circleBitmap(raw);
            } catch (Throwable ignored) { }
            if (bmp != null) CACHE.put(key, bmp);
            final Bitmap out = bmp;
            MAIN.post(() -> {
                if (!key.equals(target.getTag())) return;
                if (out != null) target.setImageBitmap(out);
                if (cb != null) cb.onResult(out != null);
            });
        });
    }

    private static Bitmap videoThumb(Context ctx, MediaItem item) throws Exception {
        // MediaStore thumbnail first (fast, cached by the OS)
        try {
            Bitmap b = MediaStore.Video.Thumbnails.getThumbnail(ctx.getContentResolver(), item.id,
                    MediaStore.Video.Thumbnails.MINI_KIND, null);
            if (b != null) return scale(b);
        } catch (Throwable ignored) { }
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(ctx, item.uri);
            Bitmap b = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            return b == null ? null : scale(b);
        } finally { try { r.release(); } catch (Throwable ignored) { } }
    }

    private static Bitmap audioArt(Context ctx, MediaItem item) throws Exception {
        if (item.albumId > 0) {
            Uri artUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), item.albumId);
            try (InputStream in = ctx.getContentResolver().openInputStream(artUri)) {
                if (in != null) {
                    Bitmap b = BitmapFactory.decodeStream(in);
                    if (b != null) return scale(b);
                }
            } catch (Throwable ignored) { }
        }
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(ctx, item.uri);
            byte[] pic = r.getEmbeddedPicture();
            if (pic != null) return scale(BitmapFactory.decodeByteArray(pic, 0, pic.length));
        } finally { try { r.release(); } catch (Throwable ignored) { } }
        return null;
    }

    private static Bitmap scale(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int max = 480;
        if (w <= max && h <= max) return b;
        float f = Math.min((float) max / w, (float) max / h);
        return Bitmap.createScaledBitmap(b, Math.max(1, Math.round(w * f)), Math.max(1, Math.round(h * f)), true);
    }

    public static void clearCache() { CACHE.evictAll(); }
}
