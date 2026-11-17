package com.nawazishali.sonora;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;

/**
 * The user's Sonora profile (v3.2, modelled on the reference design's header):
 * a display name persisted in the settings table and an optional photo copied
 * into app-private storage. Everything is local and real — the greeting is
 * computed from the actual time of day.
 */
public class Profile {
    private static final String KEY_NAME = "profile_name";
    private static final String PHOTO_FILE = "avatar.jpg";
    public static final String DEFAULT_NAME = "Sonora User";

    private static Bitmap cachedPhoto;
    private static long cachedPhotoStamp = -1;

    public static String name(Context ctx) {
        String n = Db.get(ctx).getSetting(KEY_NAME, "");
        return n == null || n.trim().isEmpty() ? DEFAULT_NAME : n.trim();
    }

    public static void setName(Context ctx, String name) {
        Db.get(ctx).setSetting(KEY_NAME, name == null ? "" : name.trim());
    }

    /** "Good Morning!" / "Good Afternoon!" / "Good Evening!" from the real clock. */
    public static String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 12) return "Good Morning!";
        if (h < 17) return "Good Afternoon!";
        return "Good Evening!";
    }

    public static File photoFile(Context ctx) {
        return new File(ctx.getFilesDir(), PHOTO_FILE);
    }

    public static boolean hasPhoto(Context ctx) {
        File f = photoFile(ctx);
        return f.exists() && f.length() > 0;
    }

    /** The stored photo, circle-cropped; null when none. Cached by file stamp. */
    public static Bitmap photoBitmap(Context ctx) {
        File f = photoFile(ctx);
        if (!f.exists() || f.length() == 0) { cachedPhoto = null; cachedPhotoStamp = -1; return null; }
        long stamp = f.lastModified();
        if (cachedPhoto != null && stamp == cachedPhotoStamp) return cachedPhoto;
        Bitmap raw = BitmapFactory.decodeFile(f.getAbsolutePath());
        cachedPhoto = raw == null ? null : Ui.circleBitmap(raw);
        cachedPhotoStamp = stamp;
        return cachedPhoto;
    }

    /** Copy a picked image into private storage, downscaled to at most 512px. */
    public static boolean savePhoto(Context ctx, Uri uri) {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return false;
            Bitmap raw = BitmapFactory.decodeStream(in);
            if (raw == null) return false;
            int max = 512;
            Bitmap scaled = raw;
            if (raw.getWidth() > max || raw.getHeight() > max) {
                float f = Math.min((float) max / raw.getWidth(), (float) max / raw.getHeight());
                scaled = Bitmap.createScaledBitmap(raw,
                        Math.max(1, Math.round(raw.getWidth() * f)),
                        Math.max(1, Math.round(raw.getHeight() * f)), true);
            }
            try (FileOutputStream out = new FileOutputStream(photoFile(ctx))) {
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, out);
            }
            cachedPhoto = null; cachedPhotoStamp = -1;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void clearPhoto(Context ctx) {
        File f = photoFile(ctx);
        if (f.exists()) f.delete();
        cachedPhoto = null; cachedPhotoStamp = -1;
    }

    /**
     * A round avatar of the given size: the user's photo when set, otherwise a
     * primary→accent gradient circle with the name's first letter — the same
     * treatment the reference design gives its profile picture.
     */
    public static FrameLayout avatarView(Context ctx, int sizeDp) {
        FrameLayout box = new FrameLayout(ctx);
        int px = Ui.dp(sizeDp);
        box.setLayoutParams(new android.widget.LinearLayout.LayoutParams(px, px));

        ImageView disc = new ImageView(ctx);
        disc.setBackground(Ui.circleGradient());
        disc.setLayoutParams(new FrameLayout.LayoutParams(px, px));
        box.addView(disc);

        String n = name(ctx);
        TextView initial = new TextView(ctx);
        initial.setText(n.isEmpty() ? "S" : n.substring(0, 1).toUpperCase());
        initial.setTextColor(0xFFFFFFFF);
        initial.setTextSize(sizeDp * 0.42f);
        initial.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        initial.setGravity(Gravity.CENTER);
        initial.setLayoutParams(new FrameLayout.LayoutParams(px, px));
        box.addView(initial);

        Bitmap photo = photoBitmap(ctx);
        if (photo != null) {
            ImageView img = new ImageView(ctx);
            img.setImageBitmap(photo);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setLayoutParams(new FrameLayout.LayoutParams(px, px));
            box.addView(img);
        }
        return box;
    }
}
