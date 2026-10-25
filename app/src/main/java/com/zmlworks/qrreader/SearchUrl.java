package com.zmlworks.qrreader;

import java.util.regex.Pattern;

/**
 * Web-search URL from a template such as www.google.com/search?q=%s (pure Java, unit-tested).
 * Templates are stored and shown without a scheme; URLs are always built with https://.
 * A scheme typed in the Settings editor is stripped when the user presses OK, not mid-typing.
 */
public final class SearchUrl {
    private SearchUrl() {}

    public static final String DEFAULT_TEMPLATE = "www.google.com/search?q=%s";

    /** a leading scheme such as "https://", "HTTP://" or "ftp://" (or a bare "//") */
    private static final Pattern SCHEME = Pattern.compile("^\\s*(?:[A-Za-z][A-Za-z0-9+.-]*:)?//+");
    /** host[:port], then the end, a path, a query or a fragment */
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?(?::\\d{1,5})?(?:[/?#].*)?$");

    /** A stored or typed template → the scheme-less form; unusable = Google. */
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
