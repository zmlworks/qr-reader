package com.zmlworks.qrreader;

import java.util.Locale;

/** Web-search URL from a template such as https://www.google.com/search?q=%s (pure Java, unit-tested). */
public final class SearchUrl {
    private SearchUrl() {}

    public static final String DEFAULT_TEMPLATE = "https://www.google.com/search?q=%s";

    /** Templates of the 0.1.0 engine presets (used once to migrate that setting). */
    static String legacyTemplate(String key) {
        if (key == null) return DEFAULT_TEMPLATE;
        switch (key) {
            case "duckduckgo": return "https://duckduckgo.com/?q=%s";
            case "bing": return "https://www.bing.com/search?q=%s";
            case "yahoo_jp": return "https://search.yahoo.co.jp/search?p=%s";
            default: return DEFAULT_TEMPLATE;
        }
    }

    /** 0.1.0 setting (engine key + custom template) → template. */
    public static String migrate(String engineKey, String custom) {
        if ("custom".equals(engineKey) && validTemplate(custom)) return custom.trim();
        return legacyTemplate(engineKey);
    }

    /** A template is usable when it is an http(s) URL containing %s (and no spaces). */
    public static boolean validTemplate(String t) {
        if (t == null) return false;
        String s = t.trim(), low = s.toLowerCase(Locale.ROOT);
        return (low.startsWith("https://") || low.startsWith("http://")) && s.contains("%s") && s.length() > 10
                && s.indexOf(' ') < 0;
    }

    /** Search URL: every %s is replaced by the UTF-8 percent-encoded query (space = %20); invalid template = Google. */
    public static String build(String template, String query) {
        String tpl = validTemplate(template) ? template.trim() : DEFAULT_TEMPLATE;
        String q = ContentType.pctEncode(query == null ? "" : query);
        return tpl.replace("%s", q);
    }
}
