package com.zmlworks.qrreader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Read from image" (pure Java part, unit-tested): grey-scale conversion of a picture and decoding of every code in
 * it with zxing-cpp (still-image options), the 2× upscale for small codes, and the Java ZXing reader as a second
 * opinion. Distinct texts, in the order found.
 */
public final class PhotoCodes {
    private PhotoCodes() {}

    /** Luminance of ARGB pixels; transparent pixels count as white paper (so a QR with a transparent background reads). */
    public static byte[] luminance(int[] argb, int w, int h) {
        byte[] y = new byte[w * h];
        for (int i = 0; i < w * h; i++) {
            int p = argb[i], a = p >>> 24, r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
            int l = (r * 77 + g * 150 + b * 29) >> 8;
            y[i] = (byte) (255 - (255 - l) * a / 255);
        }
        return y;
    }

    /** @param engines tried in order until one finds something (the later ones are fallbacks for misses) */
    public static List<QrEngine.Code> decode(byte[] y, int w, int h, List<QrEngine> engines) {
        List<QrEngine.Code> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (QrEngine e : engines) {
            if (e == null) continue;
            DecodePipeline p = new DecodePipeline(e, true);
            for (QrEngine.Code c : p.decode(y, w, h, 0)) if (seen.add(c.text)) out.add(c);
            if (!out.isEmpty()) break;
        }
        return out;
    }
}
