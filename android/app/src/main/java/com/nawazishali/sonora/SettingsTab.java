package com.nawazishali.sonora;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Settings tab, v3.1: rebuilt in the element style of the user's reference
 * screenshots — a 3-column grid of large line icons with labels, then a clean
 * icon list with thin dividers. Every cell and row opens a real screen or
 * performs a real action; no coins/VIP/ads (Sonora has none).
 */
public class SettingsTab extends Tab {
    private LinearLayout content;

    public SettingsTab(Activity act) { super(act); }

    @Override protected View create() {
        ScrollView scroll = new ScrollView(act);
        scroll.setBackgroundColor(Theme.BG);
        content = new LinearLayout(act);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(24));
        scroll.addView(content);
        rebuild();
        return scroll;
    }

    @Override public void refresh() { if (content != null) rebuild(); }

    private void rebuild() {
        content.removeAllViews();
        content.addView(Ui.title(act, "Settings"));
        TextView tag = Ui.sub(act, "Your Complete Media Experience");
        tag.setPadding(0, Ui.dp(2), 0, Ui.dp(6));
        content.addView(tag);

        // ---- Icon grid (3 x 3), all real functions ----
        content.addView(Rows.gridRow(act,
                Rows.gridCell(act, R.drawable.ic_shield, "Private Vault",
                        v -> act.startActivity(new Intent(act, VaultActivity.class))),
                Rows.gridCell(act, R.drawable.ic_extract, "Extract Audio",
                        v -> pickVideoForExtract()),
                Rows.gridCell(act, R.drawable.ic_sd, "Storage",
                        v -> act.startActivity(new Intent(act, StorageActivity.class)))));
        content.addView(Rows.gridRow(act,
                Rows.gridCell(act, R.drawable.ic_folder, "Folders",
                        v -> ((MainActivity) act).showTab(MainActivity.TAB_FOLDERS)),
                Rows.gridCell(act, R.drawable.ic_brightness, "Theme",
                        v -> toggleTheme()),
                Rows.gridCell(act, R.drawable.ic_history, "History",
                        v -> act.startActivity(new Intent(act, HistoryActivity.class)))));
        content.addView(Rows.gridRow(act,
                Rows.gridCell(act, R.drawable.ic_share, "Share App",
                        v -> Ui.shareApp(act)),
                Rows.gridCell(act, R.drawable.ic_files, "Large Files",
                        v -> act.startActivity(new Intent(act, StorageActivity.class).putExtra("focus", "large"))),
                Rows.gridCell(act, R.drawable.ic_refresh, "Refresh Library",
                        v -> { Library.scanAsync(act); Toast.makeText(act, "Refreshing library…", Toast.LENGTH_SHORT).show(); })));

        content.addView(Rows.sectionBreak(act));

        // ---- Clean icon list ----
        content.addView(Rows.menuRow(act, R.drawable.ic_brightness, "Theme",
                Theme.LIGHT ? "Light" : "Dark", v -> toggleTheme()));
        content.addView(Rows.menuDivider(act));
        content.addView(Rows.menuRow(act, R.drawable.ic_timer, "Sleep Timer", null,
                v -> SleepTimerUi.show(act)));
        content.addView(Rows.menuDivider(act));
        content.addView(Rows.menuRow(act, R.drawable.ic_equalizer, "Equalizer", null,
                v -> act.startActivity(new Intent(act, EqualizerActivity.class))));
        content.addView(Rows.menuDivider(act));
        content.addView(Rows.menuRow(act, R.drawable.ic_shield, "Private Vault", null,
                v -> act.startActivity(new Intent(act, VaultActivity.class))));
        content.addView(Rows.menuDivider(act));
        content.addView(Rows.menuRow(act, R.drawable.ic_sd, "Storage Dashboard", null,
                v -> act.startActivity(new Intent(act, StorageActivity.class))));
        content.addView(Rows.menuDivider(act));
        content.addView(Rows.menuRow(act, R.drawable.ic_history, "Playback History", null,
                v -> act.startActivity(new Intent(act, HistoryActivity.class))));
        content.addView(Rows.menuDivider(act));
        content.addView(Rows.menuRow(act, R.drawable.ic_help, "Help", null,
                v -> act.startActivity(new Intent(act, HelpActivity.class))));
        content.addView(Rows.menuDivider(act));
        content.addView(Rows.menuRow(act, R.drawable.ic_info, "About", null,
                v -> aboutDialog()));

        content.addView(Rows.sectionBreak(act));

        // ---- Permissions (kept from v3.0) ----
        boolean vizGranted = act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        content.addView(Rows.menuRow(act, R.drawable.ic_headphones, "Visualizer Permission",
                vizGranted ? "Granted" : "Not granted",
                v -> act.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 102)));
        content.addView(Rows.menuDivider(act));
        content.addView(Rows.menuRow(act, R.drawable.ic_key, "Media Permissions",
                Library.hasAnyPermission(act) ? "Granted" : "Not granted",
                v -> act.requestPermissions(Library.neededPermissions(), 101)));
    }

    private void toggleTheme() {
        Theme.setLight(act, !Theme.LIGHT);
        act.recreate();
    }

    /** Pick a video from the library, then run the real M4A extraction on it. */
    private void pickVideoForExtract() {
        final List<MediaItem> videos = Library.VIDEO;
        if (videos.isEmpty()) {
            Toast.makeText(act, "No videos in the library yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[videos.size()];
        for (int i = 0; i < videos.size(); i++) names[i] = videos.get(i).title;
        new AlertDialog.Builder(act)
                .setTitle("Extract audio from…")
                .setItems(names, (d, w) -> {
                    final MediaItem it = videos.get(w);
                    Toast.makeText(act, "Extracting audio…", Toast.LENGTH_SHORT).show();
                    AudioExtractor.extract(act, it, (ok, msg) -> {
                        Toast.makeText(act, msg, Toast.LENGTH_LONG).show();
                        if (ok) Library.scanAsync(act);
                    });
                })
                .show();
    }

    private void aboutDialog() {
        new AlertDialog.Builder(act)
                .setTitle("Sonora")
                .setMessage("Version 3.1\n\nYour Complete Media Experience\n\n"
                        + Library.AUDIO.size() + " songs  •  " + Library.VIDEO.size() + " videos on this device\n\n"
                        + "An original Sonora design in Midnight Violet. Offline video & music player — "
                        + "no account, no ads, no uploads; your media never leaves your phone.")
                .setPositiveButton("OK", null)
                .show();
    }
}
