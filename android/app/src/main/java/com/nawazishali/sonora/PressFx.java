package com.nawazishali.sonora;

import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * Tactile "3D press" feedback (v3.1): on touch-down the view scales down
 * slightly and its elevation shadow drops (as if pressed into the surface);
 * on release it springs back with a small overshoot. Framework-native
 * (ViewPropertyAnimator only), no dependencies. The touch listener always
 * returns false, so clicks, long-presses and parent scrolling keep working
 * exactly as before.
 */
public class PressFx {

    public static void apply(final View v) {
        if (v == null) return;
        v.setOnTouchListener((view, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().cancel();
                    view.animate()
                            .scaleX(0.93f).scaleY(0.93f)
                            .translationZ(0f)
                            .setDuration(90)
                            .setInterpolator(null)
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().cancel();
                    view.animate()
                            .scaleX(1f).scaleY(1f)
                            .translationZ(Ui.dp(2))
                            .setDuration(230)
                            .setInterpolator(new OvershootInterpolator(2.0f))
                            .start();
                    break;
                default:
                    break;
            }
            return false; // never consume: the view's own click handling proceeds
        });
    }
}
