package com.nawazishali.sonora;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

/** Sleep-timer chooser shared by Settings and the music player. */
public class SleepTimerUi {
    public static void show(final Activity act) {
        MusicService.ensureStarted(act);
        withService(act, svc -> {
            long remaining = svc.sleepRemainingMs();
            String current = remaining > 0 ? (" (currently " + (remaining / 60000 + 1) + " min left)") : "";
            String[] opts = {"Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes", "Custom…"};
            new AlertDialog.Builder(act)
                    .setTitle("Sleep timer" + current)
                    .setItems(opts, (d, w) -> {
                        switch (w) {
                            case 0: svc.cancelSleepTimer(); toast(act, "Sleep timer off"); break;
                            case 1: set(act, svc, 15); break;
                            case 2: set(act, svc, 30); break;
                            case 3: set(act, svc, 45); break;
                            case 4: set(act, svc, 60); break;
                            case 5:
                                final EditText input = new EditText(act);
                                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                                input.setHint("Minutes");
                                new AlertDialog.Builder(act)
                                        .setTitle("Custom sleep timer")
                                        .setView(input)
                                        .setPositiveButton("Set", (dd, ww) -> {
                                            try {
                                                long m = Long.parseLong(input.getText().toString().trim());
                                                if (m > 0) set(act, svc, m);
                                            } catch (Exception ignored) { }
                                        })
                                        .setNegativeButton("Cancel", null)
                                        .show();
                                break;
                        }
                    })
                    .show();
        });
    }

    private static void set(Activity act, MusicService svc, long minutes) {
        svc.setSleepTimer(minutes);
        toast(act, "Music will stop in " + minutes + " min");
    }

    private static void toast(Activity act, String s) { Toast.makeText(act, s, Toast.LENGTH_SHORT).show(); }

    public interface SvcCallback { void run(MusicService svc); }

    public static void withService(final Context ctx, final SvcCallback cb) {
        if (MusicService.instance != null) { cb.run(MusicService.instance); return; }
        MusicService.ensureStarted(ctx);
        Intent i = new Intent(ctx, MusicService.class);
        ctx.getApplicationContext().bindService(i, new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName n, IBinder b) {
                cb.run(((MusicService.LocalBinder) b).service());
                ctx.getApplicationContext().unbindService(this);
            }
            @Override public void onServiceDisconnected(ComponentName n) { }
        }, Context.BIND_AUTO_CREATE);
    }
}
