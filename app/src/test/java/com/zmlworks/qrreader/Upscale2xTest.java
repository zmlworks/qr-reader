package com.zmlworks.qrreader;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Upscale2xTest {
    @Test public void flatStaysFlatAndSizeDoubles() {
        byte[] src = new byte[7 * 5];
        java.util.Arrays.fill(src, (byte) 200);
        byte[] out = Upscale2x.run(src, 7, 5, null, null);
        assertEquals(14 * 10, out.length);
        for (byte b : out) assertEquals(200, b & 255);
    }
}
