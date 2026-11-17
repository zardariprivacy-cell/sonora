package com.nawazishali.sonora;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import android.view.View;

/**
 * Music spectrum visualizer driven by the framework Visualizer (FFT of the
 * player's audio session). If the permission/session is unavailable it falls
 * back to a gentle idle animation — never crashes.
 */
public class VisualizerView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Visualizer visualizer;
    private byte[] fft;
    private float[] levels = new float[24];
    private long start = System.currentTimeMillis();
    private boolean fallback = false;

    public VisualizerView(Context c) {
        super(c);
        paint.setStyle(Paint.Style.FILL);
    }

    public void attach(int audioSessionId) {
        release();
        if (audioSessionId <= 0) { fallback = true; return; }
        try {
            visualizer = new Visualizer(audioSessionId);
            visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer v, byte[] w, int rate) { }
                @Override public void onFftDataCapture(Visualizer v, byte[] f, int rate) {
                    fft = f;
                    int n = levels.length;
                    for (int i = 0; i < n; i++) {
                        int idx = 2 + i * 2;
                        if (idx + 1 < f.length) {
                            double mag = Math.hypot(f[idx], f[idx + 1]) / 128.0;
                            levels[i] = (float) Math.min(1.0, mag);
                        }
                    }
                    postInvalidate();
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);
            visualizer.setEnabled(true);
            fallback = false;
        } catch (Throwable t) {
            visualizer = null;
            fallback = true;
        }
    }

    public void release() {
        if (visualizer != null) {
            try { visualizer.setEnabled(false); visualizer.release(); } catch (Throwable ignored) { }
            visualizer = null;
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        int bars = levels.length;
        float bw = (float) w / (bars * 2 - 1);
        long t = System.currentTimeMillis() - start;
        for (int i = 0; i < bars; i++) {
            double frac;
            if (fallback) frac = 0.25 + 0.55 * Math.abs(Math.sin(t / 500.0 + i * 0.7));
            else frac = Math.max(0.06, levels[i]);
            int bh = (int) (h * frac);
            float x = i * bw * 2;
            paint.setColor(i % 3 == 0 ? Theme.ACCENT : Theme.PRIMARY);
            canvas.drawRoundRect(x, h - bh, x + bw, h, bw / 2f, bw / 2f, paint);
        }
        if (fallback) postInvalidateDelayed(50);
    }

    @Override protected void onDetachedFromWindow() { release(); super.onDetachedFromWindow(); }
}
