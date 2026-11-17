package com.nawazishali.sonora;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Animated splash: gradient wordmark + animated equalizer bars, then Main. */
public class SplashActivity extends Activity {
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Theme.init(this);
        Ui.init(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Theme.BG);

        TextView logo = new TextView(this);
        logo.setText("SONORA");
        logo.setTextSize(42);
        logo.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        android.text.SpannableString span = new android.text.SpannableString("SONORA");
        span.setSpan(new android.text.style.ForegroundColorSpan(Theme.PRIMARY), 0, 3, 0);
        span.setSpan(new android.text.style.ForegroundColorSpan(Theme.ACCENT), 3, 6, 0);
        logo.setText(span);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo);

        AnimatedBarsView bars = new AnimatedBarsView(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(120), Ui.dp(44));
        blp.setMargins(0, Ui.dp(22), 0, Ui.dp(18));
        bars.setLayoutParams(blp);
        root.addView(bars);

        TextView tag = Ui.sub(this, "Your Complete Media Experience");
        tag.setGravity(Gravity.CENTER);
        root.addView(tag);

        setContentView(root);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, 1100);
    }
}
