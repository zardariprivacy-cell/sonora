package com.nawazishali.sonora;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Music library: Songs / Artists / Albums / Playlists / Favorites with drill-in. */
public class MusicTab extends Tab {
    private static final int M_SONGS = 0, M_ARTISTS = 1, M_ALBUMS = 2, M_PLAYLISTS = 3, M_FAVS = 4;
    private LinearLayout rootL;
    private ListView list;
    private LinearLayout emptyBox;
    private TextView backRow;
    private TextView countLabel;
    private int mode = M_SONGS;
    private int sortMode = Library.SORT_NAME;
    private String drillTitle = null;   // non-null when drilled into artist/album/playlist
    private long drillPlaylistId = -1;
    private List<MediaItem> drillSongs = new ArrayList<>();
    private final List<TextView> chipViews = new ArrayList<>();

    public MusicTab(Activity act) { super(act); }

    @Override protected View create() {
        rootL = new LinearLayout(act);
        rootL.setOrientation(LinearLayout.VERTICAL);
        rootL.setBackgroundColor(Theme.BG);

        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(6));
        countLabel = Ui.text(act, "Music", 18, Theme.TEXT, true);
        countLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(countLabel);
        bar.addView(iconBtn(R.drawable.ic_add, v -> {
            if (mode == M_PLAYLISTS) newPlaylist();
            else Toast.makeText(act, "Switch to Playlists to create one", Toast.LENGTH_SHORT).show();
        }));
        bar.addView(iconBtn(R.drawable.ic_sort, v -> sortDialog()));
        bar.addView(iconBtn(R.drawable.ic_refresh, v -> { Library.scanAsync(act); Toast.makeText(act, "Refreshing library…", Toast.LENGTH_SHORT).show(); }));
        rootL.addView(bar);

