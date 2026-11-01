package com.zmlworks.qrreader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/**
 * A recreated barcode, crisp (integer pixels per module, no filtering), as wide as the view allows. 1D codes are drawn
 * with bars {@link #BAR_RATIO} of the symbol width high; 2D codes keep their aspect ratio. Quiet zones are part of the bitmap.
 */
public final class CodeView extends View {
    static final float BAR_RATIO = 0.42f;
    private Bitmap bmp;
    private boolean oneD;
    private final Paint paint = new Paint();
    private final Rect src = new Rect(), dst = new Rect();

    public CodeView(Context c, AttributeSet a) {
        super(c, a);
        paint.setFilterBitmap(false);
        paint.setAntiAlias(false);
    }

    void setCode(Recreate.Code code) {
        bmp = code == null ? null : Bitmap.createBitmap(Recreate.pixels(code), Recreate.pixelWidth(code), Recreate.pixelHeight(code), Bitmap.Config.ARGB_8888);
        oneD = code != null && code.oneD();
        requestLayout();
        invalidate();
    }

    boolean hasCode() { return bmp != null; }

    private int scale(int avail) { return bmp == null ? 0 : Math.max(1, avail / bmp.getWidth()); }

    @Override protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws);
        int avail = w - getPaddingLeft() - getPaddingRight();
        int h = 0;
        if (bmp != null) {
            int side = bmp.getWidth() * scale(avail);
            h = oneD ? Math.round(side * BAR_RATIO) : bmp.getHeight() * scale(avail);
            if (!oneD && MeasureSpec.getMode(hs) == MeasureSpec.AT_MOST) h = Math.min(h, MeasureSpec.getSize(hs));
        }
        setMeasuredDimension(w, h + getPaddingTop() + getPaddingBottom());
    }

    @Override protected void onDraw(Canvas c) {
        if (bmp == null) return;
        int avail = getWidth() - getPaddingLeft() - getPaddingRight();
        int s = scale(avail);
        int w = bmp.getWidth() * s;
        int h = oneD ? getHeight() - getPaddingTop() - getPaddingBottom() : bmp.getHeight() * s;
        int l = getPaddingLeft() + (avail - w) / 2, t = getPaddingTop();
        src.set(0, 0, bmp.getWidth(), bmp.getHeight());
        dst.set(l, t, l + w, t + h);
        c.drawBitmap(bmp, src, dst, paint);
    }
}
