package com.nawazishali.sonora;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Settings tab (v3.2): a gradient PROFILE CARD on top (avatar, time-of-day
 * greeting, editable name + photo — the reference design's profile section),
 * then the v3.1 icon grid and clean icon list regrouped inside glass cards.
 * Every cell and row opens a real screen or performs a real action.
 */
public class SettingsTab extends Tab {
    private static final int REQ_PROFILE_PHOTO = 991;
    private LinearLayout content;
    private android.app.Dialog profileDialog; // v3.4: dismissed before photo pick / after removal

    public SettingsTab(Activity act) { super(act); }

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

        // ---- Profile card (gradient, reference style) ----
        content.addView(profileCard());
        content.addView(Ui.spacer(act, 14));

        // ---- Icon grid (3 x 3), all real functions, inside a glass card ----
        LinearLayout gridCard = glassCard();
        gridCard.addView(Rows.gridRow(act,
                Rows.gridCell(act, R.drawable.ic_shield, "Private Vault",
                        v -> act.startActivity(new Intent(act, VaultActivity.class))),
                Rows.gridCell(act, R.drawable.ic_extract, "Extract Audio",
                        v -> pickVideoForExtract()),
                Rows.gridCell(act, R.drawable.ic_sd, "Storage",
                        v -> act.startActivity(new Intent(act, StorageActivity.class)))));
        gridCard.addView(Rows.gridRow(act,
                Rows.gridCell(act, R.drawable.ic_folder, "Folders",
                        v -> ((MainActivity) act).showTab(MainActivity.TAB_FOLDERS)),
                Rows.gridCell(act, R.drawable.ic_brightness, "Theme",
                        v -> toggleTheme()),
                Rows.gridCell(act, R.drawable.ic_history, "History",
                        v -> act.startActivity(new Intent(act, HistoryActivity.class)))));
        gridCard.addView(Rows.gridRow(act,
                Rows.gridCell(act, R.drawable.ic_share, "Share App",
                        v -> Ui.shareApp(act)),
                Rows.gridCell(act, R.drawable.ic_files, "Large Files",
                        v -> act.startActivity(new Intent(act, StorageActivity.class).putExtra("focus", "large"))),
                Rows.gridCell(act, R.drawable.ic_refresh, "Refresh Library",
                        v -> { Library.scanAsync(act); Toast.makeText(act, "Refreshing library…", Toast.LENGTH_SHORT).show(); })));
        content.addView(gridCard);
        content.addView(Ui.spacer(act, 14));

        // ---- Clean icon list inside a glass card ----
        LinearLayout listCard = glassCard();
        listCard.setPadding(Ui.dp(10), Ui.dp(4), Ui.dp(10), Ui.dp(4));
        listCard.addView(Rows.menuRow(act, R.drawable.ic_brightness, "Theme",
                Theme.LIGHT ? "Light" : "Dark", v -> toggleTheme()));
        listCard.addView(Rows.menuDivider(act));
        listCard.addView(Rows.menuRow(act, R.drawable.ic_timer, "Sleep Timer", null,
                v -> SleepTimerUi.show(act)));
        listCard.addView(Rows.menuDivider(act));
        listCard.addView(Rows.menuRow(act, R.drawable.ic_equalizer, "Equalizer", null,
                v -> act.startActivity(new Intent(act, EqualizerActivity.class))));
        listCard.addView(Rows.menuDivider(act));
        listCard.addView(Rows.menuRow(act, R.drawable.ic_shield, "Private Vault", null,
                v -> act.startActivity(new Intent(act, VaultActivity.class))));
        listCard.addView(Rows.menuDivider(act));
        listCard.addView(Rows.menuRow(act, R.drawable.ic_sd, "Storage Dashboard", null,
                v -> act.startActivity(new Intent(act, StorageActivity.class))));
        listCard.addView(Rows.menuDivider(act));
        listCard.addView(Rows.menuRow(act, R.drawable.ic_history, "Playback History", null,
                v -> act.startActivity(new Intent(act, HistoryActivity.class))));
        listCard.addView(Rows.menuDivider(act));
        listCard.addView(Rows.menuRow(act, R.drawable.ic_help, "Help", null,
                v -> act.startActivity(new Intent(act, HelpActivity.class))));
        listCard.addView(Rows.menuDivider(act));
        listCard.addView(Rows.menuRow(act, R.drawable.ic_info, "About", null,
                v -> aboutDialog()));
        content.addView(listCard);
        content.addView(Ui.spacer(act, 14));

        // ---- Permissions (kept from v3.0) inside a glass card ----
        LinearLayout permCard = glassCard();
        permCard.setPadding(Ui.dp(10), Ui.dp(4), Ui.dp(10), Ui.dp(4));
        boolean vizGranted = act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        permCard.addView(Rows.menuRow(act, R.drawable.ic_headphones, "Visualizer Permission",
                vizGranted ? "Granted" : "Not granted",
                v -> act.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 102)));
        permCard.addView(Rows.menuDivider(act));
        permCard.addView(Rows.menuRow(act, R.drawable.ic_key, "Media Permissions",
                Library.hasAnyPermission(act) ? "Granted" : "Not granted",
                v -> act.requestPermissions(Library.neededPermissions(), 101)));
        content.addView(permCard);
    }

    private LinearLayout glassCard() {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.cardBg());
        card.setElevation(Ui.dp(3));
        card.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        return card;
    }

    // ------------------------------------------------------------- profile

    private LinearLayout profileCard() {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.gradientBg(24));
        card.setElevation(Ui.dp(5));
        card.setPadding(Ui.dp(16), Ui.dp(16), Ui.dp(14), Ui.dp(16));

        card.addView(Profile.avatarView(act, 56));

        LinearLayout texts = new LinearLayout(act);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tlp.setMargins(Ui.dp(14), 0, Ui.dp(10), 0);
        texts.setLayoutParams(tlp);
        texts.addView(Ui.text(act, Profile.greeting(), 12.5f, Ui.withAlpha(0xFFFFFFFF, 215), false));
        TextView nm = Ui.text(act, Profile.name(act), 18, 0xFFFFFFFF, true);
        nm.setMaxLines(1);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(nm);
        texts.addView(Ui.text(act, "Your Complete Media Experience", 11.5f, Ui.withAlpha(0xFFFFFFFF, 190), false));
        card.addView(texts);

        LinearLayout edit = new LinearLayout(act);
        edit.setGravity(Gravity.CENTER);
        edit.setBackground(Ui.rounded(Ui.withAlpha(0xFFFFFFFF, 46), 16));
        edit.setPadding(Ui.dp(13), Ui.dp(7), Ui.dp(13), Ui.dp(7));
        edit.addView(Ui.icon(act, R.drawable.ic_edit, 14, 0xFFFFFFFF));
        TextView et = Ui.text(act, "Edit", 13, 0xFFFFFFFF, true);
        et.setPadding(Ui.dp(5), 0, 0, 0);
        edit.addView(et);
        edit.setOnClickListener(v -> editProfileDialog());
        PressFx.apply(edit);
        card.addView(edit);
        return card;
    }

    private void editProfileDialog() {
        // v3.3: the old stock AlertDialog rendered as a plain grey box — now a
        // centered 3D card from the shared Dialogs helper, gradient Save button.
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        final EditText nameInput = Dialogs.themedInput(act, "Your name",
                Profile.name(act).equals(Profile.DEFAULT_NAME) ? "" : Profile.name(act));
        nameInput.setHint("Your name");
        box.addView(nameInput);

        LinearLayout btnRow = new LinearLayout(act);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, Ui.dp(14), 0, Ui.dp(2));
        LinearLayout photoBtn = Ui.chip(act, R.drawable.ic_edit, "Choose photo");
        photoBtn.setOnClickListener(v -> {
            if (profileDialog != null) profileDialog.dismiss();
            pickProfilePhoto();
        });
        btnRow.addView(photoBtn);
        if (Profile.hasPhoto(act)) {
            LinearLayout removeBtn = Ui.chip(act, R.drawable.ic_delete, "Remove photo");
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(Ui.dp(10), 0, 0, 0);
            removeBtn.setLayoutParams(rlp);
            removeBtn.setOnClickListener(v -> {
                Profile.clearPhoto(act);
                Toast.makeText(act, "Profile photo removed", Toast.LENGTH_SHORT).show();
                if (profileDialog != null) profileDialog.dismiss();
                rebuild();
            });
            btnRow.addView(removeBtn);
        }
        box.addView(btnRow);

        final Dialogs.Builder builder = new Dialogs.Builder(act)
                .title("Edit profile")
                .content(box)
                .button("Cancel", Dialogs.BTN_GHOST, (Runnable) null)
                .button("Save", Dialogs.BTN_PRIMARY, () -> {
                    Profile.setName(act, nameInput.getText().toString());
                    Toast.makeText(act, "Profile saved", Toast.LENGTH_SHORT).show();
                    rebuild();
                });
        // (photo pick / removal dismiss this dialog via the profileDialog
        // field — see the handlers above — so no stale dialog floats over
        // the rebuilt Settings tab.)
        profileDialog = builder.dialog();
        builder.show();
    }

    private void pickProfilePhoto() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            act.startActivityForResult(i, REQ_PROFILE_PHOTO);
        } catch (Exception e) {
            Toast.makeText(act, "Photo picker not available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PROFILE_PHOTO && resultCode == Activity.RESULT_OK
                && data != null && data.getData() != null) {
            boolean ok = Profile.savePhoto(act, data.getData());
            Toast.makeText(act, ok ? "Profile photo updated" : "Could not use that image",
                    Toast.LENGTH_SHORT).show();
            if (ok) rebuild();
        }
    }

    // ------------------------------------------------------------- existing actions

    private void toggleTheme() {
        Theme.setLight(act, !Theme.LIGHT);
        act.recreate();
    }

    /** Pick a video from the library, then run the real M4A extraction on it. */
    private void pickVideoForExtract() {
        final List<MediaItem> videos = Library.VIDEO;
        if (videos.isEmpty()) {
            Toast.makeText(act, "No videos in the library yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[videos.size()];
        for (int i = 0; i < videos.size(); i++) names[i] = videos.get(i).title;
        Dialogs.list(act, "Extract audio from…", names, w -> {
            final MediaItem it = videos.get(w);
            Toast.makeText(act, "Extracting audio…", Toast.LENGTH_SHORT).show();
            AudioExtractor.extract(act, it, (ok, msg) -> {
                Toast.makeText(act, msg, Toast.LENGTH_LONG).show();
                if (ok) Library.scanAsync(act);
            });
        });
    }

    private void aboutDialog() {
        Dialogs.message(act, "Sonora",
                "Version 3.6\n\nYour Complete Media Experience\n\n"
                        + Library.AUDIO.size() + " songs  •  " + Library.VIDEO.size() + " videos on this device\n\n"
                        + "An original Sonora design in premium violet. Offline video & music player — "
                        + "no account, no uploads; your media never leaves your phone.");
    }
}
