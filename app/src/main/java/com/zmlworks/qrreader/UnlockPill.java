package com.zmlworks.qrreader;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

/**
 * "Unlocked · 52s" in a top bar while the authentication window is open (see {@link Auth}); hidden otherwise.
 * A tap locks at once. android:tag="compact" shows only the open lock and the seconds (the crowded detail bar).
 */
public final class UnlockPill extends TextView {
    private final Runnable tick = this::render;
    private final Runnable onAuth = this::render;

    public UnlockPill(Context c) { this(c, null); }

    public UnlockPill(Context c, AttributeSet a) {
        super(c, a);
        setOnClickListener(v -> Auth.lockNow());
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Auth.addListener(onAuth);
        render();
    }

    @Override protected void onDetachedFromWindow() {
        Auth.removeListener(onAuth);
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        if (v == View.VISIBLE) render(); else removeCallbacks(tick);
    }

    private boolean allowed = true;

    /** false: never shown (e.g. the shared top bar on the Settings tab) */
    void setAllowed(boolean on) { allowed = on; render(); }

    void render() {
        removeCallbacks(tick);
        long left = Auth.remainingMs();
        if (left <= 0 || !allowed) { setVisibility(View.GONE); return; }
        int s = (int) ((left + 999) / 1000);
        boolean compact = "compact".equals(getTag());
        setText(getContext().getString(compact ? R.string.unlocked_pill_short : R.string.unlocked_pill, s));
        setContentDescription(getResources().getQuantityString(R.plurals.unlocked_pill_cd, s, s));
        setVisibility(View.VISIBLE);
        postDelayed(tick, Math.max(50, left - (s - 1) * 1000L));   // next whole second
    }
}
