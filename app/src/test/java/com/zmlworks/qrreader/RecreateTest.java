package com.zmlworks.qrreader;

import static org.junit.Assert.*;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import org.junit.Test;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class RecreateTest {
    @Test public void mapping() {
        assertEquals(BarcodeFormat.EAN_13, Recreate.writerFormat("EAN-13"));
        assertEquals(BarcodeFormat.EAN_13, Recreate.writerFormat("ISBN"));
        assertEquals(BarcodeFormat.EAN_8, Recreate.writerFormat("EAN-8"));
        assertEquals(BarcodeFormat.UPC_A, Recreate.writerFormat("UPC-A"));
        assertEquals(BarcodeFormat.UPC_E, Recreate.writerFormat("UPC-E"));
        assertEquals(BarcodeFormat.CODE_128, Recreate.writerFormat("Code 128"));
        assertEquals(BarcodeFormat.CODE_39, Recreate.writerFormat("Code 39"));
        assertEquals(BarcodeFormat.CODE_39, Recreate.writerFormat("Code 32"));
        assertEquals(BarcodeFormat.CODE_39, Recreate.writerFormat("PZN"));
        assertEquals(BarcodeFormat.CODE_93, Recreate.writerFormat("Code 93"));
        assertEquals(BarcodeFormat.CODABAR, Recreate.writerFormat("Codabar"));
        assertEquals(BarcodeFormat.ITF, Recreate.writerFormat("ITF"));
        assertEquals(BarcodeFormat.ITF, Recreate.writerFormat("ITF-14"));
        assertEquals(BarcodeFormat.DATA_MATRIX, Recreate.writerFormat("DataMatrix"));
        assertEquals(BarcodeFormat.AZTEC, Recreate.writerFormat("Aztec"));
        assertEquals(BarcodeFormat.PDF_417, Recreate.writerFormat("PDF417"));
        for (String no : new String[]{null, "DataBar", "DataBar Expanded", "DataBar Limited", "DataBar Stacked", "Telepen", "DX Film Edge",
                "EAN-5", "EAN-2", "EAN add-on", "MaxiCode", "MicroPDF417", "Micro QR", "rMQR", "Aztec Rune"})
            assertNull(no, Recreate.writerFormat(no));
    }

    /** every label that Formats produces for a zxing-cpp enum name maps the same as the enum name itself */
    @Test public void labelsAndEnumNamesAgree() {
        for (String n : new String[]{"EAN_13", "EAN_8", "UPC_A", "UPC_E", "CODE_128", "CODE_39", "CODE_93", "CODABAR", "ITF", "ITF_14",
                "DATA_MATRIX", "AZTEC", "PDF_417", "ISBN", "CODE_32", "PZN"})
            assertEquals(n, Recreate.writerFormat(n), Recreate.writerFormat(Formats.label(n)));
    }

    static Result decode(Recreate.Code c, BarcodeFormat f) throws Exception {
        int s = 4, w = Recreate.pixelWidth(c) * s, ph = Recreate.pixelHeight(c), h = c.oneD() ? 80 : ph * s;
        int[] src = Recreate.pixels(c), px = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            px[y * w + x] = src[(c.oneD() ? 0 : y / s) * Recreate.pixelWidth(c) + x / s];
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(f));
        hints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.RETURN_CODABAR_START_END, Boolean.TRUE);
        return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w, h, px))), hints);
    }

    private static void roundtrip(String label, String text, String expect) throws Exception {
        Recreate.Code c = Recreate.encode(label, text);
        assertNotNull(label, c);
        // quiet zone: first/last columns are white
        int[] px = Recreate.pixels(c);
        for (int x = 0; x < c.quiet(); x++) assertEquals(0xFFFFFFFF, px[x]);
        Result r = decode(c, c.format);
        assertEquals(label, c.format, r.getBarcodeFormat());
        assertEquals(label, expect, r.getText());
    }

    @Test public void roundtrips() throws Exception {
        roundtrip("EAN-13", "4006381333931", "4006381333931");
        roundtrip("ISBN", "9784101010014", "9784101010014");
        roundtrip("EAN-8", "96385074", "96385074");
        roundtrip("UPC-A", "036000291452", "036000291452");
        roundtrip("UPC-E", "01234565", "01234565");
        roundtrip("Code 128", "QUIET-QR 0.9.0", "QUIET-QR 0.9.0");
        roundtrip("Code 39", "CODE-39 TEST", "CODE-39 TEST");
        roundtrip("Code 93", "CODE93 TEST", "CODE93 TEST");
        roundtrip("Codabar", "A40156B", "A40156B");
        roundtrip("ITF", "12345678", "12345678");
        roundtrip("ITF-14", "10012345678902", "10012345678902");
        roundtrip("DataMatrix", "https://github.com/zmlworks/qr-reader", "https://github.com/zmlworks/qr-reader");
        roundtrip("Aztec", "Aztec 2D テスト", "Aztec 2D テスト");
        roundtrip("PDF417", "PDF417 sample 12345", "PDF417 sample 12345");
        roundtrip("DataMatrix", "日本語", "日本語");
    }

    @Test public void cannotWrite() throws Exception {
        assertNull(Recreate.encode("DataBar", "0101234567890128"));
        assertNull(Recreate.encode("MaxiCode", "x"));
    }

    @Test public void invalidContent() {
        String[][] bad = {{"EAN-13", "4006381333932"}, {"EAN-13", "ABC"}, {"EAN-8", "123"}, {"UPC-E", "91234565"},
                {"ITF", "12345"}, {"Code 39", "lower€"}, {"Codabar", "hello"}, {"Code 128", ""}, {"Code 128", "日本"}};
        for (String[] b : bad) {
            try { Recreate.encode(b[0], b[1]); fail(b[0] + " " + b[1]); } catch (Recreate.InvalidContent expected) { }
        }
    }

    @Test public void oneDIsWide() throws Exception {
        Recreate.Code c = Recreate.encode("EAN-13", "4006381333931");
        assertTrue(c.oneD());
        assertEquals(1, Recreate.pixelHeight(c));
        assertEquals(95 + 20, Recreate.pixelWidth(c));
    }
}
