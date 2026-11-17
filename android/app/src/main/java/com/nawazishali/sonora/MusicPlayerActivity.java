package com.nawazishali.sonora;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/** Full-screen music player: art, seek, queue, shuffle/repeat, visualizer, EQ, sleep timer, lyrics (.lrc). */
public class MusicPlayerActivity extends Activity implements MusicService.Listener {
    private MusicService svc;
    private boolean bound;
    private ImageView art, playBtn, shuffleBtn, repeatBtn;
    private LinearLayout favBtn;
    private TextView title, artist, posLabel, durLabel, lyricsView, emptyLabel;
    private SeekBar seek;
    private VisualizerView visualizer;
    private ScrollView lyricsScroll;
    private boolean seeking;
    // Source-of-truth duration (ms). Same v3.0 defect as the video player:
    // progress was computed on a 0..1000 scale but seek.setMax(1000) was never
    // called, so the bar filled up at 10% of the song. v3.1 uses real ms.
    private long durationMs = -1;
    private List<LrcLine> lrc;
    private int lastLrcIndex = -1;

    private static class LrcLine { long time; String text; }

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            svc = ((MusicService.LocalBinder) b).service();
            bound = true;
            svc.addListener(MusicPlayerActivity.this);
            refreshAll();
        }
        @Override public void onServiceDisconnected(ComponentName n) { svc = null; bound = false; }
    };

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Theme.init(this);
        Ui.init(this);
        buildUi();
        MusicService.ensureStarted(this);
        bindService(new Intent(this, MusicService.class), conn, BIND_AUTO_CREATE);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG);
        root.setPadding(Ui.dp(20), Ui.dp(14), Ui.dp(20), Ui.dp(18));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "‹", 28, Theme.TEXT, true);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView h = Ui.text(this, "Now Playing", 17, Theme.TEXT, true);
        h.setGravity(Gravity.CENTER);
        h.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(h);
        bar.addView(Ui.icon(this, R.drawable.ic_music, 22, Theme.SECONDARY));
        root.addView(bar);

        emptyLabel = Ui.sub(this, "Nothing is playing. Pick a song from the Music tab.");
        emptyLabel.setGravity(Gravity.CENTER);
        emptyLabel.setPadding(0, Ui.dp(30), 0, 0);
        emptyLabel.setVisibility(View.GONE);
        root.addView(emptyLabel);

        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackground(Ui.rounded(Theme.ELEVATED, 20));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(280));
        alp.setMargins(0, Ui.dp(16), 0, Ui.dp(8));
        art.setLayoutParams(alp);
        root.addView(art);

        visualizer = new VisualizerView(this);
        visualizer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(56)));
        root.addView(visualizer);

        title = Ui.text(this, "", 19, Theme.TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(title);
        artist = Ui.sub(this, "");
        artist.setGravity(Gravity.CENTER);
        root.addView(artist);

        // Seek
        LinearLayout seekRow = new LinearLayout(this);
        seekRow.setOrientation(LinearLayout.HORIZONTAL);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        posLabel = Ui.text(this, "0:00", 12, Theme.TEXT2, false);
        seek = new SeekBar(this);
        seek.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        durLabel = Ui.text(this, "0:00", 12, Theme.TEXT2, false);
        seekRow.addView(posLabel); seekRow.addView(seek); seekRow.addView(durLabel);
        root.addView(seekRow);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                // SeekBar progress is real milliseconds (max == durationMs).
                if (user) posLabel.setText(Ui.fmtDuration(p));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { seeking = true; }
            @Override public void onStopTrackingTouch(SeekBar s) {
                seeking = false;
                if (svc != null) {
                    long target = s.getProgress();
                    if (durationMs > 0) target = Math.max(0, Math.min(target, durationMs));
                    svc.seekTo(target);
                }
            }
        });

        // Transport controls
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        shuffleBtn = controlBtn(R.drawable.ic_shuffle, v -> { if (svc != null) { svc.setShuffle(!svc.isShuffle()); refreshModes(); } });
        ImageView prev = controlBtn(R.drawable.ic_prev, v -> { if (svc != null) svc.prev(); });
        playBtn = new ImageView(this);
        playBtn.setImageResource(R.drawable.ic_play);
        playBtn.setColorFilter(0xFFFFFFFF);
        playBtn.setBackground(Ui.gradientBg(32));
        playBtn.setPadding(Ui.dp(16), Ui.dp(16), Ui.dp(16), Ui.dp(16));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(Ui.dp(64), Ui.dp(64));
        plp.setMargins(Ui.dp(14), 0, Ui.dp(14), 0);
        playBtn.setLayoutParams(plp);
        playBtn.setOnClickListener(v -> { if (svc != null) svc.toggle(); });
        PressFx.apply(playBtn);
        ImageView next = controlBtn(R.drawable.ic_next, v -> { if (svc != null) svc.next(true); });
        repeatBtn = controlBtn(R.drawable.ic_repeat, v -> { if (svc != null) { svc.cycleRepeat(); refreshModes(); } });
        controls.addView(shuffleBtn); controls.addView(prev); controls.addView(playBtn);
        controls.addView(next); controls.addView(repeatBtn);
        root.addView(controls);

        // Secondary actions
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, Ui.dp(14), 0, 0);
        favBtn = actionBtn(R.drawable.ic_fav, "Favorite", v -> toggleFav());
        actions.addView(favBtn);
        actions.addView(actionBtn(R.drawable.ic_equalizer, "EQ", v -> startActivity(new Intent(this, EqualizerActivity.class))));
        actions.addView(actionBtn(R.drawable.ic_timer, "Sleep", v -> SleepTimerUi.show(this)));
        actions.addView(actionBtn(R.drawable.ic_list, "Queue", v -> showQueue()));
        actions.addView(actionBtn(R.drawable.ic_text, "Lyrics", v -> toggleLyrics()));
        root.addView(actions);

        // Lyrics panel (hidden until toggled)
        lyricsScroll = new ScrollView(this);
        lyricsScroll.setVisibility(View.GONE);
        lyricsScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        lyricsView = Ui.text(this, "", 14.5f, Theme.TEXT, false);
        lyricsView.setPadding(Ui.dp(6), Ui.dp(10), Ui.dp(6), Ui.dp(10));
        lyricsScroll.addView(lyricsView);
        root.addView(lyricsScroll);

        setContentView(root);
    }

    private ImageView controlBtn(int res, View.OnClickListener click) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(Theme.TEXT);
        iv.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
        iv.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48)));
        iv.setOnClickListener(click);
        PressFx.apply(iv);
        return iv;
    }

    private LinearLayout actionBtn(int res, String label, View.OnClickListener click) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        l.setLayoutParams(lp);
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(Theme.TEXT2);
        iv.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(22), Ui.dp(22)));
        iv.setTag("icon");
        l.addView(iv);
        TextView t = Ui.text(this, label, 11, Theme.TEXT2, false);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        l.setOnClickListener(click);
        PressFx.apply(l);
        return l;
    }

    private void refreshAll() {
        if (svc == null) return;
        MediaItem it = svc.current();
        if (it == null) {
            emptyLabel.setVisibility(View.VISIBLE);
            return;
        }
        emptyLabel.setVisibility(View.GONE);
        onTrackChanged(it);
        onPlaybackChanged(svc.isPlaying());
        refreshModes();
        visualizer.attach(svc.getAudioSessionId());
    }

    private void refreshModes() {
        if (svc == null) return;
        shuffleBtn.setColorFilter(svc.isShuffle() ? Theme.ACCENT : Theme.TEXT2);
        int r = svc.getRepeat();
        repeatBtn.setImageResource(r == MusicService.REPEAT_ONE ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        repeatBtn.setColorFilter(r == MusicService.REPEAT_OFF ? Theme.TEXT2 : Theme.ACCENT);
        MediaItem it = svc.current();
        if (it != null && favBtn != null) {
            ImageView icon = (ImageView) favBtn.findViewWithTag("icon");
            if (icon != null) icon.setColorFilter(Db.get(this).isFavorite(it.key()) ? Theme.ACCENT : Theme.TEXT2);
        }
    }

    private void toggleFav() {
        if (svc == null || svc.current() == null) return;
        MediaItem it = svc.current();
        Db db = Db.get(this);
        boolean now = !db.isFavorite(it.key());
        db.setFavorite(it, now);
        // Animated favorite toggle
        ImageView icon = (ImageView) favBtn.findViewWithTag("icon");
        if (icon != null) {
            icon.setColorFilter(now ? Theme.ACCENT : Theme.TEXT2);
            ScaleAnimation anim = new ScaleAnimation(1f, 1.35f, 1f, 1.35f,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            anim.setDuration(140);
            anim.setRepeatCount(1);
            anim.setRepeatMode(Animation.REVERSE);
            icon.startAnimation(anim);
        }
        Toast.makeText(this, now ? "Added to favorites" : "Removed from favorites", Toast.LENGTH_SHORT).show();
    }

    private void showQueue() {
        if (svc == null) return;
        final List<MediaItem> q = svc.getQueue();
        if (q.isEmpty()) { Toast.makeText(this, "Queue is empty", Toast.LENGTH_SHORT).show(); return; }
        String[] names = new String[q.size()];
        for (int i = 0; i < q.size(); i++) names[i] = (i == svc.getIndex() ? "▶ " : "") + q.get(i).title;
        new AlertDialog.Builder(this)
                .setTitle("Play queue")
                .setItems(names, (d, w) -> svc.playAt(w))
                .show();
    }

    private void toggleLyrics() {
        if (lyricsScroll.getVisibility() == View.VISIBLE) {
            lyricsScroll.setVisibility(View.GONE);
            return;
        }
        loadLyrics();
        lyricsScroll.setVisibility(View.VISIBLE);
    }

    private void loadLyrics() {
        lrc = null; lastLrcIndex = -1;
        MediaItem it = svc == null ? null : svc.current();
        if (it == null) { lyricsView.setText("No song playing."); return; }
        String text = null;
        if (it.path != null && !it.path.isEmpty()) {
            String lrcPath = it.path.replaceAll("\\.[^.]+$", ".lrc");
            File f = new File(lrcPath);
            if (f.exists()) {
                try {
                    byte[] data = readAll(f);
                    String raw = new String(data, "UTF-8");
                    lrc = parseLrc(raw);
                    if (lrc != null && !lrc.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (LrcLine ln : lrc) sb.append(ln.text).append('\n');
                        text = sb.toString();
                    } else text = raw;
                } catch (Exception ignored) { }
            }
        }
        lyricsView.setText(text == null
                ? "No lyrics found for this song.\n\nTip: place a .lrc file with the same name next to the song file and it will show here, synced to the music."
                : text);
    }

    private byte[] readAll(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        byte[] buf = new byte[(int) f.length()];
        int off = 0, n;
        while ((n = in.read(buf, off, buf.length - off)) > 0) off += n;
        in.close();
        return buf;
    }

    private List<LrcLine> parseLrc(String raw) {
        List<LrcLine> out = new ArrayList<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\[(\\d+):(\\d+)[.:](\\d+)\\](.*)");
        for (String line : raw.split("\n")) {
            java.util.regex.Matcher m = p.matcher(line.trim());
            if (m.find()) {
                LrcLine ln = new LrcLine();
                ln.time = (Long.parseLong(m.group(1)) * 60 + Long.parseLong(m.group(2))) * 1000
                        + Long.parseLong(m.group(3)) * 10;
                ln.text = m.group(4).trim();
                out.add(ln);
            }
        }
        return out;
    }

    // ---------------- service listener ----------------
    @Override public void onTrackChanged(MediaItem item) {
        if (item == null) return;
        durationMs = -1; // new track: re-adopt the player's duration in onProgress
        seek.setProgress(0);
        posLabel.setText("0:00");
        title.setText(item.title);
        artist.setText(item.subtitle());
        ImageLoader.load(this, item, art);
        lrc = null; lastLrcIndex = -1;
        if (lyricsScroll.getVisibility() == View.VISIBLE) loadLyrics();
        refreshModes();
        if (svc != null) visualizer.attach(svc.getAudioSessionId());
    }

    @Override public void onPlaybackChanged(boolean playing) {
        int target = playing ? R.drawable.ic_pause : R.drawable.ic_play;
        if (playBtn != null) {
            playBtn.setImageResource(target);
            ScaleAnimation anim = new ScaleAnimation(0.85f, 1f, 0.85f, 1f,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            anim.setDuration(160);
            playBtn.startAnimation(anim);
        }
    }

    /** Adopt a duration (ms): the seek bar max and total-time label follow it. */
    private void adoptDuration(long d) {
        if (d <= 0 || d == durationMs) return;
        durationMs = d;
        seek.setMax((int) Math.min(d, Integer.MAX_VALUE));
        durLabel.setText(Ui.fmtDuration(d));
    }

    @Override public void onProgress(long pos, long dur) {
        if (seeking) return;
        // The service passes the player's own position/duration — the player
        // wins. If it cannot report a duration, fall back to the MediaStore
        // value of the current track so the bar still has a sane scale.
        if (dur > 0) adoptDuration(dur);
        else if (durationMs <= 0 && svc != null && svc.current() != null) adoptDuration(svc.current().duration);
        if (durationMs > 0) {
            long p = Math.max(0, Math.min(pos, durationMs));
            seek.setProgress((int) p);
            posLabel.setText(Ui.fmtDuration(p));
            durLabel.setText(Ui.fmtDuration(durationMs));
        } else {
            posLabel.setText(Ui.fmtDuration(pos));
        }
        if (lrc != null && lyricsScroll.getVisibility() == View.VISIBLE) {
            int idx = -1;
            for (int i = 0; i < lrc.size(); i++) if (lrc.get(i).time <= pos) idx = i;
            if (idx >= 0 && idx != lastLrcIndex) {
                lastLrcIndex = idx;
                lyricsScroll.smoothScrollTo(0, Math.max(0, idx * Ui.dp(22) - Ui.dp(40)));
            }
        }
    }

    @Override protected void onDestroy() {
        if (bound && svc != null) svc.removeListener(this);
        if (bound) unbindService(conn);
        if (visualizer != null) visualizer.release();
        super.onDestroy();
    }
}
