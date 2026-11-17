package com.nawazishali.sonora;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/** Midnight Violet palette with a derived light variant. Values are the exact spec hexes. */
public class Theme {
    public static int BG, CARD, ELEVATED, PRIMARY, SECONDARY, ACCENT, TEXT, TEXT2, BORDER;
    public static boolean LIGHT = false;

    public static void applyDark() {
        LIGHT = false;
        BG = Color.rgb(0x10, 0x07, 0x19);
        CARD = Color.rgb(0x1C, 0x10, 0x30);
        ELEVATED = Color.rgb(0x29, 0x18, 0x3C);
        PRIMARY = Color.rgb(0x81, 0x3C, 0xFF);
        SECONDARY = Color.rgb(0xC4, 0x5B, 0xFF);
        ACCENT = Color.rgb(0xE8, 0x4B, 0xCB);
        TEXT = Color.WHITE;
        TEXT2 = Color.rgb(0xB8, 0xAE, 0xC5);
        BORDER = Color.rgb(0x38, 0x28, 0x49);
    }

    public static void applyLight() {
        LIGHT = true;
        BG = Color.rgb(0xF5, 0xF1, 0xFA);
        CARD = Color.WHITE;
        ELEVATED = Color.rgb(0xED, 0xE6, 0xF7);
        PRIMARY = Color.rgb(0x81, 0x3C, 0xFF);
        SECONDARY = Color.rgb(0xA0, 0x3B, 0xE8);
        ACCENT = Color.rgb(0xD6, 0x2B, 0xB8);
        TEXT = Color.rgb(0x1B, 0x10, 0x30);
        TEXT2 = Color.rgb(0x5E, 0x54, 0x70);
        BORDER = Color.rgb(0xDC, 0xD2, 0xEC);
    }

    /** Load persisted choice (default: dark). Call before building any UI. */
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
