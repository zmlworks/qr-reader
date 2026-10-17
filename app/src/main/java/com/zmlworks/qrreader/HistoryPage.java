package com.zmlworks.qrreader;

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

    private void copy(History.Entry e) {
        Actions.copy(a, e.kind == History.GROUP ? App.get().history().groupText(e.id) : e.text);
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
            TextView badge = v.findViewById(R.id.row_badge), title = v.findViewById(R.id.row_title), sub = v.findViewById(R.id.row_sub);
            ImageView star = v.findViewById(R.id.row_star);
            if (e.kind == History.GROUP) {
                int n = h.childCount(e.id);
                List<History.Entry> ch = h.children(e.id);
                badge.setText(a.getString(R.string.group_badge, n));
                title.setText(a.getResources().getQuantityString(R.plurals.n_codes, n, n));
                String time = Actions.listTime(e.time);
                sub.setText(ch.isEmpty() ? time : a.getString(R.string.sub_with_preview, time, preview(ch.get(0).text)));
            } else {
                badge.setText(ContentType.parse(e.text).label());
                title.setText(preview(History.label(e)));
                String s = Actions.listTime(e.time);
                if (e.kind == History.CHILD) s = a.getString(R.string.sub_with_preview, s, a.getString(R.string.multi_scan));
                sub.setText(s);
            }
            star.setVisibility(e.fav ? View.VISIBLE : View.GONE);
            return v;
        }
    }
}
