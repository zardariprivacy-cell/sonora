package com.nawazishali.sonora;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Private vault UI: PIN setup/verify (+ device-credential unlock), encrypted
 * import (encrypt -> verify -> delete original), in-vault playback (decrypts
 * into the app-private cache only, wiped on exit), export back to the library.
 */
public class VaultActivity extends Activity {
    private static final int REQ_IMPORT = 881;
    private static final int REQ_DEVICE_UNLOCK = 882;
    private static final int REQ_DELETE_ORIGINAL = 883;
    private LinearLayout content;
    private boolean unlocked = false;
    private List<Uri> pendingDeleteOriginals = new ArrayList<>();

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Theme.init(this);
        Ui.init(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Theme.BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(18), Ui.dp(16), Ui.dp(18), Ui.dp(24));
        scroll.addView(content);
        setContentView(scroll);
        showGate();
    }

    // ---------------- gate (setup / unlock) ----------------
    private void showGate() {
        unlocked = false;
        clearPlayCache();
        content.removeAllViews();
        LinearLayout bar = new LinearLayout(this);
        TextView back = Ui.text(this, "‹", 26, Theme.TEXT, true);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        content.addView(bar);

        content.addView(Ui.spacer(this, 10));
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        center.addView(Ui.icon(this, R.drawable.ic_shield, 54, Theme.SECONDARY));
        TextView t = Ui.text(this, "Private Vault", 21, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(10), 0, 0);
        center.addView(t);
        content.addView(center);

        if (!Vault.hasPin(this)) {
            TextView m = Ui.sub(this, "Create a PIN to protect your private media. Files are encrypted with AES-256 on this device.");
            m.setGravity(Gravity.CENTER);
            content.addView(m);
            final EditText pin1 = pinField("New PIN (4+ digits)");
            final EditText pin2 = pinField("Confirm PIN");
            content.addView(pin1); content.addView(pin2);
            LinearLayout btn = Ui.chip(this, 0, "Create PIN & open vault");
            btn.setBackground(Ui.gradientBg(22));
            btn.setGravity(Gravity.CENTER);
            btn.setOnClickListener(v -> {
                String a = pin1.getText().toString(), b = pin2.getText().toString();
                if (a.length() < 4) { toast("PIN must be at least 4 characters"); return; }
                if (!a.equals(b)) { toast("PINs do not match"); return; }
                try {
                    Vault.setPin(this, a);
                    unlocked = true;
                    showVault();
                } catch (Exception e) { toast("Could not create PIN"); }
            });
            content.addView(btn);
        } else {
            final EditText pin = pinField("Enter your PIN");
            content.addView(pin);
            LinearLayout btn = Ui.chip(this, R.drawable.ic_lock, "Unlock");
            btn.setBackground(Ui.gradientBg(22));
            btn.setGravity(Gravity.CENTER);
            btn.setOnClickListener(v -> {
                if (Vault.verifyPin(this, pin.getText().toString())) {
                    unlocked = true;
                    showVault();
                } else toast("Wrong PIN");
            });
            content.addView(btn);

            LinearLayout device = Ui.chip(this, R.drawable.ic_key, "Use device lock (fingerprint / PIN)");
            device.setGravity(Gravity.CENTER);
            device.setOnClickListener(v -> {
                KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                if (km != null && km.isKeyguardSecure()) {
                    Intent i = km.createConfirmDeviceCredentialIntent("Unlock Sonora Vault",
                            "Confirm your device lock to open the vault");
                    if (i != null) { startActivityForResult(i, REQ_DEVICE_UNLOCK); return; }
                }
                toast("No secure device lock is set up on this phone");
            });
            content.addView(device);
        }
    }

    private EditText pinField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setSingleLine(true);
        e.setTextColor(Theme.TEXT);
        e.setHintTextColor(Theme.TEXT2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(12), 0, 0);
        e.setLayoutParams(lp);
        return e;
    }

    // ---------------- vault content ----------------
    private void showVault() {
        content.removeAllViews();
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "‹", 26, Theme.TEXT, true);
        back.setPadding(0, 0, Ui.dp(10), 0);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView h = Ui.text(this, "Private Vault", 19, Theme.TEXT, true);
        h.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(h);
        bar.addView(Ui.icon(this, R.drawable.ic_lock, 22, Theme.ACCENT));
        content.addView(bar);
        content.addView(Ui.sub(this, "Files here are encrypted and hidden from the normal library."));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, Ui.dp(12), 0, Ui.dp(4));
        LinearLayout imp = Ui.chip(this, R.drawable.ic_download, "Import media");
        imp.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "video/*"});
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            try { startActivityForResult(i, REQ_IMPORT); }
            catch (Exception e) { toast("File picker not available"); }
        });
        actions.addView(imp);
        LinearLayout chg = Ui.chip(this, R.drawable.ic_key, "Change PIN");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(Ui.dp(8), 0, 0, 0);
        chg.setLayoutParams(clp);
        chg.setOnClickListener(v -> changePin());
        actions.addView(chg);
        content.addView(actions);

        Cursor c = Db.get(this).vaultCursor();
        if (c.getCount() == 0) {
            content.addView(Ui.spacer(this, 16));
            content.addView(Ui.sub(this, "Your vault is empty. Import songs or videos to keep them private."));
        }
        while (c.moveToNext()) {
            final long id = c.getLong(0);
            final String name = c.getString(1);
            final String mime = c.getString(2);
            final long size = c.getLong(3);
            final String encFile = c.getString(4);
            final boolean video = c.getInt(5) == 1;
            MediaItem it = new MediaItem();
            it.id = id; it.title = name; it.size = size; it.video = video; it.mime = mime == null ? "" : mime;
            content.addView(Rows.mediaRow(this, it,
                    v -> playVaultItem(id, name, mime, video),
                    v -> { vaultItemMenu(id, name, mime, encFile, video); return true; }));
        }
        c.close();
    }

    private void changePin() {
        final EditText oldPin = pinField("Current PIN");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(oldPin);
        final EditText newPin = pinField("New PIN");
        box.addView(newPin);
        new AlertDialog.Builder(this)
                .setTitle("Change PIN")
                .setView(box)
                .setPositiveButton("Change", (d, w) -> {
                    if (!Vault.verifyPin(this, oldPin.getText().toString())) { toast("Current PIN is wrong"); return; }
                    if (newPin.getText().toString().length() < 4) { toast("New PIN too short"); return; }
                    try { Vault.setPin(this, newPin.getText().toString()); toast("PIN changed"); }
                    catch (Exception e) { toast("Could not change PIN"); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void vaultItemMenu(final long id, final String name, final String mime,
                                final String encFile, final boolean video) {
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(new String[]{"Play", "Export back to library", "Delete from vault"}, (d, w) -> {
                    if (w == 0) playVaultItem(id, name, mime, video);
                    else if (w == 1) exportItem(name, mime, encFile, video);
                    else new AlertDialog.Builder(this)
                            .setTitle("Delete from vault?")
                            .setMessage("The encrypted file will be permanently deleted.")
                            .setPositiveButton("Delete", (dd, ww) -> {
                                new File(encFile).delete();
                                Db.get(this).deleteVaultItem(id);
                                showVault();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .show();
    }

    // ---------------- import ----------------
    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_DEVICE_UNLOCK && res == RESULT_OK) {
            unlocked = true;
            showVault();
            return;
        }
        if (req == REQ_DELETE_ORIGINAL) {
            pendingDeleteOriginals.clear();
            if (unlocked) showVault();
            return;
        }
        if (req == REQ_IMPORT && res == RESULT_OK && data != null) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++)
                    uris.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) uris.add(data.getData());
            if (!uris.isEmpty()) importUris(uris);
        }
    }

    private void importUris(final List<Uri> uris) {
        toast("Encrypting " + uris.size() + " file(s)…");
        new Thread(() -> {
            int ok = 0;
            List<Uri> deleteAfter = new ArrayList<>();
            for (Uri uri : uris) {
                try {
                    String name = "Media", mime = "";
                    long size = -1;
                    try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                        if (c != null && c.moveToFirst()) {
                            int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                            int si = c.getColumnIndex(OpenableColumns.SIZE);
                            if (ni >= 0) name = c.getString(ni);
                            if (si >= 0 && !c.isNull(si)) size = c.getLong(si);
                        }
                    }
                    mime = getContentResolver().getType(uri);
                    if (mime == null) mime = "";
                    boolean video = mime.startsWith("video/");
                    long counted = 0;
                    File enc;
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        // Count while encrypting by wrapping: encrypt() streams; we know the
                        // plaintext size from the provider or by counting a second pass below.
                        enc = Vault.encrypt(this, in, name);
                    }
                    if (size < 0) {
                        // Unknown size: decrypt-count to learn plaintext size, then verify by re-count
                        final long[] n = {0};
                        Vault.decrypt(enc, new OutputStream() {
                            @Override public void write(int b) { n[0]++; }
                            @Override public void write(byte[] b, int off, int len) { n[0] += len; }
                        });
                        size = n[0];
                        counted = size;
                    } else {
                        counted = size;
                    }
                    if (!Vault.verify(enc, counted)) {
                        enc.delete();
                        continue;
                    }
                    Db.get(this).addVaultItem(name, mime, size, enc.getAbsolutePath(), video);
                    ok++;
                    // Delete the original (best effort with the platform consent flow)
                    if (deleteOriginal(uri)) deleteAfter.add(uri);
                } catch (Exception ignored) { }
            }
            final int okFinal = ok;
            runOnUiThread(() -> {
                toast(okFinal + " file(s) encrypted into the vault");
                if (unlocked) showVault();
            });
            // MediaStore items on API 30+ need the system delete dialog:
            if (Build.VERSION.SDK_INT >= 30 && !deleteAfter.isEmpty()) {
                List<Uri> mediaUris = new ArrayList<>();
                for (Uri u : deleteAfter) if (u.toString().contains("/media/")) mediaUris.add(u);
                if (!mediaUris.isEmpty()) {
                    try {
                        PendingIntent pi = MediaStore.createDeleteRequest(getContentResolver(), mediaUris);
                        pendingDeleteOriginals = mediaUris;
                        startIntentSenderForResult(pi.getIntentSender(), REQ_DELETE_ORIGINAL, null, 0, 0, 0);
                    } catch (Exception ignored) { }
                }
            }
        }).start();
    }

    /** Try to delete the original now (SAF documents). Returns true if it may need MediaStore consent. */
    private boolean deleteOriginal(Uri uri) {
        try {
            if (DocumentsContract.isDocumentUri(this, uri)) {
                return DocumentsContract.deleteDocument(getContentResolver(), uri);
            }
        } catch (Exception ignored) { }
        // MediaStore-style URI: handled by createDeleteRequest by the caller on API 30+,
        // direct delete below 30.
        if (Build.VERSION.SDK_INT < 30) {
            try { return getContentResolver().delete(uri, null, null) > 0; }
            catch (Exception ignored) { return false; }
        }
        return true;
    }

    // ---------------- playback / export ----------------
    private File playCacheDir() {
        File d = new File(getCacheDir(), "vaultplay");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private void clearPlayCache() {
        File d = playCacheDir();
        File[] files = d.listFiles();
        if (files != null) for (File f : files) f.delete();
    }

    private void playVaultItem(final long id, final String name, final String mime, final boolean video) {
        Cursor c = Db.get(this).vaultCursor();
        String encPath = null;
        while (c.moveToNext()) if (c.getLong(0) == id) { encPath = c.getString(4); break; }
        c.close();
        if (encPath == null) { toast("File not found"); return; }
        final String path = encPath;
        toast("Decrypting…");
        new Thread(() -> {
            try {
                clearPlayCache();
                String ext = extFor(mime, name);
                File plain = new File(playCacheDir(), "item" + id + ext);
                try (FileOutputStream out = new FileOutputStream(plain)) {
                    Vault.decrypt(new File(path), out);
                }
                final MediaItem it = new MediaItem();
                it.id = 900_000 + id;
                it.uri = ShareProvider.uriFor(plain);
                it.title = name;
                it.mime = mime == null ? "" : mime;
                it.video = video;
                runOnUiThread(() -> {
                    if (video) ItemMenu.playVideo(this, it);
                    else {
                        List<MediaItem> one = new ArrayList<>();
                        one.add(it);
                        ItemMenu.playAudio(this, one, 0);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Could not decrypt this file"));
            }
        }).start();
    }

    private void exportItem(final String name, final String mime, final String encFile, final boolean video) {
        toast("Exporting…");
        new Thread(() -> {
            boolean ok = false;
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                Uri collection;
                if (Build.VERSION.SDK_INT >= 29) {
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                            video ? "Movies/SonoraVault" : "Music/SonoraVault");
                    v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    collection = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                } else {
                    collection = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                Uri uri = getContentResolver().insert(collection, v);
                if (uri != null) {
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        Vault.decrypt(new File(encFile), out);
                    }
                    if (Build.VERSION.SDK_INT >= 29) {
                        v.clear();
                        v.put(MediaStore.MediaColumns.IS_PENDING, 0);
                        getContentResolver().update(uri, v, null, null);
                    }
                    ok = true;
                }
            } catch (Exception ignored) { }
            final boolean okF = ok;
            runOnUiThread(() -> {
                toast(okF ? "Exported to your library" : "Export failed");
                if (okF) Library.scanAsync(this);
            });
        }).start();
    }

    private String extFor(String mime, String name) {
        if (name != null && name.contains(".")) return name.substring(name.lastIndexOf('.'));
        if (mime == null) return ".bin";
        if (mime.contains("mp4")) return ".mp4";
        if (mime.contains("mpeg")) return ".mp3";
        if (mime.contains("mp3")) return ".mp3";
        if (mime.contains("mkv") || mime.contains("matroska")) return ".mkv";
        return ".bin";
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    @Override protected void onDestroy() {
        clearPlayCache();
        super.onDestroy();
    }
}
