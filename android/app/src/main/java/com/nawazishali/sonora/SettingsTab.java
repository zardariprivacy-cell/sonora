package com.nawazishali.sonora;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Settings: theme, equalizer, sleep timer, vault, storage, permissions, about. */
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
        tag.setPadding(0, Ui.dp(2), 0, Ui.dp(8));
        content.addView(tag);

        content.addView(Ui.section(act, "Appearance"));
        LinearLayout themeRow = new LinearLayout(act);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        themeRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        themeRow.setBackground(Ui.cardBg());
        themeRow.setPadding(Ui.dp(14), Ui.dp(13), Ui.dp(14), Ui.dp(13));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(0, 0, 0, Ui.dp(8));
        themeRow.setLayoutParams(tlp);
        themeRow.addView(Ui.icon(act, R.drawable.ic_brightness, 22, Theme.SECONDARY));
        LinearLayout texts = new LinearLayout(act);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams xlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        xlp.setMargins(Ui.dp(12), 0, 0, 0);
        texts.setLayoutParams(xlp);
        texts.addView(Ui.text(act, "Light theme", 15, Theme.TEXT, false));
        texts.addView(Ui.sub(act, Theme.LIGHT ? "Light variant is on" : "Midnight Violet dark theme is on"));
        themeRow.addView(texts);
        Switch sw = new Switch(act);
        sw.setChecked(Theme.LIGHT);
        sw.setOnCheckedChangeListener((CompoundButton b, boolean on) -> {
            Theme.setLight(act, on);
            act.recreate();
        });
        themeRow.addView(sw);
        content.addView(themeRow);

        content.addView(Ui.section(act, "Playback"));
        content.addView(Rows.settingRow(act, R.drawable.ic_equalizer, "Equalizer",
                "5-band equalizer, presets and bass boost",
                v -> act.startActivity(new Intent(act, EqualizerActivity.class))));
        content.addView(Rows.settingRow(act, R.drawable.ic_timer, "Sleep timer",
                "Stop music automatically after a while",
                v -> SleepTimerUi.show(act)));
        content.addView(Rows.settingRow(act, R.drawable.ic_headphones, "Visualizer permission",
                act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        ? "Granted — spectrum visualizer is active"
                        : "Needed for the music spectrum visualizer",
                v -> act.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 102)));

        content.addView(Ui.section(act, "Privacy"));
        content.addView(Rows.settingRow(act, R.drawable.ic_shield, "Private Vault",
                "PIN-protected encrypted folder for private media",
                v -> act.startActivity(new Intent(act, VaultActivity.class))));

        content.addView(Ui.section(act, "Library"));
        content.addView(Rows.settingRow(act, R.drawable.ic_sd, "Storage",
                "Device storage, library sizes and large files",
                v -> act.startActivity(new Intent(act, StorageActivity.class))));
        content.addView(Rows.settingRow(act, R.drawable.ic_refresh, "Refresh media library",
                "Rescan your phone for new songs and videos",
                v -> { Library.scanAsync(act); Toast.makeText(act, "Refreshing library…", Toast.LENGTH_SHORT).show(); }));
        content.addView(Rows.settingRow(act, R.drawable.ic_search, "Media permissions",
                Library.hasAnyPermission(act) ? "Media access granted" : "Media access not granted yet",
                v -> act.requestPermissions(Library.neededPermissions(), 101)));

        content.addView(Ui.section(act, "About"));
        LinearLayout about = new LinearLayout(act);
        about.setOrientation(LinearLayout.VERTICAL);
        about.setBackground(Ui.cardBg());
        about.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        about.addView(Ui.text(act, "Sonora 3.0", 15, Theme.TEXT, true));
        about.addView(Ui.sub(act, "Your Complete Media Experience"));
        about.addView(Ui.sub(act, Library.AUDIO.size() + " songs  •  " + Library.VIDEO.size() + " videos on this device"));
        about.addView(Ui.sub(act, "Offline video & music player. No account, no uploads — your media never leaves your phone."));
        about.setOnClickListener(v -> new AlertDialog.Builder(act)
                .setTitle("Sonora")
                .setMessage("Version 3.0\n\nAn original Sonora design in Midnight Violet. All playback happens on-device with the Android media framework.")
                .setPositiveButton("OK", null)
                .show());
        content.addView(about);
    }
}
