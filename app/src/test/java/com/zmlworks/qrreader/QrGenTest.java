package com.zmlworks.qrreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

/** Create page encoder: every symbol is decoded back with ZXing's QR reader from the rendered pixels. */
public class QrGenTest {
    static Result decode(QrGen.Qr q) throws Exception {
        int scale = 3, w = (q.size + 2 * QrGen.QUIET) * scale;
        int[] px = QrGen.pixels(q, scale);
        Map<DecodeHintType, Object> h = new EnumMap<>(DecodeHintType.class);
        h.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w, w, px))), h);
    }

    static void roundTrip(String text) throws Exception {
        QrGen.Qr q = QrGen.encode(text);
        assertNotNull(text, q);
        assertEquals(17 + 4 * q.version, q.size);
        Result r = decode(q);
        assertEquals(text, r.getText());
        assertEquals("M", r.getResultMetadata().get(ResultMetadataType.ERROR_CORRECTION_LEVEL));
    }

    @Test public void roundTrips() throws Exception {
        roundTrip("a");
        roundTrip("https://github.com/zmlworks/qr-reader");
        roundTrip("01234567890123456789");
        roundTrip("HELLO WORLD 123");
        roundTrip("こんにちは、世界！ QR コード 🙂");
        roundTrip("Ünïcödé café — 25 €");
        roundTrip("WIFI:T:WPA;S:Cafe;P:p@ss;;");
        roundTrip("line1\nline2\ttab");
    }

    @Test public void growsCharacterByCharacter() throws Exception {
        // the Create page re-encodes after every character: every prefix must work and the version never shrinks
        String s = "Simple QR Reader encodes every character you type 日本語 ";
        StringBuilder b = new StringBuilder();
        int last = 0;
        for (int i = 0; i < 4; i++) for (char c : s.toCharArray()) {
            b.append(c);
            QrGen.Qr q = QrGen.encode(b.toString());
            assertNotNull(q);
            assertTrue(q.version >= last);
            last = q.version;
            if (b.length() % 7 == 0) assertEquals(b.toString(), decode(q).getText());
        }
    }

    @Test public void byteModeUtf8AndLimits() throws Exception {
        QrGen.Qr q = QrGen.encode("é");
        assertEquals("BYTE", q.mode);
        assertEquals(2, q.bytes);
        assertEquals(1, q.version);
        assertNull(QrGen.encode(""));
        assertNull(QrGen.encode(null));
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 2331; i++) big.append((char) ('a' + i % 26));   // 2331 bytes = version 40-M byte capacity
        QrGen.Qr max = QrGen.encode(big.toString());
        assertNotNull(max);
        assertEquals(40, max.version);
        assertEquals(big.toString(), decode(max).getText());
        assertNull(QrGen.encode(big.append('x').toString()));             // one byte too many
    }
}
