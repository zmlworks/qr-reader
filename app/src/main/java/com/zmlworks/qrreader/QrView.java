package com.zmlworks.qrreader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/**
 * Shows a QR symbol at the largest integer number of pixels per module (crisp, no filtering).
 * When empty, draws only the three finder patterns (top-left, top-right, bottom-left) as a placeholder.
 */
public final class QrView extends View {
    /** Placeholder grid size (version-1 style) including quiet zone on each side conceptually via margins. */
    private static final int PLACEHOLDER_MODULES = 21;
    private static final int FINDER = 7;

    private Bitmap bmp;
    private boolean finders;
    private final Paint paint = new Paint();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect(), dst = new Rect();

    public QrView(Context c, AttributeSet a) {
        super(c, a);
        paint.setFilterBitmap(false);
        paint.setAntiAlias(false);
        paint.setDither(false);
        fill.setStyle(Paint.Style.FILL);
    }

    void setQr(QrGen.Qr q) {
        if (bmp != null) bmp.recycle();
        bmp = null;
        finders = false;
        if (q != null) {
            int n = q.size + 2 * QrGen.QUIET;
            bmp = Bitmap.createBitmap(QrGen.pixels(q, 1), n, n, Bitmap.Config.ARGB_8888);
        }
        invalidate();
    }

    /** Empty Create field: three characteristic finder squares, no data modules. */
    void setFindersPlaceholder() {
        if (bmp != null) { bmp.recycle(); bmp = null; }
        finders = true;
        invalidate();
    }

    boolean hasQr() { return bmp != null; }
    boolean showingFinders() { return finders && bmp == null; }

    @Override protected void onDraw(Canvas c) {
        int avail = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(), getHeight() - getPaddingTop() - getPaddingBottom());
        if (avail <= 0) return;
        if (bmp != null) {
            int n = bmp.getWidth();
            int scale = Math.max(1, avail / n);
            int side = n * scale;
            int l = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight() - side) / 2;
            int t = getPaddingTop();
            src.set(0, 0, n, n);
            dst.set(l, t, l + side, t + side);
            c.drawBitmap(bmp, src, dst, paint);
            return;
        }
        if (!finders) return;
        int scale = Math.max(1, avail / PLACEHOLDER_MODULES);
        int side = PLACEHOLDER_MODULES * scale;
        int l = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight() - side) / 2;
        int t = getPaddingTop();
        // quiet-ish inset: finders at the usual module positions of a version-1 symbol
        drawFinder(c, l, t, scale, 0, 0);
        drawFinder(c, l, t, scale, PLACEHOLDER_MODULES - FINDER, 0);
        drawFinder(c, l, t, scale, 0, PLACEHOLDER_MODULES - FINDER);
    }

    /** Classic 7×7 finder: black outer, white ring, black 3×3 eye. */
    private void drawFinder(Canvas c, int ox, int oy, int scale, int mx, int my) {
        int x = ox + mx * scale, y = oy + my * scale;
        fill.setColor(0xFF1B1A20);
        c.drawRect(x, y, x + FINDER * scale, y + FINDER * scale, fill);
        fill.setColor(0xFFFFFFFF);
        c.drawRect(x + scale, y + scale, x + (FINDER - 1) * scale, y + (FINDER - 1) * scale, fill);
        fill.setColor(0xFF1B1A20);
        c.drawRect(x + 2 * scale, y + 2 * scale, x + 5 * scale, y + 5 * scale, fill);
    }
}
