package com.zmlworks.qrreader;

import static org.junit.Assert.*;

import org.junit.Test;

public class CreateCodecTest {
    @Test public void historyFormat() {
        assertNull(CreateCodec.historyFormat(null));
        assertNull(CreateCodec.historyFormat(CreateCodec.QR));
        assertEquals("Code 128", CreateCodec.historyFormat(CreateCodec.CODE_128));
        assertEquals("EAN-13", CreateCodec.historyFormat(CreateCodec.EAN13));
    }

    @Test public void qrAndCode128Payload() throws Exception {
        assertEquals("hello", CreateCodec.payload(CreateCodec.QR, "hello"));
        assertEquals("QUIET-QR 0.10.0", CreateCodec.payload(CreateCodec.CODE_128, "QUIET-QR 0.10.0"));
        try { CreateCodec.payload(CreateCodec.CODE_128, ""); fail(); } catch (Recreate.InvalidContent expected) { }
        try { CreateCodec.payload(CreateCodec.CODE_128, "日本"); fail(); } catch (Recreate.InvalidContent expected) { }
        try { CreateCodec.payload(CreateCodec.QR, ""); fail(); } catch (Recreate.InvalidContent expected) { }
        try { CreateCodec.payload(CreateCodec.QR, null); fail(); } catch (Recreate.InvalidContent expected) { }
    }

    @Test public void ean13Accepts12Or13() throws Exception {
        assertEquals("4006381333931", CreateCodec.payload(CreateCodec.EAN13, "400638133393"));
        assertEquals("4006381333931", CreateCodec.payload(CreateCodec.EAN13, "4006381333931"));
        assertEquals("4006381333931", CreateCodec.payload(CreateCodec.EAN13, "4006-3813-33931"));
        assertEquals("4006381333931", CreateCodec.payload(CreateCodec.EAN13, " 4006 3813 3393 "));
        assertEquals(1, CreateCodec.check("400638133393"));
        assertTrue(CreateCodec.ean13BadCheck("4006381333932"));
        assertTrue(!CreateCodec.ean13BadCheck("4006381333931"));
        assertTrue(!CreateCodec.ean13BadCheck("400638133393"));   // 12 digits: not a bad check
        try { CreateCodec.payload(CreateCodec.EAN13, "4006381333932"); fail(); } catch (Recreate.InvalidContent expected) { }
        try { CreateCodec.payload(CreateCodec.EAN13, "ABC"); fail(); } catch (Recreate.InvalidContent expected) { }
        try { CreateCodec.payload(CreateCodec.EAN13, "12345"); fail(); } catch (Recreate.InvalidContent expected) { }
        try { CreateCodec.payload(CreateCodec.EAN13, ""); fail(); } catch (Recreate.InvalidContent expected) { }
    }
}
