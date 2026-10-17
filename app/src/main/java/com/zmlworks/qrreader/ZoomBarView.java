package com.zmlworks.qrreader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/**
 * Zoom indicator: a vertical track with "+" at the top and "−" at the bottom, chevrons hinting "swipe up / down",
 * and a knob showing the current zoom (e.g. "2.4×"). Highlighted while the user is swiping. Display only: the
 * swipe itself is handled anywhere on the preview.
 */
public final class ZoomBarView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), knob = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG), sign = new Paint(Paint.ANTI_ALIAS_FLAG), chev = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private float zoom = 1, min = 1, max = 1;
    private boolean active;
    private final RectF r = new RectF();
    private final Path path = new Path();
    static final int ACCENT = 0xFF5BE37D;

    public ZoomBarView(Context c, AttributeSet a) {
        super(c, a);
        dp = getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setStrokeWidth(4 * dp);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(12.5f * dp);
        text.setFakeBoldText(true);
        sign.setTextAlign(Paint.Align.CENTER);
        sign.setTextSize(22 * dp);
        sign.setFakeBoldText(true);
        chev.setStyle(Paint.Style.STROKE);
        chev.setStrokeWidth(2.5f * dp);
        chev.setStrokeCap(Paint.Cap.ROUND);
        chev.setStrokeJoin(Paint.Join.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        updateDescription();
    }

    void setRange(float min, float max) { this.min = min; this.max = Math.max(min, max); invalidate(); }

    void setZoom(float z) { zoom = z; updateDescription(); invalidate(); }

    void setActive(boolean a) { if (active != a) { active = a; invalidate(); } }

    private void updateDescription() {
        setContentDescription(getResources().getString(R.string.zoom_desc, String.format(Locale.US, "%.1f", zoom)));
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), cx = w / 2;
        int base = active ? ACCENT : 0xFFFFFFFF;
        int a = active ? 255 : 170;
        float top = 40 * dp, bottom = h - 40 * dp;
        // shadow-ish backdrop for contrast on bright scenes
        knob.setStyle(Paint.Style.FILL);
        knob.setColor(active ? 0x66000000 : 0x40000000);
        r.set(cx - 22 * dp, 4 * dp, cx + 22 * dp, h - 4 * dp);
        c.drawRoundRect(r, 22 * dp, 22 * dp, knob);
        sign.setColor(base); sign.setAlpha(a);
        c.drawText("+", cx, top - 12 * dp, sign);
        c.drawText("\u2212", cx, bottom + 28 * dp, sign);
        track.setColor(base); track.setAlpha(active ? 200 : 110);
        c.drawLine(cx, top, cx, bottom, track);
        float p = ZoomMath.position(zoom, min, max);
        float ky = bottom - p * (bottom - top);
        // chevrons: up above the knob, down below it
        chev.setColor(base); chev.setAlpha(active ? 230 : 140);
        float s = 6 * dp;
        if (ky - 38 * dp > top) {
            float y = ky - 30 * dp;
            path.reset(); path.moveTo(cx - s, y + s / 2); path.lineTo(cx, y - s / 2); path.lineTo(cx + s, y + s / 2);
            c.drawPath(path, chev);
        }
        if (ky + 38 * dp < bottom) {
            float y = ky + 30 * dp;
            path.reset(); path.moveTo(cx - s, y - s / 2); path.lineTo(cx, y + s / 2); path.lineTo(cx + s, y - s / 2);
            c.drawPath(path, chev);
        }
        knob.setColor(active ? ACCENT : 0xF2FFFFFF);
        r.set(cx - 21 * dp, ky - 13 * dp, cx + 21 * dp, ky + 13 * dp);
        c.drawRoundRect(r, 13 * dp, 13 * dp, knob);
        text.setColor(0xFF111111);
        String label = zoom >= 9.95f ? String.format(Locale.US, "%.0f\u00d7", zoom) : String.format(Locale.US, "%.1f\u00d7", zoom);
        c.drawText(label, cx, ky + 4.5f * dp, text);
    }
}
