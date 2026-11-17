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
        scroll.setBackground(Ui.appBg());
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

        // Storage summary card — v3.3: colourful 3D box with a gradient badge.
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.cardBg());
        card.setElevation(Ui.dp(4));
        card.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, Ui.dp(10), 0, Ui.dp(4));
        card.setLayoutParams(clp);
        LinearLayout cardHead = new LinearLayout(act);
        cardHead.setOrientation(LinearLayout.HORIZONTAL);
        cardHead.setGravity(Gravity.CENTER_VERTICAL);
        cardHead.addView(Ui.iconBadge(act, R.drawable.ic_sd, 0, 40, 21));
        TextView cardTitle = Ui.text(act, "Internal Storage", 15.5f, Theme.TEXT, true);
        LinearLayout.LayoutParams htlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        htlp.setMargins(Ui.dp(12), 0, 0, 0);
        cardTitle.setLayoutParams(htlp);
        cardHead.addView(cardTitle);
        cardHead.addView(Ui.text(act, "›", 22, Theme.TEXT2, false));
        card.addView(cardHead);
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long total = fs.getTotalBytes(), free = fs.getAvailableBytes();
            TextView used = Ui.bodyWrap(act, Ui.fmtSize(total - free) + " used  •  " + Ui.fmtSize(free) + " free of " + Ui.fmtSize(total));
            used.setPadding(0, Ui.dp(8), 0, 0);
            card.addView(used);
        } catch (Throwable ignored) { }
        card.addView(Ui.bodyWrap(act, "Music " + Ui.fmtSize(Library.totalAudioSize()) + "  •  Videos " + Ui.fmtSize(Library.totalVideoSize())));
        card.setOnClickListener(v -> act.startActivity(new Intent(act, StorageActivity.class)));
        PressFx.apply(card);
        content.addView(card);

        // SAF folder access — v3.3: colourful 3D card row with a gradient folder badge.
        LinearLayout saf = actionCard(R.drawable.ic_folder, 2, "Open a folder", "SD card / custom location");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.setMargins(0, Ui.dp(10), 0, Ui.dp(6));
        saf.setLayoutParams(slp);
        saf.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try { act.startActivityForResult(i, REQ_TREE); }
            catch (Exception e) { Toast.makeText(act, "Folder picker not available", Toast.LENGTH_SHORT).show(); }
        });
        content.addView(saf);

        String savedTree = Db.get(act).getSetting("saf_tree", "");
        if (!savedTree.isEmpty()) {
            LinearLayout last = actionCard(R.drawable.ic_folder, 4, "Last granted folder", "Tap to re-open it");
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            llp.setMargins(0, Ui.dp(6), 0, Ui.dp(6));
            last.setLayoutParams(llp);
            last.setOnClickListener(v -> {
                Intent i = new Intent(act, FolderMediaActivity.class);
                i.putExtra("treeUri", Db.get(act).getSetting("saf_tree", ""));
                act.startActivity(i);
            });
            content.addView(last);
        }

        if (!Library.hasAnyPermission(act)) {
            content.addView(Ui.spacer(act, 10));
            content.addView(Ui.bodyWrap(act, "Allow media access to see your media folders."));
            LinearLayout btn = Ui.chip(act, 0, "Grant permission");
            btn.setBackground(Ui.gradientBg(20));
            btn.setOnClickListener(v -> act.requestPermissions(Library.neededPermissions(), 101));
            content.addView(btn);
            return;
        }

        java.util.List<Library.Folder> folders = Library.folders();
        content.addView(Ui.sectionHeader(act, "Media folders  •  " + folders.size()));
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
            row.setElevation(Ui.dp(3));
            row.addView(Ui.iconBadge(act, R.drawable.ic_folder, f.name.hashCode(), 44, 23));
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
            final Runnable openFolder = () -> {
                Intent i = new Intent(act, FolderMediaActivity.class);
                i.putExtra("bucketId", f.id);
                i.putExtra("bucketName", f.name);
                act.startActivity(i);
            };
            row.setOnClickListener(v -> openFolder.run());
            // Long-press (v3.1): the folder "3D box" with Open / Details.
            row.setOnLongClickListener(v -> {
                MediaSheet.showFolder(act, f, openFolder);
                return true;
            });
            content.addView(row);
        }
    }

    /** A colourful 3D action card: gradient badge + title + subtitle + chevron. */
    private LinearLayout actionCard(int iconRes, int gradIndex, String title, String subtitle) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.cardBg());
        row.setElevation(Ui.dp(3));
        row.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
        row.addView(Ui.iconBadge(act, iconRes, gradIndex, 44, 23));
        LinearLayout texts = new LinearLayout(act);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tlp.setMargins(Ui.dp(12), 0, 0, 0);
        texts.setLayoutParams(tlp);
        texts.addView(Ui.text(act, title, 15, Theme.TEXT, false));
        texts.addView(Ui.sub(act, subtitle));
        row.addView(texts);
        row.addView(Ui.text(act, "›", 22, Theme.TEXT2, false));
        PressFx.apply(row);
        return row;
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
