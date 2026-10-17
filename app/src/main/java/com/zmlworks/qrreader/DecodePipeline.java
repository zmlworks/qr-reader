package com.zmlworks.qrreader;

import java.util.List;

/**
 * Decode step (pure Java, testable on the JVM; same as QR transfer 0.4.0): engine on the camera frame; if it found
 * nothing, once more on a 2× bicubic upscale ({@link Upscale2x}) — for codes below ~3 camera pixels per module — at
 * most every {@link #UPSCALE_MIN_INTERVAL_MS}. Corners found on the upscale are mapped back to frame pixels.
 */
public final class DecodePipeline {
    public static final long UPSCALE_MIN_INTERVAL_MS = 400;
    /** frames larger than this (pixels) are not upscaled (memory) */
    public static final int UPSCALE_MAX_PIXELS = 1920 * 1088;

    private final QrEngine engine;
    private final boolean upscale;
    private long lastUpscaleAt = Long.MIN_VALUE / 2;
    private byte[] up;
    private int[] tmp;
    private volatile double avgMs = -1;

    public DecodePipeline(QrEngine engine, boolean upscale) { this.engine = engine; this.upscale = upscale; }

    public QrEngine engine() { return engine; }

    public List<QrEngine.Code> decode(byte[] y, int w, int h, long nowMs) {
        long t0 = System.nanoTime();
        List<QrEngine.Code> r = engine.decode(y, w, h);
        double ms = (System.nanoTime() - t0) / 1e6;
        avgMs = avgMs < 0 ? ms : avgMs * 0.9 + ms * 0.1;
        if (r.isEmpty() && upscale && (long) w * h <= UPSCALE_MAX_PIXELS && nowMs - lastUpscaleAt >= UPSCALE_MIN_INTERVAL_MS) {
            lastUpscaleAt = nowMs;
            if (tmp == null || tmp.length < 2 * w * h) tmp = new int[2 * w * h];
            up = Upscale2x.run(y, w, h, up, tmp);
            r = engine.decode(up, 2 * w, 2 * h);
            for (QrEngine.Code c : r) if (c.corners != null) for (int i = 0; i < c.corners.length; i++) c.corners[i] *= 0.5f;
        }
        return r;
    }

    /** smoothed decode time per frame (ms), -1 before the first frame */
    public double avgMs() { return avgMs; }
}
