package com.nawazishali.sonora;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Media inside one folder: either a MediaStore bucket or a SAF-granted tree. */
public class FolderMediaActivity extends Activity {
    private ListView list;
    private String bucketId, bucketName, treeUri;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Theme.init(this);
        Ui.init(this);
        bucketId = getIntent().getStringExtra("bucketId");
        bucketName = getIntent().getStringExtra("bucketName");
        treeUri = getIntent().getStringExtra("treeUri");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.appBg());
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(Ui.dp(10), Ui.dp(12), Ui.dp(14), Ui.dp(10));
        TextView back = Ui.text(this, "‹", 26, Theme.TEXT, true);
        back.setPadding(Ui.dp(6), 0, Ui.dp(12), 0);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        bar.addView(Ui.text(this, treeUri != null ? "Folder" : (bucketName == null ? "Folder" : bucketName), 18, Theme.TEXT, true));
        root.addView(bar);

        list = new ListView(this);
        list.setDivider(null);
        list.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        if (treeUri != null) loadTree();
        else loadBucket();
    }

    private void loadBucket() {
        List<MediaItem> items = new ArrayList<>();
        for (MediaItem it : Library.AUDIO) if (bucketId != null && bucketId.equals(it.bucketId)) items.add(it);
        for (MediaItem it : Library.VIDEO) if (bucketId != null && bucketId.equals(it.bucketId)) items.add(it);
        Library.sort(items, Library.SORT_DATE);
        show(items);
    }

    private void loadTree() {
        final Uri tree = Uri.parse(treeUri);
        new Thread(() -> {
            List<MediaItem> items = new ArrayList<>();
            try {
                String treeId = DocumentsContract.getTreeDocumentId(tree);
                Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
                try (Cursor c = getContentResolver().query(children, new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE}, null, null, null)) {
                    if (c != null) while (c.moveToNext()) {
                        String docId = c.getString(0);
                        String name = c.getString(1);
                        String mime = c.getString(2);
                        long size = c.isNull(3) ? 0 : c.getLong(3);
                        if (mime == null) continue;
                        boolean isAudio = mime.startsWith("audio/");
                        boolean isVideo = mime.startsWith("video/");
                        if (!isAudio && !isVideo) continue;
                        MediaItem it = new MediaItem();
                        it.id = (long) docId.hashCode();
                        it.uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId);
                        it.title = name == null ? "Media" : name;
                        it.size = size;
                        it.mime = mime;
                        it.video = isVideo;
                        it.bucketName = "Folder";
                        // Duration via retriever when possible
                        try {
                            android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
                            r.setDataSource(this, it.uri);
                            String d = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                            if (d != null) it.duration = Long.parseLong(d);
                            r.release();
                        } catch (Throwable ignored) { }
                        items.add(it);
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Could not read this folder", Toast.LENGTH_LONG).show());
            }
            runOnUiThread(() -> show(items));
        }).start();
    }

    private void show(final List<MediaItem> items) {
        if (items.isEmpty()) {
            Toast.makeText(this, "No playable media in this folder", Toast.LENGTH_SHORT).show();
        }
        final MediaAdapter adapter = new MediaAdapter(this, items, false);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            MediaItem it = items.get(pos);
            if (it.video) {
                List<MediaItem> videos = new ArrayList<>();
                int start = 0;
                for (MediaItem m : items) if (m.video) { if (m == it) start = videos.size(); videos.add(m); }
                ItemMenu.playVideoList(this, videos, start);
            } else {
                List<MediaItem> songs = new ArrayList<>();
                int start = 0;
                for (MediaItem m : items) if (!m.video) { if (m == it) start = songs.size(); songs.add(m); }
                ItemMenu.playAudio(this, songs, start);
            }
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            ItemMenu.show(this, items.get(pos), () -> { if (treeUri == null) loadBucket(); });
            return true;
        });
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 4471) { Library.scanAsync(this); loadBucket(); }
    }
}
