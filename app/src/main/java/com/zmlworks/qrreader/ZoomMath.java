package com.zmlworks.qrreader;

/** Swipe → zoom mapping (pure Java, unit-tested): zoom changes exponentially with the vertical swipe distance. */
public final class ZoomMath {
    private ZoomMath() {}

    /** a swipe of this fraction of the screen height doubles (up) or halves (down) the zoom */
    public static final float DOUBLING_FRACTION = 0.25f;

    public static float zoomFor(float startZoom, float dy, float screenH, float min, float max) {
        float z = (float) (startZoom * Math.pow(2, -dy / (screenH * DOUBLING_FRACTION)));
        return Math.max(min, Math.min(max, z));
    }

    /** 0 at min zoom … 1 at max zoom on a log scale (for the indicator). */
    public static float position(float zoom, float min, float max) {
        if (max <= min * 1.0001f) return 0;
        float p = (float) (Math.log(zoom / min) / Math.log(max / min));
        return Math.max(0, Math.min(1, p));
    }
}
