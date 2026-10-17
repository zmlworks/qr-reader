package com.zmlworks.qrreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class HistoryTest {
    @Test public void newestFirstAndDedupeOfTheNewest() {
        History h = new History();
        History.Entry a = h.addSingle("A", 100);
        History.Entry b = h.addSingle("B", 200);
        assertEquals(List.of(b, a), h.topLevel());
        assertSame(b, h.addSingle("B", 300));   // same as the newest: moved up, not duplicated
        assertEquals(300, b.time);
        History.Entry a2 = h.addSingle("A", 400);   // not the newest: a new entry
        assertTrue(a2.id != a.id);
        assertEquals(3, h.topLevel().size());
        assertEquals(a2, h.topLevel().get(0));
    }

    @Test public void groups() {
        History h = new History();
        h.addSingle("S", 100);
        History.Entry g = h.newGroup(200);
        assertEquals(1, h.topLevel().size());              // empty group is hidden
        assertNotNull(h.addChild(g.id, "1", 201));
        assertNotNull(h.addChild(g.id, "2", 202));
        assertNull(h.addChild(g.id, "1", 203));            // duplicate inside the group
        assertNull(h.addChild(12345, "x", 1));             // no such group
        assertEquals(2, h.childCount(g.id));
        assertEquals("1", h.children(g.id).get(0).text);   // scan order
        assertEquals(g, h.topLevel().get(0));
        assertEquals("1\n2", h.groupText(g.id));
    }

    @Test public void removeAndUndoSingle() {
        History h = new History();
        History.Entry a = h.addSingle("A", 100), b = h.addSingle("B", 200);
        h.setFavorite(a.id, true);
        History.Removal r = h.remove(a.id);
        assertEquals(1, r.size());
        assertEquals(a.id, r.primaryId());
        assertNull(h.get(a.id));
        assertEquals(List.of(b), h.topLevel());
        h.undo(r);
        assertSame(a, h.get(a.id));
        assertTrue(h.get(a.id).fav);                        // restored unchanged, favourite kept
        assertEquals(List.of(b, a), h.topLevel());
        h.undo(r);                                          // second undo is a no-op
        assertEquals(2, h.size());
    }

    @Test public void removeGroupAndChildren() {
        History h = new History();
        History.Entry g = h.newGroup(10);
        History.Entry c1 = h.addChild(g.id, "1", 11), c2 = h.addChild(g.id, "2", 12);
        History.Removal r = h.remove(g.id);
        assertEquals(3, r.size());
        assertEquals(0, h.size());
        h.undo(r);
        assertEquals(2, h.childCount(g.id));
        // removing one code keeps the group; removing the last code removes the group too
        History.Removal r1 = h.remove(c1.id);
        assertEquals(1, r1.size());
        assertNotNull(h.get(g.id));
        History.Removal r2 = h.remove(c2.id);
        assertEquals(2, r2.size());
        assertNull(h.get(g.id));
        h.undo(r2);
        h.undo(r1);
        assertEquals(List.of(c1, c2), h.children(g.id));
    }

    @Test public void favoritesAndClear() {
        History h = new History();
        History.Entry a = h.addSingle("A", 1);
        History.Entry g = h.newGroup(2);
        History.Entry c = h.addChild(g.id, "C", 3);
        h.setFavorite(a.id, true);
        h.setFavorite(c.id, true);
        h.setFavorite(g.id, true);                          // groups cannot be favourites
        assertFalse(g.fav);
        assertEquals(List.of(c, a), h.favorites());
        History.Removal all = h.clear();
        assertEquals(0, h.size());
        assertEquals(3, all.size());
        h.undo(all);
        assertEquals(3, h.size());
    }

    @Test public void listenerFires() {
        History h = new History();
        int[] n = {0};
        History.Listener l = () -> n[0]++;
        h.addListener(l);
        History.Entry a = h.addSingle("x", 1);
        h.setFavorite(a.id, true);
        h.undo(h.remove(a.id));
        assertEquals(4, n[0]);
        h.removeListener(l);
        h.addSingle("y", 2);
        assertEquals(4, n[0]);
    }

    @Test public void serializeRoundTrip() {
        History h = new History();
        h.addSingle("line1\nline2\ttab \\ backslash", 100);
        History.Entry g = h.newGroup(200);
        h.addChild(g.id, "日本語 ✓", 201);
        History.Entry f = h.addSingle("fav", 300);
        h.setFavorite(f.id, true);
        h.remove(h.addSingle("gone", 400).id);
        History p = History.parse(h.serialize());
        assertEquals(h.serialize(), p.serialize());
        assertEquals("line1\nline2\ttab \\ backslash", p.topLevel().get(2).text);
        assertTrue(p.get(f.id).fav);
        assertEquals("日本語 ✓", p.children(g.id).get(0).text);
        // ids keep increasing after a reload (the removed id 5 is not reused)
        assertEquals(6, p.addSingle("new", 500).id);
        assertEquals(0, History.parse("garbage").size());
        assertEquals(0, History.parse(null).size());
    }

    @Test public void names() {
        History h = new History();
        History.Entry a = h.addSingle("https://example.com/very/long/path", 1);
        assertEquals("https://example.com/very/long/path", History.label(a));
        h.setName(a.id, "  Wi-Fi\tsticker\n ");
        assertEquals("Wi-Fi sticker", a.name);
        assertEquals("Wi-Fi sticker", History.label(a));
        History p = History.parse(h.serialize());
        assertEquals("Wi-Fi sticker", p.get(a.id).name);
        assertEquals(h.serialize(), p.serialize());
        h.setName(a.id, "   ");   // empty clears it
        assertEquals(null, a.name);
        assertEquals(a.text, History.label(a));
        History.Entry g = h.newGroup(2);
        h.setName(g.id, "group");   // groups have no names
        assertEquals(null, g.name);
        // a named code survives delete + UNDO
        h.setName(a.id, "kept");
        h.undo(h.remove(a.id));
        assertEquals("kept", h.get(a.id).name);
    }

    @Test public void readsVersion010Files() {
        History p = History.parse("QRH1\t4\n1\t0\t0\t100\t1\tline\\nbreak\n2\t0\t1\t200\t0\t\n3\t2\t2\t201\t0\tchild\n");
        assertEquals(3, p.size());
        assertEquals("line\nbreak", p.get(1).text);
        assertTrue(p.get(1).fav);
        assertEquals(null, p.get(1).name);
        assertEquals("child", p.children(2).get(0).text);
        assertTrue(p.serialize().startsWith("QRH2\t4\n"));
    }
}
