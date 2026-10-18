package com.zmlworks.qrreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchUrlTest {
    @Test public void templates() {
        assertEquals("www.google.com/search?q=%s", SearchUrl.DEFAULT_TEMPLATE);
        assertEquals("https://www.google.com/search?q=hello%20world", SearchUrl.build(SearchUrl.DEFAULT_TEMPLATE, "hello world"));
        assertEquals("https://duckduckgo.com/?q=a%26b%3Dc", SearchUrl.build("duckduckgo.com/?q=%s", "a&b=c"));
        assertEquals("https://www.bing.com/search?q=%23tag", SearchUrl.build("www.bing.com/search?q=%s", "#tag"));
        assertEquals("https://search.yahoo.co.jp/search?p=%E6%9D%B1%E4%BA%AC", SearchUrl.build("search.yahoo.co.jp/search?p=%s", "東京"));
        assertEquals("https://example.com/s?q=a%20b&src=qr", SearchUrl.build("example.com/s?q=%s&src=qr", "a b"));
        assertEquals("https://x.org/%E3%81%82/%E3%81%82", SearchUrl.build(" x.org/%s/%s ", "あ"));
        assertEquals("https://www.google.com/search?q=", SearchUrl.build(SearchUrl.DEFAULT_TEMPLATE, null));
        // always https, whatever scheme a template had
        assertEquals("https://duckduckgo.com/?q=q", SearchUrl.build("https://duckduckgo.com/?q=%s", "q"));
        assertEquals("https://intranet:8080/find?w=q", SearchUrl.build("http://intranet:8080/find?w=%s", "q"));
        assertEquals("https://example.com/?q=q", SearchUrl.build("HTTPS://example.com/?q=%s", "q"));
        // invalid template falls back to Google
        assertEquals("https://www.google.com/search?q=q", SearchUrl.build("example.com/no-placeholder", "q"));
        assertEquals("https://www.google.com/search?q=q", SearchUrl.build("javascript:alert(%s)", "q"));
        assertEquals("https://www.google.com/search?q=q", SearchUrl.build("", "q"));
        assertEquals("https://www.google.com/search?q=q", SearchUrl.build(null, "q"));
    }

    @Test public void schemeStripping() {
        assertEquals("www.google.com/search?q=%s", SearchUrl.strip("https://www.google.com/search?q=%s"));
        assertEquals("www.google.com/search?q=%s", SearchUrl.strip("  HTTP://www.google.com/search?q=%s "));
        assertEquals("a.b/?q=%s", SearchUrl.strip("https://https://a.b/?q=%s"));
        assertEquals("a.b/?q=%s", SearchUrl.strip("ftp://a.b/?q=%s"));
        assertEquals("a.b/?q=%s", SearchUrl.strip("//a.b/?q=%s"));
        assertEquals("a.b/?q=%s", SearchUrl.strip("a.b/?q=%s"));
        assertEquals("", SearchUrl.strip("https://"));
        assertEquals("", SearchUrl.strip(null));
        assertTrue(SearchUrl.hasScheme("https://x"));
        assertTrue(SearchUrl.hasScheme("http://"));
        assertFalse(SearchUrl.hasScheme("x.org/https://y"));
        assertFalse(SearchUrl.hasScheme("www.google.com/search?q=%s"));
    }

    /** templates stored by 0.2.0-0.6.0 ("https://…") are rewritten once without the scheme */
    @Test public void migrationOfStoredTemplates() {
        assertEquals("www.google.com/search?q=%s", SearchUrl.normalize("https://www.google.com/search?q=%s"));
        assertEquals("duckduckgo.com/?q=%s", SearchUrl.normalize("https://duckduckgo.com/?q=%s"));
        assertEquals("intranet/find?w=%s", SearchUrl.normalize("http://intranet/find?w=%s"));
        assertEquals("already/new?q=%s", SearchUrl.normalize("already/new?q=%s"));
        assertEquals("broken = Google", SearchUrl.DEFAULT_TEMPLATE, SearchUrl.normalize("https://example.com/"));
        assertEquals(SearchUrl.DEFAULT_TEMPLATE, SearchUrl.normalize(""));
        assertEquals(SearchUrl.DEFAULT_TEMPLATE, SearchUrl.normalize(null));
    }

    @Test public void migrationFrom010() {
        assertEquals(SearchUrl.DEFAULT_TEMPLATE, SearchUrl.migrate("google", ""));
        assertEquals("duckduckgo.com/?q=%s", SearchUrl.migrate("duckduckgo", ""));
        assertEquals("www.bing.com/search?q=%s", SearchUrl.migrate("bing", null));
        assertEquals("search.yahoo.co.jp/search?p=%s", SearchUrl.migrate("yahoo_jp", ""));
        assertEquals("example.com/?q=%s", SearchUrl.migrate("custom", " https://example.com/?q=%s "));
        assertEquals(SearchUrl.DEFAULT_TEMPLATE, SearchUrl.migrate("custom", "bad"));
        assertEquals(SearchUrl.DEFAULT_TEMPLATE, SearchUrl.migrate(null, null));
    }

    @Test public void validation() {
        assertTrue(SearchUrl.validTemplate("example.com/?q=%s"));
        assertTrue(SearchUrl.validTemplate("https://example.com/?q=%s"));
        assertTrue(SearchUrl.validTemplate("intranet:8080/find?w=%s"));
        assertTrue(SearchUrl.validTemplate("a.b/%s"));
        assertFalse("empty is invalid (no more 'empty = Google')", SearchUrl.validTemplate(""));
        assertFalse(SearchUrl.validTemplate("   "));
        assertFalse(SearchUrl.validTemplate("https://"));
        assertFalse(SearchUrl.validTemplate("example.com/"));
        assertFalse("%s in the host", SearchUrl.validTemplate("%s.example.com/"));
        assertFalse(SearchUrl.validTemplate("exa mple.com/?q=%s"));
        assertFalse(SearchUrl.validTemplate("javascript:alert(%s)"));
        assertFalse(SearchUrl.validTemplate(null));
    }
}
