package com.nawazishali.sonora;

import android.app.Activity;
import android.content.Intent;
import android.os.Environment;
import android.os.StatFs;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Home dashboard (v3.2) — restyled to the user's reference design: profile
 * header, a hero carousel of large artwork cards, pill category chips,
 * horizontal album-art cards and favorite-playlist rows with round gradient
 * play buttons. Everything shown is real device data (library + DB); a
 * section is hidden when it has no data.
 */
public class HomeTab extends Tab {
    private LinearLayout content;

    public HomeTab(Activity act) { super(act); }

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
        Db db = Db.get(act);

        // ---- Profile header (reference style): avatar + greeting + name,
        // round glass icon buttons for Search and Private Vault on the right.
        LinearLayout header = new LinearLayout(act);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Profile.avatarView(act, 46));
        LinearLayout who = new LinearLayout(act);
        who.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        wlp.setMargins(Ui.dp(12), 0, Ui.dp(8), 0);
        who.setLayoutParams(wlp);
        who.addView(Ui.sub(act, Profile.greeting()));
        TextView nm = Ui.text(act, Profile.name(act), 17, Theme.TEXT, true);
        nm.setMaxLines(1);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        who.addView(nm);
        header.addView(who);
        header.addView(roundBtn(R.drawable.ic_search,
                v -> act.startActivity(new Intent(act, SearchActivity.class))));
        LinearLayout vaultBtn = roundBtn(R.drawable.ic_shield,
                v -> act.startActivity(new Intent(act, VaultActivity.class)));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(Ui.dp(42), Ui.dp(42));
        vlp.setMargins(Ui.dp(10), 0, 0, 0);
        vaultBtn.setLayoutParams(vlp);
        header.addView(vaultBtn);
        content.addView(header);
        content.addView(Ui.spacer(act, 6));

        if (!Library.hasAnyPermission(act)) {
            content.addView(Ui.spacer(act, 14));
            LinearLayout card = new LinearLayout(act);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.cardBg());
            card.setPadding(Ui.dp(16), Ui.dp(16), Ui.dp(16), Ui.dp(16));
            card.addView(Ui.text(act, "Allow media access", 16, Theme.TEXT, true));
            card.addView(Ui.bodyWrap(act, "Sonora needs permission to find the songs and videos on your phone. Nothing is uploaded — everything stays on your device."));
            LinearLayout btn = Ui.chip(act, 0, "Grant permission");
            btn.setBackground(Ui.gradientBg(20));
            btn.setOnClickListener(v -> act.requestPermissions(Library.neededPermissions(), 101));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            blp.setMargins(0, Ui.dp(12), 0, 0);
            btn.setLayoutParams(blp);
            card.addView(btn);
            content.addView(card);
            return;
        }

        if (Library.scanning && Library.AUDIO.isEmpty() && Library.VIDEO.isEmpty()) {
            content.addView(Ui.spacer(act, 20));
            content.addView(Ui.sub(act, "Scanning your media library…"));
            return;
        }

        // ---- Hero carousel: recently added videos as large artwork cards.
        if (!Library.VIDEO.isEmpty()) {
            content.addView(Ui.sectionHeader(act, "New in your library"));
            final List<MediaItem> hero = new ArrayList<>(
                    Library.VIDEO.subList(0, Math.min(8, Library.VIDEO.size()))); // scan order = newest first
            HorizontalScrollView hs = new HorizontalScrollView(act);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < hero.size(); i++) {
                final int idx = i;
                row.addView(heroCard(hero.get(i), v -> ItemMenu.playVideoList(act, hero, idx)));
            }
            hs.addView(row);
            content.addView(hs);
        }

        // ---- Category chips (reference style): All stays; the rest navigate.
        content.addView(Ui.spacer(act, 16));
        HorizontalScrollView chipScroll = new HorizontalScrollView(act);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(act);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(navChip("All", true, null));
        chips.addView(navChip("Videos", false,
                v -> ((MainActivity) act).showTab(MainActivity.TAB_VIDEOS)));
        chips.addView(navChip("Music", false,
                v -> ((MainActivity) act).showTab(MainActivity.TAB_MUSIC)));
        chips.addView(navChip("Favorites", false,
                v -> ((MainActivity) act).openMusicFavorites()));
        chips.addView(navChip("Folders", false,
                v -> ((MainActivity) act).showTab(MainActivity.TAB_FOLDERS)));
        chipScroll.addView(chips);
        content.addView(chipScroll);

        // ---- Most Played: horizontal album-art cards (songs).
        List<MediaItem> mostAudio = new ArrayList<>();
        for (MediaItem it : Library.resolveKeys(db.mostPlayedKeys(10))) if (!it.video) mostAudio.add(it);
        if (!mostAudio.isEmpty()) {
            content.addView(Ui.sectionHeader(act, "Most Played"));
            final List<MediaItem> queue = mostAudio;
            HorizontalScrollView hs = new HorizontalScrollView(act);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < queue.size(); i++) {
                final int idx = i;
                row.addView(albumCard(queue.get(i), v -> ItemMenu.playAudio(act, queue, idx)));
            }
            hs.addView(row);
            content.addView(hs);
        }

        // ---- Favorite Playlist: favorites as rows with round gradient play buttons.
        List<MediaItem> favs = new ArrayList<>();
        for (String k : db.favoriteKeys()) {
            MediaItem it = Library.findByKey(k);
            if (it != null) favs.add(it);
        }
        if (!favs.isEmpty()) {
            content.addView(Ui.sectionHeader(act, "Favorite Playlist"));
            int n = Math.min(6, favs.size());
            for (int i = 0; i < n; i++) content.addView(favRow(favs.get(i), favs));
        }

        // ---- Continue Watching (kept from v3.1, new card style via Rows).
        List<MediaItem> cont = Library.resolveKeys(db.continueWatchingKeys(10));
        if (!cont.isEmpty()) {
            content.addView(Ui.sectionHeader(act, "Continue Watching"));
            for (MediaItem it : cont) {
                long pos = db.getPosition(it.key());
                final MediaItem f = it;
                content.addView(Rows.mediaRow(act, it,
                        v -> ItemMenu.playVideo(act, f),
                        v -> { ItemMenu.show(act, f, this::rebuild); return true; }));
                TextView prog = Ui.sub(act, "Resume at " + Ui.fmtDuration(pos) + " of " + Ui.fmtDuration(it.duration));
                prog.setPadding(Ui.dp(10), 0, 0, Ui.dp(6));
                content.addView(prog);
            }
        }

        // ---- Recently Played (mixed, kept).
        List<String> recentKeys = new ArrayList<>();
        recentKeys.addAll(db.recentKeys(false, 6));
        recentKeys.addAll(db.recentKeys(true, 6));
        List<MediaItem> recent = Library.resolveKeys(recentKeys);
        if (!recent.isEmpty()) {
            content.addView(Ui.sectionHeader(act, "Recently Played"));
            for (MediaItem it : recent) addRow(it);
        }

        // ---- Recently played music (fallback: recently added).
        List<MediaItem> recentMusic = Library.resolveKeys(db.recentKeys(false, 5));
        if (recentMusic.isEmpty() && !Library.AUDIO.isEmpty()) {
            content.addView(Ui.sectionHeader(act, "Recently Added Music"));
            int n = Math.min(5, Library.AUDIO.size());
            for (int i = 0; i < n; i++) addRow(Library.AUDIO.get(i));
        } else if (!recentMusic.isEmpty()) {
            content.addView(Ui.sectionHeader(act, "Recently Played Music"));
            for (MediaItem it : recentMusic) addRow(it);
        }

        // ---- Storage overview (kept).
        content.addView(Ui.sectionHeader(act, "Storage Overview"));
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.cardBg());
        card.setElevation(Ui.dp(3));
        card.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long total = fs.getTotalBytes(), free = fs.getAvailableBytes();
            card.addView(Ui.text(act, "Device storage", 14.5f, Theme.TEXT, true));
            card.addView(Ui.sub(act, Ui.fmtSize(total - free) + " used of " + Ui.fmtSize(total)
                    + "  •  " + Ui.fmtSize(free) + " free"));
        } catch (Throwable ignored) { }
        card.addView(Ui.sub(act, "Music library: " + Library.AUDIO.size() + " songs • " + Ui.fmtSize(Library.totalAudioSize())));
        card.addView(Ui.sub(act, "Video library: " + Library.VIDEO.size() + " videos • " + Ui.fmtSize(Library.totalVideoSize())));
        card.setOnClickListener(v -> act.startActivity(new Intent(act, StorageActivity.class)));
        content.addView(card);

        if (Library.AUDIO.isEmpty() && Library.VIDEO.isEmpty()) {
            content.addView(Ui.spacer(act, 16));
            content.addView(Ui.sub(act, "No media found yet. Add songs or videos to your phone, then pull Refresh from the Videos or Music tab."));
        }
    }

    // ------------------------------------------------------------- builders

    /** Round glass icon button used in the profile header. */
    private LinearLayout roundBtn(int iconRes, View.OnClickListener click) {
        LinearLayout b = new LinearLayout(act);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.pillBg(false));
        b.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(42), Ui.dp(42)));
        b.addView(Ui.icon(act, iconRes, 20, Theme.TEXT));
        b.setOnClickListener(click);
        PressFx.apply(b);
        return b;
    }

    private TextView navChip(String label, boolean selected, View.OnClickListener click) {
        TextView chip = Ui.text(act, label, 13.5f, selected ? 0xFFFFFFFF : Theme.TEXT, !selected);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(Ui.pillBg(selected));
        chip.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, Ui.dp(8), 0);
        chip.setLayoutParams(lp);
        if (click != null) { chip.setOnClickListener(click); PressFx.apply(chip); }
        return chip;
    }

    /** Large artwork card for the hero carousel: thumbnail, bottom scrim, title + duration. */
    private FrameLayout heroCard(final MediaItem it, View.OnClickListener click) {
        FrameLayout card = new FrameLayout(act);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(252), Ui.dp(150));
        lp.setMargins(0, 0, Ui.dp(12), 0);
        card.setLayoutParams(lp);
        card.setBackground(Ui.rounded(Theme.ELEVATED, 20));
        card.setClipToOutline(true);

        ImageView thumb = new ImageView(act);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        ImageLoader.load(act, it, thumb);
        card.addView(thumb);

        View scrim = new View(act);
        scrim.setBackground(Ui.scrimBg(0));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Ui.dp(72));
        slp.gravity = Gravity.BOTTOM;
        scrim.setLayoutParams(slp);
        card.addView(scrim);

        LinearLayout bottom = new LinearLayout(act);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.BOTTOM);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.BOTTOM;
        bottom.setLayoutParams(blp);
        bottom.setPadding(Ui.dp(12), 0, Ui.dp(10), Ui.dp(10));
        TextView title = Ui.text(act, it.title, 14.5f, 0xFFFFFFFF, true);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        bottom.addView(title);
        TextView dur = Ui.text(act, Ui.fmtDuration(it.duration), 11, 0xFFFFFFFF, false);
        dur.setBackground(Ui.rounded(Ui.withAlpha(0xFF000000, 150), 10));
        dur.setPadding(Ui.dp(7), Ui.dp(2), Ui.dp(7), Ui.dp(2));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.setMargins(Ui.dp(8), 0, 0, 0);
        dur.setLayoutParams(dlp);
        bottom.addView(dur);
        card.addView(bottom);

        card.setOnClickListener(click);
        card.setOnLongClickListener(v -> { ItemMenu.show(act, it, this::rebuild); return true; });
        PressFx.apply(card);
        return card;
    }

    /** Square album-art card for the Most Played rail: art on top, title + artist under it. */
    private LinearLayout albumCard(final MediaItem it, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(122), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, Ui.dp(12), 0);
        card.setLayoutParams(lp);

        FrameLayout artBox = new FrameLayout(act);
        artBox.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(122), Ui.dp(122)));
        artBox.setBackground(Ui.rounded(Theme.ELEVATED, 18));
        artBox.setClipToOutline(true);
        ImageView art = new ImageView(act);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        ImageLoader.load(act, it, art);
        artBox.addView(art);
        card.addView(artBox);

        TextView title = Ui.text(act, it.title, 13, Theme.TEXT, false);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(0, Ui.dp(6), 0, 0);
        card.addView(title);
        card.addView(Ui.sub(act, it.artist == null || it.artist.trim().isEmpty() ? "Unknown artist" : it.artist.trim()));

        card.setOnClickListener(click);
        card.setOnLongClickListener(v -> { ItemMenu.show(act, it, this::rebuild); return true; });
        PressFx.apply(card);
        return card;
    }

    /** Favorite-playlist row: glass card + round gradient play button (reference style). */
    private LinearLayout favRow(final MediaItem it, final List<MediaItem> favs) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(8));
        row.setBackground(Ui.cardBg());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(8));
        row.setLayoutParams(lp);

        ImageView thumb = new ImageView(act);
        thumb.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(50), Ui.dp(50)));
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackground(Ui.rounded(Theme.ELEVATED, 12));
        ImageLoader.load(act, it, thumb);
        row.addView(thumb);

        LinearLayout texts = new LinearLayout(act);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tlp.setMargins(Ui.dp(12), 0, Ui.dp(8), 0);
        texts.setLayoutParams(tlp);
        TextView title = Ui.text(act, it.title, 14.5f, Theme.TEXT, false);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);
        texts.addView(Ui.sub(act, it.subtitle()));
        row.addView(texts);

        final Runnable play = () -> {
            if (it.video) ItemMenu.playVideo(act, it);
            else {
                List<MediaItem> songs = new ArrayList<>();
                for (MediaItem f : favs) if (!f.video) songs.add(f);
                ItemMenu.playAudio(act, songs, Math.max(0, songs.indexOf(it)));
            }
        };
        LinearLayout playBtn = new LinearLayout(act);
        playBtn.setGravity(Gravity.CENTER);
        playBtn.setBackground(Ui.circleGradient());
        playBtn.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40)));
        playBtn.addView(Ui.icon(act, R.drawable.ic_play, 18, 0xFFFFFFFF));
        playBtn.setOnClickListener(v -> play.run());
        PressFx.apply(playBtn);
        row.addView(playBtn);

        row.setOnClickListener(v -> play.run());
        row.setOnLongClickListener(v -> { ItemMenu.show(act, it, this::rebuild); return true; });
        return row;
    }

    private void addRow(final MediaItem it) {
        content.addView(Rows.mediaRow(act, it,
                v -> { if (it.video) ItemMenu.playVideo(act, it); else ItemMenu.playAudio(act, singleList(it), 0); },
                v -> { ItemMenu.show(act, it, this::rebuild); return true; }));
    }

    private List<MediaItem> singleList(MediaItem it) {
        List<MediaItem> l = new ArrayList<>(); l.add(it); return l;
    }
}
