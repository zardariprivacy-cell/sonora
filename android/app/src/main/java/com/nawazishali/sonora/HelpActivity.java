package com.nawazishali.sonora;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** In-app help: real usage notes for every Sonora feature. Static text only. */
public class HelpActivity extends Activity {
    private LinearLayout content;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Theme.init(this);
        Ui.init(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Theme.BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(28));
        scroll.addView(content);
        setContentView(scroll);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        TextView back = Ui.text(this, "‹", 26, Theme.TEXT, true);
        back.setPadding(0, 0, Ui.dp(12), 0);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        bar.addView(Ui.text(this, "Help", 19, Theme.TEXT, true));
        content.addView(bar);

        section("Your media",
                "Sonora plays the songs and videos already stored on your phone — nothing is "
                        + "uploaded and no account is needed. If a tab looks empty, grant media "
                        + "access when asked, or use Refresh Library in Settings after adding "
                        + "new files.");
        section("Video player gestures",
                "• Tap the screen to show or hide the controls.\n"
                        + "• Double-tap the left side to go back 10 seconds, the right side to "
                        + "jump forward 10 seconds, and the centre to play or pause.\n"
                        + "• Swipe up or down on the left half to change brightness, on the "
                        + "right half to change volume.\n"
                        + "• Use the toolbar under the video for speed (0.25x–2x), aspect "
                        + "ratio, subtitles, audio track, repeat and rotation.\n"
                        + "• The floating-player button (or pressing Home while a video plays) "
                        + "switches to Picture-in-Picture.\n"
                        + "• Videos resume where you left them — see Continue Watching on Home.");
        section("Subtitles",
                "Open Subtitles in the video player to pick a subtitle track stored inside "
                        + "the video file, or choose “Load external .srt file…” to use a "
                        + "separate subtitle file from your storage.");
        section("Music, equalizer and lyrics",
                "Music keeps playing in the background with notification and lock-screen "
                        + "controls. The equalizer, bass boost and presets live in the music "
                        + "player (EQ button) and in Settings. Set a sleep timer to stop "
                        + "playback automatically. If a song has a .lrc lyrics file with the "
                        + "same name next to it, tap Lyrics in the music player to read it in "
                        + "sync with the song.");
        section("Private Vault",
                "The vault stores media encrypted with a key protected by your phone's "
                        + "keystore and locked with your PIN. Importing a file encrypts it "
                        + "into the vault and then removes the original from the normal "
                        + "library (your phone may ask you to approve the deletion). Export "
                        + "restores a file to the Movies or Music folder. If you forget the "
                        + "PIN there is no way back in — the encryption is real.");
        section("Files and storage",
                "Long-press any song or video for options: share, open with another app, "
                        + "rename, details, extract audio (videos only — creates an M4A copy "
                        + "in your Music library) and delete. Deleting always asks first. The "
                        + "Storage screen shows device space, library sizes, your largest "
                        + "files and recently added media.");
        section("Troubleshooting",
                "• A video that will not play usually uses a format this phone's built-in "
                        + "decoder does not support — that is a device limit, not a missing "
                        + "file.\n"
                        + "• New files not showing up? Use Refresh Library in Settings.\n"
                        + "• The spectrum visualizer needs microphone permission; it only "
                        + "analyses the music Sonora itself is playing and records nothing.");
    }

    private void section(String title, String bodyText) {
        content.addView(Ui.section(this, title));
        TextView b = Ui.body(this, bodyText);
        b.setLineSpacing(Ui.dp(3), 1.15f);
        content.addView(b);
    }
}
