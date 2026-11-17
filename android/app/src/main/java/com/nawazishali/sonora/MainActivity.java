package com.nawazishali.sonora;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * App shell: toolbar, 5 bottom tabs (Home / Videos / Music / Folders / Settings),
 * and the floating mini-player bound to MusicService.
 */
public class MainActivity extends Activity implements Library.Listener, MusicService.Listener {
    public static final int TAB_HOME = 0, TAB_VIDEOS = 1, TAB_MUSIC = 2, TAB_FOLDERS = 3, TAB_SETTINGS = 4;
    private static final int REQ_DELETE = 4471;

    private Tab[] tabs;
    private int currentTab = -1;
    private FrameLayout contentBox;
    private final LinearLayout[] navItems = new LinearLayout[5];

    private LinearLayout miniPlayer;
    private LinearLayout adBox;
    private ImageView miniThumb, miniPlay;
    private TextView miniTitle, miniSub;
    private MusicService svc;
    private boolean bound;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            svc = ((MusicService.LocalBinder) b).service();
            bound = true;
            svc.addListener(MainActivity.this);
            refreshMini();
        }
        @Override public void onServiceDisconnected(ComponentName n) { svc = null; bound = false; }
    };

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Theme.init(this);
        Ui.init(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG);

        // Toolbar
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(10), Ui.dp(10));
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView name = Ui.text(this, "SONORA", 19, Theme.TEXT, true);
        brand.addView(name);
        brand.addView(Ui.sub(this, "Your Complete Media Experience"));
        bar.addView(brand);
        LinearLayout searchBtn = new LinearLayout(this);
        searchBtn.setGravity(Gravity.CENTER);
        searchBtn.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
        searchBtn.addView(Ui.icon(this, R.drawable.ic_search, 23, Theme.TEXT));
        searchBtn.setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        bar.addView(searchBtn);
        root.addView(bar);

        // Content
        contentBox = new FrameLayout(this);
        contentBox.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(contentBox);

        // AdMob banner slot (v3.1): in the linear flow between the tab content
        // and the mini-player, so it sits directly above the mini-player when
        // that is visible and directly above the bottom navigation otherwise —
        // it can never overlap either. Stays GONE until an ad actually loads
        // (the AdMob account is still being verified, so for now it simply
        // never appears). Hidden on the Settings tab.
        adBox = new LinearLayout(this);
        adBox.setGravity(Gravity.CENTER);
        adBox.setVisibility(View.GONE);
        root.addView(adBox);

        // Mini player
        miniPlayer = buildMiniPlayer();
        miniPlayer.setVisibility(View.GONE);
        root.addView(miniPlayer);

        // Bottom navigation
        root.addView(buildNav());
        setContentView(root);

        tabs = new Tab[]{new HomeTab(this), new VideosTab(this), new MusicTab(this),
                new FoldersTab(this), new SettingsTab(this)};
        showTab(TAB_HOME);

        Ads.init(this);
        Ads.attachBanner(this, adBox);

        Library.addListener(this);
        if (!Library.scannedOnce) Library.scanAsync(this);
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 103);
        }
        MusicService.ensureStarted(this);
        bindService(new Intent(this, MusicService.class), conn, BIND_AUTO_CREATE);
    }

    private LinearLayout buildMiniPlayer() {
        LinearLayout mp = new LinearLayout(this);
        mp.setOrientation(LinearLayout.HORIZONTAL);
        mp.setGravity(Gravity.CENTER_VERTICAL);
        mp.setBackground(Ui.rounded(Theme.ELEVATED, 0));
        mp.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        miniThumb = new ImageView(this);
        miniThumb.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(44)));
        miniThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniThumb.setBackgroundColor(Theme.CARD);
        mp.addView(miniThumb);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tlp.setMargins(Ui.dp(10), 0, Ui.dp(6), 0);
        texts.setLayoutParams(tlp);
        miniTitle = Ui.text(this, "", 14, Theme.TEXT, false);
        miniTitle.setMaxLines(1);
        miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(miniTitle);
        miniSub = Ui.sub(this, "");
        texts.addView(miniSub);
        mp.addView(texts);
        miniPlay = new ImageView(this);
        miniPlay.setImageResource(R.drawable.ic_play);
        miniPlay.setColorFilter(Theme.TEXT);
        miniPlay.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
        miniPlay.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(46), Ui.dp(46)));
        miniPlay.setOnClickListener(v -> { if (svc != null) svc.toggle(); });
        PressFx.apply(miniPlay);
        mp.addView(miniPlay);
        ImageView next = new ImageView(this);
        next.setImageResource(R.drawable.ic_next);
        next.setColorFilter(Theme.TEXT);
        next.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
        next.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(46), Ui.dp(46)));
        next.setOnClickListener(v -> { if (svc != null) svc.next(true); });
        PressFx.apply(next);
        mp.addView(next);
        mp.setOnClickListener(v -> startActivity(new Intent(this, MusicPlayerActivity.class)));
        return mp;
    }

    private LinearLayout buildNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(Theme.CARD);
        nav.setPadding(0, Ui.dp(6), 0, Ui.dp(8));
        int[] icons = {R.drawable.ic_home, R.drawable.ic_video, R.drawable.ic_music,
                R.drawable.ic_folder, R.drawable.ic_settings};
        String[] labels = {"Home", "Videos", "Music", "Folders", "Settings"};
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            ImageView iv = new ImageView(this);
            iv.setImageResource(icons[i]);
            iv.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(24), Ui.dp(24)));
            iv.setTag("icon");
            item.addView(iv);
            TextView t = Ui.text(this, labels[i], 11, Theme.TEXT2, false);
            t.setGravity(Gravity.CENTER);
            t.setTag("label");
            item.addView(t);
            item.setOnClickListener(v -> showTab(idx));
            PressFx.apply(item);
            navItems[i] = item;
            nav.addView(item);
        }
        return nav;
    }

    public void showTab(int idx) {
        Ads.setBannerSuppressed(idx == TAB_SETTINGS);
        if (idx == currentTab) { tabs[idx].refresh(); return; }
        currentTab = idx;
        contentBox.removeAllViews();
        View v = tabs[idx].view();
        contentBox.addView(v);
        TranslateAnimation anim = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0.03f, Animation.RELATIVE_TO_SELF, 0,
                Animation.RELATIVE_TO_SELF, 0, Animation.RELATIVE_TO_SELF, 0);
        anim.setDuration(160);
        v.startAnimation(anim);
        for (int i = 0; i < 5; i++) {
            boolean active = i == idx;
            ImageView iv = (ImageView) navItems[i].findViewWithTag("icon");
            TextView t = (TextView) navItems[i].findViewWithTag("label");
            iv.setColorFilter(active ? Theme.ACCENT : Theme.TEXT2);
            t.setTextColor(active ? Theme.ACCENT : Theme.TEXT2);
        }
    }

    private void refreshMini() {
        if (svc == null) return;
        MediaItem it = svc.current();
        if (it == null) { miniPlayer.setVisibility(View.GONE); return; }
        miniPlayer.setVisibility(View.VISIBLE);
        miniTitle.setText(it.title);
        miniSub.setText(it.subtitle());
        ImageLoader.load(this, it, miniThumb);
        miniPlay.setImageResource(svc.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    // ---------------- listeners ----------------
    @Override public void onLibraryChanged() {
        for (Tab t : tabs) if (t != null) t.refresh();
    }

    @Override public void onTrackChanged(MediaItem item) { refreshMini(); }
    @Override public void onPlaybackChanged(boolean playing) { refreshMini(); }
    @Override public void onProgress(long pos, long dur) { }

    // ---------------- system callbacks ----------------
    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == 101) {
            Library.scanAsync(this);
            for (Tab t : tabs) if (t != null) t.refresh();
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_DELETE) {
            Library.scanAsync(this);
            return;
        }
        if (tabs != null && currentTab >= 0) tabs[currentTab].onActivityResult(req, res, data);
    }

    @Override protected void onDestroy() {
        Library.removeListener(this);
        if (bound && svc != null) svc.removeListener(this);
        if (bound) unbindService(conn);
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (currentTab != TAB_HOME) showTab(TAB_HOME);
        else super.onBackPressed();
    }
}
