package com.zmlworks.qrreader;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides what the scanner does with the codes of each frame (pure Java, unit-tested).
 * <ul>
 * <li>Single mode: the code closest to the frame centre is "read" once; afterwards the same text is ignored until it
 * has been out of view for {@link #FORGET_MS} (so returning from its detail page does not reopen it).</li>
 * <li>Multi mode: every distinct text is collected once.</li>
 * </ul>
 */
public final class ScanLogic {
    public static final long FORGET_MS = 1500;

    private String last;
    private long lastSeenAt;
    private final Set<String> collected = new LinkedHashSet<>();

    /** Single mode. @return the text to open now, or null (nothing new in view). */
    public synchronized String single(List<QrEngine.Code> codes, int w, int h, long now) {
        if (last != null && now - lastSeenAt > FORGET_MS) last = null;
        QrEngine.Code best = null;
        double bestD = Double.MAX_VALUE;
        for (QrEngine.Code c : codes) {
            if (c.text.equals(last)) { lastSeenAt = now; continue; }
            double d = 0;
            if (c.corners != null) {
                float cx = (c.corners[0] + c.corners[2] + c.corners[4] + c.corners[6]) / 4, cy = (c.corners[1] + c.corners[3] + c.corners[5] + c.corners[7]) / 4;
                d = Math.hypot(cx - w / 2.0, cy - h / 2.0);
            }
            if (d < bestD) { bestD = d; best = c; }
        }
        if (best == null) return null;
        last = best.text;
        lastSeenAt = now;
        return best.text;
    }

    /** Restarts the suppression window of the last read (call when scanning resumes). */
    public synchronized void touch(long now) { if (last != null) lastSeenAt = now; }

    /** The text that is currently suppressed in single mode (still in view), or null. */
    public synchronized String suppressed(long now) { return last != null && now - lastSeenAt <= FORGET_MS ? last : null; }

    /** Multi mode. @return true if the text is new in this session. */
    public synchronized boolean collect(String text) { return collected.add(text); }

    public synchronized int collectedCount() { return collected.size(); }
    public synchronized boolean isCollected(String text) { return collected.contains(text); }
    public synchronized void resetCollection() { collected.clear(); }
}
