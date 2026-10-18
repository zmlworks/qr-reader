package com.zmlworks.qrreader;

import java.util.regex.Pattern;

/**
 * Web-search URL from a template such as www.google.com/search?q=%s (pure Java, unit-tested).
 * Since 0.7.0 templates are stored and shown without a scheme; URLs are always built with https://.
 */
public final class SearchUrl {
    private SearchUrl() {}

    public static final String DEFAULT_TEMPLATE = "www.google.com/search?q=%s";

    /** a leading scheme such as "https://", "HTTP://" or "ftp://" (or a bare "//") */
    private static final Pattern SCHEME = Pattern.compile("^\\s*(?:[A-Za-z][A-Za-z0-9+.-]*:)?//+");
    /** host[:port], then the end, a path, a query or a fragment */
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?(?::\\d{1,5})?(?:[/?#].*)?$");

    /** Templates of the 0.1.0 engine presets (used once to migrate that setting). */
    static String legacyTemplate(String key) {
        if (key == null) return DEFAULT_TEMPLATE;
        switch (key) {
            case "duckduckgo": return "duckduckgo.com/?q=%s";
            case "bing": return "www.bing.com/search?q=%s";
            case "yahoo_jp": return "search.yahoo.co.jp/search?p=%s";
            default: return DEFAULT_TEMPLATE;
        }
    }

    /** 0.1.0 setting (engine key + custom template) → template. */
    public static String migrate(String engineKey, String custom) {
        if ("custom".equals(engineKey) && validTemplate(custom)) return strip(custom);
        return legacyTemplate(engineKey);
    }

    /** A stored template of any version (0.2.0-0.6.0 kept "https://…") → the scheme-less form; unusable = Google. */
    public static String normalize(String stored) {
        return validTemplate(stored) ? strip(stored) : DEFAULT_TEMPLATE;
    }

    /** Removes surrounding spaces and any leading scheme ("https://", "http://", …): it is always https. */
    public static String strip(String t) {
        if (t == null) return "";
        String s = t.trim();
        for (String prev = null; !s.equals(prev); ) { prev = s; s = SCHEME.matcher(s).replaceFirst("").trim(); }
        return s;
    }

    /** True if the text starts with a scheme that {@link #strip} would remove. */
    public static boolean hasScheme(String t) { return t != null && SCHEME.matcher(t).find(); }

    /** Usable (after {@link #strip}): a host name, %s after it, no spaces. Empty is not valid. */
    public static boolean validTemplate(String t) {
        String s = strip(t);
        if (s.isEmpty() || s.indexOf(' ') >= 0 || !HOST.matcher(s).matches()) return false;
        int slash = indexOfAny(s), pct = s.indexOf("%s");
        return pct >= 0 && slash >= 0 && pct > slash;
    }

    private static int indexOfAny(String s) {
        for (int i = 0; i < s.length(); i++) if ("/?#".indexOf(s.charAt(i)) >= 0) return i;
        return -1;
    }

    /** Search URL: https:// + the template with every %s replaced by the UTF-8 percent-encoded query; invalid template = Google. */
    public static String build(String template, String query) {
        String tpl = validTemplate(template) ? strip(template) : DEFAULT_TEMPLATE;
        String q = ContentType.pctEncode(query == null ? "" : query);
        return "https://" + tpl.replace("%s", q);
    }
}
