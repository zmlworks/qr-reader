package com.zmlworks.qrreader;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import zxingcpp.BarcodeReader;

/**
 * zxing-cpp (native, Apache-2.0; AAR io.github.zxing-cpp:android) on the camera's Y plane — the decoding path of
 * QR transfer 0.4.0. The Y bytes are copied into an {@code ALPHA_8} bitmap, which the library reads as luminance.
 * Options: QR only, up to 255 symbols per frame, tryHarder, no rotate/invert/downscale retries.
 * <p>{@link #create()} returns null if the native library cannot be loaded (an ABI that is not packaged); the
 * scanner then uses {@link JavaEngine}.
 */
final class CppEngine implements QrEngine {
    private final BarcodeReader reader;
    private Bitmap bmp, bmpUp;
    private final Rect rect = new Rect();
    private byte[] rowCopy;

    private CppEngine(BarcodeReader r) { reader = r; }

    static CppEngine create() { return create(false); }

    /** @param image a still picture (gallery): also try rotated, inverted and downscaled */
    static CppEngine create(boolean image) {
        try {
            BarcodeReader.Options o = new BarcodeReader.Options();
            Set<BarcodeReader.Format> f = EnumSet.of(BarcodeReader.Format.QR_CODE);
            o.setFormats(f);
            o.setTryHarder(true);
            o.setTryRotate(image);
            o.setTryInvert(image);
            o.setTryDownscale(image);
            o.setMaxNumberOfSymbols(255);
            o.setBinarizer(BarcodeReader.Binarizer.LOCAL_AVERAGE);
            return new CppEngine(new BarcodeReader(o));
        } catch (Throwable t) {   // UnsatisfiedLinkError, NoClassDefFoundError, ExceptionInInitializerError
            return null;
        }
    }

    @Override public String name() { return "zxing-cpp"; }

    @Override public List<Code> decode(byte[] y, int w, int h) {
        // two cached bitmaps: the camera size and the 2× upscale used by DecodePipeline
        Bitmap b;
        if (bmp != null && bmp.getWidth() == w && bmp.getHeight() == h) b = bmp;
        else if (bmpUp != null && bmpUp.getWidth() == w && bmpUp.getHeight() == h) b = bmpUp;
        else {
            b = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
            if (bmp == null) bmp = b;
            else { if (bmpUp != null) bmpUp.recycle(); bmpUp = b; }
        }
        int rb = b.getRowBytes();
        if (rb == w) b.copyPixelsFromBuffer(ByteBuffer.wrap(y, 0, w * h));
        else {   // padded rows (not seen for ALPHA_8 in practice)
            if (rowCopy == null || rowCopy.length < rb * h) rowCopy = new byte[rb * h];
            for (int r = 0; r < h; r++) System.arraycopy(y, r * w, rowCopy, r * rb, w);
            b.copyPixelsFromBuffer(ByteBuffer.wrap(rowCopy, 0, rb * h));
        }
        rect.set(0, 0, w, h);
        List<BarcodeReader.Result> rs;
        try {
            rs = reader.read(b, rect, 0);
        } catch (RuntimeException e) {
            return Collections.emptyList();   // never let one bad frame kill the camera thread
        }
        if (rs == null || rs.isEmpty()) return Collections.emptyList();
        List<Code> out = new ArrayList<>(rs.size());
        Set<String> seen = new HashSet<>();
        for (BarcodeReader.Result r : rs) {
            String t = r.getText();
            if (t == null || t.isEmpty() || r.getError() != null || !seen.add(t)) continue;
            float[] c = null;
            BarcodeReader.Position p = r.getPosition();
            if (p != null) c = new float[]{x(p.getTopLeft()), yy(p.getTopLeft()), x(p.getTopRight()), yy(p.getTopRight()),
                    x(p.getBottomRight()), yy(p.getBottomRight()), x(p.getBottomLeft()), yy(p.getBottomLeft())};
            out.add(new Code(t, c));
        }
        return out;
    }

    private static float x(Point p) { return p == null ? 0 : p.x; }
    private static float yy(Point p) { return p == null ? 0 : p.y; }
}
