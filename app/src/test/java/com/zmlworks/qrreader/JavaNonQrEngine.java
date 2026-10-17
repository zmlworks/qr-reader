package com.zmlworks.qrreader;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Tests only: every format Java ZXing reads except QR, standing in for zxing-cpp's non-QR set (the native library
 * does not load on the JVM), so the format plumbing of "Read from image" can be checked.
 */
final class JavaNonQrEngine implements QrEngine {
    @Override public String name() { return "ZXing (non-QR, tests)"; }

    @Override public List<Code> decode(byte[] y, int w, int h) {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        java.util.EnumSet<com.google.zxing.BarcodeFormat> f = java.util.EnumSet.allOf(com.google.zxing.BarcodeFormat.class);
        f.remove(com.google.zxing.BarcodeFormat.QR_CODE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, f);
        MultiFormatReader r = new MultiFormatReader();
        r.setHints(hints);
        try {
            return JavaEngine.codes(new GenericMultipleBarcodeReader(r).decodeMultiple(
                    new BinaryBitmap(new HybridBinarizer(new PlanarYUVLuminanceSource(y, w, h, 0, 0, w, h, false))), hints));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
