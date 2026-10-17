package com.zmlworks.qrreader;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;
import com.google.zxing.qrcode.QRCodeReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fallback decoder (pure Java ZXing core) for devices where the zxing-cpp native library is not available. */
final class JavaEngine implements QrEngine {
    private final Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
    private final QRCodeMultiReader multi = new QRCodeMultiReader();
    private final QRCodeReader single = new QRCodeReader();

    JavaEngine() {
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    }

    @Override public String name() { return "ZXing"; }

    @Override public List<Code> decode(byte[] y, int w, int h) {
        BinaryBitmap bb = new BinaryBitmap(new HybridBinarizer(new PlanarYUVLuminanceSource(y, w, h, 0, 0, w, h, false)));
        Result[] rs;
        try {
            rs = multi.decodeMultiple(bb, hints);
        } catch (NotFoundException e) {
            try { rs = new Result[]{single.decode(bb, hints)}; } catch (Exception e2) { return Collections.emptyList(); }
        } catch (RuntimeException e) {
            return Collections.emptyList();
        } finally {
            single.reset();
        }
        List<Code> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Result r : rs) {
            if (r == null || r.getText() == null || r.getText().isEmpty() || !seen.add(r.getText())) continue;
            ResultPoint[] p = r.getResultPoints();
            float[] c = null;
            if (p != null && p.length >= 3) {
                // bottom-left, top-left, top-right finder centres; estimate the fourth corner
                float bx = p[0].getX(), by = p[0].getY(), tx = p[1].getX(), ty = p[1].getY(), rx = p[2].getX(), ry = p[2].getY();
                c = new float[]{tx, ty, rx, ry, rx + bx - tx, ry + by - ty, bx, by};
            }
            out.add(new Code(r.getText(), c));
        }
        return out;
    }
}
