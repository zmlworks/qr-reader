package com.zmlworks.qrreader;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;

public class JumpDateTest {
    @Test public void findsTheDayOrTheNearestEarlier() {
        ZoneId z = ZoneId.of("UTC");
        long d17 = LocalDate.of(2026, 10, 17).atTime(9, 0).atZone(z).toInstant().toEpochMilli();
        long d15a = LocalDate.of(2026, 10, 15).atTime(20, 0).atZone(z).toInstant().toEpochMilli();
        long d15b = LocalDate.of(2026, 10, 15).atTime(8, 0).atZone(z).toInstant().toEpochMilli();
        long d10 = LocalDate.of(2026, 10, 10).atTime(8, 0).atZone(z).toInstant().toEpochMilli();
        java.util.List<Long> t = Arrays.asList(d17, d15a, d15b, d10);
        assertEquals(0, JumpDate.index(t, LocalDate.of(2026, 10, 17), z));
        assertEquals("the first (newest) row of that day", 1, JumpDate.index(t, LocalDate.of(2026, 10, 15), z));
        assertEquals("16 -> 15", 1, JumpDate.index(t, LocalDate.of(2026, 10, 16), z));
        assertEquals(3, JumpDate.index(t, LocalDate.of(2026, 10, 12), z));
        assertEquals(-1, JumpDate.index(t, LocalDate.of(2026, 10, 9), z));
        assertEquals(0, JumpDate.index(t, LocalDate.of(2027, 1, 1), z));
    }
}
