package com.zmlworks.qrreader;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * "Recreate this code": re-encodes a non-QR item in the format it was read in, with ZXing's writers (pure Java,
 * unit-tested). Formats come from history as {@link Formats#label} strings ("EAN-13", "Code 128" …); zxing-cpp / ZXing
 * enum names are accepted too.
 */
public final class Recreate {
    private Recreate() {}

    /** quiet zone in modules on each side: 1D codes need at least 10, 2D codes 2 (PDF417) or less */
    static final int QUIET_1D = 10, QUIET_2D = 2;

    /** The ZXing format that rewrites `format` (a history label or enum name), or null when ZXing cannot write it. */
    public static BarcodeFormat writerFormat(String format) {
        if (format == null) return null;
        switch (format) {
            case "EAN-13": case "EAN_13": case "ISBN": return BarcodeFormat.EAN_13;
            case "EAN-8": case "EAN_8": return BarcodeFormat.EAN_8;
            case "UPC-A": case "UPC_A": return BarcodeFormat.UPC_A;
            case "UPC-E": case "UPC_E": return BarcodeFormat.UPC_E;
            case "Code 128": case "CODE_128": return BarcodeFormat.CODE_128;
            case "Code 39": case "CODE_39": case "CODE_39_STD": case "CODE_39_EXT":
            case "Code 32": case "CODE_32": case "PZN": return BarcodeFormat.CODE_39;
            case "Code 93": case "CODE_93": return BarcodeFormat.CODE_93;
            case "Codabar": case "CODABAR": return BarcodeFormat.CODABAR;
            case "ITF": case "ITF-14": case "ITF_14": return BarcodeFormat.ITF;
            case "DataMatrix": case "DATA_MATRIX": return BarcodeFormat.DATA_MATRIX;
            case "Aztec": case "AZTEC": case "AZTEC_CODE": return BarcodeFormat.AZTEC;
            case "PDF417": case "PDF_417": return BarcodeFormat.PDF_417;
            default: return null;   // DataBar, Telepen, DX film edge, EAN add-ons, MaxiCode, MicroPDF417, Micro QR, rMQR, Aztec Rune …
        }
    }

    public static boolean is1D(BarcodeFormat f) {
        return f != BarcodeFormat.DATA_MATRIX && f != BarcodeFormat.AZTEC && f != BarcodeFormat.PDF_417 && f != BarcodeFormat.QR_CODE;
    }

    /** A re-encoded symbol: modules only (no quiet zone; 1D codes are one row high). */
    public static final class Code {
        public final BarcodeFormat format;
        public final BitMatrix matrix;
        Code(BarcodeFormat f, BitMatrix m) { format = f; matrix = m; }
        public boolean oneD() { return is1D(format); }
        public int quiet() { return oneD() ? QUIET_1D : QUIET_2D; }
    }

    /** Thrown when the format is writable but the text is not valid for it (e.g. a bad check digit). */
    public static final class InvalidContent extends Exception {
        InvalidContent(String m, Throwable c) { super(m, c); }
    }

    /**
     * @return the symbol, or null when the format cannot be recreated
     * @throws InvalidContent when the text does not fit the format
     */
    public static Code encode(String format, String text) throws InvalidContent {
        BarcodeFormat f = writerFormat(format);
        if (f == null) return null;
        if (text == null || text.isEmpty()) throw new InvalidContent("empty", null);
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 0);
        if (!is1D(f)) {
            boolean ascii = StandardCharsets.US_ASCII.newEncoder().canEncode(text);
            if (!ascii) {
                hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");   // with ECI
                if (f == BarcodeFormat.DATA_MATRIX) hints.put(EncodeHintType.DATA_MATRIX_COMPACT, Boolean.TRUE);   // the encoder that honours it
            }
            if (f == BarcodeFormat.AZTEC) hints.put(EncodeHintType.ERROR_CORRECTION, 33);
        }
        try {
            BitMatrix m = new MultiFormatWriter().encode(text, f, 0, 0, hints);
            if (f == BarcodeFormat.PDF_417) m = trim(m);
            return new Code(f, m);
        } catch (Exception | Error ex) {   // WriterException, IllegalArgumentException (bad digits / check digit / chars)
            throw new InvalidContent(ex.getMessage(), ex);
        }
    }

    /** drop all-white border rows/columns (PDF417Writer adds its own white space) */
    static BitMatrix trim(BitMatrix m) {
        int[] r = m.getEnclosingRectangle();
        if (r == null) return m;
        BitMatrix o = new BitMatrix(r[2], r[3]);
        for (int y = 0; y < r[3]; y++) for (int x = 0; x < r[2]; x++) if (m.get(r[0] + x, r[1] + y)) o.set(x, y);
        return o;
    }

    /** ARGB pixels, 1 px per module, quiet zone included (black on white); 1D codes are one row. */
    public static int[] pixels(Code c) {
        int q = c.quiet(), w = c.matrix.getWidth() + 2 * q, h = c.oneD() ? 1 : c.matrix.getHeight() + 2 * q;
        int[] px = new int[w * h];
        java.util.Arrays.fill(px, 0xFFFFFFFF);
        int oy = c.oneD() ? 0 : q;
        int rows = c.oneD() ? 1 : c.matrix.getHeight();
        for (int y = 0; y < rows; y++) for (int x = 0; x < c.matrix.getWidth(); x++)
            if (c.matrix.get(x, c.oneD() ? 0 : y)) px[(y + oy) * w + x + q] = 0xFF000000;
        return px;
    }

    public static int pixelWidth(Code c) { return c.matrix.getWidth() + 2 * c.quiet(); }
    public static int pixelHeight(Code c) { return c.oneD() ? 1 : c.matrix.getHeight() + 2 * c.quiet(); }
}
