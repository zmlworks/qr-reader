package com.zmlworks.qrreader;

import java.util.List;

/** One decoding engine: every QR code (or, in non-QR mode, every other barcode) found in a grey-scale (luminance) frame. */
public interface QrEngine {
    /** A decoded code: its text, four corners in frame pixels (x0,y0 … x3,y3; may be null) and format label (null = QR). */
    final class Code {
        public final String text;
        public final float[] corners;
        public final String format;
        public Code(String text, float[] corners) { this(text, corners, null); }
        public Code(String text, float[] corners, String format) { this.text = text; this.corners = corners; this.format = format; }
    }

    /** @param y luminance, row-major w×h (one byte per pixel) */
    List<Code> decode(byte[] y, int w, int h);
    /** short name for logs */
    String name();

    /** reads nothing (non-QR mode where zxing-cpp is missing: the Java fallback reads QR only) */
    QrEngine NONE = new QrEngine() {
        @Override public List<Code> decode(byte[] y, int w, int h) { return java.util.Collections.emptyList(); }
        @Override public String name() { return "none"; }
    };
}
