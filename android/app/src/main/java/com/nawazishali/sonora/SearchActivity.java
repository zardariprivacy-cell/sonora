package com.nawazishali.sonora;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Global search across the whole audio + video library. */
public class SearchActivity extends Activity {
    private ListView list;
    private TextView status;
    private List<MediaItem> results = new ArrayList<>();

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Theme.init(this);
        Ui.init(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.appBg());

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(10), Ui.dp(12), Ui.dp(14), Ui.dp(10));
        TextView back = Ui.text(this, "‹", 26, Theme.TEXT, true);
        back.setPadding(Ui.dp(6), 0, Ui.dp(10), 0);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        EditText input = new EditText(this);
        input.setHint("Search songs and videos…");
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setTextColor(Theme.TEXT);
        input.setHintTextColor(Theme.TEXT2);
        input.setBackground(Ui.rounded(Theme.ELEVATED, 22));
        input.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(16), Ui.dp(10));
        input.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(input);
        root.addView(bar);

        status = Ui.sub(this, "Type to search your whole library");
        status.setPadding(Ui.dp(16), Ui.dp(4), Ui.dp(16), Ui.dp(8));
        root.addView(status);

        list = new ListView(this);
        list.setDivider(null);
        list.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { run(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        input.requestFocus();

        list.setOnItemClickListener((p, v, pos, id) -> {
            MediaItem it = results.get(pos);
            if (it.video) {
                List<MediaItem> videos = new ArrayList<>();
                int start = 0;
                for (MediaItem m : results) if (m.video) { if (m == it) start = videos.size(); videos.add(m); }
                ItemMenu.playVideoList(this, videos, start);
            } else {
                List<MediaItem> songs = new ArrayList<>();
                int start = 0;
                for (MediaItem m : results) if (!m.video) { if (m == it) start = songs.size(); songs.add(m); }
                ItemMenu.playAudio(this, songs, start);
            }
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            ItemMenu.show(this, results.get(pos), null);
            return true;
        });
    }

    private void run(String q) {
        q = q.trim().toLowerCase(Locale.US);
        results = new ArrayList<>();
        if (!q.isEmpty()) {
            for (MediaItem it : Library.AUDIO) if (matches(it, q)) results.add(it);
            for (MediaItem it : Library.VIDEO) if (matches(it, q)) results.add(it);
        }
        status.setText(q.isEmpty() ? "Type to search your whole library"
                : results.size() + " result" + (results.size() == 1 ? "" : "s"));
        list.setAdapter(new MediaAdapter(this, results, false));
    }

    private boolean matches(MediaItem it, String q) {
        return it.title.toLowerCase(Locale.US).contains(q)
                || (it.artist != null && it.artist.toLowerCase(Locale.US).contains(q))
                || (it.album != null && it.album.toLowerCase(Locale.US).contains(q))
                || (it.bucketName != null && it.bucketName.toLowerCase(Locale.US).contains(q));
    }

    @Override protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 4471) Library.scanAsync(this);
    }
}
