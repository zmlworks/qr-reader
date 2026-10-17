package com.zmlworks.qrreader;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings (SharedPreferences). */
final class Prefs {
    static final int THEME_SYSTEM = 0, THEME_LIGHT = 1, THEME_DARK = 2;
    private final SharedPreferences p;

    Prefs(Context c) { p = c.getSharedPreferences("settings", Context.MODE_PRIVATE); }

    boolean leftHanded() { return p.getBoolean("left_handed", false); }
    void setLeftHanded(boolean v) { p.edit().putBoolean("left_handed", v).apply(); }

    boolean vibrate() { return p.getBoolean("vibrate", true); }
    void setVibrate(boolean v) { p.edit().putBoolean("vibrate", v).apply(); }

    int theme() { return p.getInt("theme", THEME_SYSTEM); }
    void setTheme(int v) { p.edit().putInt("theme", v).apply(); }

    String engine() { return p.getString("search_engine", SearchUrl.GOOGLE); }
    void setEngine(String v) { p.edit().putString("search_engine", v).apply(); }

    String customTemplate() { return p.getString("search_custom", ""); }
    void setCustomTemplate(String v) { p.edit().putString("search_custom", v).apply(); }

    /** how many times the "swipe to zoom" hint was shown (shown on the first 3 launches) */
    int zoomHints() { return p.getInt("zoom_hints", 0); }
    void countZoomHint() { p.edit().putInt("zoom_hints", zoomHints() + 1).apply(); }

    boolean multi() { return p.getBoolean("multi", false); }
    void setMulti(boolean v) { p.edit().putBoolean("multi", v).apply(); }
}
