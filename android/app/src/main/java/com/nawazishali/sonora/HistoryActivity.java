package com.nawazishali.sonora;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Playback history: everything in the history table, most recent first. */
public class HistoryActivity extends Activity {
    private LinearLayout content;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Theme.init(this);
        Ui.init(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Theme.BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(24));
        scroll.addView(content);
        setContentView(scroll);
        rebuild();
    }

    private void rebuild() {
        content.removeAllViews();
        Db db = Db.get(this);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "‹", 26, Theme.TEXT, true);
        back.setPadding(0, 0, Ui.dp(12), 0);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView title = Ui.text(this, "Playback History", 19, Theme.TEXT, true);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(title);
        if (db.historyCount() > 0) {
            TextView clear = Ui.text(this, "Clear", 14, Theme.ACCENT, false);
            clear.setPadding(Ui.dp(10), Ui.dp(6), Ui.dp(2), Ui.dp(6));
            clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Clear history?")
                    .setMessage("This removes the playback history list. Your files are not touched.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear", (d, w) -> { Db.get(this).clearHistory(); rebuild(); })
                    .show());
            bar.addView(clear);
        }
        content.addView(bar);
        content.addView(Ui.spacer(this, 6));

        List<MediaItem> items = Library.resolveKeys(db.recentAllKeys(200));
        if (items.isEmpty()) {
            content.addView(Ui.sub(this, "Nothing here yet. Songs and videos you play will appear in this list."));
            return;
        }
        for (final MediaItem it : items) {
            content.addView(Rows.mediaRow(this, it,
                    v -> { if (it.video) ItemMenu.playVideo(this, it); else ItemMenu.playAudio(this, one(it), 0); },
                    v -> { ItemMenu.show(this, it, this::rebuild); return true; }));
        }
    }

    private List<MediaItem> one(MediaItem it) {
        List<MediaItem> l = new java.util.ArrayList<>(); l.add(it); return l;
    }

    @Override protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 4471) { Library.scanAsync(this); rebuild(); }
    }
}
