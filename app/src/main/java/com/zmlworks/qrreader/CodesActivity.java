package com.zmlworks.qrreader;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** The codes of one multi scan ("Just scanned" right after scanning, "5 codes" from the history). */
public final class CodesActivity extends BaseActivity {
    static final String EXTRA_GROUP = "group", EXTRA_JUST_SCANNED = "just";
    private long groupId;
    private List<History.Entry> items = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private Snack snack;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_codes);
        padForInsets(findViewById(R.id.root));
        groupId = getIntent().getLongExtra(EXTRA_GROUP, 0);
        snack = new Snack(findViewById(R.id.root));
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_copy_all).setOnClickListener(v -> Actions.copy(this, App.get().history().groupText(groupId)));
        findViewById(R.id.btn_share_all).setOnClickListener(v -> Actions.share(this, App.get().history().groupText(groupId)));
        SwipeListView list = findViewById(R.id.list);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) ->
                startActivity(new Intent(this, DetailActivity.class).putExtra(DetailActivity.EXTRA_ID, items.get(pos).id)));
        list.setCallback(new SwipeListView.Callback() {
            @Override public void onSwipeRight(int pos) {
                if (pos < 0 || pos >= items.size()) return;
                final History.Removal r = App.get().history().remove(items.get(pos).id);
                App.get().pendingUndo = r;
                refresh();
                snack.show(getString(R.string.deleted), () -> { App.get().history().undo(r); refresh(); });
            }
            @Override public void onSwipeLeft(int pos) {
                if (pos < 0 || pos >= items.size()) return;
                final History.Entry e = items.get(pos);
                if (e.prot) Auth.require(CodesActivity.this, true, getString(R.string.auth_copy_title), () -> Actions.copy(CodesActivity.this, e.text), null);
                else Actions.copy(CodesActivity.this, e.text);
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        History h = App.get().history();
        items = h.children(groupId);
        int n = items.size();
        History.Entry g = h.get(groupId);
        TextView title = findViewById(R.id.title), sub = findViewById(R.id.sub);
        title.setText(getIntent().getBooleanExtra(EXTRA_JUST_SCANNED, false) ? getString(R.string.just_scanned)
                : getResources().getQuantityString(R.plurals.n_codes, n, n));
        sub.setText(g == null ? "" : Actions.longTime(g.time) + " · " + getResources().getQuantityString(R.plurals.n_codes, n, n));
        findViewById(R.id.empty).setVisibility(n == 0 ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return items.get(i).id; }
        @Override public boolean hasStableIds() { return true; }

        @Override public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(CodesActivity.this).inflate(R.layout.row_history, parent, false);
            SwipeListView.reset(v);
            HistoryPage.bindCode(CodesActivity.this, v, items.get(pos), getString(R.string.row_number, pos + 1));
            return v;
        }
    }
}
