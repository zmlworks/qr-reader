package com.zmlworks.qrreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchUrlTest {
    @Test public void engines() {
        assertEquals("https://www.google.com/search?q=hello%20world", SearchUrl.build(SearchUrl.GOOGLE, null, "hello world"));
        assertEquals("https://duckduckgo.com/?q=a%26b%3Dc", SearchUrl.build(SearchUrl.DUCKDUCKGO, null, "a&b=c"));
        assertEquals("https://www.bing.com/search?q=%23tag", SearchUrl.build(SearchUrl.BING, null, "#tag"));
        assertEquals("https://search.yahoo.co.jp/search?p=%E6%9D%B1%E4%BA%AC", SearchUrl.build(SearchUrl.YAHOO_JAPAN, null, "東京"));
        assertEquals("https://www.google.com/search?q=x", SearchUrl.build("unknown", null, "x"));
        assertEquals("https://www.google.com/search?q=", SearchUrl.build(SearchUrl.GOOGLE, null, null));
    }

    @Test public void custom() {
        assertEquals("https://example.com/s?q=a%20b&src=qr", SearchUrl.build(SearchUrl.CUSTOM, "https://example.com/s?q=%s&src=qr", "a b"));
        assertEquals("https://x.org/%E3%81%82/%E3%81%82", SearchUrl.build(SearchUrl.CUSTOM, " https://x.org/%s/%s ", "あ"));
        // invalid template falls back to Google
        assertEquals("https://www.google.com/search?q=q", SearchUrl.build(SearchUrl.CUSTOM, "https://example.com/no-placeholder", "q"));
        assertEquals("https://www.google.com/search?q=q", SearchUrl.build(SearchUrl.CUSTOM, "javascript:alert(%s)", "q"));
    }

    @Test public void validation() {
        assertTrue(SearchUrl.validTemplate("https://example.com/?q=%s"));
        assertTrue(SearchUrl.validTemplate("http://intranet/find?w=%s"));
        assertFalse(SearchUrl.validTemplate("https://example.com/"));
        assertFalse(SearchUrl.validTemplate("example.com/?q=%s"));
        assertFalse(SearchUrl.validTemplate("https://exa mple.com/?q=%s"));
        assertFalse(SearchUrl.validTemplate(null));
        assertEquals("Yahoo! JAPAN", SearchUrl.name(SearchUrl.YAHOO_JAPAN));
    }
}
