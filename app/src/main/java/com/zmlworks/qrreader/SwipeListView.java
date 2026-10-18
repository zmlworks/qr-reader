package com.zmlworks.qrreader;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
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
 * <p>
 * A swipe counts when the row is let go past {@link #SWIPE_FRACTION} of the width, or flung after at least
 * {@link #FLING_FRACTION} of it. With {@link #setDragCallback} set, a press on a visible {@code R.id.row_handle}
 * (and only there) drags the row up or down instead (manual order of the Favorites tab).
 */
public final class SwipeListView extends ListView {
    /** 0.1.0-0.6.0: 0.35 of the width, or a fling after 2 × touch slop */
    static final float SWIPE_FRACTION = 0.55f, FLING_FRACTION = 0.3f;

    public interface Callback {
        void onSwipeRight(int position);
        void onSwipeLeft(int position);
        /** false: the row springs back instead of sliding out (the delete waits for an authentication) */
        default boolean swipeRightSlidesOut(int position) { return true; }
    }

    public interface DragCallback {
        /** the dragged row passed over `to`: move it there (the list shows the new order at once) */
        void onDragMove(int from, int to);
        /** let go: keep the order */
        void onDrop(int position);
    }

    private Callback callback;
    private DragCallback dragCallback;
    private final int slop, minFling;
    private float downX, downY;
    private int downPos = INVALID_POSITION;
    private View row, content;
    private boolean swiping;
    private int dragPos = INVALID_POSITION;
    private VelocityTracker vt;
    private final Rect hit = new Rect();

    public SwipeListView(Context c, AttributeSet a) {
        super(c, a);
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity() * 6;
    }

    public void setCallback(Callback c) { callback = c; }

    /** null = no dragging (the handles are hidden by the adapter then) */
    public void setDragCallback(DragCallback c) { dragCallback = c; }

    /** the row being dragged, or INVALID_POSITION */
    public int draggingPosition() { return dragPos; }

    private boolean onHandle(View r, float x, float y) {
        if (dragCallback == null || r == null) return false;
        View hnd = r.findViewById(R.id.row_handle);
        if (hnd == null || hnd.getVisibility() != VISIBLE || !hnd.isShown()) return false;
        hnd.getDrawingRect(hit);
        offsetDescendantRectToMyCoords(hnd, hit);
        return hit.contains((int) x, (int) y);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY(); swiping = false; dragPos = INVALID_POSITION;
                downPos = pointToPosition((int) downX, (int) downY);
                row = downPos == INVALID_POSITION ? null : getChildAt(downPos - getFirstVisiblePosition());
                content = row == null ? null : row.findViewById(R.id.row_content);
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                if (onHandle(row, downX, downY)) {   // the handle: a drag, never a swipe or a scroll
                    dragPos = downPos;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    invalidateViews();
                    return true;
                }
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

    private boolean drag(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE: {
                int y = (int) e.getY(), edge = getHeight() / 10;
                if (y < edge && getFirstVisiblePosition() > 0) scrollListBy(-Math.max(4, (edge - y) / 3));
                else if (y > getHeight() - edge && getLastVisiblePosition() < getCount() - 1) scrollListBy(Math.max(4, (y - getHeight() + edge) / 3));
                int to = pointToPosition(getWidth() / 2, Math.max(0, Math.min(getHeight() - 1, y)));
                if (to != INVALID_POSITION && to != dragPos && to < getCount()) {
                    int from = dragPos;
                    dragPos = to;
                    dragCallback.onDragMove(from, to);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                int p = dragPos;
                dragPos = INVALID_POSITION;
                dragCallback.onDrop(p);
                invalidateViews();
                return true;
            }
            default:
                return true;
        }
    }

    @SuppressLint("ClickableViewAccessibility")   // taps still reach ListView (item click); only sideways drags are taken
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (dragPos != INVALID_POSITION && dragCallback != null) return drag(e);
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
                boolean right = up && isSwipe(dx, v, w, minFling);
                boolean left = up && isSwipe(-dx, -v, w, minFling);
                final View r = row, ct = content;
                final int pos = downPos;
                if (right && (callback == null || callback.swipeRightSlidesOut(pos))) {
                    ct.animate().translationX(w).setDuration(140).setListener(new AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(Animator a) {
                            ct.animate().setListener(null);
                            if (callback != null) callback.onSwipeRight(pos);
                            reset(r);
                        }
                    }).start();
                } else {
                    if (right && callback != null) callback.onSwipeRight(pos);   // e.g. asks for authentication first
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

    /** A row let go `dx` px towards a side (positive) at velocity `v` (px/s, positive = that side) on a list `w` px wide. */
    static boolean isSwipe(float dx, float v, int w, int minFling) {
        return dx > w * SWIPE_FRACTION || (dx > w * FLING_FRACTION && v > minFling);
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
