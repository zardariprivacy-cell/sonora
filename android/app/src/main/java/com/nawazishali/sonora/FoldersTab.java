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
import android.widget.Toast;

/** Folder browser: MediaStore buckets with real counts/sizes + SAF folder access. */
public class FoldersTab extends Tab {
    public static final int REQ_TREE = 778;
    private LinearLayout content;

    public FoldersTab(Activity act) { super(act); }

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
        content.addView(Ui.title(act, "Folders"));

        // Storage summary card
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.cardBg());
        card.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, Ui.dp(10), 0, Ui.dp(4));
        card.setLayoutParams(clp);
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long total = fs.getTotalBytes(), free = fs.getAvailableBytes();
            card.addView(Ui.text(act, "Internal Storage", 15, Theme.TEXT, true));
            card.addView(Ui.sub(act, Ui.fmtSize(total - free) + " used  •  " + Ui.fmtSize(free) + " free of " + Ui.fmtSize(total)));
        } catch (Throwable ignored) { }
        card.addView(Ui.sub(act, "Music " + Ui.fmtSize(Library.totalAudioSize()) + "  •  Videos " + Ui.fmtSize(Library.totalVideoSize())));
        card.setOnClickListener(v -> act.startActivity(new Intent(act, StorageActivity.class)));
        content.addView(card);

        // SAF folder access
        LinearLayout saf = Ui.chip(act, R.drawable.ic_folder, "Open a folder (SD card / custom location)");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.setMargins(0, Ui.dp(10), 0, Ui.dp(6));
        saf.setLayoutParams(slp);
        saf.setGravity(Gravity.CENTER);
        saf.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try { act.startActivityForResult(i, REQ_TREE); }
            catch (Exception e) { Toast.makeText(act, "Folder picker not available", Toast.LENGTH_SHORT).show(); }
        });
        content.addView(saf);

        String savedTree = Db.get(act).getSetting("saf_tree", "");
        if (!savedTree.isEmpty()) {
            LinearLayout last = Ui.chip(act, R.drawable.ic_folder, "Re-open last granted folder");
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            llp.setMargins(0, Ui.dp(6), 0, Ui.dp(6));
            last.setLayoutParams(llp);
            last.setGravity(Gravity.CENTER);
            last.setOnClickListener(v -> {
                Intent i = new Intent(act, FolderMediaActivity.class);
                i.putExtra("treeUri", Db.get(act).getSetting("saf_tree", ""));
                act.startActivity(i);
            });
            content.addView(last);
        }

        if (!Library.hasAnyPermission(act)) {
            content.addView(Ui.spacer(act, 10));
            content.addView(Ui.sub(act, "Allow media access to see your media folders."));
            LinearLayout btn = Ui.chip(act, 0, "Grant permission");
            btn.setBackground(Ui.gradientBg(20));
            btn.setOnClickListener(v -> act.requestPermissions(Library.neededPermissions(), 101));
            content.addView(btn);
            return;
        }

        java.util.List<Library.Folder> folders = Library.folders();
        content.addView(Ui.section(act, "Media folders  •  " + folders.size()));
        if (folders.isEmpty()) {
            content.addView(Ui.sub(act, "No media folders found yet."));
            return;
        }
        for (final Library.Folder f : folders) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Ui.cardBg());
            row.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, Ui.dp(8));
            row.setLayoutParams(lp);
            row.addView(Ui.icon(act, R.drawable.ic_folder, 30, Theme.SECONDARY));
            LinearLayout texts = new LinearLayout(act);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            tlp.setMargins(Ui.dp(12), 0, 0, 0);
            texts.setLayoutParams(tlp);
            texts.addView(Ui.text(act, f.name, 15, Theme.TEXT, false));
            StringBuilder sb = new StringBuilder();
            if (f.audioCount > 0) sb.append(f.audioCount).append(" songs");
            if (f.videoCount > 0) { if (sb.length() > 0) sb.append("  •  "); sb.append(f.videoCount).append(" videos"); }
            sb.append("  •  ").append(Ui.fmtSize(f.totalSize()));
            texts.addView(Ui.sub(act, sb.toString()));
            row.addView(texts);
            row.addView(Ui.text(act, "›", 22, Theme.TEXT2, false));
            row.setOnClickListener(v -> {
                Intent i = new Intent(act, FolderMediaActivity.class);
                i.putExtra("bucketId", f.id);
                i.putExtra("bucketName", f.name);
                act.startActivity(i);
            });
            content.addView(row);
        }
    }

    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_TREE && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            try {
                act.getContentResolver().takePersistableUriPermission(data.getData(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
            Db.get(act).setSetting("saf_tree", data.getData().toString());
            Intent i = new Intent(act, FolderMediaActivity.class);
            i.putExtra("treeUri", data.getData().toString());
            act.startActivity(i);
        }
    }
}
