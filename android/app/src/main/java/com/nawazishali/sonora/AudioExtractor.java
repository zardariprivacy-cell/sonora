package com.nawazishali.sonora;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Video -> audio extraction. Android has no built-in MP3 encoder, so the audio
 * track is copied (no re-encode) into an .m4a container — labelled honestly in
 * the UI as "Extract Audio (M4A)". Runs on a background thread.
 */
public class AudioExtractor {
    public interface Callback { void onDone(boolean ok, String message); }

    public static void extract(final Context ctx, final MediaItem video, final Callback cb) {
        new Thread(() -> {
            MediaExtractor ex = null;
            MediaMuxer mux = null;
            File tmp = null;
            try {
                ex = new MediaExtractor();
                ex.setDataSource(ctx, video.uri, null);
                int audioTrack = -1;
                MediaFormat fmt = null;
                for (int i = 0; i < ex.getTrackCount(); i++) {
                    MediaFormat f = ex.getTrackFormat(i);
                    String mime = f.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("audio/")) { audioTrack = i; fmt = f; break; }
                }
                if (audioTrack < 0) { post(cb, false, "This video has no audio track"); return; }
                ex.selectTrack(audioTrack);

                String base = sanitize(video.title);
                tmp = new File(ctx.getCacheDir(), base + ".m4a");
                mux = new MediaMuxer(tmp.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                int outTrack = mux.addTrack(fmt);
                mux.start();

                ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                while (true) {
                    info.offset = 0;
                    info.size = ex.readSampleData(buffer, 0);
                    if (info.size < 0) break;
                    info.presentationTimeUs = ex.getSampleTime();
                    info.flags = ex.getSampleFlags();
                    mux.writeSampleData(outTrack, buffer, info);
                    ex.advance();
                }
                mux.stop();
                mux.release(); mux = null;

                // Publish into the Music library (MediaStore Audio, Music folder on 29+)
                Uri inserted = publishToLibrary(ctx, tmp, base, video.duration);
                tmp.delete();
                if (inserted != null) post(cb, true, "Audio saved to your music library");
                else post(cb, false, "Could not save the extracted audio");
            } catch (Exception e) {
                if (tmp != null) tmp.delete();
                post(cb, false, "Extraction failed: " + e.getMessage());
            } finally {
                if (mux != null) try { mux.release(); } catch (Exception ignored) { }
                if (ex != null) ex.release();
            }
        }, "sonora-extract").start();
    }

    private static Uri publishToLibrary(Context ctx, File tmp, String base, long durationMs) throws Exception {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Audio.Media.DISPLAY_NAME, base + ".m4a");
        v.put(MediaStore.Audio.Media.TITLE, base);
        v.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4");
        v.put(MediaStore.Audio.Media.DURATION, durationMs);
        if (Build.VERSION.SDK_INT >= 29) {
            v.put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Sonora");
            v.put(MediaStore.Audio.Media.IS_PENDING, 1);
        }
        Uri uri = ctx.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) return null;
        try (OutputStream out = ctx.getContentResolver().openOutputStream(uri);
             FileInputStream in = new FileInputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            v.clear(); v.put(MediaStore.Audio.Media.IS_PENDING, 0);
            ctx.getContentResolver().update(uri, v, null, null);
        }
        return uri;
    }

    private static String sanitize(String s) {
        if (s == null || s.trim().isEmpty()) return "Sonora audio";
        String out = s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (out.length() > 60) out = out.substring(0, 60);
        return out.isEmpty() ? "Sonora audio" : out;
    }

    private static void post(Callback cb, boolean ok, String msg) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> cb.onDone(ok, msg));
    }
}
