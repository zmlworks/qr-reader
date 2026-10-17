package com.zmlworks.qrreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchUrlTest {
    @Test public void templates() {
        assertEquals("https://www.google.com/search?q=hello%20world", SearchUrl.build(SearchUrl.DEFAULT_TEMPLATE, "hello world"));
        assertEquals("https://duckduckgo.com/?q=a%26b%3Dc", SearchUrl.build("https://duckduckgo.com/?q=%s", "a&b=c"));
        assertEquals("https://www.bing.com/search?q=%23tag", SearchUrl.build("https://www.bing.com/search?q=%s", "#tag"));
        assertEquals("https://search.yahoo.co.jp/search?p=%E6%9D%B1%E4%BA%AC", SearchUrl.build("https://search.yahoo.co.jp/search?p=%s", "東京"));
        assertEquals("https://example.com/s?q=a%20b&src=qr", SearchUrl.build("https://example.com/s?q=%s&src=qr", "a b"));
        assertEquals("https://x.org/%E3%81%82/%E3%81%82", SearchUrl.build(" https://x.org/%s/%s ", "あ"));
        assertEquals("https://www.google.com/search?q=", SearchUrl.build(SearchUrl.DEFAULT_TEMPLATE, null));
        // invalid template falls back to Google
        assertEquals("https://www.google.com/search?q=q", SearchUrl.build("https://example.com/no-placeholder", "q"));
        assertEquals("https://www.google.com/search?q=q", SearchUrl.build("javascript:alert(%s)", "q"));
        assertEquals("https://www.google.com/search?q=q", SearchUrl.build(null, "q"));
    }

    @Test public void migrationFrom010() {
        assertEquals(SearchUrl.DEFAULT_TEMPLATE, SearchUrl.migrate("google", ""));
        assertEquals("https://duckduckgo.com/?q=%s", SearchUrl.migrate("duckduckgo", ""));
        assertEquals("https://www.bing.com/search?q=%s", SearchUrl.migrate("bing", null));
        assertEquals("https://search.yahoo.co.jp/search?p=%s", SearchUrl.migrate("yahoo_jp", ""));
        assertEquals("https://example.com/?q=%s", SearchUrl.migrate("custom", " https://example.com/?q=%s "));
        assertEquals(SearchUrl.DEFAULT_TEMPLATE, SearchUrl.migrate("custom", "bad"));
        assertEquals(SearchUrl.DEFAULT_TEMPLATE, SearchUrl.migrate(null, null));
    }

    @Test public void validation() {
        assertTrue(SearchUrl.validTemplate("https://example.com/?q=%s"));
        assertTrue(SearchUrl.validTemplate("http://intranet/find?w=%s"));
        assertFalse(SearchUrl.validTemplate("https://example.com/"));
        assertFalse(SearchUrl.validTemplate("example.com/?q=%s"));
        assertFalse(SearchUrl.validTemplate("https://exa mple.com/?q=%s"));
        assertFalse(SearchUrl.validTemplate(null));
    }
}
