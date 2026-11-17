package com.nawazishali.sonora;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Storage dashboard: device totals, library sizes, large files, recently added. */
public class StorageActivity extends Activity {
    private ScrollView scroll;
    private TextView largeHeader;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Theme.init(this);
        Ui.init(this);

        scroll = new ScrollView(this);
        scroll.setBackground(Ui.appBg());
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(24));
        scroll.addView(content);
        setContentView(scroll);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        TextView back = Ui.text(this, "‹", 26, Theme.TEXT, true);
        back.setPadding(0, 0, Ui.dp(12), 0);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        bar.addView(Ui.text(this, "Storage", 19, Theme.TEXT, true));
        content.addView(bar);

        // Device totals
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.cardBg());
        card.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long total = fs.getTotalBytes(), free = fs.getAvailableBytes();
            card.addView(Ui.text(this, "Internal storage", 15, Theme.TEXT, true));
            card.addView(Ui.sub(this, "Total: " + Ui.fmtSize(total)));
            card.addView(Ui.sub(this, "Used: " + Ui.fmtSize(total - free)));
            card.addView(Ui.sub(this, "Available: " + Ui.fmtSize(free)));
        } catch (Throwable t) {
            card.addView(Ui.sub(this, "Storage information not available on this device."));
        }
        content.addView(card);

        content.addView(Ui.section(this, "Your media library"));
        LinearLayout lib = new LinearLayout(this);
        lib.setOrientation(LinearLayout.VERTICAL);
        lib.setBackground(Ui.cardBg());
        lib.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        lib.addView(Ui.sub(this, "Music: " + Library.AUDIO.size() + " songs  •  " + Ui.fmtSize(Library.totalAudioSize())));
        lib.addView(Ui.sub(this, "Videos: " + Library.VIDEO.size() + " videos  •  " + Ui.fmtSize(Library.totalVideoSize())));
        content.addView(lib);

        // Large files
        List<MediaItem> all = new ArrayList<>();
        all.addAll(Library.AUDIO); all.addAll(Library.VIDEO);
        Collections.sort(all, (a, b) -> Long.compare(b.size, a.size));
        largeHeader = Ui.section(this, "Largest files");
        content.addView(largeHeader);
        int n = Math.min(15, all.size());
        if (n == 0) content.addView(Ui.sub(this, "No media in the library yet."));
        for (int i = 0; i < n; i++) {
            final MediaItem it = all.get(i);
            content.addView(Rows.mediaRow(this, it,
                    v -> { if (it.video) ItemMenu.playVideo(this, it); else ItemMenu.playAudio(this, one(it), 0); },
                    v -> { ItemMenu.show(this, it, null); return true; }));
        }

        // Recently added
        List<MediaItem> recent = new ArrayList<>();
        recent.addAll(Library.AUDIO); recent.addAll(Library.VIDEO);
        Collections.sort(recent, (a, b) -> Long.compare(b.dateAdded, a.dateAdded));
        content.addView(Ui.section(this, "Recently added"));
        int m = Math.min(15, recent.size());
        for (int i = 0; i < m; i++) {
            final MediaItem it = recent.get(i);
            content.addView(Rows.mediaRow(this, it,
                    v -> { if (it.video) ItemMenu.playVideo(this, it); else ItemMenu.playAudio(this, one(it), 0); },
                    v -> { ItemMenu.show(this, it, null); return true; }));
        }

        // "Large Files" shortcuts (Home/Settings grids) land on the largest-files section
        if ("large".equals(getIntent().getStringExtra("focus"))) {
            content.post(() -> scroll.scrollTo(0, Math.max(0, largeHeader.getTop() - Ui.dp(6))));
        }
    }

    private List<MediaItem> one(MediaItem it) { List<MediaItem> l = new ArrayList<>(); l.add(it); return l; }

    @Override protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 4471) Library.scanAsync(this);
    }
}
