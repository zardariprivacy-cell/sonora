package com.nawazishali.sonora;

import android.app.Activity;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Real 5-band Equalizer + BassBoost bound to the music player's audio session,
 * with the device presets (Normal/Pop/Rock/… as provided by the platform) and
 * custom band sliders. Settings persist and are re-applied by MusicService.
 */
public class EqualizerActivity extends Activity {
    private MusicService svc;
    private boolean bound;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Theme.init(this);
        Ui.init(this);
        MusicService.ensureStarted(this);
        SleepTimerUi.withService(this, s -> { svc = s; bound = true; build(); });
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Theme.BG);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(24));
        scroll.addView(content);
        setContentView(scroll);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        TextView back = Ui.text(this, "‹", 26, Theme.TEXT, true);
        back.setPadding(0, 0, Ui.dp(12), 0);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        bar.addView(Ui.text(this, "Equalizer", 19, Theme.TEXT, true));
        content.addView(bar);

        final Equalizer eq = svc == null ? null : svc.getEqualizer();
        final BassBoost bb = svc == null ? null : svc.getBassBoost();
        if (eq == null) {
            content.addView(Ui.spacer(this, 14));
            content.addView(Ui.sub(this, "The equalizer is not available on this device."));
            return;
        }

        // Presets
        content.addView(Ui.section(this, "Preset"));
        final short nPresets = eq.getNumberOfPresets();
        String[] names = new String[nPresets];
        for (short i = 0; i < nPresets; i++) names[i] = eq.getPresetName(i);
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        spinner.setAdapter(ad);
        try { spinner.setSelection(eq.getCurrentPreset()); } catch (Throwable ignored) { }
        content.addView(spinner);
        final boolean[] fromPreset = {false};
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) {
                try {
                    eq.usePreset((short) pos);
                    Db.get(EqualizerActivity.this).setSetting("eq_preset", eq.getPresetName((short) pos));
                    Db.get(EqualizerActivity.this).setSetting("eq_bands", "");
                    fromPreset[0] = true;
                    build(); // refresh sliders to the preset levels
                } catch (Throwable ignored) { }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });

        // Bands
        content.addView(Ui.section(this, "Bands"));
        final short bands = eq.getNumberOfBands();
        final short[] range = eq.getBandLevelRange();
        for (short b = 0; b < bands; b++) {
            final short band = b;
            int freq = eq.getCenterFreq(b) / 1000;
            String label = freq >= 1000 ? (freq / 1000) + " kHz" : freq + " Hz";
            TextView t = Ui.text(this, label, 13.5f, Theme.TEXT2, false);
            t.setPadding(0, Ui.dp(10), 0, Ui.dp(2));
            content.addView(t);
            SeekBar sb = new SeekBar(this);
            sb.setMax(range[1] - range[0]);
            sb.setProgress(eq.getBandLevel(b) - range[0]);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int prog, boolean user) {
                    if (!user) return;
                    try {
                        eq.setBandLevel(band, (short) (prog + range[0]));
                        persistBands(eq);
                    } catch (Throwable ignored) { }
                }
                @Override public void onStartTrackingTouch(SeekBar s) { }
                @Override public void onStopTrackingTouch(SeekBar s) { }
            });
            content.addView(sb);
        }

        // Bass boost
        content.addView(Ui.section(this, "Bass boost"));
        if (bb != null) {
            SeekBar sb = new SeekBar(this);
            sb.setMax(1000);
            try { sb.setProgress(bb.getRoundedStrength()); } catch (Throwable ignored) { }
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int prog, boolean user) {
                    if (!user) return;
                    try {
                        bb.setStrength((short) prog);
                        Db.get(EqualizerActivity.this).setSetting("bass_boost", "" + prog);
                    } catch (Throwable ignored) { }
                }
                @Override public void onStartTrackingTouch(SeekBar s) { }
                @Override public void onStopTrackingTouch(SeekBar s) { }
            });
            content.addView(sb);
        } else {
            content.addView(Ui.sub(this, "Bass boost is not supported on this device."));
        }

        TextView note = Ui.sub(this, "Changes apply to the music player immediately and are remembered for the next launch.");
        note.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(note);
    }

    private void persistBands(Equalizer eq) {
        StringBuilder sb = new StringBuilder();
        for (short b = 0; b < eq.getNumberOfBands(); b++) {
            if (b > 0) sb.append(',');
            sb.append(eq.getBandLevel(b));
        }
        Db.get(this).setSetting("eq_bands", sb.toString());
        Db.get(this).setSetting("eq_preset", "");
    }

    @Override protected void onDestroy() {
        super.onDestroy();
    }
}
