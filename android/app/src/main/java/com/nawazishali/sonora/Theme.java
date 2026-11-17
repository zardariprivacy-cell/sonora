package com.nawazishali.sonora;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/**
 * Premium violet palette (v3.2, from the user's reference design) with a light
 * variant. Base hexes are the user's exact values: primary #9B6BFF,
 * background #100D1C, cards #211A35, accent #C084FC. Elevated surface, border
 * and secondary text are derived from the same family.
 */
public class Theme {
    public static int BG, CARD, ELEVATED, PRIMARY, SECONDARY, ACCENT, TEXT, TEXT2, BORDER;
    /** Deep-violet glow tint used at the top of the app background gradient (dark only). */
    public static int GLOW;
    public static boolean LIGHT = false;

    public static void applyDark() {
        LIGHT = false;
        BG = Color.rgb(0x10, 0x0D, 0x1C);
        CARD = Color.rgb(0x21, 0x1A, 0x35);
        ELEVATED = Color.rgb(0x2C, 0x22, 0x47);
        PRIMARY = Color.rgb(0x9B, 0x6B, 0xFF);
        SECONDARY = Color.rgb(0xC0, 0x84, 0xFC);
        ACCENT = Color.rgb(0xC0, 0x84, 0xFC);
        TEXT = Color.WHITE;
        TEXT2 = Color.rgb(0xB8, 0xAD, 0xD4);
        BORDER = Color.rgb(0x3A, 0x2C, 0x5E);
        GLOW = Color.rgb(0x2E, 0x16, 0x52);
    }

    public static void applyLight() {
        LIGHT = true;
        BG = Color.rgb(0xF5, 0xF1, 0xFA);
        CARD = Color.WHITE;
        ELEVATED = Color.rgb(0xEA, 0xE3, 0xF8);
        PRIMARY = Color.rgb(0x8A, 0x5C, 0xF6);
        SECONDARY = Color.rgb(0xA0, 0x3B, 0xE8);
        ACCENT = Color.rgb(0x9B, 0x6B, 0xFF);
        TEXT = Color.rgb(0x1B, 0x10, 0x30);
        TEXT2 = Color.rgb(0x5E, 0x54, 0x70);
        BORDER = Color.rgb(0xDC, 0xD2, 0xEC);
        GLOW = Color.rgb(0xE4, 0xDA, 0xF8);
    }

    /** Load persisted choice (default: dark — the v3.2 reference theme). Call before building any UI. */
    public static void init(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("sonora_theme", Context.MODE_PRIVATE);
        if (sp.getBoolean("light", false)) applyLight(); else applyDark();
    }

    public static void setLight(Context ctx, boolean light) {
        ctx.getSharedPreferences("sonora_theme", Context.MODE_PRIVATE)
                .edit().putBoolean("light", light).apply();
        if (light) applyLight(); else applyDark();
    }
}
