package com.zmlworks.qrreader;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * "Slide to delete": drag the round thumb across the track to the far end to confirm; let go earlier and it springs
 * back. A tap does nothing. TalkBack users get a "Delete" accessibility action instead of the gesture.
 */
public final class SlideToConfirm extends View {
    public interface Listener { void onConfirmed(); }

    /** how far (fraction of the travel) the thumb must go */
    static final float CONFIRM_AT = 0.9f;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
            thumb = new Paint(Paint.ANTI_ALIAS_FLAG), label = new Paint(Paint.ANTI_ALIAS_FLAG), arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float pos;          // 0..1 along the travel
    private float downX, downPos;
    private boolean dragging, done;
    private CharSequence text = "";
    private Listener listener;
    private ValueAnimator back;

    public SlideToConfirm(Context c) {
        super(c);
        float d = getResources().getDisplayMetrics().density;
        int danger = c.getColor(R.color.danger);
        track.setColor(c.getColor(R.color.surface2));
        fill.setColor((danger & 0x00FFFFFF) | 0x40000000);
        thumb.setColor(danger);
        label.setColor(c.getColor(R.color.dim));
        label.setTextSize(15 * d);
        label.setTextAlign(Paint.Align.CENTER);
        arrow.setColor(0xFFFFFFFF);
        arrow.setStrokeWidth(2.5f * d);
        arrow.setStrokeCap(Paint.Cap.ROUND);
        arrow.setStyle(Paint.Style.STROKE);
        setMinimumHeight(Math.round(56 * d));
        setFocusable(true);
    }

    void setText(CharSequence t) { text = t; setContentDescription(t); invalidate(); }
    void setListener(Listener l) { listener = l; }
    private final Paint.FontMetrics fm = new Paint.FontMetrics();

    float positionForTest() { return pos; }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), w), resolveSize(getSuggestedMinimumHeight(), h));
    }

    private float thumbSize() { return getHeight() - getPaddingTop() - getPaddingBottom(); }
    private float travel() { return Math.max(1, getWidth() - getPaddingLeft() - getPaddingRight() - thumbSize()); }

    @Override protected void onDraw(Canvas c) {
        float h = thumbSize(), left = getPaddingLeft(), top = getPaddingTop(), right = getWidth() - getPaddingRight();
        r.set(left, top, right, top + h);
        c.drawRoundRect(r, h / 2, h / 2, track);
        float tx = left + pos * travel();
        r.set(left, top, tx + h, top + h);
        c.drawRoundRect(r, h / 2, h / 2, fill);
        label.getFontMetrics(fm);
        label.setAlpha(Math.round(255 * (1 - Math.min(1, pos * 1.6f))));
        c.drawText(text, 0, text.length(), (left + h + right) / 2, top + h / 2 - (fm.ascent + fm.descent) / 2, label);
        c.drawCircle(tx + h / 2, top + h / 2, h / 2, thumb);
        float cx = tx + h / 2, cy = top + h / 2, a = h * 0.14f;   // »
        for (int k = -1; k <= 1; k += 2) {
            float o = k * a * 0.7f;
            c.drawLine(cx + o - a / 2, cy - a, cx + o + a / 2, cy, arrow);
            c.drawLine(cx + o + a / 2, cy, cx + o - a / 2, cy + a, arrow);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (done || !isEnabled()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float tx = getPaddingLeft() + pos * travel();
                if (e.getX() < tx - thumbSize() * 0.25f || e.getX() > tx + thumbSize() * 1.25f) return false;   // start on the thumb
                if (back != null) back.cancel();
                dragging = true;
                downX = e.getX();
                downPos = pos;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return false;
                pos = Math.max(0, Math.min(1, downPos + (e.getX() - downX) / travel()));
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) return false;
                dragging = false;
                if (e.getActionMasked() == MotionEvent.ACTION_UP && pos >= CONFIRM_AT) performClick();
                else springBack();
                return true;
            default:
                return dragging;
        }
    }

    /** a click (the slide reaching the end, or an accessibility click) confirms; a plain tap never gets here */
    @Override public boolean performClick() {
        super.performClick();
        if (!done) confirm();
        return true;
    }

    private void confirm() {
        done = true;
        pos = 1;
        invalidate();
        if (listener != null) listener.onConfirmed();
    }

    private void springBack() {
        back = ValueAnimator.ofFloat(pos, 0).setDuration(180);
        back.addUpdateListener(a -> { pos = (float) a.getAnimatedValue(); invalidate(); });
        back.start();
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, text));
    }

    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        if (action == AccessibilityNodeInfo.ACTION_CLICK && !done) { confirm(); return true; }
        return super.performAccessibilityAction(action, args);
    }
}
