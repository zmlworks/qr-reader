package com.zmlworks.qrreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ContentTypeTest {
    private static ContentType.Kind k(String s) { return ContentType.parse(s).kind; }

    @Test public void urls() {
        assertEquals(ContentType.Kind.URL, k("https://example.com/a?b=c"));
        assertEquals(ContentType.Kind.URL, k("HTTP://EXAMPLE.COM"));
        assertEquals(ContentType.Kind.URL, k("  https://example.com  "));
        assertEquals("https://www.example.org", ContentType.parse("www.example.org").uri);
        assertEquals(ContentType.Kind.TEXT, k("https://"));
        assertEquals(ContentType.Kind.TEXT, k("https://a.com and more text"));
        assertEquals(ContentType.Kind.TEXT, k("www."));
        assertEquals(ContentType.Kind.TEXT, k("ftp://example.com"));   // only http(s) opens in the browser
    }

    @Test public void wifi() {
        ContentType t = ContentType.parse("WIFI:T:WPA;S:My\\;Net;P:pa\\:ss\\\\word;H:true;;");
        assertEquals(ContentType.Kind.WIFI, t.kind);
        assertEquals("My;Net", t.ssid);
        assertEquals("pa:ss\\word", t.password);
        assertEquals("WPA", t.security);
        assertTrue(t.hidden);
        ContentType open = ContentType.parse("wifi:S:Free;T:nopass;P:;;");
        assertEquals("Free", open.ssid);
        assertNull(open.security);
        assertNull(open.password);
        assertFalse(open.hidden);
        assertEquals(ContentType.Kind.TEXT, k("WIFI:T:WPA;P:x;;"));   // no SSID
        assertEquals("Wi-Fi", t.label());
    }

    @Test public void telMailSmsGeo() {
        ContentType tel = ContentType.parse("tel:+81-3-1234-5678");
        assertEquals(ContentType.Kind.TEL, tel.kind);
        assertEquals("+81-3-1234-5678", tel.number);
        assertEquals(ContentType.Kind.TEXT, k("tel:"));

        ContentType m = ContentType.parse("mailto:info@example.com?subject=Hello%20there&body=%E3%81%82");
        assertEquals(ContentType.Kind.EMAIL, m.kind);
        assertEquals("info@example.com", m.address);
        assertEquals("Hello there", m.subject);
        assertEquals("あ", m.body);
        ContentType mm = ContentType.parse("MATMSG:TO:a@b.jp;SUB:Hi;BODY:Text;;");
        assertEquals(ContentType.Kind.EMAIL, mm.kind);
        assertEquals("a@b.jp", mm.address);
        assertEquals("Hi", mm.subject);

        ContentType s = ContentType.parse("SMSTO:+123456:see you");
        assertEquals(ContentType.Kind.SMS, s.kind);
        assertEquals("+123456", s.number);
        assertEquals("see you", s.body);
        assertEquals("hi", ContentType.parse("sms:555?body=hi").body);

        assertEquals(ContentType.Kind.GEO, k("geo:35.6812,139.7671"));
        assertEquals(ContentType.Kind.GEO, k("geo:35.6812,139.7671?q=Tokyo"));
        assertEquals(ContentType.Kind.TEXT, k("geo:here"));
    }

    @Test public void text() {
        assertEquals(ContentType.Kind.TEXT, k("Hello world"));
        assertEquals(ContentType.Kind.TEXT, k(""));
        assertEquals(ContentType.Kind.TEXT, k(null));
        assertEquals(ContentType.Kind.TEXT, k("こんにちは"));
        assertEquals("Text", ContentType.parse("x").label());
    }

    @Test public void percentCoding() {
        assertEquals("a b/日本", ContentType.pctDecode("a%20b%2F%E6%97%A5%E6%9C%AC"));
        assertEquals("a%20b%2F%E6%97%A5", ContentType.pctEncode("a b/日"));
        assertEquals("100%", ContentType.pctDecode("100%"));   // stray % kept
    }
}
