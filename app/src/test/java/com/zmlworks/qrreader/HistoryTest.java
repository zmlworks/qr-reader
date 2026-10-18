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
        assertTrue(p.serialize().startsWith("QRH4\t4\t\n"));
    }

    @Test public void readsVersion020Files() {
        History p = History.parse("QRH2\t3\n1\t0\t0\t100\t1\tSticker\thttps://a\n2\t0\t0\t200\t0\t\tplain\n");
        assertEquals(2, p.size());
        assertEquals("Sticker", p.get(1).name);
        assertTrue(p.get(1).fav);
        assertFalse(p.get(1).prot);
        assertEquals(null, p.get(1).format);
        assertEquals("plain", p.get(2).text);
    }

    @Test public void protectionAndFormats() {
        History h = new History();
        History.Entry a = h.addSingle("secret", 100);
        h.setProtected(a.id, true);
        assertTrue(a.prot);
        // reading the same code again makes a new, unprotected entry instead of reusing the protected one
        History.Entry b = h.addSingle("secret", 200);
        assertFalse(b.id == a.id);
        assertFalse(b.prot);
        // same text, other format = other entry; same format merges
        History.Entry e1 = h.addSingle("4006381333931", 300, "EAN-13");
        History.Entry e2 = h.addSingle("4006381333931", 301, "EAN-13");
        assertSame(e1, e2);
        assertFalse(h.addSingle("4006381333931", 302, null).id == e1.id);
        History.Entry g = h.newGroup(400);
        h.addChild(g.id, "visible", 401, "Code 128");
        History.Entry hidden = h.addChild(g.id, "hidden", 402, null);
        h.setProtected(hidden.id, true);
        assertEquals("protected codes are left out of group copy / share", "visible", h.groupText(g.id));
        h.setProtected(g.id, true);
        assertFalse("groups cannot be protected", g.prot);
        History p = History.parse(h.serialize());
        assertEquals(h.serialize(), p.serialize());
        assertTrue(p.get(a.id).prot);
        assertEquals("EAN-13", p.get(e1.id).format);
        assertEquals("Code 128", p.children(g.id).get(0).format);
        h.setProtected(a.id, false);
        assertFalse(a.prot);
    }

    @Test public void clearKeepsFavoritesAndProtectedOnRequest() {
        History h = new History();
        History.Entry a = h.addSingle("a", 1);
        History.Entry b = h.addSingle("b", 2);
        h.setFavorite(b.id, true);
        h.setProtected(a.id, true);
        History.Entry g = h.newGroup(3);
        History.Entry c1 = h.addChild(g.id, "c1", 4);
        h.addChild(g.id, "c2", 5);
        h.setFavorite(c1.id, true);
        History.Entry g2 = h.newGroup(6);
        h.addChild(g2.id, "d", 7);
        History.Entry e = h.addSingle("e", 8);   // a favorite and protected: kept unless both are included
        h.setFavorite(e.id, true);
        h.setProtected(e.id, true);
        History.ClearPlan p = h.planClear(false, false);
        assertEquals(2, p.codes);          // c2, d
        assertEquals(0, p.prot);
        p = h.planClear(false, true);
        assertEquals(3, p.codes);          // a, c2, d
        assertEquals(1, p.prot);
        p = h.planClear(true, false);
        assertEquals(4, p.codes);          // b, c1, c2, d
        assertEquals(0, p.prot);
        p = h.planClear(true, true);
        assertEquals(6, p.codes);
        assertEquals(2, p.prot);
        h.clear(false, false);
        assertNotNull(h.get(a.id));
        assertNotNull(h.get(b.id));
        assertNotNull("a group keeps its favorite codes", h.get(g.id));
        assertEquals(1, h.childCount(g.id));
        assertNull(h.get(g2.id));
        assertEquals(0, h.planClear(false, false).codes);
        h.clear(false, true);
        assertNull(h.get(a.id));
        assertNotNull(h.get(e.id));
        h.clear(true, false);
        assertNotNull(h.get(e.id));
        assertEquals(1, h.size());
        h.clear(true, true);
        assertEquals(0, h.size());
    }

    // ---- 0.7.0 ----

    /** a 0.3.0-0.6.0 file (QRH3) loads; the new fields default to no memo, not created, favourites newest first */
    @Test public void readsVersion030To060Files() {
        String qrh3 = "QRH3\t7\n"
                + "1\t0\t0\t100\t1\t\tSticker\thttps://a\n"          // favourite, named
                + "2\t0\t0\t200\t2\tEAN-13\t\t4006381333931\n"       // protected, EAN-13
                + "3\t0\t1\t300\t0\t\t\t\n"                          // group
                + "4\t3\t2\t301\t3\t\t\tchild\\tx\n"               // favourite + protected child
                + "6\t0\t0\t400\t1\t\t\tnewer fav\n";
        History p = History.parse(qrh3);
        assertEquals(5, p.size());
        assertEquals("Sticker", p.get(1).name);
        assertTrue(p.get(1).fav);
        assertTrue(p.get(2).prot);
        assertEquals("EAN-13", p.get(2).format);
        assertEquals("child\tx", p.get(4).text);
        assertTrue(p.get(4).fav && p.get(4).prot);
        for (History.Entry e : p.rawEntries()) {
            assertNull(e.memo);
            assertFalse(e.created);
        }
        assertEquals("favourites newest first until reordered", List.of(p.get(6), p.get(4), p.get(1)), p.favorites());
        assertEquals(7, p.addSingle("next", 500).id);
        String out = p.serialize();
        assertTrue(out.startsWith("QRH4\t8\t\n"));
        assertEquals(out, History.parse(out).serialize());
    }

    @Test public void memoPersistsAndClears() {
        History h = new History();
        History.Entry a = h.addSingle("code", 1);
        int[] n = {0};
        h.addListener(() -> n[0]++);
        h.setMemo(a.id, "line 1\nline 2\twith tab \\ backslash");
        assertEquals(1, n[0]);
        h.setMemo(a.id, "line 1\nline 2\twith tab \\ backslash");   // unchanged: no save
        assertEquals(1, n[0]);
        History p = History.parse(h.serialize());
        assertEquals("line 1\nline 2\twith tab \\ backslash", p.get(a.id).memo);
        assertEquals(h.serialize(), p.serialize());
        h.setMemo(a.id, "  \n ");
        assertNull("blank = no memo", a.memo);
        History.Entry g = h.newGroup(2);
        h.setMemo(g.id, "x");
        assertNull("groups have no memo", g.memo);
        // a memo survives delete + UNDO
        h.setMemo(a.id, "kept");
        h.undo(h.remove(a.id));
        assertEquals("kept", h.get(a.id).memo);
    }

    @Test public void createdItems() {
        History h = new History();
        assertEquals(History.SAVE_EMPTY, h.saveCreated("", 1));
        assertEquals(History.SAVE_EMPTY, h.saveCreated("  \n ", 1));
        assertEquals(History.SAVE_EMPTY, h.saveCreated(null, 1));
        assertEquals(0, h.size());
        assertEquals(History.SAVED, h.saveCreated("https://example.com", 10));
        History.Entry c = h.topLevel().get(0);
        assertTrue(c.created);
        assertEquals(History.SINGLE, c.kind);
        assertEquals("tapping twice", History.SAVE_DUPLICATE, h.saveCreated("https://example.com", 11));
        assertEquals(1, h.size());
        assertEquals(10, c.time);
        assertEquals(History.SAVED, h.saveCreated("other", 12));
        assertEquals("not the newest any more: a new item", History.SAVED, h.saveCreated("https://example.com", 13));
        assertEquals(3, h.size());
        // a scan of the same text is a scanned item, not merged into the created one (and vice versa)
        History.Entry s = h.addSingle("https://example.com", 14);
        assertFalse(s.created);
        assertEquals(4, h.size());
        assertEquals(History.SAVED, h.saveCreated("https://example.com", 15));
        assertEquals(5, h.size());
        History p = History.parse(h.serialize());
        assertTrue(p.get(c.id).created);
        assertFalse(p.get(s.id).created);
    }

    @Test public void searchFilterLockedAndUnlocked() {
        History h = new History();
        History.Entry url = h.addSingle("https://github.com/zmlworks/qr-reader", 1);
        History.Entry named = h.addSingle("WIFI:S:Cafe;P:pw;;", 2);
        h.setName(named.id, "Office Wi-Fi");
        History.Entry memo = h.addSingle("plain", 3);
        h.setMemo(memo.id, "Parking ticket for Tuesday");
        History.Entry ean = h.addSingle("4006381333931", 4, "EAN-13");
        History.Entry secret = h.addSingle("secret github token", 5);
        h.setProtected(secret.id, true);
        History.Entry g = h.newGroup(6);
        h.addChild(g.id, "INV-001", 7);
        History.Entry hiddenChild = h.addChild(g.id, "INV-SECRET-github", 8);
        h.setProtected(hiddenChild.id, true);
        List<History.Entry> all = h.topLevel();
        assertSame("empty query: no filter", all, h.filter(all, "  ", false));
        assertEquals("text, case-insensitive", List.of(url), h.filter(all, "GITHUB", false));
        assertEquals("name", List.of(named), h.filter(all, "office", false));
        assertEquals("memo", List.of(memo), h.filter(all, "tuesday", false));
        assertEquals("format", List.of(ean), h.filter(all, "ean-13", false));
        assertEquals("every word must match", List.of(memo), h.filter(all, "parking plain", false));
        assertEquals(List.of(), h.filter(all, "parking github", false));
        assertEquals("a group through its codes", List.of(g), h.filter(all, "inv-001", false));
        // locked: protected codes never match and are counted as hidden, whatever the query
        assertEquals(List.of(url), h.filter(all, "github", false));
        assertEquals(2, h.protectedIn(all));
        assertEquals(List.of(), h.filter(all, "secret", false));
        // unlocked (authentication window): searched like the others
        assertEquals(List.of(g, secret, url), h.filter(all, "github", true));
        assertEquals(List.of(g, secret), h.filter(all, "secret", true));
        // Favorites scope
        h.setFavorite(secret.id, true);
        h.setFavorite(url.id, true);
        List<History.Entry> fav = h.favorites();
        assertEquals(List.of(url), h.filter(fav, "github", false));
        assertEquals(1, h.protectedIn(fav));
        assertEquals(List.of(secret, url), h.filter(fav, "github", true));
        assertTrue(h.hasProtected(g));
        assertTrue(h.hasProtected(secret));
        assertFalse(h.hasProtected(url));
    }

    @Test public void favoritesManualOrderPersists() {
        History h = new History();
        History.Entry a = h.addSingle("a", 1), b = h.addSingle("b", 2), c = h.addSingle("c", 3);
        for (History.Entry e : List.of(a, b, c)) h.setFavorite(e.id, true);
        assertEquals(List.of(c, b, a), h.favorites());
        h.moveFavorite(0, 2);   // c to the bottom
        assertEquals(List.of(b, a, c), h.favorites());
        History p = History.parse(h.serialize());
        assertEquals(List.of(b.id, a.id, c.id), p.favoriteOrder());
        assertEquals(h.serialize(), p.serialize());
        // a new favourite goes on top; the manual order of the others stays
        History.Entry d = h.addSingle("d", 4);
        h.setFavorite(d.id, true);
        assertEquals(List.of(d, b, a, c), h.favorites());
        // removing a favourite and UNDO puts it back in its place
        History.Removal r = h.remove(a.id);
        assertEquals(List.of(d, b, c), h.favorites());
        h.undo(r);
        assertEquals(List.of(d, b, a, c), h.favorites());
        // unfavourite + favourite again = new, on top
        h.setFavorite(c.id, false);
        h.setFavorite(c.id, true);
        assertEquals(List.of(c, d, b, a), h.favorites());
        h.setFavoriteOrder(List.of(a.id, b.id, c.id, d.id));
        assertEquals(List.of(a.id, b.id, c.id, d.id), History.parse(h.serialize()).favoriteOrder());
        // bad positions are ignored
        h.moveFavorite(-1, 2);
        h.moveFavorite(0, 9);
        assertEquals(List.of(a, b, c, d), h.favorites());
    }
}
