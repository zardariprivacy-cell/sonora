package com.nawazishali.sonora;

import android.app.Activity;
import android.content.Intent;
import android.os.Environment;
import android.os.StatFs;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Home dashboard — everything shown here is real device data (library + DB). */
public class HomeTab extends Tab {
    private LinearLayout content;

    public HomeTab(Activity act) { super(act); }

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
        Db db = Db.get(act);

        // Welcome header
        TextView hello = Ui.text(act, "Welcome to Sonora", 22, Theme.TEXT, true);
        content.addView(hello);
        TextView tag = Ui.sub(act, "Your Complete Media Experience");
        tag.setPadding(0, Ui.dp(2), 0, Ui.dp(10));
        content.addView(tag);

        // Search bar (opens global search)
        LinearLayout search = new LinearLayout(act);
        search.setOrientation(LinearLayout.HORIZONTAL);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setBackground(Ui.rounded(Theme.ELEVATED, 24));
        search.setPadding(Ui.dp(14), Ui.dp(11), Ui.dp(14), Ui.dp(11));
        search.addView(Ui.icon(act, R.drawable.ic_search, 20, Theme.TEXT2));
        TextView hint = Ui.text(act, "Search songs and videos…", 14, Theme.TEXT2, false);
        hint.setPadding(Ui.dp(10), 0, 0, 0);
        search.addView(hint);
        search.setOnClickListener(v -> act.startActivity(new Intent(act, SearchActivity.class)));
        content.addView(search);

