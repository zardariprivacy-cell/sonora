package com.nawazishali.sonora;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen video player on framework MediaPlayer + TextureView.
 * Controls, double-tap seek, swipe brightness/volume, speed, aspect modes,
 * screen lock, embedded audio/subtitle track selection, external .srt via SAF
 * (own parser -> caption overlay), resume, repeat, prev/next, Picture-in-Picture.
 */
public class VideoPlayerActivity extends Activity implements TextureView.SurfaceTextureListener {
    public static List<MediaItem> queue = new ArrayList<>();
    public static int queueIndex = 0;

    private static final int REQ_SRT = 991;
    private static final int ASPECT_FIT = 0, ASPECT_FILL = 1, ASPECT_16_9 = 2, ASPECT_4_3 = 3, ASPECT_ORIGINAL = 4;

    private TextureView texture;
    private MediaPlayer player;
    private Surface surface;
    private LinearLayout topBar, bottomBar, centerControls;
    private ImageView unlockBtn;
    private TextView titleView, captionView, indicator, posLabel, durLabel, speedLabel;
    private ImageView playBtn, lockBtn;
    private SeekBar seek;
    private ProgressBar buffering;
    private FrameLayout root;

    private boolean prepared = false, controlsVisible = true, locked = false, seeking = false;
    private int aspectMode = ASPECT_FIT;
    private float speed = 1f;
    private boolean repeatOne = false;
    private int videoW = 0, videoH = 0;
    private List<SrtParser.Cue> srtCues;
    private Uri srtUri;
    private AudioManager audioManager;
    private GestureDetector gestures;
    private float gestureStartY, gestureStartValue;
    private int gestureMode = 0; // 0 none, 1 brightness, 2 volume
    private boolean gestureMoved;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideControls = () -> setControlsVisible(false);
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateProgress();
            handler.postDelayed(this, 300);
        }
    };

    private MediaItem current() {
        return queue != null && queueIndex >= 0 && queueIndex < queue.size() ? queue.get(queueIndex) : null;
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Theme.init(this);
        Ui.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        queueIndex = getIntent().getIntExtra("index", queueIndex);
        buildUi();
        setupGestures();
        handler.post(ticker);
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        texture = new TextureView(this);
        texture.setSurfaceTextureListener(this);
        root.addView(texture, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        captionView = Ui.text(this, "", 16, 0xFFFFFFFF, true);
        captionView.setGravity(Gravity.CENTER);
        captionView.setShadowLayer(4, 0, 2, 0xFF000000);
        captionView.setBackgroundColor(0x66000000);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        clp.setMargins(Ui.dp(24), 0, Ui.dp(24), Ui.dp(84));
        captionView.setLayoutParams(clp);
        captionView.setVisibility(View.GONE);
        root.addView(captionView);

        buffering = new ProgressBar(this);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.CENTER;
        buffering.setLayoutParams(blp);
        buffering.setVisibility(View.GONE);
        root.addView(buffering);

        indicator = Ui.text(this, "", 15, 0xFFFFFFFF, true);
        indicator.setBackground(Ui.rounded(0xAA1C1030, 12));
        indicator.setPadding(Ui.dp(14), Ui.dp(8), Ui.dp(14), Ui.dp(8));
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        ilp.gravity = Gravity.CENTER;
        indicator.setLayoutParams(ilp);
        indicator.setVisibility(View.GONE);
        root.addView(indicator);

        // Top bar
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(Ui.dp(8), Ui.dp(10), Ui.dp(8), Ui.dp(10));
        topBar.setBackgroundColor(0x88000000);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.TOP;
        topBar.setLayoutParams(tlp);
        TextView back = Ui.text(this, "‹", 28, 0xFFFFFFFF, true);
        back.setPadding(Ui.dp(8), 0, Ui.dp(10), 0);
        back.setOnClickListener(v -> finish());
        topBar.addView(back);
        titleView = Ui.text(this, "", 15.5f, 0xFFFFFFFF, false);
        titleView.setMaxLines(1);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        topBar.addView(titleView);
        topBar.addView(topBtn(R.drawable.ic_pip, v -> enterPip()));
        lockBtn = topBtn(R.drawable.ic_lock, v -> setLocked(true));
        topBar.addView(lockBtn);
        root.addView(topBar);

        // Standalone unlock affordance (the only visible control while locked)
        unlockBtn = new ImageView(this);
        unlockBtn.setImageResource(R.drawable.ic_lock);
        unlockBtn.setColorFilter(Theme.ACCENT);
        unlockBtn.setBackground(Ui.rounded(0xAA1C1030, 26));
        unlockBtn.setPadding(Ui.dp(13), Ui.dp(13), Ui.dp(13), Ui.dp(13));
        FrameLayout.LayoutParams ulp = new FrameLayout.LayoutParams(Ui.dp(52), Ui.dp(52));
        ulp.gravity = Gravity.TOP | Gravity.END;
        ulp.setMargins(0, Ui.dp(14), Ui.dp(14), 0);
        unlockBtn.setLayoutParams(ulp);
        unlockBtn.setVisibility(View.GONE);
        unlockBtn.setOnClickListener(v -> setLocked(false));
        root.addView(unlockBtn);

        // Center controls
        centerControls = new LinearLayout(this);
        centerControls.setOrientation(LinearLayout.HORIZONTAL);
        centerControls.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams cclp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        cclp.gravity = Gravity.CENTER;
        centerControls.setLayoutParams(cclp);
        centerControls.addView(centerBtn(R.drawable.ic_prev, v -> prevVideo()));
        TextView back10 = centerTextBtn("-10s", v -> seekBy(-10_000));
        centerControls.addView(back10);
        playBtn = new ImageView(this);
        playBtn.setImageResource(R.drawable.ic_pause);
        playBtn.setColorFilter(0xFFFFFFFF);
        playBtn.setBackground(Ui.gradientBg(34));
        playBtn.setPadding(Ui.dp(15), Ui.dp(15), Ui.dp(15), Ui.dp(15));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(Ui.dp(66), Ui.dp(66));
        plp.setMargins(Ui.dp(16), 0, Ui.dp(16), 0);
        playBtn.setLayoutParams(plp);
        playBtn.setOnClickListener(v -> togglePlay());
        centerControls.addView(playBtn);
        centerControls.addView(centerTextBtn("+10s", v -> seekBy(10_000)));
        centerControls.addView(centerBtn(R.drawable.ic_next, v -> nextVideo(true)));
        root.addView(centerControls);

        // Bottom bar
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setBackgroundColor(0x88000000);
        bottomBar.setPadding(Ui.dp(10), Ui.dp(6), Ui.dp(10), Ui.dp(10));
        FrameLayout.LayoutParams bbp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        bbp.gravity = Gravity.BOTTOM;
        bottomBar.setLayoutParams(bbp);

        LinearLayout seekRow = new LinearLayout(this);
        seekRow.setOrientation(LinearLayout.HORIZONTAL);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        posLabel = Ui.text(this, "0:00", 12, 0xFFFFFFFF, false);
        seek = new SeekBar(this);
        seek.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        durLabel = Ui.text(this, "0:00", 12, 0xFFFFFFFF, false);
        seekRow.addView(posLabel); seekRow.addView(seek); seekRow.addView(durLabel);
        bottomBar.addView(seekRow);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user && player != null && prepared) {
                    try { posLabel.setText(Ui.fmtDuration(player.getDuration() * p / 1000)); }
                    catch (Throwable ignored) { }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) { seeking = true; }
            @Override public void onStopTrackingTouch(SeekBar s) {
                seeking = false;
                if (player != null && prepared) {
                    try { player.seekTo((int) (player.getDuration() * (long) s.getProgress() / 1000)); }
                    catch (Throwable ignored) { }
                }
                scheduleHide();
            }
        });

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER);
        speedLabel = toolBtn("1.0x", v -> speedMenu());
        tools.addView(speedLabel);
        tools.addView(toolBtn("Aspect", v -> aspectMenu()));
        tools.addView(toolBtn("Subtitles", v -> subtitleMenu()));
        tools.addView(toolBtn("Audio", v -> audioTrackMenu()));
        tools.addView(toolBtn(repeatOne ? "Repeat: On" : "Repeat", v -> {
            repeatOne = !repeatOne;
            Toast.makeText(this, repeatOne ? "Repeat one: on" : "Repeat: off", Toast.LENGTH_SHORT).show();
            ((TextView) v).setText(repeatOne ? "Repeat: On" : "Repeat");
            scheduleHide();
        }));
        tools.addView(toolBtn("Rotate", v -> rotateScreen()));
        bottomBar.addView(tools);
        root.addView(bottomBar);

        setContentView(root);
        scheduleHide();
    }

    private ImageView topBtn(int res, View.OnClickListener click) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(0xFFFFFFFF);
        iv.setPadding(Ui.dp(9), Ui.dp(9), Ui.dp(9), Ui.dp(9));
        iv.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(42), Ui.dp(42)));
        iv.setOnClickListener(click);
        return iv;
    }

    private ImageView centerBtn(int res, View.OnClickListener click) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(0xFFFFFFFF);
        iv.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
        iv.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(52), Ui.dp(52)));
        iv.setOnClickListener(click);
        return iv;
    }

    private TextView centerTextBtn(String label, View.OnClickListener click) {
        TextView t = Ui.text(this, label, 13, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.rounded(0x661C1030, 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(52), Ui.dp(40));
        lp.setMargins(Ui.dp(8), 0, Ui.dp(8), 0);
        t.setLayoutParams(lp);
        t.setOnClickListener(click);
        return t;
    }

    private TextView toolBtn(String label, View.OnClickListener click) {
        TextView t = Ui.text(this, label, 13, 0xFFFFFFFF, false);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        t.setLayoutParams(lp);
        t.setPadding(0, Ui.dp(8), 0, Ui.dp(2));
        t.setOnClickListener(click);
        return t;
    }

    // ---------------- gestures ----------------
    private void setupGestures() {
        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (locked || texture.getWidth() == 0) return true;
                float third = texture.getWidth() / 3f;
                if (e.getX() < third) { seekBy(-10_000); showIndicator("−10 seconds"); }
                else if (e.getX() > third * 2) { seekBy(10_000); showIndicator("+10 seconds"); }
                else togglePlay();
                return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (locked) {
                    unlockBtn.setVisibility(unlockBtn.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                    return true;
                }
                setControlsVisible(!controlsVisible);
                return true;
            }
        });
        texture.setOnTouchListener((v, e) -> {
            gestures.onTouchEvent(e);
            handleSwipe(e);
            return true;
        });
    }

    private void handleSwipe(MotionEvent e) {
        if (locked) return;
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                gestureStartY = e.getY();
                gestureMoved = false;
                gestureMode = 0;
                break;
            case MotionEvent.ACTION_MOVE: {
                float dy = gestureStartY - e.getY();
                if (Math.abs(dy) < Ui.dp(24)) return;
                if (gestureMode == 0) {
                    gestureMode = e.getX() < texture.getWidth() / 2f ? 1 : 2;
                    if (gestureMode == 1) {
                        float b = getWindow().getAttributes().screenBrightness;
                        gestureStartValue = b < 0 ? 0.5f : b;
                    } else {
                        gestureStartValue = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    }
                    gestureMoved = true;
                }
                float frac = dy / (float) texture.getHeight();
                if (gestureMode == 1) {
                    float nb = Math.max(0.01f, Math.min(1f, gestureStartValue + frac));
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = nb;
                    getWindow().setAttributes(lp);
                    showIndicator("Brightness " + Math.round(nb * 100) + "%");
                } else {
                    int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int nv = Math.max(0, Math.min(max, Math.round(gestureStartValue + frac * max)));
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0);
                    showIndicator("Volume " + Math.round(nv * 100f / max) + "%");
                }
                break;
            }
            case MotionEvent.ACTION_UP:
                gestureMode = 0;
                break;
        }
    }

    private void showIndicator(String text) {
        indicator.setText(text);
        indicator.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideIndicator);
        handler.postDelayed(hideIndicator, 900);
    }
    private final Runnable hideIndicator = () -> indicator.setVisibility(View.GONE);

    // ---------------- player lifecycle ----------------
    @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        surface = new Surface(st);
        if (player == null) startCurrent();
        else { player.setSurface(surface); updateAspect(); }
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { updateAspect(); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        if (player != null) player.setSurface(null);
        if (surface != null) surface.release();
        surface = null;
        return true;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }

    private void startCurrent() {
        final MediaItem it = current();
        if (it == null) { finish(); return; }
        titleView.setText(it.title);
        releasePlayer();
        prepared = false;
        player = new MediaPlayer();
        try {
            player.setDataSource(this, it.uri);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot play this file", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (surface != null) player.setSurface(surface);
        player.setAudioAttributes(new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build());
        player.setOnPreparedListener(mp -> {
            prepared = true;
            videoW = mp.getVideoWidth(); videoH = mp.getVideoHeight();
            updateAspect();
            applySpeed();
            long resume = Db.get(this).getPosition(it.key());
            if (resume > 5000 && resume < mp.getDuration() - 10000) mp.seekTo((int) resume);
            mp.start();
            playBtn.setImageResource(R.drawable.ic_pause);
            buffering.setVisibility(View.GONE);
            updatePipParams();
        });
        player.setOnCompletionListener(mp -> {
            Db.get(this).clearPosition(it.key());
            if (repeatOne) { mp.seekTo(0); mp.start(); }
            else nextVideo(false);
        });
        player.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, "Playback error", Toast.LENGTH_SHORT).show();
            return true;
        });
        player.setOnBufferingUpdateListener((mp, pct) -> { /* seek bar secondary progress */ });
        player.setOnInfoListener((mp, what, extra) -> {
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) buffering.setVisibility(View.VISIBLE);
            else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) buffering.setVisibility(View.GONE);
            return false;
        });
        player.setOnTimedTextListener((mp, text) -> {
            if (text == null || text.getText() == null) captionView.setVisibility(View.GONE);
            else {
                captionView.setText(text.getText());
                captionView.setVisibility(View.VISIBLE);
            }
        });
        buffering.setVisibility(View.VISIBLE);
        player.prepareAsync();
        Db.get(this).recordPlay(it);
        srtCues = null; srtUri = null;
        captionView.setVisibility(View.GONE);
    }

    private void releasePlayer() {
        if (player != null) {
            savePosition();
            try { player.stop(); } catch (Throwable ignored) { }
            player.release();
            player = null;
        }
    }

    private void savePosition() {
        MediaItem it = current();
        if (it != null && player != null && prepared) {
            try { Db.get(this).savePosition(it.key(), player.getCurrentPosition(), player.getDuration()); }
            catch (Throwable ignored) { }
        }
    }

    private void togglePlay() {
        if (player == null || !prepared) return;
        if (player.isPlaying()) {
            player.pause();
            playBtn.setImageResource(R.drawable.ic_play);
            savePosition();
        } else {
            player.start();
            playBtn.setImageResource(R.drawable.ic_pause);
        }
        scheduleHide();
    }

    private void seekBy(long delta) {
        if (player == null || !prepared) return;
        try {
            long target = Math.max(0, Math.min(player.getDuration(), player.getCurrentPosition() + delta));
            player.seekTo((int) target);
        } catch (Throwable ignored) { }
    }

    public void nextVideo(boolean manual) {
        if (queue == null || queue.isEmpty()) { finish(); return; }
        if (queueIndex + 1 < queue.size()) { queueIndex++; startCurrent(); }
        else if (manual) { queueIndex = 0; startCurrent(); }
        else finish();
    }

    private void prevVideo() {
        if (queue == null || queue.isEmpty()) return;
        if (player != null && prepared && player.getCurrentPosition() > 3000) { player.seekTo(0); return; }
        if (queueIndex > 0) { queueIndex--; startCurrent(); }
    }

    private void updateProgress() {
        if (player == null || !prepared) return;
        try {
            if (!seeking) {
                long pos = player.getCurrentPosition(), dur = player.getDuration();
                if (dur > 0) seek.setProgress((int) (pos * 1000 / dur));
                posLabel.setText(Ui.fmtDuration(pos));
                durLabel.setText(Ui.fmtDuration(dur));
            }
            if (srtCues != null) {
                SrtParser.Cue cue = SrtParser.at(srtCues, player.getCurrentPosition());
                if (cue == null) captionView.setVisibility(View.GONE);
                else {
                    captionView.setText(cue.text);
                    captionView.setVisibility(View.VISIBLE);
                }
            }
        } catch (Throwable ignored) { }
    }

    // ---------------- controls visibility / lock ----------------
    private void setControlsVisible(boolean vis) {
        controlsVisible = vis;
        int v = vis ? View.VISIBLE : View.GONE;
        topBar.setVisibility(v);
        bottomBar.setVisibility(v);
        centerControls.setVisibility(v);
        if (vis) scheduleHide();
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideControls);
        if (!locked) handler.postDelayed(hideControls, 3500);
    }

    private void setLocked(boolean lock) {
        locked = lock;
        if (lock) {
            handler.removeCallbacks(hideControls);
            topBar.setVisibility(View.GONE);
            bottomBar.setVisibility(View.GONE);
            centerControls.setVisibility(View.GONE);
            unlockBtn.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Screen locked — tap the screen, then the lock icon, to unlock",
                    Toast.LENGTH_SHORT).show();
            handler.postDelayed(() -> { if (locked) unlockBtn.setVisibility(View.GONE); }, 3000);
        } else {
            unlockBtn.setVisibility(View.GONE);
            setControlsVisible(true);
        }
    }

    // ---------------- menus ----------------
    private void speedMenu() {
        final float[] speeds = {0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        String[] labels = {"0.25x", "0.5x", "0.75x", "1.0x (normal)", "1.25x", "1.5x", "2.0x"};
        new AlertDialog.Builder(this)
                .setTitle("Playback speed")
                .setItems(labels, (d, w) -> { speed = speeds[w]; applySpeed(); speedLabel.setText(labels[w].replace(" (normal)", "")); })
                .show();
    }

    private void applySpeed() {
        if (player == null || !prepared) return;
        try {
            android.media.PlaybackParams pp = player.getPlaybackParams();
            pp.setSpeed(speed);
            player.setPlaybackParams(pp);
        } catch (Throwable ignored) { }
    }

    private void aspectMenu() {
        String[] labels = {"Fit to screen", "Fill (crop)", "16:9", "4:3", "Original size"};
        new AlertDialog.Builder(this)
                .setTitle("Aspect ratio")
                .setItems(labels, (d, w) -> { aspectMode = w; updateAspect(); })
                .show();
    }

    private void updateAspect() {
        if (texture == null || videoW <= 0 || videoH <= 0) return;
        int W = texture.getWidth(), H = texture.getHeight();
        if (W == 0 || H == 0) return;
        double viewAR = (double) W / H;
        double targetAR;
        switch (aspectMode) {
            case ASPECT_16_9: targetAR = 16.0 / 9.0; break;
            case ASPECT_4_3: targetAR = 4.0 / 3.0; break;
            case ASPECT_ORIGINAL: {
                Matrix m = new Matrix();
                m.setScale((float) videoW / W, (float) videoH / H, W / 2f, H / 2f);
                texture.setTransform(m);
                return;
            }
            default: targetAR = (double) videoW / videoH;
        }
        double sx, sy;
        if (aspectMode == ASPECT_FILL) {
            if (targetAR > viewAR) { sx = targetAR / viewAR; sy = 1; }
            else { sx = 1; sy = viewAR / targetAR; }
        } else {
            if (targetAR > viewAR) { sx = 1; sy = viewAR / targetAR; }
            else { sx = targetAR / viewAR; sy = 1; }
        }
        Matrix m = new Matrix();
        m.setScale((float) sx, (float) sy, W / 2f, H / 2f);
        texture.setTransform(m);
    }

    private List<Integer> trackIndices(int type) {
        List<Integer> out = new ArrayList<>();
        if (player == null) return out;
        try {
            MediaPlayer.TrackInfo[] infos = player.getTrackInfo();
            for (int i = 0; i < infos.length; i++)
                if (infos[i].getTrackType() == type) out.add(i);
        } catch (Throwable ignored) { }
        return out;
    }

    private void audioTrackMenu() {
        final List<Integer> tracks = trackIndices(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO);
        if (tracks.isEmpty()) {
            Toast.makeText(this, "Only one audio track in this video", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[tracks.size()];
        for (int i = 0; i < tracks.size(); i++) {
            String lang = "";
            try { lang = player.getTrackInfo()[tracks.get(i)].getLanguage(); } catch (Throwable ignored) { }
            labels[i] = "Audio track " + (i + 1) + (lang == null || lang.isEmpty() || "und".equals(lang) ? "" : " (" + lang + ")");
        }
        new AlertDialog.Builder(this)
                .setTitle("Audio track")
                .setItems(labels, (d, w) -> {
                    try { player.selectTrack(tracks.get(w)); } catch (Throwable ignored) { }
                })
                .show();
    }

    private void subtitleMenu() {
        final List<Integer> tracks = trackIndices(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT);
        List<String> labels = new ArrayList<>();
        labels.add("Off");
        for (int i = 0; i < tracks.size(); i++) {
            String lang = "";
            try { lang = player.getTrackInfo()[tracks.get(i)].getLanguage(); } catch (Throwable ignored) { }
            labels.add("Embedded subtitle " + (i + 1) + (lang == null || lang.isEmpty() || "und".equals(lang) ? "" : " (" + lang + ")"));
        }
        labels.add("Load external .srt file…");
        new AlertDialog.Builder(this)
                .setTitle("Subtitles")
                .setItems(labels.toArray(new String[0]), (d, w) -> {
                    if (w == 0) {
                        srtCues = null;
                        for (int t : tracks) try { player.deselectTrack(t); } catch (Throwable ignored) { }
                        captionView.setVisibility(View.GONE);
                    } else if (w == labels.size() - 1) {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.setType("*/*");
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        try { startActivityForResult(i, REQ_SRT); }
                        catch (Exception e) { Toast.makeText(this, "File picker not available", Toast.LENGTH_SHORT).show(); }
                    } else {
                        srtCues = null;
                        try { player.selectTrack(tracks.get(w - 1)); } catch (Throwable ignored) { }
                    }
                })
                .show();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_SRT && res == RESULT_OK && data != null && data.getData() != null) {
            srtUri = data.getData();
            try { getContentResolver().takePersistableUriPermission(srtUri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (Exception ignored) { }
            final Uri uri = srtUri;
            new Thread(() -> {
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    final List<SrtParser.Cue> cues = SrtParser.parse(in);
                    runOnUiThread(() -> {
                        srtCues = cues;
                        Toast.makeText(this, "Subtitles loaded (" + cues.size() + " lines)", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "Could not read that subtitle file", Toast.LENGTH_LONG).show());
                }
            }).start();
        }
    }

    private void rotateScreen() {
        int o = getResources().getConfiguration().orientation;
        setRequestedOrientation(o == Configuration.ORIENTATION_LANDSCAPE
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        scheduleHide();
    }

    // ---------------- PiP ----------------
    private void enterPip() {
        if (Build.VERSION.SDK_INT >= 26) {
            try { enterPictureInPictureMode(buildPipParams()); }
            catch (Exception e) { Toast.makeText(this, "Picture-in-Picture not available", Toast.LENGTH_SHORT).show(); }
        } else {
            Toast.makeText(this, "Picture-in-Picture needs Android 8+", Toast.LENGTH_SHORT).show();
        }
    }

    private PictureInPictureParams buildPipParams() {
        PictureInPictureParams.Builder b = new PictureInPictureParams.Builder();
        if (videoW > 0 && videoH > 0) {
            int w = videoW, h = videoH;
            // Clamp to PiP-allowed aspect range
            double ar = (double) w / h;
            if (ar > 2.39) { w = 239; h = 100; } else if (ar < 0.418) { w = 100; h = 239; }
            else { w = videoW; h = videoH; }
            b.setAspectRatio(new Rational(w, h));
        }
        return b.build();
    }

    private void updatePipParams() {
        if (Build.VERSION.SDK_INT >= 26) {
            try { setPictureInPictureParams(buildPipParams()); } catch (Throwable ignored) { }
        }
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (player != null && prepared && player.isPlaying()) enterPip();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        queueIndex = intent.getIntExtra("index", queueIndex);
        if (surface != null) startCurrent();
    }

    @Override public void onPictureInPictureModeChanged(boolean inPip, Configuration cfg) {
        super.onPictureInPictureModeChanged(inPip, cfg);
        if (inPip) {
            topBar.setVisibility(View.GONE);
            bottomBar.setVisibility(View.GONE);
            centerControls.setVisibility(View.GONE);
            captionView.setVisibility(View.GONE);
        } else {
            setControlsVisible(true);
        }
    }

    // ---------------- system UI / lifecycle ----------------
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override protected void onPause() {
        super.onPause();
        savePosition();
        if (player != null && prepared && player.isPlaying()
                && !(Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode())) {
            player.pause();
            playBtn.setImageResource(R.drawable.ic_play);
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(hideControls);
        releasePlayer();
        super.onDestroy();
    }
}
