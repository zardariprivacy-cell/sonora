package com.nawazishali.sonora;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The shared centered "3D box" dialog (v3.3). Every prompt in the app —
 * confirmations, details, pickers, text input, the Edit Profile form — is
 * built on this one helper: a gradient-bordered card floating in the middle
 * of the screen with a deep shadow, a pop-in animation and gradient buttons.
 * (Any AlertDialog that remains is still themed by the SonoraDialog theme.)
 */
public class Dialogs {
    public static final int BTN_PRIMARY = 0, BTN_GHOST = 1, BTN_DANGER = 2;

    public interface Choice { void onChoice(int index); }
    public interface Input { void onInput(String text); }
    public interface Action { void run(Dialog dialog); }

    // ------------------------------------------------------------ window

    /** Apply the centered-popup window setup. Call after setContentView. */
    public static void applyWindow(Activity act, Dialog dialog) {
        Window w = dialog.getWindow();
        if (w == null) return;
        w.setGravity(Gravity.CENTER);
        int width = Math.min(
                act.getResources().getDisplayMetrics().widthPixels - Ui.dp(28), Ui.dp(560));
        w.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setDimAmount(0.62f);
        w.setWindowAnimations(R.style.SonoraPopAnim);
        dialog.setCanceledOnTouchOutside(true);
    }

