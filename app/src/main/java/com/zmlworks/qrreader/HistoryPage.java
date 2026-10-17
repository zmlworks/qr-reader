package com.zmlworks.qrreader;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** History tab: All / Favorites, newest first; a named code shows its name. Swipe right = delete (UNDO), swipe left = copy, tap = detail. */
final class HistoryPage {
    private final MainActivity a;
    private final SwipeListView list;
    private final TextView empty, segAll, segFav;
    private boolean favOnly;
    private List<History.Entry> items = new ArrayList<>();
    private final Adapter adapter = new Adapter();

    HistoryPage(MainActivity a, View root) {
        this.a = a;
        list = root.findViewById(R.id.list);
        empty = root.findViewById(R.id.empty);
        segAll = root.findViewById(R.id.seg_all);
        segFav = root.findViewById(R.id.seg_fav);
        list.setAdapter(adapter);
        segAll.setOnClickListener(v -> { favOnly = false; refresh(); });
        segFav.setOnClickListener(v -> { favOnly = true; refresh(); });
        list.setOnItemClickListener((p, v, pos, id) -> open(items.get(pos)));
        list.setCallback(new SwipeListView.Callback() {
            @Override public void onSwipeRight(int pos) { if (pos >= 0 && pos < items.size()) delete(items.get(pos)); }
            @Override public void onSwipeLeft(int pos) { if (pos >= 0 && pos < items.size()) copy(items.get(pos)); }
        });
    }

    void refresh() {
        History h = App.get().history();
        items = favOnly ? h.favorites() : h.topLevel();
        segAll.setSelected(!favOnly);
        segFav.setSelected(favOnly);
        adapter.notifyDataSetChanged();
        boolean none = items.isEmpty();
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        empty.setText(favOnly ? R.string.empty_favorites : R.string.empty_history);
    }

    /** the last Jump to date dialog (tests) */
    android.app.AlertDialog jumpDialog;

    /** the pickers of the last Jump to date dialog (tests): day, month, year (whatever their order on screen) */
    android.widget.NumberPicker[] jumpPickers;

    /**
     * Jump to date: three wheels for day / month / year in the order of the Settings date format, from the oldest row's day to today, starting
     * at the newest row's day. Scrolls to the first row of that day or of the nearest earlier day.
     */
    void jumpToDate() {
        if (items.isEmpty()) { android.widget.Toast.makeText(a, favOnly ? R.string.empty_favorites : R.string.empty_history, android.widget.Toast.LENGTH_SHORT).show(); return; }
        java.time.ZoneId z = java.time.ZoneId.systemDefault();
        java.time.LocalDate newest = JumpDate.dayOf(items.get(0).time, z), oldest = JumpDate.dayOf(items.get(items.size() - 1).time, z);
        java.time.LocalDate today = java.time.LocalDate.now(z);
        final java.time.LocalDate min = oldest.isBefore(today) ? oldest : today, max = newest.isAfter(today) ? newest : today;
        final android.widget.NumberPicker day = picker(R.id.jump_day, 1, 31), month = picker(R.id.jump_month, 1, 12),
                year = picker(R.id.jump_year, min.getYear(), max.getYear());
        final String[] names = new java.text.DateFormatSymbols().getShortMonths();
        final java.time.LocalDate[] cur = {newest};
        final Runnable show = () -> {   // each wheel only offers what stays within oldest .. today
            java.time.LocalDate c = cur[0];
            boolean firstYear = c.getYear() == min.getYear(), lastYear = c.getYear() == max.getYear();
            int m0 = firstYear ? min.getMonthValue() : 1, m1 = lastYear ? max.getMonthValue() : 12;
            month.setDisplayedValues(null);
            month.setMinValue(m0);
            month.setMaxValue(m1);
            month.setDisplayedValues(java.util.Arrays.copyOfRange(names, m0 - 1, m1));
            boolean firstMonth = firstYear && c.getMonthValue() == min.getMonthValue(), lastMonth = lastYear && c.getMonthValue() == max.getMonthValue();
            day.setMinValue(firstMonth ? min.getDayOfMonth() : 1);
            day.setMaxValue(lastMonth ? max.getDayOfMonth() : c.lengthOfMonth());
            day.setValue(c.getDayOfMonth());
            month.setValue(c.getMonthValue());
            year.setValue(c.getYear());
        };
        android.widget.NumberPicker.OnValueChangeListener l = (p, o, n) -> {
            int y = year.getValue(), m = month.getValue();
            java.time.LocalDate d = java.time.LocalDate.of(y, m, Math.min(day.getValue(), java.time.YearMonth.of(y, m).lengthOfMonth()));
            cur[0] = d.isBefore(min) ? min : d.isAfter(max) ? max : d;   // only days with history up to today
            show.run();
        };
        for (android.widget.NumberPicker p : new android.widget.NumberPicker[]{day, month, year}) p.setOnValueChangedListener(l);
        show.run();
        android.widget.LinearLayout row = new android.widget.LinearLayout(a);
        row.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        int pad = (int) (8 * a.getResources().getDisplayMetrics().density);
        row.setPadding(pad, pad, pad, 0);
        for (char c : App.get().prefs().dateFmt().order)   // e.g. YYYY/MM/DD: year, month, day
            row.addView(c == 'd' ? labelled(day, R.string.jump_day) : c == 'm' ? labelled(month, R.string.jump_month) : labelled(year, R.string.jump_year));
        jumpPickers = new android.widget.NumberPicker[]{day, month, year};
        jumpDialog = new android.app.AlertDialog.Builder(a).setTitle(R.string.jump_to_date).setView(row)
                .setPositiveButton(R.string.jump, (d, w) -> jumpTo(cur[0])).setNegativeButton(R.string.cancel, null).show();
    }

