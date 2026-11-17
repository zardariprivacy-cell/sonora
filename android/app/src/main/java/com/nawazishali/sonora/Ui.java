package com.nawazishali.sonora;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.widget.Toast;

import java.util.Locale;

/** Small programmatic-UI helpers so every screen shares the premium violet look (v3.2). */
public class Ui {
    public static float density = 2f;

    public static void init(Context ctx) { density = ctx.getResources().getDisplayMetrics().density; }
    public static int dp(int v) { return Math.round(v * density); }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    /**
     * The v3.2 app background: a deep-violet gradient — primary-tinted glow at
     * the top fading into the near-black violet base (#100D1C), with a soft
     * radial glow behind the header area. Static drawables only (no runtime
     * blur) so scrolling stays cheap. Light theme: a flat subtle gradient.
     * Returns a NEW drawable each call (drawables are not shared between views).
     */
    public static Drawable appBg() {
        if (Theme.LIGHT) {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Theme.GLOW, Theme.BG});
            return g;
        }
        GradientDrawable linear = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Theme.GLOW, Theme.BG, Theme.BG});
        GradientDrawable radial = new GradientDrawable();
        radial.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        radial.setGradientCenter(0.5f, 0.0f);
        radial.setGradientRadius(dp(430));
        radial.setColors(new int[]{withAlpha(Theme.PRIMARY, 105), withAlpha(Theme.PRIMARY, 0)});
        return new LayerDrawable(new Drawable[]{linear, radial});
    }

    /**
     * Glass card — v3.3: upgraded to the layered "3D box" treatment used across
     * the whole app: a Primary→Accent gradient edge, a fill lit from the top,
     * 22dp corners. Every card/row built on cardBg() gets the look at once.
     */
    public static Drawable cardBg() {
        return card3d(22);
    }

    /**
     * The shared layered 3D background: gradient border layer + top-lit fill.
     * Returns a NEW drawable each call (drawables are not shared between views).
     */
    public static Drawable card3d(int radiusDp) {
        GradientDrawable border = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{withAlpha(Theme.PRIMARY, Theme.LIGHT ? 165 : 185),
                        withAlpha(Theme.ACCENT, Theme.LIGHT ? 110 : 120)});
        border.setCornerRadius(dp(radiusDp));
        int top = Theme.LIGHT ? Theme.CARD : lighten(Theme.ELEVATED, 0.16f);
        int bottom = Theme.LIGHT ? Theme.ELEVATED : Theme.CARD;
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, bottom});
        fill.setCornerRadius(dp(Math.max(4, radiusDp - 2)));
        LayerDrawable ld = new LayerDrawable(new Drawable[]{border, fill});
        ld.setLayerInset(1, dp(2), dp(2), dp(2), dp(2));
        return ld;
    }

    /** Big centered-popup variant of the 3D card (MediaSheet / Dialogs). */
    public static Drawable dialogCardBg() {
        return card3d(26);
    }

    /** Mix a colour toward white by {@code f} (0..1). */
    public static int lighten(int color, float f) {
        return Color.rgb(
                Math.min(255, Math.round(Color.red(color) + (255 - Color.red(color)) * f)),
                Math.min(255, Math.round(Color.green(color) + (255 - Color.green(color)) * f)),
                Math.min(255, Math.round(Color.blue(color) + (255 - Color.blue(color)) * f)));
    }

    /**
     * The colourful gradient family (v3.3) — premium futuristic music vibe:
     * violet, pink, blue, teal, purple-pink, indigo-cyan. Rotated through by
     * index/hash for icon badges and generated artwork.
     */
    public static final int[][] GRADIENTS = {
            {0xFF9B6BFF, 0xFFC084FC},
            {0xFFF15BB5, 0xFF9B6BFF},
            {0xFF5B8CFF, 0xFF9B6BFF},
            {0xFF00C9A7, 0xFF5B8CFF},
            {0xFFB06BFF, 0xFFF15BB5},
            {0xFF6C5CE7, 0xFF00D2FF},
    };

    public static int[] gradientFor(int index) {
        return GRADIENTS[Math.abs(index) % GRADIENTS.length];
    }

    /** A colourful gradient squircle badge with a white glyph — grid cells, menu rows, folders. */
    public static LinearLayout iconBadge(Context c, int iconRes, int gradIndex, int sizeDp, int iconDp) {
        int[] g = gradientFor(gradIndex);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, g);
        bg.setCornerRadius(dp(Math.max(8, sizeDp * 2 / 5)));
        LinearLayout box = new LinearLayout(c);
        box.setGravity(Gravity.CENTER);
        box.setBackground(bg);
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        box.setElevation(dp(3));
        ImageView iv = new ImageView(c);
        iv.setImageResource(iconRes);
        iv.setColorFilter(0xFFFFFFFF);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)));
        box.addView(iv);
        return box;
    }

    /** Paint a TextView's text with the Primary→Accent brand gradient (SONORA wordmark). */
    public static void brandify(TextView tv) {
        String s = tv.getText() == null ? "" : tv.getText().toString();
        float w = Math.max(1f, tv.getPaint().measureText(s));
        tv.getPaint().setShader(new LinearGradient(0, 0, w, 0,
                Theme.PRIMARY, Theme.ACCENT, Shader.TileMode.CLAMP));
        tv.invalidate();
    }

    /** Multi-line secondary text for cards/empty states (never single-line truncated). */
    public static TextView bodyWrap(Context c, String s) {
        TextView t = text(c, s, 13, Theme.TEXT2, false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    /** Section header with a tiny gradient EQ-bars decoration (music vibe, v3.3). */
    public static LinearLayout sectionHeader(Context c, String s) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(18), 0, dp(8));
        AnimatedBarsView bars = new AnimatedBarsView(c);
        bars.setLayoutParams(new LinearLayout.LayoutParams(dp(17), dp(14)));
        row.addView(bars);
        TextView t = text(c, s, 16, Theme.TEXT, true);
        t.setPadding(dp(8), 0, 0, 0);
        row.addView(t);
        return row;
    }

    /** Primary gradient (Primary #9B6BFF → Accent #C084FC) for buttons and selected states. */
    public static GradientDrawable gradientBg(int radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Theme.PRIMARY, Theme.ACCENT});
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    /** Round gradient fill (avatars, round play buttons). */
    public static GradientDrawable circleGradient() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.PRIMARY, Theme.ACCENT});
        g.setShape(GradientDrawable.OVAL);
        return g;
    }

    /** Pill chip background: selected = primary gradient; unselected = glass card + border. */
    public static Drawable pillBg(boolean selected) {
        if (selected) return gradientBg(20);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Theme.LIGHT ? Theme.CARD : withAlpha(Theme.CARD, 200));
        g.setCornerRadius(dp(20));
        g.setStroke(dp(1), Theme.BORDER);
        return g;
    }

    /**
     * A glowing gradient ring: an oval gradient disc with a smaller solid disc
     * inset on top, leaving a ring of gradient visible around the edge. Place
     * artwork (circle-cropped, slightly smaller again) over the centre.
     */
    public static Drawable ringDrawable() {
        GradientDrawable outer = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.ACCENT, Theme.PRIMARY, Theme.ACCENT});
        outer.setShape(GradientDrawable.OVAL);
        GradientDrawable inner = new GradientDrawable();
        inner.setShape(GradientDrawable.OVAL);
        inner.setColor(Theme.LIGHT ? Theme.BG : Color.rgb(0x16, 0x10, 0x2A));
        LayerDrawable ld = new LayerDrawable(new Drawable[]{outer, inner});
        int inset = dp(5);
        ld.setLayerInset(1, inset, inset, inset, inset);
        return ld;
    }

    /** Bottom scrim for artwork cards: transparent fading to near-black. */
    public static GradientDrawable scrimBg(int radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, withAlpha(Color.BLACK, 178)});
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    /** Circle-crop a bitmap (centre square). Returns a new bitmap; input untouched. */
    public static Bitmap circleBitmap(Bitmap src) {
        if (src == null) return null;
        int size = Math.min(src.getWidth(), src.getHeight());
        if (size <= 0) return null;
        int x = (src.getWidth() - size) / 2, y = (src.getHeight() - size) / 2;
        Bitmap squared = Bitmap.createBitmap(src, x, y, size, size);
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new android.graphics.BitmapShader(squared, android.graphics.Shader.TileMode.CLAMP,
                android.graphics.Shader.TileMode.CLAMP));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        return out;
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

    /** A tappable glass pill "chip" button with icon + label. */
    public static LinearLayout chip(Context c, int iconRes, String label) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER);
        l.setBackground(pillBg(false));
        l.setPadding(dp(14), dp(8), dp(14), dp(8));
        if (iconRes != 0) {
            ImageView iv = icon(c, iconRes, 18, Theme.TEXT);
            l.addView(iv);
        }
        TextView t = text(c, label, 13.5f, Theme.TEXT, false);
        if (iconRes != 0) t.setPadding(dp(7), 0, 0, 0);
        l.addView(t);
        PressFx.apply(l);
        return l;
    }

    public static View spacer(Context c, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(hDp)));
        return v;
    }

    /** Share Sonora itself (name, tagline and the permanent APK link) via the system chooser. */
    public static void shareApp(Context c) {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(android.content.Intent.EXTRA_SUBJECT, "Sonora — Your Complete Media Experience");
        i.putExtra(android.content.Intent.EXTRA_TEXT,
                "Sonora — Your Complete Media Experience\n"
                        + "Offline video & music player with a private vault.\n"
                        + "Download the APK: https://drive.google.com/uc?id=17DexUZs_8GX_ODWNoGMEpmvU7QuFmMBt&export=download");
        try {
            c.startActivity(android.content.Intent.createChooser(i, "Share Sonora"));
        } catch (Exception e) {
            android.widget.Toast.makeText(c, "No app available to share with", Toast.LENGTH_SHORT).show();
        }
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
