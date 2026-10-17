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

    @Test public void qrFamily() {
        for (String q : new String[]{"QR_CODE", "QR_CODE_MODEL_1", "QR_CODE_MODEL_2", "MICRO_QR_CODE", "RMQR_CODE"})
            org.junit.Assert.assertTrue(q, Formats.isQrFamily(q));
        for (String n : new String[]{"EAN_13", "DATA_MATRIX", "AZTEC", "PDF_417", "MAXI_CODE", "CODE_128"})
            org.junit.Assert.assertTrue(n, !Formats.isQrFamily(n));
    }

    /** the non-QR mode's zxing-cpp format set: every family but QR (QR, Micro QR, rMQR); prints the whole enum for the report */
    @Test public void nonQrFormatSet() {
        java.util.Set<zxingcpp.BarcodeReader.Format> set = CppEngine.NON_QR;
        for (zxingcpp.BarcodeReader.Format f : set) org.junit.Assert.assertTrue(f.name(), !Formats.isQrFamily(f.name()) && !f.name().startsWith("ALL_MATRIX") && !f.name().equals("ALL"));
        for (String n : new String[]{"ALL_LINEAR", "DATA_MATRIX", "AZTEC", "PDF_417", "MAXI_CODE"})
            org.junit.Assert.assertTrue(n, set.contains(zxingcpp.BarcodeReader.Format.valueOf(n)));
        StringBuilder b = new StringBuilder();
        for (zxingcpp.BarcodeReader.Format f : zxingcpp.BarcodeReader.Format.values()) b.append(f.name()).append(set.contains(f) ? "+ " : " ");
        System.out.println("zxing-cpp formats (+ = non-QR mode): " + b);
    }
}
