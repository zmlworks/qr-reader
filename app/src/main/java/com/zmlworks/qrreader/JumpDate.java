package com.zmlworks.qrreader;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** History "Jump to date" (pure Java, unit-tested): parse ddmmyyyy / dd/mm/yyyy, find the row to scroll to. */
public final class JumpDate {
    private JumpDate() {}

    /**
     * "17102026", "17/10/2026", "7/1/2026"; also with '.', '-' or spaces as separators. Day first, 4-digit year.
     * @return null if it is not a real date
     */
    public static LocalDate parse(String s) {
        if (s == null) return null;
        String t = s.trim();
        int d, m, y;
        try {
            if (t.matches("\\d{8}")) {
                d = Integer.parseInt(t.substring(0, 2));
                m = Integer.parseInt(t.substring(2, 4));
                y = Integer.parseInt(t.substring(4));
            } else if (t.matches("\\d{1,2}\\s*[/.\\- ]\\s*\\d{1,2}\\s*[/.\\- ]\\s*\\d{4}")) {
                String[] f = t.split("\\s*[/.\\- ]\\s*");
                d = Integer.parseInt(f[0]);
                m = Integer.parseInt(f[1]);
                y = Integer.parseInt(f[2]);
            } else return null;
            return LocalDate.of(y, m, d);
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }

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
