package com.nawazishali.sonora;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
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
    private static final int ASPECT_FIT = 0, ASPECT_FILL = 1, ASPECT_16_9 = 2, ASPECT_4_3 = 3, ASPECT_ORIGINAL = 4,
            ASPECT_STRETCH = 5;

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
    private boolean startInPip = false; // MediaSheet "Play in Picture-in-Picture"
    // Source-of-truth duration (ms) for the seek bar. v3.0 drove the SeekBar on a
    // 0..1000 scale but never called seek.setMax(1000) — the SeekBar default max
    // is 100, so the bar saturated at 10% of the video while playback continued
    // ("timeline finishes early"). v3.1 uses real milliseconds end to end:
    // max = duration, progress = position, both from the player (see below).
    private long durationMs = -1;
    private int aspectMode = ASPECT_FIT;
    private float speed = 1f;
    private boolean repeatOne = false;
    private int subSel = 0; // subtitle menu selection: 0 = Off, 1..n = embedded, last = external .srt
    // v3.5: the bottom options bar is a set of colourful 3D boxes, not text.
    private LinearLayout toolsBox;
    private View[] toolBoxes;
    private View repeatBox;
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
        startInPip = getIntent().getBooleanExtra("startInPip", false);
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
        playBtn.setElevation(Ui.dp(8)); // v3.5: real shadow under the gradient disc
        playBtn.setPadding(Ui.dp(15), Ui.dp(15), Ui.dp(15), Ui.dp(15));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(Ui.dp(66), Ui.dp(66));
        plp.setMargins(Ui.dp(16), 0, Ui.dp(16), 0);
        playBtn.setLayoutParams(plp);
        playBtn.setOnClickListener(v -> togglePlay());
        PressFx.apply(playBtn);
        centerControls.addView(playBtn);
        centerControls.addView(centerTextBtn("+10s", v -> seekBy(10_000)));
        centerControls.addView(centerBtn(R.drawable.ic_next, v -> nextVideo(true)));
        root.addView(centerControls);

        // Bottom bar
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setBackgroundColor(0x88000000);
        bottomBar.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(12));
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
                // SeekBar progress is real milliseconds (max == durationMs).
                if (user) posLabel.setText(Ui.fmtDuration(p));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { seeking = true; }
            @Override public void onStopTrackingTouch(SeekBar s) {
                seeking = false;
                if (player != null && prepared) {
                    long target = s.getProgress();
                    long dur = effectiveDuration();
                    if (dur > 0) target = Math.max(0, Math.min(target, dur));
                    try { player.seekTo((int) target); } catch (Throwable ignored) { }
                }
                scheduleHide();
            }
        });

        // v3.5: the bottom options BAR itself is now made of colourful 3D
        // boxes — one per option — replacing the bare white text labels the
        // user rejected ("Bottom waly option 3D box main nahi kiya"). Each
        // box carries its own tint from the gradient-badge family; tapping a
        // box runs exactly the v3.4 action (the centered 3D option menu).
        toolsBox = new LinearLayout(this);
        toolsBox.setOrientation(LinearLayout.VERTICAL);
        toolBoxes = new View[6];
        toolBoxes[0] = toolBox(R.drawable.ic_timer, "1.0x", 0, v -> speedMenu());
        toolBoxes[1] = toolBox(R.drawable.ic_crop, "Aspect", 1, v -> aspectMenu());
        toolBoxes[2] = toolBox(R.drawable.ic_subtitle, "Subtitles", 2, v -> subtitleMenu());
        toolBoxes[3] = toolBox(R.drawable.ic_volume, "Audio", 3, v -> audioTrackMenu());
        toolBoxes[4] = toolBox(R.drawable.ic_repeat, "Repeat", 4, v -> repeatMenu());
        toolBoxes[5] = toolBox(R.drawable.ic_refresh, "Rotate", 5, v -> rotateMenu());
        speedLabel = (TextView) toolBoxes[0].getTag();
        repeatBox = toolBoxes[4];
        layoutTools();
        styleRepeatBox();
        bottomBar.addView(toolsBox);
        root.addView(bottomBar);

        setContentView(root);
        scheduleHide();
    }

    private ImageView topBtn(int res, View.OnClickListener click) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(0xFFFFFFFF);
        // v3.5: small circular 3D chip, same language as the rest of the chrome.
        iv.setBackground(Ui.circle3d(new int[]{Theme.PRIMARY, Theme.ACCENT}));
        iv.setElevation(Ui.dp(4));
        iv.setPadding(Ui.dp(9), Ui.dp(9), Ui.dp(9), Ui.dp(9));
        LinearLayout.LayoutParams tlp2 = new LinearLayout.LayoutParams(Ui.dp(42), Ui.dp(42));
        tlp2.setMargins(Ui.dp(6), 0, 0, 0);
        iv.setLayoutParams(tlp2);
        iv.setOnClickListener(click);
        PressFx.apply(iv);
        return iv;
    }

    private ImageView centerBtn(int res, View.OnClickListener click) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(0xFFFFFFFF);
        // v3.5: circular 3D chip instead of a bare glyph.
        iv.setBackground(Ui.circle3d(new int[]{Theme.PRIMARY, Theme.ACCENT}));
        iv.setElevation(Ui.dp(4));
        iv.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
        iv.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(52), Ui.dp(52)));
        iv.setOnClickListener(click);
        PressFx.apply(iv);
        return iv;
    }

    private TextView centerTextBtn(String label, View.OnClickListener click) {
        TextView t = Ui.text(this, label, 12, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        // v3.5: circular 3D chip instead of the flat dark oval.
        t.setBackground(Ui.circle3d(new int[]{Theme.PRIMARY, Theme.ACCENT}));
        t.setElevation(Ui.dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48));
        lp.setMargins(Ui.dp(10), 0, Ui.dp(10), 0);
        t.setLayoutParams(lp);
        t.setOnClickListener(click);
        PressFx.apply(t);
        return t;
    }

    /**
     * One colourful 3D box of the bottom options bar (v3.5): layered 3D
     * background tinted by the gradient-badge family, a white glyph on top
     * and a small label under it. The label TextView is kept as the box's tag
     * so Speed ("1.0x") and Repeat can update their text/state in place.
     */
    private LinearLayout toolBox(int iconRes, String label, int gradIndex, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(4), Ui.dp(7), Ui.dp(4), Ui.dp(6));
        box.setBackground(Ui.card3d(14, Ui.gradientFor(gradIndex)));
        box.setElevation(Ui.dp(4));
        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        iv.setColorFilter(0xFFFFFFFF);
        iv.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(20), Ui.dp(20)));
        box.addView(iv);
        TextView t = Ui.text(this, label, 10.5f, 0xFFFFFFFF, false);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        t.setPadding(0, Ui.dp(3), 0, 0);
        box.addView(t);
        box.setTag(t);
        box.setOnClickListener(click);
        PressFx.apply(box);
        return box;
    }

    /**
     * Lay the six option boxes out: PORTRAIT = two rows of three (each box
     * wide enough for "Subtitles"), LANDSCAPE = one row of six. The activity
     * handles rotation itself (configChanges), so this runs at build time and
     * again from onConfigurationChanged.
     */
    private void layoutTools() {
        if (toolsBox == null || toolBoxes == null) return;
        for (View b : toolBoxes) {
            if (b != null && b.getParent() != null)
                ((android.view.ViewGroup) b.getParent()).removeView(b);
        }
        toolsBox.removeAllViews();
        boolean portrait = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
        int perRow = portrait ? 3 : 6;
        for (int start = 0; start < toolBoxes.length; start += perRow) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (start > 0) rlp.setMargins(0, Ui.dp(6), 0, 0);
            row.setLayoutParams(rlp);
            for (int i = start; i < Math.min(start + perRow, toolBoxes.length); i++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                lp.setMargins(Ui.dp(3), 0, Ui.dp(3), 0);
                toolBoxes[i].setLayoutParams(lp);
                row.addView(toolBoxes[i]);
            }
            toolsBox.addView(row);
        }
    }

    /**
     * Repeat state on its box. v3.6: OFF now uses the saturated tint box
     * (violet→pink) like the other five boxes, so ON needs more than a
     * fill difference — ON = the brighter Primary→Accent gradient fill
     * PLUS a bold "Repeat ✓" label (the box's tag TextView).
     */
    private void styleRepeatBox() {
        if (repeatBox == null) return;
        repeatBox.setBackground(repeatOne
                ? Ui.gradientBg(14)
                : Ui.card3d(14, Ui.gradientFor(4)));
        Object tag = repeatBox.getTag();
        if (tag instanceof TextView) {
            TextView t = (TextView) tag;
            t.setText(repeatOne ? "Repeat ✓" : "Repeat");
            t.setTypeface(repeatOne ? android.graphics.Typeface.DEFAULT_BOLD
                    : android.graphics.Typeface.DEFAULT);
        }
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        layoutTools();
        // v3.6: re-fit the video to the NEW screen dimensions. The direct
        // call covers the common case; the posted call runs after the
        // rotation relayout settles, when root.getWidth()/getHeight()
        // definitely report the new orientation's size.
        updateAspect();
        if (texture != null) texture.post(this::updateAspect);
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
        // v3.6: the listener lives on the ROOT, not the TextureView. Since
        // v3.6 the TextureView is sized to the video's fitted rect, so taps
        // and swipes landing on the black letterbox bars would never reach
        // a listener on the view itself. Events are translated into
        // texture space before use, so the double-tap thirds and the
        // brightness (left) / volume (right) halves track the actual video
        // area exactly as they did when the view was full-window. Buttons
        // and the seek bar still consume their own touches first.
        root.setOnTouchListener((v, e) -> {
            MotionEvent te = MotionEvent.obtain(e);
            te.offsetLocation(-texture.getLeft(), -texture.getTop());
            gestures.onTouchEvent(te);
            handleSwipe(te);
            te.recycle();
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
        // Reset the timeline for the new video; provisionally trust the
        // MediaStore duration (ms) until the player reports its own.
        durationMs = -1;
        seek.setProgress(0);
        posLabel.setText("0:00");
        if (it.duration > 0) adoptDuration(it.duration);
        else { seek.setMax(100); durLabel.setText("0:00"); }
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
        // v3.6: some streams only report their real size after prepare (or
        // change it mid-stream) — keep the layout fitted when that happens.
        player.setOnVideoSizeChangedListener((mp, w, h) -> {
            if (w > 0 && h > 0 && (w != videoW || h != videoH)) {
                videoW = w; videoH = h;
                updateAspect();
                updatePipParams();
            }
        });
        player.setAudioAttributes(new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build());
        player.setOnPreparedListener(mp -> {
            prepared = true;
            videoW = mp.getVideoWidth(); videoH = mp.getVideoHeight();
            updateAspect();
            applySpeed();
            adoptDuration(resolveDuration(mp, it));
            long resume = Db.get(this).getPosition(it.key());
            long dur = effectiveDuration();
            if (resume > 5000 && (dur <= 0 || resume < dur - 10000)) mp.seekTo((int) resume);
            mp.start();
            playBtn.setImageResource(R.drawable.ic_pause);
            buffering.setVisibility(View.GONE);
            updatePipParams();
            if (startInPip) {
                startInPip = false;
                handler.postDelayed(() -> enterPip(), 200);
            }
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
        srtCues = null; srtUri = null; subSel = 0;
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
            try { Db.get(this).savePosition(it.key(), player.getCurrentPosition(), effectiveDuration()); }
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
            long dur = effectiveDuration();
            long target = player.getCurrentPosition() + delta;
            if (target < 0) target = 0;
            if (dur > 0 && target > dur) target = dur;
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

    // ---------- duration source of truth ----------
    /** Player duration wins; then MediaMetadataRetriever; then the MediaStore value. */
    private long resolveDuration(MediaPlayer mp, MediaItem it) {
        long d = -1;
        try { d = mp.getDuration(); } catch (Throwable ignored) { }
        if (d <= 0) {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(this, it.uri);
                String s = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (s != null) d = Long.parseLong(s);
            } catch (Throwable ignored) { }
            try { r.release(); } catch (Throwable ignored) { }
        }
        if (d <= 0 && it != null) d = it.duration;
        return d;
    }

    /** Adopt a duration (ms): seek bar max and the total-time label follow it. */
    private void adoptDuration(long d) {
        if (d <= 0 || d == durationMs) return;
        durationMs = d;
        seek.setMax((int) Math.min(d, Integer.MAX_VALUE));
        durLabel.setText(Ui.fmtDuration(d));
    }

    private long effectiveDuration() {
        if (durationMs > 0) return durationMs;
        if (player != null) {
            try { long d = player.getDuration(); if (d > 0) return d; } catch (Throwable ignored) { }
        }
        return 0;
    }

    private void updateProgress() {
        if (player == null || !prepared) return;
        try {
            // If the player's own duration disagrees with what we show, the
            // player wins — update the seek bar max dynamically.
            long pd = player.getDuration();
            if (pd > 0 && pd != durationMs) adoptDuration(pd);
            if (!seeking) {
                long pos = player.getCurrentPosition();
                if (durationMs > 0) {
                    pos = Math.max(0, Math.min(pos, durationMs));
                    seek.setProgress((int) pos);
                    durLabel.setText(Ui.fmtDuration(durationMs));
                }
                posLabel.setText(Ui.fmtDuration(pos));
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

    // ---------------- menus (v3.4: all open as the centered 3D popup) ----
    private void speedMenu() {
        final float[] speeds = {0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        String[] labels = {"0.25x", "0.5x", "0.75x", "1.0x (normal)", "1.25x", "1.5x", "2.0x"};
        List<MediaSheet.Option> opts = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            final int w = i;
            opts.add(new MediaSheet.Option(R.drawable.ic_timer, labels[i], speed == speeds[i],
                    () -> { speed = speeds[w]; applySpeed(); speedLabel.setText(labels[w].replace(" (normal)", "")); }));
        }
        MediaSheet.showOptions(this, "Playback speed", R.drawable.ic_timer, opts);
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
        // v3.6: "Stretch" added (the brief's five modes + the existing
        // "Original size"). Labels and mode constants are parallel arrays —
        // the menu order is NOT the constant order.
        String[] labels = {"Fit to screen", "Fill (crop)", "Stretch", "16:9", "4:3", "Original size"};
        final int[] modes = {ASPECT_FIT, ASPECT_FILL, ASPECT_STRETCH, ASPECT_16_9, ASPECT_4_3, ASPECT_ORIGINAL};
        List<MediaSheet.Option> opts = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            final int mode = modes[i];
            opts.add(new MediaSheet.Option(R.drawable.ic_crop, labels[i], aspectMode == mode,
                    () -> { aspectMode = mode; updateAspect(); }));
        }
        MediaSheet.showOptions(this, "Aspect ratio", R.drawable.ic_crop, opts);
    }

    /** Largest rect of aspect {@code ar} (w/h) that fits inside availW×availH. */
    private static int[] fitRect(int availW, int availH, double ar) {
        int w = availW, h = (int) Math.round(availW / ar);
        if (h > availH) { h = availH; w = (int) Math.round(availH * ar); }
        return new int[]{Math.max(1, w), Math.max(1, h)};
    }

    private boolean aspectPostPending = false;

    /**
     * v3.6 sizing rewrite — the TextureView itself is SIZED to the display
     * rect (centred in the root); it is no longer a permanent full-window
     * view with the picture squeezed by a centre-pivot matrix.
     *
     * Why the old way failed (v3.0–v3.5): the view always filled the whole
     * window and updateAspect() only set a Matrix computed from
     * getVideoWidth()/getVideoHeight() captured once at onPrepared. Those
     * are the stream's CODED dimensions — for files whose display ratio
     * differs (rotation / sample-aspect metadata, common in phone and
     * WhatsApp videos) the matrix shrank the picture into a small centred
     * band, the size was never refreshed (no onVideoSizeChanged), and
     * rotation only re-ran the same stale numbers through the surface-size
     * callback. Now the layout rect is recomputed from the player's
     * reported size at every relevant event (prepared, video-size-changed,
     * surface-size-changed, configuration-changed) against the CURRENT
     * root (screen) dimensions:
     *
     *   FIT (default): rect = largest rect of the video's own ratio inside
     *     the screen — natural format, as large as possible, letterbox /
     *     pillarbox on the black root, zero distortion, zero crop.
     *   16:9 / 4:3: same fit math with the forced ratio.
     *   ORIGINAL: rect = the video's pixel size, centred (overflow, if
     *     any, is cropped symmetrically by the root).
     *   FILL: view = whole screen; a cover matrix scales the picture to
     *     cover it, ratio preserved, overflow cropped.
     *   STRETCH: view = whole screen, identity matrix — the picture fills
     *     the screen exactly, ratio ignored.
     * FIT / 16:9 / 4:3 / ORIGINAL use the identity matrix (the view's size
     * does the work), so switching away from Fill always resets the
     * transform. Re-applying the same rect is a no-op (guarded), which is
     * what keeps the surface-size callback from looping.
     */
    private void updateAspect() {
        if (texture == null || root == null || videoW <= 0 || videoH <= 0) return;
        int availW = root.getWidth(), availH = root.getHeight();
        if (availW <= 0 || availH <= 0) {
            // Not laid out yet (e.g. onPrepared beat the first layout pass):
            // retry once after layout instead of dropping the sizing.
            if (!aspectPostPending) {
                aspectPostPending = true;
                texture.post(() -> { aspectPostPending = false; updateAspect(); });
            }
            return;
        }
        double videoAR = (double) videoW / videoH;
        int dispW, dispH;
        switch (aspectMode) {
            case ASPECT_FILL:
            case ASPECT_STRETCH:
                dispW = availW; dispH = availH;
                break;
            case ASPECT_16_9: {
                int[] r = fitRect(availW, availH, 16.0 / 9.0);
                dispW = r[0]; dispH = r[1];
                break;
            }
            case ASPECT_4_3: {
                int[] r = fitRect(availW, availH, 4.0 / 3.0);
                dispW = r[0]; dispH = r[1];
                break;
            }
            case ASPECT_ORIGINAL:
                dispW = videoW; dispH = videoH;
                break;
            default: { // ASPECT_FIT — the video's own natural ratio
                int[] r = fitRect(availW, availH, videoAR);
                dispW = r[0]; dispH = r[1];
                break;
            }
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) texture.getLayoutParams();
        if (lp.width != dispW || lp.height != dispH || lp.gravity != Gravity.CENTER) {
            lp.width = dispW;
            lp.height = dispH;
            lp.gravity = Gravity.CENTER;
            texture.setLayoutParams(lp);
        }
        Matrix m = new Matrix();
        if (aspectMode == ASPECT_FILL) {
            double viewAR = (double) availW / availH;
            if (videoAR > viewAR) m.setScale((float) (videoAR / viewAR), 1f, availW / 2f, availH / 2f);
            else m.setScale(1f, (float) (viewAR / videoAR), availW / 2f, availH / 2f);
        }
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
        int selectedTrack = -1;
        try { selectedTrack = player.getSelectedTrack(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO); }
        catch (Throwable ignored) { }
        List<MediaSheet.Option> opts = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            final int w = i;
            String lang = "";
            try { lang = player.getTrackInfo()[tracks.get(i)].getLanguage(); } catch (Throwable ignored) { }
            String label = "Audio track " + (i + 1) + (lang == null || lang.isEmpty() || "und".equals(lang) ? "" : " (" + lang + ")");
            opts.add(new MediaSheet.Option(R.drawable.ic_volume, label, tracks.get(i) == selectedTrack,
                    () -> { try { player.selectTrack(tracks.get(w)); } catch (Throwable ignored) { } }));
        }
        MediaSheet.showOptions(this, "Audio track", R.drawable.ic_volume, opts);
    }

    private void subtitleMenu() {
        final List<Integer> tracks = trackIndices(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT);
        final int total = tracks.size() + 2; // Off + embedded + external loader
        List<MediaSheet.Option> opts = new ArrayList<>();
        opts.add(new MediaSheet.Option(R.drawable.ic_close, "Off", subSel == 0, () -> {
            subSel = 0;
            srtCues = null;
            for (int t : tracks) try { player.deselectTrack(t); } catch (Throwable ignored) { }
            captionView.setVisibility(View.GONE);
        }));
        for (int i = 0; i < tracks.size(); i++) {
            final int w = i + 1;
            String lang = "";
            try { lang = player.getTrackInfo()[tracks.get(i)].getLanguage(); } catch (Throwable ignored) { }
            String label = "Embedded subtitle " + (i + 1) + (lang == null || lang.isEmpty() || "und".equals(lang) ? "" : " (" + lang + ")");
            opts.add(new MediaSheet.Option(R.drawable.ic_subtitle, label, subSel == w, () -> {
                subSel = w;
                srtCues = null;
                try { player.selectTrack(tracks.get(w - 1)); } catch (Throwable ignored) { }
            }));
        }
        opts.add(new MediaSheet.Option(R.drawable.ic_upload, "Load external .srt file…", subSel == total - 1, () -> {
            subSel = total - 1;
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            try { startActivityForResult(i, REQ_SRT); }
            catch (Exception e) { Toast.makeText(this, "File picker not available", Toast.LENGTH_SHORT).show(); }
        }));
        MediaSheet.showOptions(this, "Subtitles", R.drawable.ic_subtitle, opts);
    }

    private void repeatMenu() {
        List<MediaSheet.Option> opts = new ArrayList<>();
        opts.add(new MediaSheet.Option(R.drawable.ic_close, "Off", !repeatOne, () -> setRepeat(false)));
        opts.add(new MediaSheet.Option(R.drawable.ic_repeat_one, "Repeat one", repeatOne, () -> setRepeat(true)));
        MediaSheet.showOptions(this, "Repeat", R.drawable.ic_repeat, opts);
    }

    /** Same end state + feedback as the old one-tap Repeat toggle; the state
     *  now shows on the Repeat box itself (gradient fill when ON, v3.5). */
    private void setRepeat(boolean on) {
        repeatOne = on;
        Toast.makeText(this, on ? "Repeat one: on" : "Repeat: off", Toast.LENGTH_SHORT).show();
        styleRepeatBox();
        scheduleHide();
    }

    private void rotateMenu() {
        int o = getResources().getConfiguration().orientation;
        List<MediaSheet.Option> opts = new ArrayList<>();
        opts.add(new MediaSheet.Option(R.drawable.ic_refresh, "Portrait",
                o == Configuration.ORIENTATION_PORTRAIT,
                () -> { setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); scheduleHide(); }));
        opts.add(new MediaSheet.Option(R.drawable.ic_refresh, "Landscape",
                o == Configuration.ORIENTATION_LANDSCAPE,
                () -> { setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE); scheduleHide(); }));
        MediaSheet.showOptions(this, "Rotate screen", R.drawable.ic_refresh, opts);
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
        startInPip = intent.getBooleanExtra("startInPip", false);
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
