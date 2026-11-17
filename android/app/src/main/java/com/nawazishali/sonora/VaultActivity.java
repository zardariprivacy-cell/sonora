package com.nawazishali.sonora;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.ImageView;
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
        scroll.setBackground(Ui.appBg());
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(18), Ui.dp(16), Ui.dp(18), Ui.dp(24));
        scroll.addView(content);
        setContentView(scroll);
        showGate();
    }

    // ---------------- gate (setup / unlock) ----------------
    // v3.6: the gate is now a centered colourful 3D card — gradient shield
    // badge on top, the PIN field(s) inside a tinted inner box, and full-
    // width gradient 3D buttons. Presentation only: every handler below is
    // byte-identical to v3.5 (same Vault.setPin / Vault.verifyPin calls,
    // same toasts, same device-credential intent). Note the vault has no
    // custom number pad — PIN entry is the system password field (its dots
    // are what sits in the tinted box); no keypad was invented because that
    // would change the PIN entry flow.
    private void showGate() {
        unlocked = false;
        clearPlayCache();
        content.removeAllViews();
        LinearLayout bar = new LinearLayout(this);
        TextView back = Ui.text(this, "‹", 26, Theme.TEXT, true);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        content.addView(bar);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(Ui.cardBg());
        card.setElevation(Ui.dp(6));
        card.setPadding(Ui.dp(22), Ui.dp(26), Ui.dp(22), Ui.dp(24));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, Ui.dp(14), 0, 0);
        card.setLayoutParams(cardLp);
        card.addView(Ui.iconBadge(this, R.drawable.ic_shield, 0, 68, 34));
        TextView t = Ui.text(this, "Private Vault", 22, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(12), 0, 0);
        card.addView(t);
        content.addView(card);

        if (!Vault.hasPin(this)) {
            // v3.3: was Ui.sub() (single line) — the text was cut off with "…".
            TextView m = Ui.bodyWrap(this, "Create a PIN to protect your private media. Files are encrypted with AES-256 on this device.");
            m.setGravity(Gravity.CENTER);
            m.setPadding(0, Ui.dp(6), 0, Ui.dp(2));
            card.addView(m);
            LinearLayout pinBox = pinBox();
            final EditText pin1 = pinField("New PIN (4+ digits)");
            final EditText pin2 = pinField("Confirm PIN");
            pinBox.addView(pin1); pinBox.addView(pin2);
            card.addView(pinBox);
            LinearLayout btn = gateButton(0, "Create PIN & open vault", Ui.gradientBg(20));
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
            card.addView(btn);
        } else {
            TextView m = Ui.bodyWrap(this, "Enter your PIN to unlock your encrypted media.");
            m.setGravity(Gravity.CENTER);
            m.setPadding(0, Ui.dp(6), 0, Ui.dp(2));
            card.addView(m);
            LinearLayout pinBox = pinBox();
            final EditText pin = pinField("Enter your PIN");
            pinBox.addView(pin);
            card.addView(pinBox);
            LinearLayout btn = gateButton(R.drawable.ic_lock, "Unlock", Ui.gradientBg(20));
            btn.setOnClickListener(v -> {
                if (Vault.verifyPin(this, pin.getText().toString())) {
                    unlocked = true;
                    showVault();
                } else toast("Wrong PIN");
            });
            card.addView(btn);

            LinearLayout device = gateButton(R.drawable.ic_key, "Use device lock (fingerprint / PIN)",
                    Ui.card3d(20, Ui.gradientFor(5)));
            device.setOnClickListener(v -> {
                KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                if (km != null && km.isKeyguardSecure()) {
                    Intent i = km.createConfirmDeviceCredentialIntent("Unlock Sonora Vault",
                            "Confirm your device lock to open the vault");
                    if (i != null) { startActivityForResult(i, REQ_DEVICE_UNLOCK); return; }
                }
                toast("No secure device lock is set up on this phone");
            });
            card.addView(device);
        }
    }

    /** Tinted inner box that holds the gate's PIN field(s) (v3.6). */
    private LinearLayout pinBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.LIGHT ? Theme.ELEVATED : Ui.withAlpha(Theme.ELEVATED, 235));
        bg.setCornerRadius(Ui.dp(18));
        bg.setStroke(Ui.dp(1), Theme.BORDER);
        box.setBackground(bg);
        box.setPadding(Ui.dp(16), Ui.dp(2), Ui.dp(16), Ui.dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(16), 0, 0);
        box.setLayoutParams(lp);
        return box;
    }

    /** Full-width gradient 3D button for the gate (white icon + label). */
    private LinearLayout gateButton(int iconRes, String label, Drawable bg) {
        LinearLayout b = Ui.chip(this, iconRes, label);
        b.setBackground(bg);
        b.setElevation(Ui.dp(4));
        b.setPadding(Ui.dp(16), Ui.dp(13), Ui.dp(16), Ui.dp(13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(14), 0, 0);
        b.setLayoutParams(lp);
        forceWhite(b);
        return b;
    }

    /** Force a chip's icon + label white (saturated fills always carry white). */
    private void forceWhite(LinearLayout chip) {
        for (int i = 0; i < chip.getChildCount(); i++) {
            View ch = chip.getChildAt(i);
            if (ch instanceof ImageView) ((ImageView) ch).setColorFilter(0xFFFFFFFF);
            else if (ch instanceof TextView) ((TextView) ch).setTextColor(0xFFFFFFFF);
        }
    }

    private EditText pinField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setSingleLine(true);
        e.setTextColor(Theme.TEXT);
        e.setHintTextColor(Theme.TEXT2);
        // v3.6: the field itself is a tinted inset box (deep base in the dark
        // theme, white on lavender in the light theme, thin border). Input
        // type, hint and colours are unchanged; also restyles the Change
        // PIN dialog's fields, which share this helper.
        e.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.LIGHT ? Theme.CARD : Theme.BG);
        bg.setCornerRadius(Ui.dp(14));
        bg.setStroke(Ui.dp(1), Theme.BORDER);
        e.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(12), 0, 0);
        e.setLayoutParams(lp);
        return e;
    }

    // ---------------- vault content ----------------
    /** One vault row's data, collected up front so the header can show counts. */
    private static class VaultEntry {
        long id; String name; String mime; long size; String encFile; boolean video;
    }

    // v3.6: header is a 3D card (gradient shield badge + live counts/usage),
    // Import is a big gradient 3D box beside a tinted Change PIN box, every
    // item row is a colourful saturated 3D card with a gradient type badge,
    // and the empty state sits in a dashed card. Presentation only — the
    // import intent, changePin() and the rows' click (play) / long-press
    // (vault menu) targets are exactly as before, and vault rows still have
    // no ⋮ (v3.4).
    private void showVault() {
        content.removeAllViews();
        LinearLayout bar = new LinearLayout(this);
        TextView back = Ui.text(this, "‹", 26, Theme.TEXT, true);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        content.addView(bar);

        List<VaultEntry> items = new ArrayList<>();
        long totalBytes = 0;
        Cursor c = Db.get(this).vaultCursor();
        while (c.moveToNext()) {
            VaultEntry e = new VaultEntry();
            e.id = c.getLong(0);
            e.name = c.getString(1);
            e.mime = c.getString(2) == null ? "" : c.getString(2);
            e.size = c.getLong(3);
            e.encFile = c.getString(4);
            e.video = c.getInt(5) == 1;
            items.add(e);
            totalBytes += e.size;
        }
        c.close();

        // Header 3D card: gradient badge + title + counts/usage line.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(Ui.cardBg());
        header.setElevation(Ui.dp(4));
        header.setPadding(Ui.dp(18), Ui.dp(16), Ui.dp(18), Ui.dp(16));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.setMargins(0, Ui.dp(10), 0, 0);
        header.setLayoutParams(hlp);
        LinearLayout hrow = new LinearLayout(this);
        hrow.setOrientation(LinearLayout.HORIZONTAL);
        hrow.setGravity(Gravity.CENTER_VERTICAL);
        hrow.addView(Ui.iconBadge(this, R.drawable.ic_shield, 0, 54, 29));
        LinearLayout htexts = new LinearLayout(this);
        htexts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams htlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        htlp.setMargins(Ui.dp(14), 0, 0, 0);
        htexts.setLayoutParams(htlp);
        htexts.addView(Ui.text(this, "Private Vault", 19, Theme.TEXT, true));
        htexts.addView(Ui.bodyWrap(this, items.isEmpty()
                ? "No files yet"
                : items.size() + (items.size() == 1 ? " file" : " files") + "  •  "
                        + Ui.fmtSize(totalBytes) + " encrypted on this device"));
        hrow.addView(htexts);
        header.addView(hrow);
        TextView desc = Ui.bodyWrap(this, "Files here are encrypted and hidden from the normal library.");
        desc.setPadding(0, Ui.dp(10), 0, 0);
        header.addView(desc);
        content.addView(header);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, Ui.dp(14), 0, Ui.dp(14));
        LinearLayout imp = Ui.chip(this, R.drawable.ic_download, "Import media");
        imp.setBackground(Ui.gradientBg(18));
        imp.setElevation(Ui.dp(4));
        imp.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12));
        imp.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        forceWhite(imp);
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
        chg.setBackground(Ui.card3d(18, Ui.gradientFor(5)));
        chg.setElevation(Ui.dp(4));
        chg.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        clp.setMargins(Ui.dp(8), 0, 0, 0);
        chg.setLayoutParams(clp);
        forceWhite(chg);
        chg.setOnClickListener(v -> changePin());
        actions.addView(chg);
        content.addView(actions);

        if (items.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(Ui.dp(22), Ui.dp(24), Ui.dp(22), Ui.dp(24));
            empty.setBackground(dashedCardBg());
            empty.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            empty.addView(Ui.iconBadge(this, R.drawable.ic_shield, 3, 52, 28));
            TextView t = Ui.bodyWrap(this, "Your vault is empty. Import songs or videos to keep them private.");
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(12), 0, 0);
            empty.addView(t);
            content.addView(empty);
        }
        for (VaultEntry e : items) content.addView(vaultRow(e));
    }

    /** Dashed-border card background for the vault empty state (v3.6). */
    private Drawable dashedCardBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.withAlpha(Theme.CARD, Theme.LIGHT ? 255 : 150));
        g.setCornerRadius(Ui.dp(20));
        g.setStroke(Ui.dp(1), Theme.BORDER, Ui.dp(7), Ui.dp(5));
        return g;
    }

    /**
     * One vault item as a colourful 3D card (v3.6): saturated gradient fill
     * from the badge family (blue = video, violet = audio, pink = photo,
     * indigo-cyan = file), a matching gradient type badge, white title and
     * a size • type • Encrypted line. Click plays, long-press opens the
     * vault's own menu — the same targets Rows.mediaRow wired before; no ⋮.
     */
    private View vaultRow(final VaultEntry e) {
        final boolean audio = e.mime.startsWith("audio/");
        final boolean image = e.mime.startsWith("image/");
        final int g = e.video ? 2 : audio ? 0 : image ? 1 : 5;
        final int icon = e.video ? R.drawable.ic_video : audio ? R.drawable.ic_music : R.drawable.ic_files;
        final String kind = e.video ? "Video" : audio ? "Audio" : image ? "Photo" : "File";
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        row.setBackground(Ui.card3d(18, Ui.gradientFor(g)));
        row.setElevation(Ui.dp(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(10));
        row.setLayoutParams(lp);
        row.addView(Ui.iconBadge(this, icon, g, 46, 24));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tlp.setMargins(Ui.dp(12), 0, Ui.dp(8), 0);
        texts.setLayoutParams(tlp);
        TextView title = Ui.text(this, e.name, 14.5f, 0xFFFFFFFF, true);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);
        TextView sub = Ui.text(this, Ui.fmtSize(e.size) + "  •  " + kind + "  •  Encrypted",
                12.5f, Ui.withAlpha(0xFFFFFFFF, 215), false);
        sub.setMaxLines(1);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(sub);
        row.addView(texts);
        row.addView(Ui.icon(this, R.drawable.ic_lock, 16, Ui.withAlpha(0xFFFFFFFF, 200)));
        row.setOnClickListener(v -> playVaultItem(e.id, e.name, e.mime, e.video));
        row.setOnLongClickListener(v -> { vaultItemMenu(e.id, e.name, e.mime, e.encFile, e.video); return true; });
        PressFx.apply(row);
        return row;
    }

    private void changePin() {
        final EditText oldPin = pinField("Current PIN");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        // v3.6: the dialog card itself was already the 3D dialog card
        // (Dialogs uses Ui.dialogCardBg); the fields now carry the tinted
        // inset-box style via pinField(). Flow and validation unchanged.
        box.setPadding(Ui.dp(2), Ui.dp(4), Ui.dp(2), 0);
        box.addView(oldPin);
        final EditText newPin = pinField("New PIN");
        box.addView(newPin);
        new Dialogs.Builder(this)
                .title("Change PIN")
                .content(box)
                .button("Cancel", Dialogs.BTN_GHOST, (Runnable) null)
                .button("Change", Dialogs.BTN_PRIMARY, () -> {
                    if (!Vault.verifyPin(this, oldPin.getText().toString())) { toast("Current PIN is wrong"); return; }
                    if (newPin.getText().toString().length() < 4) { toast("New PIN too short"); return; }
                    try { Vault.setPin(this, newPin.getText().toString()); toast("PIN changed"); }
                    catch (Exception e) { toast("Could not change PIN"); }
                })
                .show();
    }

    private void vaultItemMenu(final long id, final String name, final String mime,
                                final String encFile, final boolean video) {
        Dialogs.list(this, name,
                new String[]{"Play", "Export back to library", "Delete from vault"}, w -> {
                    if (w == 0) playVaultItem(id, name, mime, video);
                    else if (w == 1) exportItem(name, mime, encFile, video);
                    else Dialogs.confirm(this, "Delete from vault?",
                            "The encrypted file will be permanently deleted.",
                            "Delete", true, () -> {
                                new File(encFile).delete();
                                Db.get(this).deleteVaultItem(id);
                                showVault();
                            });
                });
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
