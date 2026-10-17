package com.zmlworks.qrreader;

import java.util.Locale;

/** Web-search URL for the chosen engine (pure Java, unit-tested). */
public final class SearchUrl {
    private SearchUrl() {}

    public static final String GOOGLE = "google", DUCKDUCKGO = "duckduckgo", BING = "bing", YAHOO_JAPAN = "yahoo_jp", CUSTOM = "custom";
    public static final String[] KEYS = {GOOGLE, DUCKDUCKGO, BING, YAHOO_JAPAN, CUSTOM};
    public static final String[] NAMES = {"Google", "DuckDuckGo", "Bing", "Yahoo! JAPAN", "Custom URL"};

    public static String template(String key) {
        switch (key == null ? GOOGLE : key) {
            case DUCKDUCKGO: return "https://duckduckgo.com/?q=%s";
            case BING: return "https://www.bing.com/search?q=%s";
            case YAHOO_JAPAN: return "https://search.yahoo.co.jp/search?p=%s";
            default: return "https://www.google.com/search?q=%s";
        }
    }

    public static String name(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return NAMES[i];
        return NAMES[0];
    }

    /** A custom template is usable when it is an http(s) URL containing %s. */
    public static boolean validTemplate(String t) {
        if (t == null) return false;
        String s = t.trim(), low = s.toLowerCase(Locale.ROOT);
        return (low.startsWith("https://") || low.startsWith("http://")) && s.contains("%s") && s.length() > 10
                && s.indexOf(' ') < 0;
    }

    /** Search URL: every %s in the template is replaced by the UTF-8 percent-encoded query (space = %20). */
    public static String build(String key, String custom, String query) {
        String tpl = CUSTOM.equals(key) && validTemplate(custom) ? custom.trim() : template(key);
        String q = ContentType.pctEncode(query == null ? "" : query);
        return tpl.replace("%s", q);
    }
}