    private android.widget.NumberPicker picker(int id, int from, int to) {
        android.widget.NumberPicker p = new android.widget.NumberPicker(a);
        p.setId(id);
        p.setMinValue(from);
        p.setMaxValue(to);
        p.setWrapSelectorWheel(false);
        p.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);   // wheels only, no keyboard
        return p;
    }

    private View labelled(android.widget.NumberPicker p, int label) {
        android.widget.LinearLayout col = new android.widget.LinearLayout(a);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        col.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        int pad = (int) (10 * a.getResources().getDisplayMetrics().density);
        col.setPadding(pad, 0, pad, 0);
        TextView t = new TextView(a);
        t.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        t.setText(label);
        t.setTextColor(a.getColor(R.color.dim));
        t.setTextSize(12);
        col.addView(t, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(p);
        p.setContentDescription(a.getString(label));
        return col;
    }

    /** @return the row scrolled to, or -1 */
    int jumpTo(java.time.LocalDate day) {
        java.time.ZoneId z = java.time.ZoneId.systemDefault();
        List<Long> times = new ArrayList<>();
        for (History.Entry e : items) times.add(e.time);
        int i = JumpDate.index(times, day, z);
        DateFmt f = App.get().prefs().dateFmt();
        if (i < 0) {
            android.widget.Toast.makeText(a, a.getString(R.string.jump_none, f.date(day)), android.widget.Toast.LENGTH_SHORT).show();
            if (!items.isEmpty()) list.setSelection(items.size() - 1);
            return -1;
        }
        java.time.LocalDate got = JumpDate.dayOf(items.get(i).time, z);
        if (!got.equals(day))
            android.widget.Toast.makeText(a, a.getString(R.string.jump_earlier, f.date(day), f.date(got)), android.widget.Toast.LENGTH_SHORT).show();
        list.setSelection(i);
        return i;
    }

    private void open(History.Entry e) {
        if (e.kind == History.GROUP) a.startActivity(new Intent(a, CodesActivity.class).putExtra(CodesActivity.EXTRA_GROUP, e.id));
        else a.startActivity(new Intent(a, DetailActivity.class).putExtra(DetailActivity.EXTRA_ID, e.id));
    }

    private void delete(History.Entry e) {
        App.get().pendingUndo = App.get().history().remove(e.id);
        refresh();
        a.showUndo(a.getString(R.string.deleted), 0);
    }

    /** Swipe-left copy. A protected code is copied only after authentication; a group copies its unprotected codes. */
    private void copy(History.Entry e) {
        if (e.kind == History.GROUP) { Actions.copy(a, App.get().history().groupText(e.id)); return; }
        if (e.prot) Auth.require(a, true, a.getString(R.string.auth_copy_title), () -> Actions.copy(a, e.text), null);
        else Actions.copy(a, e.text);
    }

    /**
     * One code row (history and multi-scan lists): badge, content line, date line. Protected: the lock as badge and a
     * blurred placeholder as content (see {@link Obscured}: no text, name, type or format), the date line as usual.
     */
    static void bindCode(Context c, View v, History.Entry e, String sub) {
        TextView badge = v.findViewById(R.id.row_badge), title = v.findViewById(R.id.row_title), subV = v.findViewById(R.id.row_sub);
        subV.setVisibility(View.VISIBLE);
        subV.setText(sub);
        if (e.prot) {
            android.graphics.drawable.Drawable d = c.getDrawable(R.drawable.ic_lock_small);
            if (d != null) d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            android.text.SpannableString ss = new android.text.SpannableString(" ");
            if (d != null) ss.setSpan(new android.text.style.ImageSpan(d, android.text.style.ImageSpan.ALIGN_BOTTOM), 0, 1, 0);
            badge.setText(ss);
            badge.setContentDescription(c.getString(R.string.protected_title));
            Obscured.apply(title, e.id, true);
        } else {
            Obscured.apply(title, e.id, false);
            badge.setText(e.format != null ? e.format : ContentType.parse(e.text).label());
            badge.setContentDescription(null);
            title.setText(preview(History.label(e)));
        }
        ((ImageView) v.findViewById(R.id.row_star)).setVisibility(e.fav && !e.prot ? View.VISIBLE : View.GONE);
    }

    static String preview(String s) {
        String t = s.replace('\n', ' ').trim();
        return t.length() > 300 ? t.substring(0, 300) : t;
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return items.get(i).id; }
        @Override public boolean hasStableIds() { return true; }

        @Override public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(a).inflate(R.layout.row_history, parent, false);
            SwipeListView.reset(v);
            History.Entry e = items.get(pos);
            History h = App.get().history();
            if (e.kind == History.GROUP) {
                TextView badge = v.findViewById(R.id.row_badge), title = v.findViewById(R.id.row_title), sub = v.findViewById(R.id.row_sub);
                Obscured.apply(title, e.id, false);   // a recycled protected row
                int n = h.childCount(e.id);
                String first = null;   // preview: the first code that is not protected
                for (History.Entry c : h.children(e.id)) if (!c.prot) { first = History.label(c); break; }
                badge.setText(a.getString(R.string.group_badge, n));
                badge.setContentDescription(null);
                title.setText(a.getResources().getQuantityString(R.plurals.n_codes, n, n));
                String time = Actions.listTime(e.time);
                sub.setVisibility(View.VISIBLE);
                sub.setText(first == null ? time : a.getString(R.string.sub_with_preview, time, preview(first)));
                v.findViewById(R.id.row_star).setVisibility(View.GONE);
            } else {
                String s = Actions.listTime(e.time);
                if (e.kind == History.CHILD) s = a.getString(R.string.sub_with_preview, s, a.getString(R.string.multi_scan));
                bindCode(a, v, e, s);
            }
            return v;
        }
    }
}