        if (!Library.hasAnyPermission(act)) {
            content.addView(Ui.spacer(act, 14));
            LinearLayout card = new LinearLayout(act);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.cardBg());
            card.setPadding(Ui.dp(16), Ui.dp(16), Ui.dp(16), Ui.dp(16));
            card.addView(Ui.text(act, "Allow media access", 16, Theme.TEXT, true));
            card.addView(Ui.sub(act, "Sonora needs permission to find the songs and videos on your phone. Nothing is uploaded — everything stays on your device."));
            LinearLayout btn = Ui.chip(act, 0, "Grant permission");
            btn.setBackground(Ui.gradientBg(20));
            btn.setOnClickListener(v -> act.requestPermissions(Library.neededPermissions(), 101));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            blp.setMargins(0, Ui.dp(12), 0, 0);
            btn.setLayoutParams(blp);
            card.addView(btn);
            content.addView(card);
            return;
        }

        if (Library.scanning && Library.AUDIO.isEmpty() && Library.VIDEO.isEmpty()) {
            content.addView(Ui.spacer(act, 20));
            content.addView(Ui.sub(act, "Scanning your media library…"));
            return;
        }

        // Quick access — v3.1: icon grid in the same element style as Settings
        // (large line icons + labels), each cell opening a real tab or screen.
        content.addView(Ui.section(act, "Quick access"));
        content.addView(Rows.gridRow(act,
                Rows.gridCell(act, R.drawable.ic_video, "Videos",
                        v -> ((MainActivity) act).showTab(MainActivity.TAB_VIDEOS)),
                Rows.gridCell(act, R.drawable.ic_music, "Music",
                        v -> ((MainActivity) act).showTab(MainActivity.TAB_MUSIC)),
                Rows.gridCell(act, R.drawable.ic_folder, "Folders",
                        v -> ((MainActivity) act).showTab(MainActivity.TAB_FOLDERS))));
        content.addView(Rows.gridRow(act,
                Rows.gridCell(act, R.drawable.ic_shield, "Private Vault",
                        v -> act.startActivity(new Intent(act, VaultActivity.class))),
                Rows.gridCell(act, R.drawable.ic_history, "History",
                        v -> act.startActivity(new Intent(act, HistoryActivity.class))),
                Rows.gridCell(act, R.drawable.ic_sd, "Storage",
                        v -> act.startActivity(new Intent(act, StorageActivity.class)))));

        // Continue watching
        List<MediaItem> cont = Library.resolveKeys(db.continueWatchingKeys(10));
        if (!cont.isEmpty()) {
            content.addView(Ui.section(act, "Continue Watching"));
            for (MediaItem it : cont) {
                long pos = db.getPosition(it.key());
                final MediaItem f = it;
                content.addView(Rows.mediaRow(act, it,
                        v -> ItemMenu.playVideo(act, f),
                        v -> { ItemMenu.show(act, f, this::rebuild); return true; }));
                TextView prog = Ui.sub(act, "Resume at " + Ui.fmtDuration(pos) + " of " + Ui.fmtDuration(it.duration));
                prog.setPadding(Ui.dp(10), 0, 0, Ui.dp(6));
                content.addView(prog);
            }
        }

        // Recently played (mixed)
        List<String> recentKeys = new ArrayList<>();
        recentKeys.addAll(db.recentKeys(false, 6));
        recentKeys.addAll(db.recentKeys(true, 6));
        List<MediaItem> recent = Library.resolveKeys(recentKeys);
        if (!recent.isEmpty()) {
            content.addView(Ui.section(act, "Recently Played"));
            for (MediaItem it : recent) addRow(it);
        }

        // Recently added videos
        if (!Library.VIDEO.isEmpty()) {
            content.addView(Ui.section(act, "Recently Added Videos"));
            int n = Math.min(5, Library.VIDEO.size());
            for (int i = 0; i < n; i++) addRow(Library.VIDEO.get(i)); // scan order = newest first
        }

        // Recently played music (fallback: recently added)
        List<MediaItem> recentMusic = Library.resolveKeys(db.recentKeys(false, 5));
        if (recentMusic.isEmpty() && !Library.AUDIO.isEmpty()) {
            content.addView(Ui.section(act, "Recently Added Music"));
            int n = Math.min(5, Library.AUDIO.size());
            for (int i = 0; i < n; i++) addRow(Library.AUDIO.get(i));
        } else if (!recentMusic.isEmpty()) {
            content.addView(Ui.section(act, "Recently Played Music"));
            for (MediaItem it : recentMusic) addRow(it);
        }

        // Most played
        List<MediaItem> most = Library.resolveKeys(db.mostPlayedKeys(5));
        if (!most.isEmpty()) {
            content.addView(Ui.section(act, "Most Played"));
            for (MediaItem it : most) addRow(it);
        }

        // Favorites
        List<MediaItem> favs = new ArrayList<>();
        for (String k : db.favoriteKeys()) {
            MediaItem it = Library.findByKey(k);
            if (it != null) favs.add(it);
        }
        if (!favs.isEmpty()) {
            content.addView(Ui.section(act, "Favorites"));
            for (MediaItem it : favs) addRow(it);
        }

        // Storage overview
        content.addView(Ui.section(act, "Storage Overview"));
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.cardBg());
        card.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long total = fs.getTotalBytes(), free = fs.getAvailableBytes();
            card.addView(Ui.text(act, "Device storage", 14.5f, Theme.TEXT, true));
            card.addView(Ui.sub(act, Ui.fmtSize(total - free) + " used of " + Ui.fmtSize(total)
                    + "  •  " + Ui.fmtSize(free) + " free"));
        } catch (Throwable ignored) { }
        card.addView(Ui.sub(act, "Music library: " + Library.AUDIO.size() + " songs • " + Ui.fmtSize(Library.totalAudioSize())));
        card.addView(Ui.sub(act, "Video library: " + Library.VIDEO.size() + " videos • " + Ui.fmtSize(Library.totalVideoSize())));
        card.setOnClickListener(v -> act.startActivity(new Intent(act, StorageActivity.class)));
        content.addView(card);

        if (Library.AUDIO.isEmpty() && Library.VIDEO.isEmpty()) {
            content.addView(Ui.spacer(act, 16));
            content.addView(Ui.sub(act, "No media found yet. Add songs or videos to your phone, then pull Refresh from the Videos or Music tab."));
        }
    }

    private void addRow(final MediaItem it) {
        content.addView(Rows.mediaRow(act, it,
                v -> { if (it.video) ItemMenu.playVideo(act, it); else ItemMenu.playAudio(act, singleList(it), 0); },
                v -> { ItemMenu.show(act, it, this::rebuild); return true; }));
    }

    private List<MediaItem> singleList(MediaItem it) {
        List<MediaItem> l = new ArrayList<>(); l.add(it); return l;
    }
}
