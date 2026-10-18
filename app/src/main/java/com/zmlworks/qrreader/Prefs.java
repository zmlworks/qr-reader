package com.zmlworks.qrreader;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** Settings (SharedPreferences). */
final class Prefs {
    private final SharedPreferences p;

    Prefs(Context c) { p = c.getSharedPreferences("settings", Context.MODE_PRIVATE); }

    boolean leftHanded() { return p.getBoolean("left_handed", false); }
    void setLeftHanded(boolean v) { p.edit().putBoolean("left_handed", v).apply(); }

    boolean vibrate() { return p.getBoolean("vibrate", true); }
    void setVibrate(boolean v) { p.edit().putBoolean("vibrate", v).apply(); }

    /**
     * Dark mode on/off. Not set yet: follows the system's current state once and is then remembered
     * (a 0.1.0 Light/Dark choice is carried over).
     */
    boolean dark(Configuration system) {
        if (!p.contains("dark")) {
            int old = p.getInt("theme", 0);   // 0.1.0: 0 system, 1 light, 2 dark
            boolean d = old == 2 || (old != 1 && (system.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
            p.edit().putBoolean("dark", d).remove("theme").apply();
            return d;
        }
        return p.getBoolean("dark", false);
    }
    void setDark(boolean v) { p.edit().putBoolean("dark", v).apply(); }
    /** test hook: forget the choice (next start follows the system again) */
    void resetDark() { p.edit().remove("dark").remove("theme").apply(); }

    /** date format for every date shown (default DD/MM/YYYY) */
    DateFmt dateFmt() { return DateFmt.of(p.getString("date_format", null)); }
    void setDateFmt(DateFmt f) { p.edit().putString("date_format", f.name()).apply(); }

    boolean confirmBrowser() { return p.getBoolean("confirm_browser", true); }
    void setConfirmBrowser(boolean v) { p.edit().putBoolean("confirm_browser", v).apply(); }

    /**
     * Search URL template with %s, without a scheme (https:// is always used). Templates saved by 0.2.0-0.6.0
     * ("https://…") are rewritten once in the new form; a 0.1.0 engine choice is carried over.
     */
    String searchTemplate() {
        String t = p.getString("search_template", null);
        if (t != null) {
            String n = SearchUrl.normalize(t);
            if (!n.equals(t)) p.edit().putString("search_template", n).apply();
            return n;
        }
        if (p.contains("search_engine")) {
            t = SearchUrl.migrate(p.getString("search_engine", null), p.getString("search_custom", ""));
            p.edit().putString("search_template", t).remove("search_engine").remove("search_custom").apply();
            return t;
        }
        return SearchUrl.DEFAULT_TEMPLATE;
    }
    /** stores the scheme-less form */
    void setSearchTemplate(String v) { p.edit().putString("search_template", SearchUrl.strip(v)).apply(); }

    /** how many times the "swipe to zoom" hint was shown (shown on the first 3 launches) */
    int zoomHints() { return p.getInt("zoom_hints", 0); }
    void countZoomHint() { p.edit().putInt("zoom_hints", zoomHints() + 1).apply(); }

    boolean multi() { return p.getBoolean("multi", false); }
    void setMulti(boolean v) { p.edit().putBoolean("multi", v).apply(); }
}
