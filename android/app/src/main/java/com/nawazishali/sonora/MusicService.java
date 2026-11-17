package com.nawazishali.sonora;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Music playback engine: foreground service + framework MediaSession.
 * Queue, shuffle, repeat (off/all/one), audio focus, headset/noisy handling,
 * 5-band Equalizer + BassBoost with presets, sleep timer, resume + history.
 */
public class MusicService extends Service {
    public static final int REPEAT_OFF = 0, REPEAT_ALL = 1, REPEAT_ONE = 2;
    private static final String CH = "sonora_playback";
    private static final int NOTIF_ID = 1001;
    public static final String A_PLAYPAUSE = "com.nawazishali.sonora.PLAYPAUSE";
    public static final String A_NEXT = "com.nawazishali.sonora.NEXT";
    public static final String A_PREV = "com.nawazishali.sonora.PREV";
    public static final String A_STOP = "com.nawazishali.sonora.STOP";

    public interface Listener {
        void onTrackChanged(MediaItem item);
        void onPlaybackChanged(boolean playing);
        void onProgress(long pos, long dur);
    }

    public static MusicService instance;
    private final List<Listener> listeners = new ArrayList<>();
    private final IBinder binder = new LocalBinder();
    public class LocalBinder extends Binder { public MusicService service() { return MusicService.this; } }

    private MediaPlayer player;
    private MediaSession session;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private boolean focusHeld = false, resumeOnGain = false, ducked = false;

    private final List<MediaItem> queue = new ArrayList<>();
    private int index = -1;
    private boolean shuffle = false;
    private int repeat = REPEAT_OFF;
    private Equalizer equalizer;
    private BassBoost bassBoost;
    private Bitmap artBitmap;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable sleepRunnable;
    private long sleepFireAt = 0;

