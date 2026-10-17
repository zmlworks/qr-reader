package com.zmlworks.qrreader;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**
 * History / Create / Settings with bottom tabs. Back (or the scan icon in the top bar of History and Settings) returns
 * to the scanner. Create has no top bar, and the tabs hide while the keyboard is open, so the QR and the input fit.
 */
public final class MainActivity extends BaseActivity {
    static final String EXTRA_TAB = "tab", EXTRA_CREATE_TEXT = "create_text", EXTRA_UNDO = "undo", EXTRA_UNDO_DETAIL = "undo_detail";
    static final int TAB_HISTORY = 0, TAB_CREATE = 1, TAB_SETTINGS = 2;

    private int tab = TAB_HISTORY;
    private View[] pages;
    private TextView[] tabs;
    private TextView title;
    private View topBar, tabBar, tabLine, root;
    private boolean keyboard;
    HistoryPage historyPage;
    CreatePage createPage;
    SettingsPage settingsPage;
    Snack snack;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.root);
        padForInsets(root);
        title = findViewById(R.id.title);
        topBar = findViewById(R.id.top_bar);
        tabBar = findViewById(R.id.tab_bar);
        tabLine = findViewById(R.id.tab_line);
        root.addOnLayoutChangeListener((v, l, t, r, b2, ol, ot, or, ob) -> checkKeyboard());
        pages = new View[]{findViewById(R.id.page_history), findViewById(R.id.page_create), findViewById(R.id.page_settings)};
        tabs = new TextView[]{findViewById(R.id.tab_history), findViewById(R.id.tab_create), findViewById(R.id.tab_settings)};
        for (int i = 0; i < 3; i++) { final int t = i; tabs[i].setOnClickListener(v -> select(t)); }
        findViewById(R.id.btn_scan_top).setOnClickListener(v -> scan());
        snack = new Snack(root);
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
        topBar.setVisibility(t == TAB_CREATE ? View.GONE : View.VISIBLE);
        renderBars();
        if (t == TAB_HISTORY) historyPage.refresh();
        if (t == TAB_SETTINGS) settingsPage.refresh();
        if (t != TAB_CREATE) createPage.hideKeyboard();
    }

    /** Is the keyboard open? (then Create hides the tabs) */
    private void checkKeyboard() {
        boolean k;
        android.view.WindowInsets wi = root.getRootWindowInsets();
        if (android.os.Build.VERSION.SDK_INT >= 30 && wi != null) k = wi.isVisible(android.view.WindowInsets.Type.ime());
        else {   // API 26-29: adjustResize shrinks the window
            int screen = getResources().getDisplayMetrics().heightPixels;
            k = screen > 0 && root.getHeight() > 0 && root.getHeight() < screen * 0.72f;
        }
        if (k != keyboard) { keyboard = k; root.post(this::renderBars); }
    }

    private void renderBars() {
        int vis = keyboard && tab == TAB_CREATE ? View.GONE : View.VISIBLE;
        tabBar.setVisibility(vis);
        tabLine.setVisibility(vis);
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
    void setKeyboardForTest(boolean k) { keyboard = k; renderBars(); }
}
