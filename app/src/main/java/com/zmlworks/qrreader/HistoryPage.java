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
     * One code row (history and multi-scan lists). Protected: a lock badge and the date and time only (no text, name,
     * type or format). Otherwise: type or barcode format, name or text, and `sub`.
     */
    static void bindCode(Context c, View v, History.Entry e, String sub) {
        TextView badge = v.findViewById(R.id.row_badge), title = v.findViewById(R.id.row_title), subV = v.findViewById(R.id.row_sub);
        if (e.prot) {
            android.graphics.drawable.Drawable d = c.getDrawable(R.drawable.ic_lock_small);
            if (d != null) d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            android.text.SpannableString ss = new android.text.SpannableString(" ");
            if (d != null) ss.setSpan(new android.text.style.ImageSpan(d, android.text.style.ImageSpan.ALIGN_BOTTOM), 0, 1, 0);
            badge.setText(ss);
            badge.setContentDescription(c.getString(R.string.protected_title));
            title.setText(Actions.listTime(e.time));
            subV.setVisibility(View.GONE);
        } else {
            badge.setText(e.format != null ? e.format : ContentType.parse(e.text).label());
            badge.setContentDescription(null);
            title.setText(preview(History.label(e)));
            subV.setVisibility(View.VISIBLE);
            subV.setText(sub);
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
