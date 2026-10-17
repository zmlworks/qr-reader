package com.zmlworks.qrreader;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ListView;

/**
 * ListView whose rows can be swiped sideways: right = {@link Callback#onSwipeRight} (delete), left =
 * {@link Callback#onSwipeLeft} (copy). A row needs a child {@code R.id.row_content} that moves, and may have
 * {@code R.id.row_bg_right} / {@code R.id.row_bg_left} (shown behind it while swiping right / left).
 */
public final class SwipeListView extends ListView {
    public interface Callback {
        void onSwipeRight(int position);
        void onSwipeLeft(int position);
    }

    private Callback callback;
    private final int slop, minFling;
    private float downX, downY;
    private int downPos = INVALID_POSITION;
    private View row, content;
    private boolean swiping;
    private VelocityTracker vt;

    public SwipeListView(Context c, AttributeSet a) {
        super(c, a);
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity() * 6;
    }

    public void setCallback(Callback c) { callback = c; }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY(); swiping = false;
                downPos = pointToPosition((int) downX, (int) downY);
                row = downPos == INVALID_POSITION ? null : getChildAt(downPos - getFirstVisiblePosition());
                content = row == null ? null : row.findViewById(R.id.row_content);
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                break;
            case MotionEvent.ACTION_MOVE:
                if (vt != null) vt.addMovement(e);
                if (content != null && startSwipe(e)) return true;
                break;
            default: break;
        }
        return super.onInterceptTouchEvent(e);
    }

    private boolean startSwipe(MotionEvent e) {
        float dx = e.getX() - downX, dy = e.getY() - downY;
        if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
            swiping = true;
            downX = e.getX();   // no jump by the slop
            getParent().requestDisallowInterceptTouchEvent(true);
            row.setPressed(false);
            return true;
        }
        return false;
    }

    @Override public boolean performClick() { return super.performClick(); }

    @SuppressLint("ClickableViewAccessibility")   // taps still reach ListView (item click); only sideways drags are taken
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (vt != null) vt.addMovement(e);
        if (!swiping && content != null && e.getActionMasked() == MotionEvent.ACTION_MOVE && startSwipe(e)) {
            MotionEvent c = MotionEvent.obtain(e);
            c.setAction(MotionEvent.ACTION_CANCEL);
            super.onTouchEvent(c);
            c.recycle();
        }
        if (!swiping) return super.onTouchEvent(e);
        float dx = e.getX() - downX;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                content.setTranslationX(dx);
                show(row, dx);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                swiping = false;
                float v = 0;
                if (vt != null) { vt.computeCurrentVelocity(1000); v = vt.getXVelocity(); }
                int w = getWidth();
                boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
                boolean right = up && (dx > w * 0.35f || (dx > slop * 2 && v > minFling));
                boolean left = up && (dx < -w * 0.35f || (dx < -slop * 2 && v < -minFling));
                final View r = row, ct = content;
                final int pos = downPos;
                if (right) {
                    ct.animate().translationX(w).setDuration(140).setListener(new AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(Animator a) {
                            ct.animate().setListener(null);
                            if (callback != null) callback.onSwipeRight(pos);
                            reset(r);
                        }
                    }).start();
                } else {
                    if (left && callback != null) callback.onSwipeLeft(pos);
                    ct.animate().translationX(0).setDuration(160).setListener(new AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(Animator a) { ct.animate().setListener(null); reset(r); }
                    }).start();
                }
                return true;
            }
            default:
                return true;
        }
    }

    private static void show(View row, float dx) {
        View r = row.findViewById(R.id.row_bg_right), l = row.findViewById(R.id.row_bg_left);
        if (r != null) r.setVisibility(dx > 0 ? VISIBLE : INVISIBLE);
        if (l != null) l.setVisibility(dx < 0 ? VISIBLE : INVISIBLE);
    }

    /** Puts a (recycled) row back to rest. */
    static void reset(View row) {
        if (row == null) return;
        View c = row.findViewById(R.id.row_content);
        if (c != null) { c.animate().cancel(); c.setTranslationX(0); }
        show(row, 0);
    }
}
