package com.nawazishali.sonora;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Small programmatic-UI helpers so every screen shares the Midnight Violet look. */
public class Ui {
    public static float density = 2f;

    public static void init(Context ctx) { density = ctx.getResources().getDisplayMetrics().density; }
    public static int dp(int v) { return Math.round(v * density); }

    public static GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    public static GradientDrawable cardBg() {
        GradientDrawable g = rounded(Theme.CARD, 16);
        g.setStroke(dp(1), Theme.BORDER);
        return g;
    }

    public static GradientDrawable gradientBg(int radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.PRIMARY, Theme.ACCENT});
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    public static TextView title(Context c, String s) { return text(c, s, 20, Theme.TEXT, true); }
    public static TextView section(Context c, String s) {
        TextView t = text(c, s, 16, Theme.TEXT, true);
        t.setPadding(0, dp(18), 0, dp(8));
        return t;
    }
    public static TextView body(Context c, String s) { return text(c, s, 14, Theme.TEXT, false); }
    public static TextView sub(Context c, String s) {
        TextView t = text(c, s, 12.5f, Theme.TEXT2, false);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    public static ImageView icon(Context c, int res, int sizeDp, int tint) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setColorFilter(tint);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }

    /** A tappable rounded "chip" button with icon + label. */
    public static LinearLayout chip(Context c, int iconRes, String label) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER);
        l.setBackground(rounded(Theme.ELEVATED, 20));
        l.setPadding(dp(14), dp(8), dp(14), dp(8));
        if (iconRes != 0) {
            ImageView iv = icon(c, iconRes, 18, Theme.TEXT);
            l.addView(iv);
        }
        TextView t = text(c, label, 13.5f, Theme.TEXT, false);
        if (iconRes != 0) t.setPadding(dp(7), 0, 0, 0);
        l.addView(t);
        return l;
    }

    public static View spacer(Context c, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(hDp)));
        return v;
    }

    public static String fmtDuration(long ms) {
        if (ms <= 0) return "0:00";
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        return String.format(Locale.US, "%d:%02d", m, sec);
    }

    public static String fmtSize(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] u = {"B", "KB", "MB", "GB", "TB"};
        int i = 0; double v = bytes;
        while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
        return String.format(Locale.US, v >= 100 ? "%.0f %s" : "%.1f %s", v, u[i]);
    }

    public static String fmtDate(long seconds) {
        if (seconds <= 0) return "";
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d MMM yyyy", Locale.US);
        return f.format(new java.util.Date(seconds * 1000L));
    }
}
