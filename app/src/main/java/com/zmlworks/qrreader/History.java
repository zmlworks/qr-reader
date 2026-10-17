package com.zmlworks.qrreader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Scan history (pure Java, unit-tested; persisted by {@link HistoryStore}).
 * <ul>
 * <li>{@link #SINGLE}: one code read in single mode.</li>
 * <li>{@link #GROUP}: one multi-mode session ("5 codes"); its codes are {@link #CHILD} entries with parent = group id.</li>
 * </ul>
 * Lists are newest first. Every removal returns a {@link Removal} that {@link #undo} puts back unchanged.
 * Not thread-safe: use from the UI thread only.
 */
public final class History {
    public static final int SINGLE = 0, GROUP = 1, CHILD = 2;

    public static final class Entry {
        public final long id, parent;
        public final int kind;
        public final String text;
        public long time;
        public boolean fav;
        /** user-given name shown instead of the text (null = none) */
        public String name;

        Entry(long id, long parent, int kind, long time, boolean fav, String text) {
            this.id = id; this.parent = parent; this.kind = kind; this.time = time; this.fav = fav; this.text = text;
        }
    }

    /** What one remove() took out (a group with its codes, or a code and its then-empty group). */
    public static final class Removal {
        final List<Entry> entries;
        Removal(List<Entry> e) { entries = e; }
        public int size() { return entries.size(); }
        /** id of the entry the user removed (first in the list) */
        public long primaryId() { return entries.isEmpty() ? 0 : entries.get(0).id; }
    }

    public interface Listener { void onChanged(); }

    private final List<Entry> entries = new ArrayList<>();   // insertion order
    private long nextId = 1;
    private final List<Listener> listeners = new ArrayList<>();

    private static final Comparator<Entry> NEWEST_FIRST = (a, b) -> a.time != b.time ? Long.compare(b.time, a.time) : Long.compare(b.id, a.id);
    private static final Comparator<Entry> SCAN_ORDER = (a, b) -> a.time != b.time ? Long.compare(a.time, b.time) : Long.compare(a.id, b.id);

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }
    private void changed() { for (Listener l : new ArrayList<>(listeners)) l.onChanged(); }

    public Entry get(long id) {
        for (Entry e : entries) if (e.id == id) return e;
        return null;
    }

    public int size() { return entries.size(); }

    /**
     * Single-mode read. If the newest top-level entry is the same text it is moved to the top instead of being
     * duplicated.
     */
    public Entry addSingle(String text, long now) {
        List<Entry> top = topLevel();
        if (!top.isEmpty()) {
            Entry n = top.get(0);
            if (n.kind == SINGLE && n.text.equals(text)) { n.time = Math.max(now, n.time); changed(); return n; }
        }
        Entry e = new Entry(nextId++, 0, SINGLE, now, false, text);
        entries.add(e);
        changed();
        return e;
    }

    public Entry newGroup(long now) {
        Entry g = new Entry(nextId++, 0, GROUP, now, false, "");
        entries.add(g);
        changed();
        return g;
    }

    /** Adds a code to a multi-scan group; null if that group already has this text (or the group is gone). */
    public Entry addChild(long groupId, String text, long now) {
        Entry g = get(groupId);
        if (g == null || g.kind != GROUP) return null;
        for (Entry e : entries) if (e.parent == groupId && e.text.equals(text)) return null;
        Entry c = new Entry(nextId++, groupId, CHILD, now, false, text);
        entries.add(c);
        changed();
        return c;
    }

    /** Singles and non-empty groups, newest first. */
    public List<Entry> topLevel() {
        List<Entry> r = new ArrayList<>();
        for (Entry e : entries) if (e.kind == SINGLE || (e.kind == GROUP && childCount(e.id) > 0)) r.add(e);
        r.sort(NEWEST_FIRST);
        return r;
    }

    /** Favourite codes (singles and codes inside groups), newest first. */
    public List<Entry> favorites() {
        List<Entry> r = new ArrayList<>();
        for (Entry e : entries) if (e.fav && e.kind != GROUP) r.add(e);
        r.sort(NEWEST_FIRST);
        return r;
    }

    /** Codes of a group in the order they were scanned. */
    public List<Entry> children(long groupId) {
        List<Entry> r = new ArrayList<>();
        for (Entry e : entries) if (e.parent == groupId && e.kind == CHILD) r.add(e);
        r.sort(SCAN_ORDER);
        return r;
    }

    public int childCount(long groupId) {
        int n = 0;
        for (Entry e : entries) if (e.parent == groupId && e.kind == CHILD) n++;
        return n;
    }

    public void setFavorite(long id, boolean fav) {
        Entry e = get(id);
        if (e != null && e.kind != GROUP && e.fav != fav) { e.fav = fav; changed(); }
    }

    /** Names a code (blank or null clears the name). */
    public void setName(long id, String name) {
        Entry e = get(id);
        if (e == null || e.kind == GROUP) return;
        String n = name == null ? null : name.replace('\n', ' ').replace('\t', ' ').trim();
        if (n != null && n.isEmpty()) n = null;
        if (n == null ? e.name == null : n.equals(e.name)) return;
        e.name = n;
        changed();
    }

    /** What a list shows for a code: its name, else its text. */
    public static String label(Entry e) { return e.name != null ? e.name : e.text; }

    /** Removes an entry (a group with all its codes; the last code of a group also removes the group). */
    public Removal remove(long id) {
        Entry e = get(id);
        List<Entry> out = new ArrayList<>();
        if (e == null) return new Removal(out);
        out.add(e);
        if (e.kind == GROUP) out.addAll(children(id));
        else if (e.kind == CHILD && childCount(e.parent) == 1) {
            Entry g = get(e.parent);
            if (g != null) out.add(g);
        }
        entries.removeAll(out);
        changed();
        return new Removal(out);
    }

    public Removal clear() {
        List<Entry> out = new ArrayList<>(entries);
        entries.clear();
        changed();
        return new Removal(out);
    }

    /** Puts back what a removal took out (entries that are already present again are skipped). */
    public void undo(Removal r) {
        if (r == null) return;
        boolean any = false;
        for (Entry e : r.entries) if (get(e.id) == null) { entries.add(e); any = true; }
        if (any) changed();
    }

    // ---------------- persistence: one line per entry, tab separated, text escaped ----------------
    // QRH2: id parent kind time fav name text (QRH1 of 0.1.0 had no name column and is still read)
    public String serialize() {
        StringBuilder b = new StringBuilder("QRH2\t").append(nextId).append('\n');
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingLong(x -> x.id));
        for (Entry e : sorted) {
            b.append(e.id).append('\t').append(e.parent).append('\t').append(e.kind).append('\t').append(e.time)
                    .append('\t').append(e.fav ? 1 : 0).append('\t').append(e.name == null ? "" : escape(e.name))
                    .append('\t').append(escape(e.text)).append('\n');
        }
        return b.toString();
    }

    public static History parse(String s) {
        History h = new History();
        if (s == null || s.isEmpty()) return h;
        String[] lines = s.split("\n", -1);
        boolean v2 = lines[0].startsWith("QRH2\t");
        if (!v2 && !lines[0].startsWith("QRH1\t")) return h;
        int cols = v2 ? 7 : 6;
        try { h.nextId = Long.parseLong(lines[0].substring(5).trim()); } catch (NumberFormatException ignored) { }
        long maxId = 0;
        for (int i = 1; i < lines.length; i++) {
            String[] f = lines[i].split("\t", cols);
            if (f.length < cols) continue;
            try {
                Entry e = new Entry(Long.parseLong(f[0]), Long.parseLong(f[1]), Integer.parseInt(f[2]), Long.parseLong(f[3]),
                        "1".equals(f[4]), unescape(f[cols - 1]));
                if (v2 && !f[5].isEmpty()) e.name = unescape(f[5]);
                h.entries.add(e);
                maxId = Math.max(maxId, e.id);
            } catch (NumberFormatException ignored) { }
        }
        h.nextId = Math.max(h.nextId, maxId + 1);
        return h;
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': b.append("\\\\"); break;
                case '\t': b.append("\\t"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }

    static String unescape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 't' ? '\t' : n == 'n' ? '\n' : n == 'r' ? '\r' : n);
            } else b.append(c);
        }
        return b.toString();
    }

    /** All texts of a group joined by newlines (for copy/share of a whole multi scan). */
    public String groupText(long groupId) {
        List<String> t = new ArrayList<>();
        for (Entry e : children(groupId)) t.add(e.text);
        return String.join("\n", t);
    }

    List<Entry> rawEntries() { return Collections.unmodifiableList(entries); }
}
