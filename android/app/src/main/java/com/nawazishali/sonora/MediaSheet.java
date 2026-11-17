package com.nawazishali.sonora;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The Sonora "3D box" (v3.1): a raised bottom sheet that opens when a media
 * item's ⋮ overflow is tapped or the item is long-pressed. Strong elevation
 * shadow, rounded top corners, a subtle gradient header with the item's
 * artwork + real metadata, and every action as an icon row underneath.
 * Slides up with a window animation, dims the background, dismisses on
 * outside tap or a drag-down on the header. Framework-native only.
 *
 * Every row performs the real action through the existing feature code
 * (ItemMenu / MediaActions / AudioExtractor / Db) — no dead rows.
 */
public class MediaSheet {

    // ---------------------------------------------------------------- media
    public static void show(final Activity act, final MediaItem it, final Runnable onChanged) {
        final Dialog dialog = new Dialog(act);
        LinearLayout sheet = buildShell(act, dialog);

        // Header: artwork + title + real metadata on a subtle gradient band
        LinearLayout header = headerBand(act);
        ImageView thumb = new ImageView(act);
        thumb.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(58), Ui.dp(58)));
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackground(Ui.rounded(Theme.ELEVATED, 12));
        ImageLoader.load(act, it, thumb);
        header.addView(thumb);
        LinearLayout texts = new LinearLayout(act);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tlp.setMargins(Ui.dp(14), 0, 0, 0);
        texts.setLayoutParams(tlp);
        TextView title = Ui.text(act, it.title, 16, Theme.TEXT, true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);
        StringBuilder meta = new StringBuilder();
        meta.append(Ui.fmtDuration(it.duration)).append("  •  ").append(Ui.fmtSize(it.size));
        String fmt = formatLabel(it);
        if (!fmt.isEmpty()) meta.append("  •  ").append(fmt);
        texts.addView(Ui.sub(act, meta.toString()));
        if (it.path != null && !it.path.isEmpty()) {
            TextView p = Ui.text(act, it.path, 11, Theme.TEXT2, false);
            p.setMaxLines(1);
            p.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            texts.addView(p);
        }
        header.addView(texts);
        sheet.addView(header);

        final Db db = Db.get(act);
        final boolean fav = db.isFavorite(it.key());
        List<View> rows = new ArrayList<>();

        if (it.video) {
            rows.add(actionRow(act, dialog, R.drawable.ic_play, "Play", false,
                    () -> ItemMenu.playVideo(act, it)));
            rows.add(actionRow(act, dialog, R.drawable.ic_pip, "Play in Picture-in-Picture", false,
                    () -> ItemMenu.playVideoPip(act, it)));
            rows.add(actionRow(act, dialog, R.drawable.ic_fav,
                    fav ? "Remove from Favorites" : "Add to Favorites", false,
                    () -> toggleFavorite(act, it, fav, onChanged)));
            rows.add(actionRow(act, dialog, R.drawable.ic_extract, "Extract Audio (M4A)", false,
                    () -> ItemMenu.extractAudio(act, it)));
        } else {
            rows.add(actionRow(act, dialog, R.drawable.ic_play, "Play", false,
                    () -> ItemMenu.playAudio(act, single(it), 0)));
            rows.add(actionRow(act, dialog, R.drawable.ic_next, "Play Next", false,
                    () -> ItemMenu.playNext(act, it)));
            rows.add(actionRow(act, dialog, R.drawable.ic_add, "Add to Queue", false,
                    () -> ItemMenu.addToQueue(act, it)));
            rows.add(actionRow(act, dialog, R.drawable.ic_list, "Add to Playlist", false,
                    () -> ItemMenu.playlistPicker(act, it)));
            rows.add(actionRow(act, dialog, R.drawable.ic_fav,
                    fav ? "Remove from Favorites" : "Add to Favorites", false,
                    () -> toggleFavorite(act, it, fav, onChanged)));
        }
        rows.add(actionRow(act, dialog, R.drawable.ic_share, "Share", false,
                () -> MediaActions.share(act, it)));
        rows.add(actionRow(act, dialog, R.drawable.ic_upload, "Open with…", false,
                () -> MediaActions.openWith(act, it)));
        rows.add(actionRow(act, dialog, R.drawable.ic_edit, "Rename", false,
                () -> ItemMenu.renameDialog(act, it, onChanged)));
        rows.add(actionRow(act, dialog, R.drawable.ic_info, "Details", false,
                () -> ItemMenu.detailsDialog(act, it)));
        rows.add(actionRow(act, dialog, R.drawable.ic_delete, "Delete", true,
                () -> ItemMenu.deleteDialog(act, it, onChanged)));
        addRows(act, sheet, rows);
        sheet.addView(Ui.spacer(act, 8));

        finish(act, dialog, sheet, header);
    }

    // ---------------------------------------------------------------- folder
    public static void showFolder(final Activity act, final Library.Folder f, final Runnable open) {
        final Dialog dialog = new Dialog(act);
        LinearLayout sheet = buildShell(act, dialog);

        LinearLayout header = headerBand(act);
        LinearLayout badge = new LinearLayout(act);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.rounded(Theme.ELEVATED, 12));
        badge.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(58), Ui.dp(58)));
        badge.addView(Ui.icon(act, R.drawable.ic_folder, 32, Theme.SECONDARY));
        header.addView(badge);
        LinearLayout texts = new LinearLayout(act);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tlp.setMargins(Ui.dp(14), 0, 0, 0);
        texts.setLayoutParams(tlp);
        TextView title = Ui.text(act, f.name, 16, Theme.TEXT, true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);
        texts.addView(Ui.sub(act, countsLine(f) + "  •  " + Ui.fmtSize(f.totalSize())));
        header.addView(texts);
        sheet.addView(header);

        List<View> rows = new ArrayList<>();
        rows.add(actionRow(act, dialog, R.drawable.ic_folder, "Open", false, open));
        rows.add(actionRow(act, dialog, R.drawable.ic_info, "Details", false, () -> {
            String msg = "Folder: " + f.name + "\n"
                    + "Songs: " + f.audioCount + "  (" + Ui.fmtSize(f.audioSize) + ")\n"
                    + "Videos: " + f.videoCount + "  (" + Ui.fmtSize(f.videoSize) + ")\n"
                    + "Total size: " + Ui.fmtSize(f.totalSize());
            new AlertDialog.Builder(act)
                    .setTitle("Folder details")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show();
        }));
        addRows(act, sheet, rows);
        sheet.addView(Ui.spacer(act, 8));

        finish(act, dialog, sheet, header);
    }

    private static String countsLine(Library.Folder f) {
        StringBuilder sb = new StringBuilder();
        if (f.audioCount > 0) sb.append(f.audioCount).append(" songs");
        if (f.videoCount > 0) {
            if (sb.length() > 0) sb.append("  •  ");
            sb.append(f.videoCount).append(" videos");
        }
        return sb.length() == 0 ? "Empty folder" : sb.toString();
    }

    // ---------------------------------------------------------------- shell
    /** Sheet container with the raised 3D look; returns the column to fill. */
    private static LinearLayout buildShell(Activity act, Dialog dialog) {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet = new LinearLayout(act);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Theme.ELEVATED, Theme.CARD});
        float r = Ui.dp(22);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setStroke(Ui.dp(1), Theme.BORDER);
        sheet.setBackground(bg);
        sheet.setElevation(Ui.dp(18)); // the real shadow that makes it a "3D box"

        // Grab handle
        LinearLayout handleBox = new LinearLayout(act);
        handleBox.setGravity(Gravity.CENTER);
        handleBox.setPadding(0, Ui.dp(10), 0, Ui.dp(2));
        View handle = new View(act);
        handle.setBackground(Ui.rounded(Theme.BORDER, 3));
        handle.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(5)));
        handleBox.addView(handle);
        sheet.addView(handleBox);
        return sheet;
    }

    private static LinearLayout headerBand(Activity act) {
        LinearLayout header = new LinearLayout(act);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(18), Ui.dp(12), Ui.dp(18), Ui.dp(14));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{withAlpha(Theme.PRIMARY, 110), withAlpha(Theme.ACCENT, 60), withAlpha(Theme.CARD, 0)});
        header.setBackground(g);
        return header;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static void finish(final Activity act, final Dialog dialog, final LinearLayout sheet, View dragArea) {
        // Padded window so the elevation shadow has room to fall on the dim
        FrameLayout outer = new FrameLayout(act);
        outer.setPadding(0, Ui.dp(22), 0, 0);
        outer.addView(sheet);
        dialog.setContentView(outer);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.55f);
            w.setWindowAnimations(R.style.SonoraSheetAnim);
        }
        dialog.setCanceledOnTouchOutside(true);
        attachDragDismiss(act, dialog, sheet, dragArea);
        if (sheet.getChildCount() > 0) attachDragDismiss(act, dialog, sheet, sheet.getChildAt(0)); // grab handle
        dialog.show();
    }

    /** Drag the header down to dismiss; release early and it springs back. */
    private static void attachDragDismiss(Activity act, final Dialog dialog, final LinearLayout sheet, View dragArea) {
        final float[] startY = {0f};
        final boolean[] dragging = {false};
        View.OnTouchListener tl = (v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startY[0] = e.getRawY() - sheet.getTranslationY();
                    dragging[0] = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (dragging[0]) {
                        float dy = e.getRawY() - startY[0];
                        sheet.setTranslationY(Math.max(0, dy));
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging[0] = false;
                    if (sheet.getTranslationY() > Ui.dp(70)) dialog.dismiss();
                    else sheet.animate().translationY(0).setDuration(160).start();
                    break;
                default:
                    break;
            }
            return true;
        };
        dragArea.setOnTouchListener(tl);
    }

    // ---------------------------------------------------------------- rows
    private static LinearLayout actionRow(final Activity act, final Dialog dialog, int iconRes,
                                          String label, boolean danger, final Runnable action) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(18), Ui.dp(13), Ui.dp(18), Ui.dp(13));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        int tint = danger ? 0xFFFF7A93 : Theme.SECONDARY;
        row.addView(Ui.icon(act, iconRes, 22, tint));
        TextView t = Ui.text(act, label, 15, danger ? 0xFFFF7A93 : Theme.TEXT, false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tlp.setMargins(Ui.dp(16), 0, 0, 0);
        t.setLayoutParams(tlp);
        row.addView(t);
        PressFx.apply(row);
        row.setOnClickListener(v -> {
            dialog.dismiss();
            if (action != null) action.run();
        });
        return row;
    }

    private static void addRows(Activity act, LinearLayout sheet, List<View> rows) {
        for (int i = 0; i < rows.size(); i++) {
            sheet.addView(rows.get(i));
            if (i < rows.size() - 1) {
                View div = new View(act);
                div.setBackgroundColor(Theme.BORDER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                lp.setMargins(Ui.dp(56), 0, Ui.dp(18), 0);
                div.setLayoutParams(lp);
                sheet.addView(div);
            }
        }
    }

    // ---------------------------------------------------------------- helpers
    private static void toggleFavorite(Activity act, MediaItem it, boolean wasFav, Runnable onChanged) {
        Db.get(act).setFavorite(it, !wasFav);
        Toast.makeText(act, wasFav ? "Removed from favorites" : "Added to favorites",
                Toast.LENGTH_SHORT).show();
        if (onChanged != null) onChanged.run();
    }

    private static String formatLabel(MediaItem it) {
        if (it.mime != null && it.mime.contains("/")) {
            String sub = it.mime.substring(it.mime.indexOf('/') + 1).trim().toUpperCase();
            if (!sub.isEmpty() && sub.length() <= 10) return sub;
        }
        if (it.path != null && it.path.contains(".")) {
            String ext = it.path.substring(it.path.lastIndexOf('.') + 1).trim().toUpperCase();
            if (!ext.isEmpty() && ext.length() <= 6 && !ext.contains("/")) return ext;
        }
        return "";
    }

    private static List<MediaItem> single(MediaItem it) {
        List<MediaItem> l = new ArrayList<>();
        l.add(it);
        return l;
    }
}
