package com.zmlworks.qrreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

/** ScanLogic (single read once, multi collect), ZoomMath and DecodePipeline's upscale retry. */
public class LogicTest {
    static QrEngine.Code code(String t, float cx, float cy) {
        return new QrEngine.Code(t, new float[]{cx - 10, cy - 10, cx + 10, cy - 10, cx + 10, cy + 10, cx - 10, cy + 10});
    }

    @Test public void singleReadsOnceUntilOutOfView() {
        ScanLogic l = new ScanLogic();
        assertEquals("A", l.single(List.of(code("A", 50, 50)), 100, 100, 0));
        assertNull(l.single(List.of(code("A", 50, 50)), 100, 100, 100));          // still in view: ignored
        assertEquals("A", l.suppressed(100));
        assertNull(l.single(List.of(code("A", 50, 50)), 100, 100, 1500));
        l.touch(5000);                                                              // came back from the detail page
        assertNull(l.single(List.of(code("A", 50, 50)), 100, 100, 5100));
        assertNull(l.single(Collections.emptyList(), 100, 100, 5200));
        assertEquals("A", l.single(List.of(code("A", 50, 50)), 100, 100, 5100 + ScanLogic.FORGET_MS + 1));   // gone, then back
    }

    @Test public void singlePrefersTheCentreAndOtherCodes() {
        ScanLogic l = new ScanLogic();
        assertEquals("C", l.single(List.of(code("E", 5, 5), code("C", 52, 48)), 100, 100, 0));
        assertEquals("E", l.single(List.of(code("E", 5, 5), code("C", 52, 48)), 100, 100, 10));   // C suppressed, E is new
    }

    @Test public void multiCollectsDistinct() {
        ScanLogic l = new ScanLogic();
        assertTrue(l.collect("1"));
        assertTrue(l.collect("2"));
        assertFalse(l.collect("1"));
        assertEquals(2, l.collectedCount());
        l.resetCollection();
        assertEquals(0, l.collectedCount());
        assertTrue(l.collect("1"));
    }

    @Test public void zoomIsExponentialInSwipeDistance() {
        float H = 2000;
        assertEquals(2f, ZoomMath.zoomFor(1, -H * ZoomMath.DOUBLING_FRACTION, H, 1, 8), 1e-4);   // up = in
        assertEquals(4f, ZoomMath.zoomFor(2, -H * ZoomMath.DOUBLING_FRACTION, H, 1, 8), 1e-4);
        assertEquals(1f, ZoomMath.zoomFor(2, H * ZoomMath.DOUBLING_FRACTION, H, 1, 8), 1e-4);    // down = out
        assertEquals(8f, ZoomMath.zoomFor(1, -H * 5, H, 1, 8), 1e-4);                             // clamped
        assertEquals(1f, ZoomMath.zoomFor(3, H * 5, H, 1, 8), 1e-4);
        float a = ZoomMath.zoomFor(1, -100, H, 1, 8), b = ZoomMath.zoomFor(1, -101, H, 1, 8);
        assertTrue(b > a && b - a < 0.01f);                                                        // smooth
        assertEquals(0f, ZoomMath.position(1, 1, 8), 1e-6);
        assertEquals(1f, ZoomMath.position(8, 1, 8), 1e-6);
        assertEquals(0.5f, ZoomMath.position((float) Math.sqrt(8), 1, 8), 1e-4);
        assertEquals(0f, ZoomMath.position(1, 1, 1), 1e-6);
    }

    @Test public void pipelineUpscalesOnlyWhenNothingFoundAndRateLimited() {
        final int[] calls = {0};
        QrEngine fake = new QrEngine() {   // finds a code only in frames ≥ 200 px wide (like a code with < 3 px/module)
            public List<Code> decode(byte[] y, int w, int h) {
                calls[0]++;
                return w >= 200 ? List.of(new Code("x", new float[]{20, 20, 40, 20, 40, 40, 20, 40})) : Collections.emptyList();
            }
            public String name() { return "fake"; }
        };
        DecodePipeline p = new DecodePipeline(fake, true);
        byte[] y = new byte[100 * 60];
        List<QrEngine.Code> r = p.decode(y, 100, 60, 0);
        assertEquals(1, r.size());
        assertEquals(10f, r.get(0).corners[0], 1e-6);    // corners mapped back from the 2× image
        assertEquals(2, calls[0]);
        assertEquals(0, p.decode(y, 100, 60, 100).size());   // rate-limited
        assertEquals(3, calls[0]);
        assertEquals(1, p.decode(y, 100, 60, DecodePipeline.UPSCALE_MIN_INTERVAL_MS).size());
    }
}
