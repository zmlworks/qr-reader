package com.zmlworks.qrreader;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * Text → QR matrix with everything chosen automatically (pure Java, ZXing core; unit-tested by decoding it back).
 * <ul>
 * <li>EC level M (15 %), the smallest version that fits (1–40), ZXing's lowest-penalty mask.</li>
 * <li>Text with any non-ASCII character is encoded as UTF-8 bytes in byte mode with an ECI (UTF-8) header, so
 * readers do not have to guess the charset. Plain ASCII goes without ECI; ZXing still uses the compact numeric or
 * alphanumeric mode when the whole text is digits or upper-case alphanumerics (decodes to the identical text).</li>
 * </ul>
 */
public final class QrGen {
    private QrGen() {}

    public static final int QUIET = 4;   // quiet zone, modules
    public static final ErrorCorrectionLevel EC = ErrorCorrectionLevel.M;

    public static final class Qr {
        public final int size, version, bytes;   // size = modules per side without the quiet zone
        public final boolean[] dark;             // size*size, row-major
        public final String mode;
        Qr(int size, int version, int bytes, boolean[] dark, String mode) { this.size = size; this.version = version; this.bytes = bytes; this.dark = dark; this.mode = mode; }
        public boolean get(int x, int y) { return dark[y * size + x]; }
    }

    static boolean ascii(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) > 0x7F) return false;
        return true;
    }

    /** @return the symbol, or null if the text is empty or too long for version 40-M (about 2.3 KB) */
    public static Qr encode(String text) {
        if (text == null || text.isEmpty()) return null;
        Map<EncodeHintType, Object> h = new EnumMap<>(EncodeHintType.class);
        if (!ascii(text)) h.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        QRCode q;
        try { q = Encoder.encode(text, EC, h); } catch (WriterException | IllegalArgumentException e) { return null; }
        ByteMatrix m = q.getMatrix();
        int n = m.getWidth();
        boolean[] d = new boolean[n * n];
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) d[y * n + x] = m.get(x, y) == 1;
        return new Qr(n, q.getVersion().getVersionNumber(), text.getBytes(StandardCharsets.UTF_8).length, d, q.getMode().name());
    }

    /** ARGB pixels at `scale` px per module including the quiet zone (black on white). */
    public static int[] pixels(Qr q, int scale) {
        int w = (q.size + 2 * QUIET) * scale;
        int[] px = new int[w * w];
        java.util.Arrays.fill(px, 0xFFFFFFFF);
        for (int y = 0; y < q.size; y++) for (int x = 0; x < q.size; x++) if (q.get(x, y)) {
            int ox = (x + QUIET) * scale, oy = (y + QUIET) * scale;
            for (int yy = 0; yy < scale; yy++) java.util.Arrays.fill(px, (oy + yy) * w + ox, (oy + yy) * w + ox + scale, 0xFF000000);
        }
        return px;
    }
}
