package com.zmlworks.qrreader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Draws outlines of the codes seen in the last frame (fading out) and the tap-to-focus ring. */
public final class OverlayView extends View {
    static final class Shape {
        final float[] pts; final boolean fresh;
        Shape(float[] p, boolean fresh) { pts = p; this.fresh = fresh; }
    }

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private List<Shape> shapes = new ArrayList<>();
    private long shapesAt, focusAt = -10_000;
    private float fx, fy;
    private final float dp;
    static final long FADE_MS = 450, FOCUS_MS = 900;

    public OverlayView(Context c, AttributeSet a) {
        super(c, a);
        dp = getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(3 * dp);
        line.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2 * dp);
        ring.setColor(0xFFFFFFFF);
    }

    /** @param s outlines in view coordinates (8 floats each) */
    void setShapes(List<Shape> s) {
        shapes = s;
        shapesAt = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    void showFocus(float x, float y) {
        fx = x; fy = y; focusAt = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    @Override public boolean performClick() { return super.performClick(); }

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        boolean again = false;
        long age = now - shapesAt;
        if (!shapes.isEmpty() && age < FADE_MS) {
            float k = age < 150 ? 1 : 1 - (age - 150) / (float) (FADE_MS - 150);
            for (Shape s : shapes) {
                path.reset();
                path.moveTo(s.pts[0], s.pts[1]);
                for (int i = 2; i < 8; i += 2) path.lineTo(s.pts[i], s.pts[i + 1]);
                path.close();
                int col = s.fresh ? 0xFFFFD54F : ZoomBarView.ACCENT;
                fill.setColor(col); fill.setAlpha((int) (60 * k));
                line.setColor(col); line.setAlpha((int) (255 * k));
                c.drawPath(path, fill);
                c.drawPath(path, line);
            }
            again = true;
        }
        long fa = now - focusAt;
        if (fa < FOCUS_MS) {
            float k = fa / (float) FOCUS_MS;
            ring.setAlpha((int) (255 * (1 - k)));
            c.drawCircle(fx, fy, (36 - 10 * Math.min(1, k * 3)) * dp, ring);
            again = true;
        }
        if (again) postInvalidateOnAnimation();
    }
}
