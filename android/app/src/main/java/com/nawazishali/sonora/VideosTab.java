package com.nawazishali.sonora;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.AbsListView;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Video library: all/recent/favorites, sort, grid/list, search entry, refresh. */
public class VideosTab extends Tab {
    private LinearLayout rootL;
    private GridView grid;
    private MediaAdapter adapter;
    private LinearLayout emptyBox;
    private int filter = 0; // 0 all, 1 recent, 2 favorites
    private boolean gridMode = true;
    private int sortMode = Library.SORT_DATE;
    private TextView countLabel;
    private final List<TextView> chipViews = new ArrayList<>();

    public VideosTab(Activity act) { super(act); }

    @Override protected View create() {
        rootL = new LinearLayout(act);
        rootL.setOrientation(LinearLayout.VERTICAL);
        rootL.setBackgroundColor(Theme.BG);

        // Toolbar
        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(6));
        countLabel = Ui.text(act, "Videos", 18, Theme.TEXT, true);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        countLabel.setLayoutParams(clp);
        bar.addView(countLabel);
        bar.addView(iconBtn(R.drawable.ic_sort, v -> sortDialog()));
        bar.addView(iconBtn(gridMode ? R.drawable.ic_list : R.drawable.ic_grid, v -> {
            gridMode = !gridMode;
            applyLayout();
        }));
        bar.addView(iconBtn(R.drawable.ic_refresh, v -> { Library.scanAsync(act); toast("Refreshing library…"); }));
        rootL.addView(bar);

        // Filter chips
        LinearLayout chips = new LinearLayout(act);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(Ui.dp(14), Ui.dp(2), Ui.dp(14), Ui.dp(8));
        String[] names = {"All Videos", "Recent", "Favorites"};
        chipViews.clear();
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView chip = Ui.text(act, names[i], 13, Theme.TEXT, false);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(14), Ui.dp(7), Ui.dp(14), Ui.dp(7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, Ui.dp(8), 0);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> { filter = idx; styleChips(); applyData(); });
            chipViews.add(chip);
            chips.addView(chip);
        }
        rootL.addView(chips);
        styleChips();

        grid = new GridView(act);
        grid.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        grid.setPadding(Ui.dp(8), 0, Ui.dp(8), Ui.dp(8));
        grid.setVerticalSpacing(Ui.dp(6));
        grid.setHorizontalSpacing(Ui.dp(6));
        grid.setOnItemClickListener((p, v, pos, id) -> {
            List<MediaItem> data = currentData();
            ItemMenu.playVideoList(act, data, pos);
        });
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            ItemMenu.show(act, adapter.getItem(pos), this::applyData);
            return true;
        });
        rootL.addView(grid);

        emptyBox = new LinearLayout(act);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);
        emptyBox.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        rootL.addView(emptyBox);

        applyLayout();
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
            boolean active = i == filter;
            c.setBackground(active ? Ui.gradientBg(18) : Ui.rounded(Theme.ELEVATED, 18));
            c.setTextColor(active ? 0xFFFFFFFF : Theme.TEXT2);
        }
    }

    private void applyLayout() {
        grid.setNumColumns(gridMode ? 2 : 1);
        adapter = new MediaAdapter(act, currentData(), gridMode);
        grid.setAdapter(adapter);
    }

    private List<MediaItem> currentData() {
        List<MediaItem> data = new ArrayList<>();
        if (!Library.hasVideoPermission(act)) return data;
        Db db = Db.get(act);
        switch (filter) {
            case 1: data.addAll(Library.VIDEO); break; // scan order is newest-first
            case 2:
                for (String k : db.favoriteKeys()) {
                    MediaItem it = Library.findByKey(k);
                    if (it != null && it.video) data.add(it);
                }
                break;
            default: data.addAll(Library.VIDEO);
        }
        Library.sort(data, sortMode);
        if (filter == 1) Library.sort(data, Library.SORT_DATE);
        return data;
    }

    private void applyData() {
        if (grid == null) return;
        List<MediaItem> data = currentData();
        if (adapter == null) applyLayout(); else adapter.setItems(data);
        countLabel.setText("Videos  •  " + data.size());
        boolean showEmpty = data.isEmpty();
        grid.setVisibility(showEmpty ? View.GONE : View.VISIBLE);
        emptyBox.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (showEmpty) {
            emptyBox.removeAllViews();
            if (!Library.hasVideoPermission(act)) {
                emptyBox.addView(Ui.text(act, "Video access needed", 16, Theme.TEXT, true));
                TextView m = Ui.sub(act, "Allow access to your videos to browse and play them here.");
                m.setGravity(Gravity.CENTER);
                emptyBox.addView(m);
                LinearLayout btn = Ui.chip(act, 0, "Grant permission");
                btn.setBackground(Ui.gradientBg(20));
                btn.setOnClickListener(v -> act.requestPermissions(Library.neededPermissions(), 101));
                emptyBox.addView(btn);
            } else {
                emptyBox.addView(Ui.icon(act, R.drawable.ic_video, 48, Theme.TEXT2));
                emptyBox.addView(Ui.text(act, "No videos found", 16, Theme.TEXT, true));
                emptyBox.addView(Ui.sub(act, "Videos on your phone will appear here automatically."));
            }
        }
    }

    private void sortDialog() {
        String[] names = {"Name (A–Z)", "Date added (newest)", "Size (largest)", "Duration (longest)"};
        new AlertDialog.Builder(act)
                .setTitle("Sort videos by")
                .setItems(names, (d, w) -> { sortMode = w; applyData(); })
                .show();
    }

    private void toast(String s) { android.widget.Toast.makeText(act, s, android.widget.Toast.LENGTH_SHORT).show(); }

    @Override public void refresh() { applyData(); }
}
