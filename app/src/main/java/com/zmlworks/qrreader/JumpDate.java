package com.zmlworks.qrreader;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** History "Jump to date" (pure Java, unit-tested): the row to scroll to for a day. */
public final class JumpDate {
    private JumpDate() {}

    /**
     * @param newestFirst item times (ms), newest first, as the list shows them
     * @return the first row on that day or, if none, the first row of the nearest earlier day; -1 if every row is later
     */
    public static int index(List<Long> newestFirst, LocalDate day, ZoneId zone) {
        for (int i = 0; i < newestFirst.size(); i++)
            if (!Instant.ofEpochMilli(newestFirst.get(i)).atZone(zone).toLocalDate().isAfter(day)) return i;
        return -1;
    }

    public static LocalDate dayOf(long t, ZoneId zone) { return Instant.ofEpochMilli(t).atZone(zone).toLocalDate(); }
}
