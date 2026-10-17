package com.zmlworks.qrreader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/** Shows a QR symbol at the largest integer number of pixels per module (crisp, no filtering). */
public final class QrView extends View {
    private Bitmap bmp;
    private final Paint paint = new Paint();
    private final Rect src = new Rect(), dst = new Rect();

    public QrView(Context c, AttributeSet a) {
        super(c, a);
        paint.setFilterBitmap(false);
        paint.setAntiAlias(false);
        paint.setDither(false);
    }

    void setQr(QrGen.Qr q) {
        if (bmp != null) bmp.recycle();
        bmp = null;
        if (q != null) {
            int n = q.size + 2 * QrGen.QUIET;
            bmp = Bitmap.createBitmap(QrGen.pixels(q, 1), n, n, Bitmap.Config.ARGB_8888);
        }
        invalidate();
    }

    boolean hasQr() { return bmp != null; }

    @Override protected void onDraw(Canvas c) {
        if (bmp == null) return;
        int n = bmp.getWidth();
        int avail = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(), getHeight() - getPaddingTop() - getPaddingBottom());
        int scale = Math.max(1, avail / n);
        int side = n * scale;
        int l = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight() - side) / 2;
        int t = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom() - side) / 2;
        src.set(0, 0, n, n);
        dst.set(l, t, l + side, t + side);
        c.drawBitmap(bmp, src, dst, paint);
    }
}