        LinearLayout chips = new LinearLayout(act);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(Ui.dp(14), Ui.dp(2), Ui.dp(14), Ui.dp(8));
        String[] names = {"Songs", "Artists", "Albums", "Playlists", "Favorites"};
        chipViews.clear();
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView chip = Ui.text(act, names[i], 13, Theme.TEXT, false);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(13), Ui.dp(7), Ui.dp(13), Ui.dp(7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, Ui.dp(7), 0);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> { mode = idx; drillTitle = null; drillPlaylistId = -1; styleChips(); applyData(); });
            chipViews.add(chip);
            chips.addView(chip);
        }
        rootL.addView(chips);
        styleChips();

        backRow = Ui.text(act, "", 14.5f, Theme.SECONDARY, true);
        backRow.setPadding(Ui.dp(14), Ui.dp(4), Ui.dp(14), Ui.dp(8));
        backRow.setVisibility(View.GONE);
        backRow.setOnClickListener(v -> { drillTitle = null; drillPlaylistId = -1; applyData(); });
        rootL.addView(backRow);

        list = new ListView(act);
        list.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        list.setDivider(null);
        list.setBackgroundColor(Theme.BG);
        rootL.addView(list);

        emptyBox = new LinearLayout(act);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);
        emptyBox.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        rootL.addView(emptyBox);

        list.setOnItemClickListener((p, v, pos, id) -> onItemClick(pos));
        list.setOnItemLongClickListener((p, v, pos, id) -> onItemLongClick(pos));

        applyData();
        return rootL;
    }

    private View iconBtn(int res, View.OnClickListener click) {
        LinearLayout b = new LinearLayout(act);
        b.setGravity(Gravity.CENTER);
        b.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        b.addView(Ui.icon(act, res, 21, Theme.TEXT));
        b.setOnClickListener(click);
        return b;
    }

    private void styleChips() {
        for (int i = 0; i < chipViews.size(); i++) {
            TextView c = chipViews.get(i);
            boolean active = i == mode;
            c.setBackground(active ? Ui.gradientBg(18) : Ui.rounded(Theme.ELEVATED, 18));
            c.setTextColor(active ? 0xFFFFFFFF : Theme.TEXT2);
        }
    }

    // ---------------- data ----------------
    private List<MediaItem> songsData() {
        List<MediaItem> data = new ArrayList<>();
        if (!Library.hasAudioPermission(act)) return data;
        if (drillTitle != null) return new ArrayList<>(drillSongs);
        switch (mode) {
            case M_FAVS:
                for (String k : Db.get(act).favoriteKeys()) {
                    MediaItem it = Library.findByKey(k);
                    if (it != null && !it.video) data.add(it);
                }
                break;
            default: data.addAll(Library.AUDIO);
        }
        Library.sort(data, sortMode);
        return data;
    }

    private Map<String, List<MediaItem>> groupBy(boolean artist) {
        Map<String, List<MediaItem>> map = new LinkedHashMap<>();
        for (MediaItem it : Library.AUDIO) {
            String k = artist ? it.artist : it.album;
            if (k == null || k.trim().isEmpty() || k.equals("<unknown>")) k = artist ? "Unknown artist" : "Unknown album";
            List<MediaItem> l = map.get(k);
            if (l == null) { l = new ArrayList<>(); map.put(k, l); }
            l.add(it);
        }
        return map;
    }

    private void applyData() {
        if (list == null) return;
        backRow.setVisibility(drillTitle != null ? View.VISIBLE : View.GONE);
        if (drillTitle != null) backRow.setText("‹  " + drillTitle);

        if (drillTitle == null && mode == M_ARTISTS || drillTitle == null && mode == M_ALBUMS) {
            Map<String, List<MediaItem>> map = groupBy(mode == M_ARTISTS);
            List<String> rows = new ArrayList<>();
            for (Map.Entry<String, List<MediaItem>> e : map.entrySet())
                rows.add(e.getKey() + "\n" + e.getValue().size() + " songs");
            list.setAdapter(new ArrayAdapter<>(act, android.R.layout.simple_list_item_1, rows));
            setEmpty(rows.isEmpty(), "No music found", "Songs on your phone will appear here automatically.");
            countLabel.setText("Music  •  " + Library.AUDIO.size() + " songs");
            return;
        }
        if (drillTitle == null && mode == M_PLAYLISTS) {
            final List<long[]> pls = Db.get(act).playlists();
            final List<String> names = Db.get(act).playlistNames();
            List<String> rows = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) rows.add(names.get(i) + "\n" + pls.get(i)[1] + " songs");
            rows.add("+  New playlist");
            list.setAdapter(new ArrayAdapter<>(act, android.R.layout.simple_list_item_1, rows));
            setEmpty(false, "", "");
            countLabel.setText("Playlists  •  " + names.size());
            return;
        }
        List<MediaItem> data = songsData();
        list.setAdapter(new MediaAdapter(act, data, false));
        setEmpty(data.isEmpty(),
                !Library.hasAudioPermission(act) ? "Music access needed" : "Nothing here yet",
                !Library.hasAudioPermission(act)
                        ? "Allow access to your music to browse and play it here."
                        : "Songs on your phone will appear here automatically.");
        countLabel.setText("Music  •  " + data.size() + " songs");
    }

    private void setEmpty(boolean empty, String title, String msg) {
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyBox.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) return;
        emptyBox.removeAllViews();
        emptyBox.addView(Ui.icon(act, R.drawable.ic_music, 48, Theme.TEXT2));
        TextView t = Ui.text(act, title, 16, Theme.TEXT, true);
        t.setPadding(0, Ui.dp(10), 0, 0);
        emptyBox.addView(t);
        TextView m = Ui.sub(act, msg);
        m.setGravity(Gravity.CENTER);
        emptyBox.addView(m);
        if (!Library.hasAudioPermission(act)) {
            LinearLayout btn = Ui.chip(act, 0, "Grant permission");
            btn.setBackground(Ui.gradientBg(20));
            btn.setOnClickListener(v -> act.requestPermissions(Library.neededPermissions(), 101));
            emptyBox.addView(btn);
        }
    }

    // ---------------- clicks ----------------
    private void onItemClick(int pos) {
        if (drillTitle == null && (mode == M_ARTISTS || mode == M_ALBUMS)) {
            Map<String, List<MediaItem>> map = groupBy(mode == M_ARTISTS);
            String key = new ArrayList<>(map.keySet()).get(pos);
            drillTitle = key;
            drillSongs = map.get(key);
            applyData();
            return;
        }
        if (drillTitle == null && mode == M_PLAYLISTS) {
            List<long[]> pls = Db.get(act).playlists();
            List<String> names = Db.get(act).playlistNames();
            if (pos == names.size()) { newPlaylist(); return; }
            drillPlaylistId = pls.get(pos)[0];
            drillTitle = names.get(pos);
            drillSongs = Library.resolveKeys(Db.get(act).playlistKeys(drillPlaylistId));
            applyData();
            return;
        }
        List<MediaItem> data = songsData();
        if (pos >= 0 && pos < data.size()) ItemMenu.playAudio(act, data, pos);
    }

    private boolean onItemLongClick(int pos) {
        if (drillTitle == null && mode == M_PLAYLISTS) {
            final List<long[]> pls = Db.get(act).playlists();
            final List<String> names = Db.get(act).playlistNames();
            if (pos >= names.size()) return false;
            final long pid = pls.get(pos)[0];
            new AlertDialog.Builder(act)
                    .setTitle(names.get(pos))
                    .setItems(new String[]{"Rename", "Delete playlist"}, (d, w) -> {
                        if (w == 0) ItemMenu.nameDialog(act, "Rename playlist", name -> {
                            Db.get(act).renamePlaylist(pid, name); applyData();
                        });
                        else new AlertDialog.Builder(act)
                                .setTitle("Delete playlist?")
                                .setMessage("The songs stay on your phone; only the playlist is removed.")
                                .setPositiveButton("Delete", (dd, ww) -> { Db.get(act).deletePlaylist(pid); applyData(); })
                                .setNegativeButton("Cancel", null).show();
                    })
                    .show();
            return true;
        }
        if (drillTitle == null && (mode == M_ARTISTS || mode == M_ALBUMS)) return false;
        List<MediaItem> data = songsData();
        if (pos >= 0 && pos < data.size()) {
            final MediaItem it = data.get(pos);
            if (drillPlaylistId > 0) {
                new AlertDialog.Builder(act)
                        .setTitle(it.title)
                        .setItems(new String[]{"Play", "Remove from playlist", "More options…"}, (d, w) -> {
                            if (w == 0) ItemMenu.playAudio(act, data, pos);
                            else if (w == 1) {
                                Db.get(act).removeFromPlaylist(drillPlaylistId, it.key());
                                drillSongs = Library.resolveKeys(Db.get(act).playlistKeys(drillPlaylistId));
                                applyData();
                            } else ItemMenu.show(act, it, this::applyData);
                        })
                        .show();
            } else ItemMenu.show(act, it, this::applyData);
            return true;
        }
        return false;
    }

    private void newPlaylist() {
        ItemMenu.nameDialog(act, "New playlist", name -> {
            Db.get(act).createPlaylist(name);
            Toast.makeText(act, "Playlist created. Long-press any song to add it.", Toast.LENGTH_LONG).show();
            applyData();
        });
    }

    private void sortDialog() {
        String[] names = {"Name (A–Z)", "Date added (newest)", "Size (largest)", "Duration (longest)"};
        new AlertDialog.Builder(act)
                .setTitle("Sort songs by")
                .setItems(names, (d, w) -> { sortMode = w; applyData(); })
                .show();
    }

    @Override public void refresh() { applyData(); }
}
