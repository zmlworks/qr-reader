package com.zmlworks.qrreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

/** "Read from image": grey conversion and decoding of one or several codes in a picture (Java ZXing on the JVM). */
public class PhotoCodesTest {
    /** A w×h ARGB "photo" (light grey paper) with the given codes pasted at (x, y) with `scale` px per module. */
    static int[] picture(int w, int h, String[] texts, int[][] at, int scale) {
        int[] px = new int[w * h];
        java.util.Arrays.fill(px, 0xFFDDDDDD);
        for (int k = 0; k < texts.length; k++) {
            QrGen.Qr q = QrGen.encode(texts[k]);
            int n = (q.size + 2 * QrGen.QUIET) * scale;
            int[] qp = QrGen.pixels(q, scale);
            for (int y = 0; y < n; y++) System.arraycopy(qp, y * n, px, (at[k][1] + y) * w + at[k][0], n);
        }
        return px;
    }

    static List<String> decode(int[] px, int w, int h) {
        return PhotoCodes.decode(PhotoCodes.luminance(px, w, h), w, h, Collections.singletonList(new JavaEngine()));
    }

    @Test public void luminance() {
        byte[] y = PhotoCodes.luminance(new int[]{0xFF000000, 0xFFFFFFFF, 0x00000000, 0x80000000, 0xFF00FF00}, 5, 1);
        assertEquals(0, y[0] & 255);
        assertEquals(255, y[1] & 255);
        assertEquals("transparent = white paper", 255, y[2] & 255);
        assertTrue(Math.abs((y[3] & 255) - 128) <= 1);
        assertEquals(149, y[4] & 255);
    }

    @Test public void oneCode() {
        int w = 800, h = 600;
        List<String> r = decode(picture(w, h, new String[]{"https://github.com/zmlworks/qr-reader"}, new int[][]{{200, 120}}, 8), w, h);
        assertEquals(Collections.singletonList("https://github.com/zmlworks/qr-reader"), r);
    }

    @Test public void severalCodes() {
        int w = 1200, h = 500;
        String[] t = {"INV-2026-10-101", "INV-2026-10-102", "INV-2026-10-103"};
        List<String> r = decode(picture(w, h, t, new int[][]{{40, 60}, {440, 100}, {840, 40}}, 9), w, h);
        assertEquals(3, r.size());
        for (String s : t) assertTrue(s, r.contains(s));
    }

    @Test public void tinyCodeNeedsUpscale() {
        int w = 300, h = 200;
        List<String> r = decode(picture(w, h, new String[]{"tiny"}, new int[][]{{100, 60}}, 2), w, h);
        assertEquals(Collections.singletonList("tiny"), r);
    }

    @Test public void noCode() {
        int w = 400, h = 300;
        assertTrue(decode(picture(w, h, new String[0], new int[0][], 1), w, h).isEmpty());
    }
}
