package com.nawazishali.sonora;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Lightweight animated equalizer-bars decoration (splash screen + idle states). */
public class AnimatedBarsView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long start = System.currentTimeMillis();
    private boolean running = true;

    public AnimatedBarsView(Context c) {
        super(c);
        paint.setColor(Theme.PRIMARY);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        int bars = 5;
        int bw = w / (bars * 2 - 1);
        long t = System.currentTimeMillis() - start;
        for (int i = 0; i < bars; i++) {
            double phase = (t / 380.0) + i * 0.9;
            double frac = 0.35 + 0.65 * Math.abs(Math.sin(phase));
            int bh = (int) (h * frac);
            int x = i * bw * 2;
            paint.setColor(i % 2 == 0 ? Theme.PRIMARY : Theme.ACCENT);
            canvas.drawRoundRect(x, h - bh, x + bw, h, bw / 2f, bw / 2f, paint);
        }
        if (running) postInvalidateDelayed(40);
    }

    @Override protected void onDetachedFromWindow() { running = false; super.onDetachedFromWindow(); }
}