    private final Runnable progressTick = new Runnable() {
        @Override public void run() {
            if (player != null && index >= 0) {
                try {
                    long pos = player.getCurrentPosition();
                    long dur = player.getDuration();
                    for (Listener l : new ArrayList<>(listeners)) l.onProgress(pos, dur);
                    if (player.isPlaying() && pos > 0 && pos % 5000 < 300)
                        Db.get(MusicService.this).savePosition(current().key(), pos, dur);
                } catch (Throwable ignored) { }
            }
            handler.postDelayed(this, 300);
        }
    };

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction())) pause();
        }
    };

    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                resumeOnGain = false; pause(); abandonFocus(); break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (isPlaying()) { resumeOnGain = true; pause(); }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (isPlaying()) { player.setVolume(0.25f, 0.25f); ducked = true; }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (ducked) { player.setVolume(1f, 1f); ducked = false; }
                if (resumeOnGain) { resumeOnGain = false; play(); }
                break;
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        player = new MediaPlayer();
        player.setWakeMode(getApplicationContext(), android.os.PowerManager.PARTIAL_WAKE_LOCK);
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        player.setOnCompletionListener(mp -> onCompletion());
        player.setOnErrorListener((mp, what, extra) -> { notifyPlayback(); return true; });
        setupSession();
        setupEqualizer();
        registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        createChannel();
        // startForegroundService() requires startForeground() promptly (Android 12+
        // crashes otherwise) — post a lightweight "ready" notification immediately;
        // updateNotification() replaces it once a track is loaded.
        try {
            Notification.Builder rb = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.ic_music)
                    .setContentTitle("Sonora")
                    .setContentText("Your Complete Media Experience");
            if (Build.VERSION.SDK_INT >= 26) rb.setChannelId(CH);
            startForeground(NOTIF_ID, rb.build());
        } catch (Throwable ignored) { }
        handler.post(progressTick);
        // restore modes
        Db db = Db.get(this);
        shuffle = "1".equals(db.getSetting("shuffle", "0"));
        try { repeat = Integer.parseInt(db.getSetting("repeat", "0")); } catch (Exception ignored) { }
    }

    private void setupSession() {
        session = new MediaSession(this, "Sonora");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { play(); }
            @Override public void onPause() { pause(); }
            @Override public void onSkipToNext() { next(true); }
            @Override public void onSkipToPrevious() { prev(); }
            @Override public void onSeekTo(long pos) { seekTo(pos); }
            @Override public void onStop() { stopPlayback(); }
        });
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    }

    private void setupEqualizer() {
        try {
            equalizer = new Equalizer(0, player.getAudioSessionId());
            equalizer.setEnabled(true);
            bassBoost = new BassBoost(0, player.getAudioSessionId());
            bassBoost.setEnabled(true);
            Db db = Db.get(this);
            String preset = db.getSetting("eq_preset", "");
            if (!preset.isEmpty()) {
                for (short i = 0; i < equalizer.getNumberOfPresets(); i++) {
                    if (equalizer.getPresetName(i).equals(preset)) { equalizer.usePreset(i); break; }
                }
            } else {
                String bands = db.getSetting("eq_bands", "");
                if (!bands.isEmpty()) {
                    String[] parts = bands.split(",");
                    for (int i = 0; i < parts.length && i < equalizer.getNumberOfBands(); i++) {
                        try { equalizer.setBandLevel((short) i, (short) Integer.parseInt(parts[i])); }
                        catch (Exception ignored) { }
                    }
                }
                String bb = db.getSetting("bass_boost", "");
                if (!bb.isEmpty()) try { bassBoost.setStrength((short) Integer.parseInt(bb)); } catch (Exception ignored) { }
            }
        } catch (Throwable t) { equalizer = null; bassBoost = null; }
    }

    public Equalizer getEqualizer() { return equalizer; }
    public BassBoost getBassBoost() { return bassBoost; }
    public int getAudioSessionId() { return player != null ? player.getAudioSessionId() : 0; }

    // ---------------- queue / playback ----------------
    public void setQueue(List<MediaItem> items, int start) {
        queue.clear();
        queue.addAll(items);
        index = -1;
        if (!items.isEmpty()) playAt(Math.max(0, Math.min(start, items.size() - 1)));
    }

    public List<MediaItem> getQueue() { return new ArrayList<>(queue); }
    public int getIndex() { return index; }
    public MediaItem current() { return index >= 0 && index < queue.size() ? queue.get(index) : null; }
    public boolean isPlaying() { try { return player != null && player.isPlaying(); } catch (Throwable t) { return false; } }
    public boolean isShuffle() { return shuffle; }
    public int getRepeat() { return repeat; }
    public long getPosition() { try { return player.getCurrentPosition(); } catch (Throwable t) { return 0; } }
    public long getDuration() { try { return player.getDuration(); } catch (Throwable t) { return 0; } }

    public void playAt(int i) {
        if (i < 0 || i >= queue.size()) return;
        index = i;
        final MediaItem it = queue.get(i);
        try {
            player.reset();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setDataSource(this, it.uri);
            player.setOnPreparedListener(mp -> {
                long resume = Db.get(MusicService.this).getPosition(it.key());
                if (resume > 5000 && resume < mp.getDuration() - 10000) mp.seekTo((int) resume);
                requestFocus();
                mp.start();
                notifyTrack(); notifyPlayback();
            });
            player.prepareAsync();
            Db.get(this).recordPlay(it);
            Db.get(this).setSetting("last_key", it.key());
            loadArtAsync(it);
        } catch (Exception e) {
            notifyPlayback();
        }
    }

    private void loadArtAsync(final MediaItem it) {
        artBitmap = null;
        new Thread(() -> {
            try {
                android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
                r.setDataSource(MusicService.this, it.uri);
                byte[] pic = r.getEmbeddedPicture();
                r.release();
                if (pic != null) artBitmap = android.graphics.BitmapFactory.decodeByteArray(pic, 0, pic.length);
            } catch (Throwable ignored) { }
            handler.post(() -> { updateSessionMetadata(); updateNotification(); });
        }).start();
    }

    public void play() {
        if (current() == null) return;
        if (requestFocus()) {
            try { player.start(); } catch (Throwable ignored) { }
            notifyPlayback();
        }
    }

    public void pause() {
        try { if (player.isPlaying()) player.pause(); } catch (Throwable ignored) { }
        MediaItem it = current();
        if (it != null) try { Db.get(this).savePosition(it.key(), player.getCurrentPosition(), player.getDuration()); }
        catch (Throwable ignored) { }
        notifyPlayback();
    }

    public void toggle() { if (isPlaying()) pause(); else if (current() != null) play(); }

    public void seekTo(long pos) {
        try { player.seekTo((int) Math.max(0, pos)); } catch (Throwable ignored) { }
    }

    public void next(boolean manual) {
        if (queue.isEmpty()) return;
        int n;
        if (shuffle && queue.size() > 1) {
            do { n = (int) (Math.random() * queue.size()); } while (n == index);
        } else n = index + 1;
        if (n >= queue.size()) {
            if (repeat == REPEAT_ALL || manual) n = 0; else { stopPlayback(); return; }
        }
        playAt(n);
    }

    public void prev() {
        if (queue.isEmpty()) return;
        if (getPosition() > 3000) { seekTo(0); return; }
        int n = index - 1;
        if (n < 0) n = repeat == REPEAT_ALL ? queue.size() - 1 : 0;
        playAt(n);
    }

    private void onCompletion() {
        MediaItem it = current();
        if (it != null) Db.get(this).clearPosition(it.key());
        if (repeat == REPEAT_ONE && it != null) { seekTo(0); play(); return; }
        next(false);
    }

    public void stopPlayback() {
        try { player.stop(); } catch (Throwable ignored) { }
        index = -1;
        queue.clear();
        stopForeground(true);
        notifyPlayback();
    }

    public void setShuffle(boolean s) {
        shuffle = s;
        Db.get(this).setSetting("shuffle", s ? "1" : "0");
    }

    public void cycleRepeat() {
        repeat = (repeat + 1) % 3;
        Db.get(this).setSetting("repeat", "" + repeat);
    }

    // ---------------- sleep timer ----------------
    public void setSleepTimer(long minutes) {
        cancelSleepTimer();
        if (minutes <= 0) return;
        sleepFireAt = System.currentTimeMillis() + minutes * 60_000L;
        sleepRunnable = () -> { pause(); sleepFireAt = 0; };
        handler.postDelayed(sleepRunnable, minutes * 60_000L);
    }
    public void cancelSleepTimer() {
        if (sleepRunnable != null) handler.removeCallbacks(sleepRunnable);
        sleepRunnable = null; sleepFireAt = 0;
    }
    public long sleepRemainingMs() { return sleepFireAt == 0 ? 0 : Math.max(0, sleepFireAt - System.currentTimeMillis()); }

    // ---------------- audio focus ----------------
    private boolean requestFocus() {
        if (focusHeld) return true;
        int result;
        if (Build.VERSION.SDK_INT >= 26) {
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setOnAudioFocusChangeListener(focusListener)
                        .setWillPauseWhenDucked(true)
                        .build();
            }
            result = audioManager.requestAudioFocus(focusRequest);
        } else {
            result = audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        focusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return focusHeld;
    }

    private void abandonFocus() {
        if (!focusHeld) return;
        if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
        else audioManager.abandonAudioFocus(focusListener);
        focusHeld = false;
    }

    // ---------------- session / notification ----------------
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(CH, "Sonora playback", NotificationManager.IMPORTANCE_LOW));
        }
    }

    private void updateSessionMetadata() {
        MediaItem it = current();
        if (it == null || session == null) return;
        android.media.MediaMetadata.Builder mb = new android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, it.title)
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, it.artist)
                .putString(android.media.MediaMetadata.METADATA_KEY_ALBUM, it.album)
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, getDuration());
        if (artBitmap != null) mb.putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, artBitmap);
        session.setMetadata(mb.build());
        session.setActive(true);
    }

    private void notifyTrack() {
        updateSessionMetadata();
        for (Listener l : new ArrayList<>(listeners)) l.onTrackChanged(current());
        updateNotification();
    }

    private void notifyPlayback() {
        MediaItem it = current();
        if (session != null && it != null) {
            long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                    | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_PLAY_PAUSE;
            session.setPlaybackState(new PlaybackState.Builder()
                    .setState(isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                            getPosition(), 1f)
                    .setActions(actions).build());
        }
        for (Listener l : new ArrayList<>(listeners)) l.onPlaybackChanged(isPlaying());
        updateNotification();
    }

    private void updateNotification() {
        MediaItem it = current();
        if (it == null) return;
        Intent open = new Intent(this, MusicPlayerActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder b = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_music)
                .setContentTitle(it.title)
                .setContentText(it.artist == null || it.artist.isEmpty() ? "Sonora" : it.artist)
                .setContentIntent(contentPi)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying());
        if (Build.VERSION.SDK_INT >= 26) b.setChannelId(CH);
        if (artBitmap != null) b.setLargeIcon(artBitmap);
        b.addAction(R.drawable.ic_prev, "Previous", actionPi(A_PREV, 1));
        b.addAction(isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play,
                isPlaying() ? "Pause" : "Play", actionPi(A_PLAYPAUSE, 2));
        b.addAction(R.drawable.ic_next, "Next", actionPi(A_NEXT, 3));
        b.setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));
        Notification n = b.build();
        if (isPlaying() || getPosition() > 0) {
            try { startForeground(NOTIF_ID, n); }
            catch (Throwable t) { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF_ID, n); }
        } else {
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF_ID, n);
        }
    }

    private PendingIntent actionPi(String action, int code) {
        Intent i = new Intent(this, MusicService.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getService(this, code, i, flags);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case A_PLAYPAUSE: toggle(); break;
                case A_NEXT: next(true); break;
                case A_PREV: prev(); break;
                case A_STOP: stopPlayback(); break;
            }
        }
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override public void onDestroy() {
        handler.removeCallbacks(progressTick);
        cancelSleepTimer();
        try { unregisterReceiver(noisy); } catch (Throwable ignored) { }
        abandonFocus();
        if (equalizer != null) equalizer.release();
        if (bassBoost != null) bassBoost.release();
        if (player != null) player.release();
        if (session != null) session.release();
        instance = null;
        super.onDestroy();
    }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    /** Convenience: start the service (from an Activity) if not running. */
    public static void ensureStarted(Context ctx) {
        Intent i = new Intent(ctx, MusicService.class);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i); else ctx.startService(i);
    }
}
