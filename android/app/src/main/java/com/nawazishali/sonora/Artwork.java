package com.nawazishali.sonora;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Generated placeholder artwork (v3.3): a song or video without real artwork
 * must never show a blank square again. The placeholder is a colourful
 * gradient — its hue family picked deterministically from the item key, so a
 * given song always gets the same art — with a white music-note (or play)
 * glyph and a subtle equalizer-bars motif along the bottom. Drawn once per
 * item and cached by ImageLoader's LruCache.
 */
public class Artwork {

    public static Bitmap placeholder(Context ctx, MediaItem item, int sizePx) {
        int size = Math.max(48, sizePx);
        int hash = item.key().hashCode();
        int[] g = Ui.gradientFor(hash);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        // Base gradient, diagonal.
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, size, size, g[0], g[1], Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, size, size, p);

        // Soft light bloom, top-left.
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setShader(new RadialGradient(size * 0.24f, size * 0.16f, size * 0.75f,
                0x3DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, size, size, glow);

        // Deepen the bottom so the EQ motif reads.
        Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        shade.setShader(new LinearGradient(0, size * 0.55f, 0, size,
                0x00000000, 0x38000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, size, size, shade);

        // Glyph: music note for audio, play triangle for video.
        try {
            Drawable d = ctx.getResources().getDrawable(
                    item.video ? R.drawable.ic_play : R.drawable.ic_music, ctx.getTheme());
            if (d != null) {
                d = d.mutate();
                d.setTint(0xF2FFFFFF);
                int gs = Math.round(size * 0.42f);
                int gx = (size - gs) / 2, gy = Math.round(size * 0.20f);
                d.setBounds(gx, gy, gx + gs, gy + gs);
                d.draw(canvas);
            }
        } catch (Throwable ignored) { }

        // Equalizer-bars motif along the bottom (audio only).
        if (!item.video) {
            Paint bars = new Paint(Paint.ANTI_ALIAS_FLAG);
            bars.setColor(0xA6FFFFFF);
            int n = 5;
            float bw = size * 0.075f;
            float gap = size * 0.045f;
            float total = n * bw + (n - 1) * gap;
            float x = (size - total) / 2f;
            float base = size * 0.90f;
            float maxH = size * 0.20f;
            for (int i = 0; i < n; i++) {
                double frac = 0.35 + 0.65 * Math.abs(Math.sin((hash % 97) * 0.35 + i * 0.95));
                float bh = (float) (maxH * frac);
                canvas.drawRoundRect(x, base - bh, x + bw, base, bw / 2f, bw / 2f, bars);
                x += bw + gap;
            }
        }
        return bmp;
    }
}
