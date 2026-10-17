package com.zmlworks.qrreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class FormatsTest {
    @Test public void labels() {
        assertNull(Formats.label(null));
        assertNull(Formats.label("QR_CODE"));
        assertNull(Formats.label("QR_CODE_MODEL_2"));
        assertEquals("Micro QR", Formats.label("MICRO_QR_CODE"));
        assertEquals("EAN-13", Formats.label("EAN_13"));
        assertEquals("Code 128", Formats.label("CODE_128"));
        assertEquals("DataMatrix", Formats.label("DATA_MATRIX"));
        assertEquals("PDF417", Formats.label("PDF_417"));
        assertEquals("Aztec", Formats.label("AZTEC"));
        assertEquals("MaxiCode", Formats.label("MAXICODE"));       // Java ZXing spelling
        assertEquals("Some New Code", Formats.label("SOME_NEW_CODE"));
    }
}
