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

        if (Db.get(ctx).isFavorite(it.key())) {
            ImageView fav = Ui.icon(ctx, R.drawable.ic_fav, 18, Theme.ACCENT);
            row.addView(fav);
        }
        if (click != null) row.setOnClickListener(click);
        if (longClick != null) row.setOnLongClickListener(longClick);
        return row;
    }

    public static LinearLayout settingRow(Context ctx, int iconRes, String title, String subtitle,
                                          View.OnClickListener click) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(14), Ui.dp(13), Ui.dp(14), Ui.dp(13));
        row.setBackground(Ui.cardBg());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(8));
        row.setLayoutParams(lp);
        row.addView(Ui.icon(ctx, iconRes, 22, Theme.SECONDARY));
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
