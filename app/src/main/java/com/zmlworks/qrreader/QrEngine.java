package com.zmlworks.qrreader;

import java.util.List;

/** One QR decoding engine: every QR code found in a grey-scale (luminance) frame. */
public interface QrEngine {
    /** A decoded code: its text and its four corners in frame pixels (x0,y0 … x3,y3; may be null). */
    final class Code {
        public final String text;
        public final float[] corners;
        public Code(String text, float[] corners) { this.text = text; this.corners = corners; }
    }

    /** @param y luminance, row-major w×h (one byte per pixel) */
    List<Code> decode(byte[] y, int w, int h);
    /** short name for logs */
    String name();
}
