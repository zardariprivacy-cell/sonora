package com.nawazishali.sonora;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Programmatic row/card builders shared by Home, Folders and Storage screens. */
public class Rows {

    public static LinearLayout mediaRow(final Context ctx, final MediaItem it,
                                        View.OnClickListener click, View.OnLongClickListener longClick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(8));
        row.setBackground(Ui.cardBg());
        row.setElevation(Ui.dp(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(8));
        row.setLayoutParams(lp);

        ImageView thumb = new ImageView(ctx);
        thumb.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(52), Ui.dp(52)));
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundColor(Theme.ELEVATED);
        ImageLoader.load(ctx, it, thumb);
        row.addView(thumb);

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tlp.setMargins(Ui.dp(12), 0, Ui.dp(6), 0);
        texts.setLayoutParams(tlp);
        TextView title = Ui.text(ctx, it.title, 14.5f, Theme.TEXT, false);
        title.setMaxLines(1); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);
        texts.addView(Ui.sub(ctx, it.subtitle()));
        row.addView(texts);

        // v3.3 music vibe: the currently-playing song gets mini EQ bars
        // (animated while playing, frozen mid-dance while paused).
        // v3.4: guarded like MediaAdapter — decoration degrades silently.
        try {
            if (!it.video && MusicService.instance != null && MusicService.instance.current() != null
                    && it.key().equals(MusicService.instance.current().key())) {
                AnimatedBarsView eq = new AnimatedBarsView(ctx);
                LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(Ui.dp(22), Ui.dp(16));
                elp.setMargins(0, 0, Ui.dp(8), 0);
                eq.setLayoutParams(elp);
                eq.setAnimating(MusicService.instance.isPlaying());
                row.addView(eq);
            }
        } catch (Throwable ignored) { }
        boolean isFavRow = false;
        try { isFavRow = Db.get(ctx).isFavorite(it.key()); } catch (Throwable ignored) { }
        if (isFavRow) {
            ImageView fav = Ui.icon(ctx, R.drawable.ic_fav, 18, Theme.ACCENT);
            row.addView(fav);
        }
        // Overflow button (v3.1): opens the same 3D MediaSheet as long-press.
        // v3.4 audit fix: vault rows skip it — a vault item has no playable
        // uri (it is encrypted), so the standard sheet's Play/Share/Rename/
        // Delete/Extract rows cannot work on it. The vault's own menu
        // (Play / Export / Delete) stays on the row's long-press instead.
        if (!it.vault) {
            ImageView more = new ImageView(ctx);
            more.setImageResource(R.drawable.ic_more);
            more.setColorFilter(Theme.TEXT2);
            more.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(2), Ui.dp(8));
            more.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(36), Ui.dp(36)));
            more.setOnClickListener(v -> {
                if (ctx instanceof android.app.Activity) ItemMenu.show((android.app.Activity) ctx, it, null);
            });
            PressFx.apply(more);
            row.addView(more);
        }
        if (click != null) row.setOnClickListener(click);
        if (longClick != null) row.setOnLongClickListener(longClick);
        return row;
    }

    // ---------- "Me"-style elements: big icon grid + clean icon list rows ----------
    // Airy layout in the spirit of the user's reference screenshots, drawn with
    // Sonora's own vector icons in the Midnight Violet palette. No cards in the
    // grid; list rows are flat with thin dividers.

    /** One big-icon grid cell: large icon in a colourful gradient badge, label underneath. */
    public static LinearLayout gridCell(Context ctx, int iconRes, String label, View.OnClickListener click) {
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(Ui.dp(4), Ui.dp(14), Ui.dp(4), Ui.dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        cell.setLayoutParams(lp);
        cell.addView(Ui.iconBadge(ctx, iconRes, label.hashCode(), 52, 26));
        TextView t = Ui.text(ctx, label, 13, Theme.TEXT, false);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        t.setPadding(0, Ui.dp(8), 0, 0);
        cell.addView(t);
        if (click != null) cell.setOnClickListener(click);
        PressFx.apply(cell);
        return cell;
    }

    /** A horizontal row of grid cells (pass 3 for the reference 3-column look). */
    public static LinearLayout gridRow(Context ctx, View... cells) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        for (View c : cells) row.addView(c);
        return row;
    }

    /** Flat list row: left icon + label (+ optional trailing state text), no card. */
    public static LinearLayout menuRow(Context ctx, int iconRes, String label, String trailing,
                                       View.OnClickListener click) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(6), Ui.dp(15), Ui.dp(6), Ui.dp(15));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(Ui.iconBadge(ctx, iconRes, label.hashCode(), 36, 19));
        TextView t = Ui.text(ctx, label, 15.5f, Theme.TEXT, false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tlp.setMargins(Ui.dp(16), 0, 0, 0);
        t.setLayoutParams(tlp);
        row.addView(t);
        if (trailing != null && !trailing.isEmpty()) {
            row.addView(Ui.text(ctx, trailing, 13, Theme.TEXT2, false));
        }
        if (click != null) row.setOnClickListener(click);
        PressFx.apply(row);
        return row;
    }

    /** Thin divider under a menu row, aligned with the label (like the reference). */
    public static View menuDivider(Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(Theme.BORDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(Ui.dp(58), 0, 0, 0);
        v.setLayoutParams(lp);
        return v;
    }

    /** Thick soft break between a grid block and a list block. */
    public static View sectionBreak(Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(Theme.ELEVATED);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(8));
        lp.setMargins(0, Ui.dp(10), 0, Ui.dp(4));
        v.setLayoutParams(lp);
        return v;
    }

    public static LinearLayout settingRow(Context ctx, int iconRes, String title, String subtitle,
                                          View.OnClickListener click) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(14), Ui.dp(13), Ui.dp(14), Ui.dp(13));
        row.setBackground(Ui.cardBg());
        row.setElevation(Ui.dp(3));
        row.setElevation(Ui.dp(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(8));
        row.setLayoutParams(lp);
        row.addView(Ui.iconBadge(ctx, iconRes, title.hashCode(), 38, 20));
        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tlp.setMargins(Ui.dp(12), 0, 0, 0);
        texts.setLayoutParams(tlp);
        texts.addView(Ui.text(ctx, title, 15, Theme.TEXT, false));
        if (subtitle != null && !subtitle.isEmpty()) texts.addView(Ui.sub(ctx, subtitle));
        row.addView(texts);
        row.addView(Ui.text(ctx, "›", 22, Theme.TEXT2, false));
        if (click != null) row.setOnClickListener(click);
        return row;
    }
}
