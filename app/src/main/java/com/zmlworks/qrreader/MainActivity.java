package com.zmlworks.qrreader;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** History / Create / Settings with bottom tabs. Back (or Scan) returns to the scanner. */
public final class MainActivity extends BaseActivity {
    static final String EXTRA_TAB = "tab", EXTRA_CREATE_TEXT = "create_text", EXTRA_UNDO = "undo", EXTRA_UNDO_DETAIL = "undo_detail";
    static final int TAB_HISTORY = 0, TAB_CREATE = 1, TAB_SETTINGS = 2;

    private int tab = TAB_HISTORY;
    private View[] pages;
    private TextView[] tabs;
    private TextView title;
    private View fab;
    HistoryPage historyPage;
    CreatePage createPage;
    SettingsPage settingsPage;
    Snack snack;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        padForInsets(findViewById(R.id.root));
        title = findViewById(R.id.title);
        fab = findViewById(R.id.fab_scan);
        pages = new View[]{findViewById(R.id.page_history), findViewById(R.id.page_create), findViewById(R.id.page_settings)};
        tabs = new TextView[]{findViewById(R.id.tab_history), findViewById(R.id.tab_create), findViewById(R.id.tab_settings)};
        for (int i = 0; i < 3; i++) { final int t = i; tabs[i].setOnClickListener(v -> select(t)); }
        findViewById(R.id.btn_scan_top).setOnClickListener(v -> scan());
        fab.setOnClickListener(v -> scan());
        snack = new Snack(findViewById(R.id.root));
        historyPage = new HistoryPage(this, pages[0]);
        createPage = new CreatePage(this, pages[1]);
        settingsPage = new SettingsPage(this, pages[2]);
        if (b != null) tab = b.getInt(EXTRA_TAB, TAB_HISTORY);
        handle(getIntent(), b == null);
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handle(i, true);
    }

    private void handle(Intent i, boolean fresh) {
        if (fresh && i != null) {
            tab = i.getIntExtra(EXTRA_TAB, tab);
            String ct = i.getStringExtra(EXTRA_CREATE_TEXT);
            if (ct != null) { tab = TAB_CREATE; createPage.setText(ct); }
        }
        select(tab);
        if (fresh && i != null && i.getBooleanExtra(EXTRA_UNDO, false)) {
            final long detail = i.getLongExtra(EXTRA_UNDO_DETAIL, 0);
            i.removeExtra(EXTRA_UNDO);
            showUndo(getString(R.string.deleted), detail);
        }
    }

    /** Snackbar for App.pendingUndo; UNDO restores it and, if `detailId` != 0, reopens that detail page. */
    void showUndo(CharSequence msg, long detailId) {
        final History.Removal r = App.get().pendingUndo;
        if (r == null) return;
        snack.show(msg, () -> {
            App.get().history().undo(r);
            if (App.get().pendingUndo == r) App.get().pendingUndo = null;
            historyPage.refresh();
            if (detailId != 0) startActivity(new Intent(this, DetailActivity.class).putExtra(DetailActivity.EXTRA_ID, detailId));
        });
    }

    void select(int t) {
        tab = t;
        for (int i = 0; i < 3; i++) {
            pages[i].setVisibility(i == t ? View.VISIBLE : View.GONE);
            tabs[i].setSelected(i == t);
        }
        title.setText(t == TAB_HISTORY ? R.string.history : t == TAB_CREATE ? R.string.create : R.string.settings);
        fab.setVisibility(t == TAB_HISTORY ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) fab.getLayoutParams();
        lp.gravity = App.get().prefs().leftHanded() ? Gravity.START : Gravity.END;
        fab.setLayoutParams(lp);
        if (t == TAB_HISTORY) historyPage.refresh();
        if (t == TAB_SETTINGS) settingsPage.refresh();
        if (t != TAB_CREATE) createPage.hideKeyboard();
    }

    void scan() {
        startActivity(new Intent(this, ScannerActivity.class));
        finish();
    }

    @Override protected void onResume() {
        super.onResume();
        if (tab == TAB_HISTORY) historyPage.refresh();
        if (tab == TAB_SETTINGS) settingsPage.refresh();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(EXTRA_TAB, tab);
    }

    @Override protected void onDestroy() {
        createPage.release();
        super.onDestroy();
    }

    int tabForTest() { return tab; }
}
