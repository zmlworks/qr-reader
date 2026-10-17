package com.zmlworks.qrreader;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class DateFmtTest {
    @Test public void formats() {
        LocalDate d = LocalDate.of(2026, 1, 7);
        assertEquals("07/01/2026", DateFmt.DMY.date(d));
        assertEquals("01/07/2026", DateFmt.MDY.date(d));
        assertEquals("2026/01/07", DateFmt.YMD.date(d));
        ZoneId z = ZoneId.of("UTC");
        long t = LocalDateTime.of(2026, 10, 17, 23, 4, 9).atZone(z).toInstant().toEpochMilli();
        assertEquals("17/10/2026 23:04", DateFmt.DMY.dateTime(t, z, false));
        assertEquals("10/17/2026 23:04:09", DateFmt.MDY.dateTime(t, z, true));
        assertEquals("2026/10/17 23:04", DateFmt.YMD.dateTime(t, z, false));
    }

    @Test public void defaultsCycleAndOrder() {
        assertEquals(DateFmt.DMY, DateFmt.of(null));
        assertEquals(DateFmt.DMY, DateFmt.of("garbage"));
        assertEquals(DateFmt.YMD, DateFmt.of("YMD"));
        assertEquals(DateFmt.MDY, DateFmt.DMY.next());
        assertEquals(DateFmt.YMD, DateFmt.MDY.next());
        assertEquals(DateFmt.DMY, DateFmt.YMD.next());
        assertEquals("DD/MM/YYYY", DateFmt.DMY.label);
        assertArrayEquals(new char[]{'y', 'm', 'd'}, DateFmt.YMD.order);
        assertArrayEquals(new char[]{'d', 'm', 'y'}, DateFmt.DMY.order);
    }
}
