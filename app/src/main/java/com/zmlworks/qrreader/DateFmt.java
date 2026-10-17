package com.zmlworks.qrreader;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The date format chosen in Settings (pure Java, unit-tested): DD/MM/YYYY, MM/DD/YYYY or YYYY/MM/DD, always with '/',
 * followed by a 24-hour time. It also gives the order of the Jump to date wheels.
 */
enum DateFmt {
    DMY("DD/MM/YYYY", 'd', 'm', 'y'),
    MDY("MM/DD/YYYY", 'm', 'd', 'y'),
    YMD("YYYY/MM/DD", 'y', 'm', 'd');

    final String label;
    /** field order: 'd' day, 'm' month, 'y' year */
    final char[] order;

    DateFmt(String label, char... order) { this.label = label; this.order = order; }

    /** the stored value; anything unknown (or nothing yet) is the default, DMY */
    static DateFmt of(String name) {
        if (name != null) for (DateFmt f : values()) if (f.name().equals(name)) return f;
        return DMY;
    }

    DateFmt next() { return values()[(ordinal() + 1) % values().length]; }

    String date(LocalDate d) {
        StringBuilder b = new StringBuilder(10);
        for (char c : order) {
            if (b.length() > 0) b.append('/');
            if (c == 'y') b.append(String.format(java.util.Locale.ROOT, "%04d", d.getYear()));
            else b.append(String.format(java.util.Locale.ROOT, "%02d", c == 'm' ? d.getMonthValue() : d.getDayOfMonth()));
        }
        return b.toString();
    }

    /** "17/10/2026 14:05" (or with ":09" seconds) */
    String dateTime(long t, ZoneId z, boolean seconds) {
        LocalDateTime x = LocalDateTime.ofInstant(Instant.ofEpochMilli(t), z);
        String time = seconds ? String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", x.getHour(), x.getMinute(), x.getSecond())
                : String.format(java.util.Locale.ROOT, "%02d:%02d", x.getHour(), x.getMinute());
        return date(x.toLocalDate()) + " " + time;
    }
}
