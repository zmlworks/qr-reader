package com.zmlworks.qrreader;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * Create tab layout: child 0 (the QR) is a square fixed at the top, as large as the width and the height left over
 * by the other children allow; the other children (input, info line) follow right under it. With the keyboard open
 * (adjustResize) the square simply shrinks, so the QR and the input both stay visible.
 */
public final class CreateLayout extends ViewGroup {
    private final int gap;

    public CreateLayout(Context c, AttributeSet a) {
        super(c, a);
        gap = Math.round(8 * c.getResources().getDisplayMetrics().density);
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        int iw = w - getPaddingLeft() - getPaddingRight(), ih = h - getPaddingTop() - getPaddingBottom();
        int rest = 0;
        for (int i = 1; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            c.measure(MeasureSpec.makeMeasureSpec(iw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.max(0, ih / 2), MeasureSpec.AT_MOST));
            rest += c.getMeasuredHeight() + gap;
        }
        int side = Math.max(0, Math.min(iw, ih - rest));
        if (getChildCount() > 0)
            getChildAt(0).measure(MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY));
        setMeasuredDimension(w, h);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int y = getPaddingTop(), iw = r - l - getPaddingLeft() - getPaddingRight();
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            int x = getPaddingLeft() + (iw - c.getMeasuredWidth()) / 2;
            c.layout(x, y, x + c.getMeasuredWidth(), y + c.getMeasuredHeight());
            y += c.getMeasuredHeight() + gap;
        }
    }
}