    /** A bare 3D card column to fill, inside a shadow-padded frame. */
    public static LinearLayout cardShell(Activity act, Dialog dialog) {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.dialogCardBg());
        card.setElevation(Ui.dp(24));
        card.setPadding(Ui.dp(22), Ui.dp(20), Ui.dp(22), Ui.dp(18));
        FrameLayout outer = new FrameLayout(act);
        outer.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(14));
        outer.addView(card);
        dialog.setContentView(outer);
        applyWindow(act, dialog);
        return card;
    }

    // ------------------------------------------------------------ builder

    public static class Builder {
        private final Activity act;
        private final Dialog dialog;
        private final LinearLayout card;
        private LinearLayout btnRow;

        public Builder(Activity act) {
            this.act = act;
            this.dialog = new Dialog(act);
            this.card = cardShell(act, dialog);
        }

        public Builder title(String t) {
            TextView tv = Ui.text(act, t, 17.5f, Theme.TEXT, true);
            tv.setPadding(0, 0, 0, Ui.dp(4));
            card.addView(tv);
            View bar = new View(act);
            bar.setBackground(Ui.gradientBg(3));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(38), Ui.dp(3));
            lp.setMargins(0, 0, 0, Ui.dp(10));
            bar.setLayoutParams(lp);
            card.addView(bar);
            return this;
        }

        public Builder message(String m) {
            TextView tv = Ui.bodyWrap(act, m);
            tv.setPadding(0, 0, 0, Ui.dp(2));
            card.addView(tv);
            return this;
        }

        public Builder content(View v) {
            card.addView(v);
            return this;
        }

        /** Button that dismisses the dialog, then runs the action. */
        public Builder button(String label, int style, final Runnable action) {
            return button(label, style, d -> { d.dismiss(); if (action != null) action.run(); });
        }

        /** Button whose action receives the dialog (decides itself when to dismiss). */
        public Builder button(String label, int style, final Action action) {
            if (btnRow == null) {
                btnRow = new LinearLayout(act);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                btnRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, Ui.dp(18), 0, 0);
                btnRow.setLayoutParams(lp);
                card.addView(btnRow);
            }
            TextView b = Ui.text(act, label, 14.5f,
                    style == BTN_GHOST ? Theme.TEXT : 0xFFFFFFFF, true);
            b.setGravity(Gravity.CENTER);
            b.setPadding(Ui.dp(20), Ui.dp(10), Ui.dp(20), Ui.dp(10));
            if (style == BTN_PRIMARY) {
                b.setBackground(Ui.gradientBg(16));
            } else if (style == BTN_DANGER) {
                GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{0xFFFF6B6B, 0xFFEE5253});
                g.setCornerRadius(Ui.dp(16));
                b.setBackground(g);
            } else {
                GradientDrawable g = new GradientDrawable();
                g.setColor(Color.TRANSPARENT);
                g.setCornerRadius(Ui.dp(16));
                g.setStroke(Ui.dp(1), Theme.BORDER);
                b.setBackground(g);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (btnRow.getChildCount() > 0) lp.setMargins(Ui.dp(10), 0, 0, 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> { if (action != null) action.run(dialog); });
            PressFx.apply(b);
            btnRow.addView(b);
            return this;
        }

        public Dialog show() {
            dialog.show();
            return dialog;
        }

        public Dialog dialog() { return dialog; }
    }

    public static Builder builder(Activity act) { return new Builder(act); }

    // ------------------------------------------------------------ shortcuts

    public static void confirm(Activity act, String title, String message,
                                 String positiveLabel, boolean danger, final Runnable onPositive) {
        new Builder(act)
                .title(title)
                .message(message)
                .button("Cancel", BTN_GHOST, (Runnable) null)
                .button(positiveLabel, danger ? BTN_DANGER : BTN_PRIMARY, onPositive)
                .show();
    }

    public static void message(Activity act, String title, String message) {
        new Builder(act)
                .title(title)
                .message(message)
                .button("OK", BTN_PRIMARY, (Runnable) null)
                .show();
    }

    /** Option list: each row carries a colourful gradient letter badge. */
    public static void list(Activity act, String title, final String[] items, final Choice choice) {
        final Builder b = new Builder(act);
        b.title(title);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(2), Ui.dp(11), Ui.dp(2), Ui.dp(11));
            int[] g = Ui.gradientFor(idx + (title == null ? 0 : title.length()));
            GradientDrawable badgeBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, g);
            badgeBg.setShape(GradientDrawable.OVAL);
            TextView badge = new TextView(act);
            String label = items[i] == null ? "" : items[i];
            badge.setText(label.isEmpty() ? "•" : label.substring(0, 1).toUpperCase());
            badge.setTextColor(0xFFFFFFFF);
            badge.setTextSize(13);
            badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(badgeBg);
            badge.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(30), Ui.dp(30)));
            row.addView(badge);
            TextView t = Ui.text(act, label, 15, Theme.TEXT, false);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            tlp.setMargins(Ui.dp(13), 0, 0, 0);
            t.setLayoutParams(tlp);
            row.addView(t);
            row.setOnClickListener(v -> {
                b.dialog().dismiss();
                if (choice != null) choice.onChoice(idx);
            });
            PressFx.apply(row);
            col.addView(row);
            if (i < items.length - 1) {
                View div = new View(act);
                div.setBackgroundColor(Ui.withAlpha(Theme.BORDER, 130));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                dlp.setMargins(Ui.dp(45), 0, 0, 0);
                div.setLayoutParams(dlp);
                col.addView(div);
            }
        }
        b.content(col);
        b.show();
    }

    /** Themed single-line text field (also reused by forms inside custom dialogs). */
    public static EditText themedInput(Activity act, String hint, String initial) {
        EditText e = new EditText(act);
        e.setHint(hint);
        if (initial != null) { e.setText(initial); e.setSelection(initial.length()); }
        e.setSingleLine(true);
        e.setTextColor(Theme.TEXT);
        e.setHintTextColor(Theme.TEXT2);
        e.setTextSize(15);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.LIGHT ? 0xFFFFFFFF : Ui.withAlpha(Theme.ELEVATED, 235));
        bg.setCornerRadius(Ui.dp(14));
        bg.setStroke(Ui.dp(1), Theme.BORDER);
        e.setBackground(bg);
        e.setPadding(Ui.dp(14), Ui.dp(11), Ui.dp(14), Ui.dp(11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(4), 0, 0);
        e.setLayoutParams(lp);
        return e;
    }

    public static void input(Activity act, String title, String hint, String initial,
                             String positiveLabel, final Input input) {
        final EditText e = themedInput(act, hint, initial);
        new Builder(act)
                .title(title)
                .content(e)
                .button("Cancel", BTN_GHOST, (Runnable) null)
                .button(positiveLabel, BTN_PRIMARY,
                        () -> { if (input != null) input.onInput(e.getText().toString()); })
                .show();
    }
}
