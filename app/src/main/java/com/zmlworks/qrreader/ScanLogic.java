package com.zmlworks.qrreader;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides what the scanner does with the codes of each frame (pure Java, unit-tested).
 * <ul>
 * <li>Single mode: the code closest to the frame centre is "read" once. A scanning session runs from the camera
 * starting (onResume) to onPause. Codes read in the session are not read again in it; the code read last is carried
 * into the next session of the same visit (back from its detail page, the photo picker...) so it does not reopen at
 * once, until it has been out of view for {@link #FORGET_MS} (counted from the session's first frame).
 * {@link #reset()} (leaving the app, ×) forgets everything.</li>
 * <li>"Already read": {@link #suppressed} names a code only when a frame <i>of the current session</i> showed one of
 * those codes again, within the last {@link #PILL_MS}. Never from a timer, a resume, or the frame of the read itself.</li>
 * <li>Multi mode: every distinct text is collected once.</li>
 * </ul>
 */
public final class ScanLogic {
    public static final long FORGET_MS = 1500;
    /** the "Already read" pill stays this long after the repeat code was last in a frame */
    public static final long PILL_MS = 800;

    private final Set<String> readInSession = new LinkedHashSet<>();
    private final Set<String> collected = new LinkedHashSet<>();
    private String carry;            // the last read, carried into the next session of this visit
    private long carrySeenAt;        // frame time it was last in view (or the session's first frame)
    private long firstFrame = -1;    // -1 = no frame in this session yet
    private String repeat;           // a suppressed code seen again in a frame of this session
    private long repeatAt;

    /** A new scanning session (camera starting): keeps only the code read last, for the no-reopen guard. */
    public synchronized void startSession() {
        readInSession.clear();
        firstFrame = -1;
        repeat = null;
    }

    /** Forgets everything (the user left the app or closed the scanner). */
    public synchronized void reset() {
        startSession();
        carry = null;
    }

    /** Every camera frame of the session, also the empty ones (starts the session clock, expires the carried code). */
    public synchronized void frame(long now) {
        if (firstFrame < 0) { firstFrame = now; carrySeenAt = now; }
        if (carry != null && now - carrySeenAt > FORGET_MS) carry = null;
    }

    /** Single mode. @return the text to open now, or null (nothing new in view). */
    public synchronized String single(List<QrEngine.Code> codes, int w, int h, long now) {
        frame(now);
        QrEngine.Code best = null;
        double bestD = Double.MAX_VALUE;
        for (QrEngine.Code c : codes) {
            boolean isCarry = c.text.equals(carry);
            if (isCarry) carrySeenAt = now;
            if (isCarry || readInSession.contains(c.text)) { repeat = c.text; repeatAt = now; continue; }
            double d = 0;
            if (c.corners != null) {
                float cx = (c.corners[0] + c.corners[2] + c.corners[4] + c.corners[6]) / 4, cy = (c.corners[1] + c.corners[3] + c.corners[5] + c.corners[7]) / 4;
                d = Math.hypot(cx - w / 2.0, cy - h / 2.0);
            }
            if (d < bestD) { bestD = d; best = c; }
        }
        if (best == null) return null;
        readInSession.add(best.text);
        carry = best.text;
        carrySeenAt = now;
        return best.text;
    }

    /** Takes back a read that was not opened (the scanner paused before it could be). */
    public synchronized void unread(String text) {
        readInSession.remove(text);
        if (text.equals(carry)) carry = null;
    }

    /** The code to show as "Already read" now, or null (see the class comment). */
    public synchronized String suppressed(long now) { return repeat != null && now - repeatAt <= PILL_MS ? repeat : null; }

    /** Multi mode. @return true if the text is new in this session. */
    public synchronized boolean collect(String text) { return collected.add(text); }

    public synchronized int collectedCount() { return collected.size(); }
    public synchronized boolean isCollected(String text) { return collected.contains(text); }
    public synchronized void resetCollection() { collected.clear(); }
}
