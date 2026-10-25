package com.zmlworks.qrreader;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

/**
 * History / Create / Settings with bottom tabs. Back (or the scan icon in the top bar of History and Settings) returns
 * to the scanner. Create also shows the top bar (scan only); the tabs hide while the keyboard is open, so the QR and the input fit.
 * <p>
 * On Create (while the app is in light mode) the chrome (top bar, tabs, status/nav bars) uses the same night colours
 * as {@code values-night}, so the whole screen matches dark mode — not only the Create page body.
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
        findViewById(R.id.btn_jump).setOnClickListener(v -> historyPage.jumpToDate());
        findViewById(R.id.btn_search).setOnClickListener(v -> historyPage.toggleSearch());
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
        topBar.setVisibility(View.VISIBLE);
        renderTopButtons();
        if (t != TAB_HISTORY) historyPage.hideKeyboard();
        ((UnlockPill) findViewById(R.id.unlock_pill)).setAllowed(t != TAB_CREATE);   // History and Settings
        renderBars();
        applyChrome();
        if (t == TAB_HISTORY) historyPage.refresh();
        if (t == TAB_SETTINGS) settingsPage.refresh();
        if (t != TAB_CREATE) { createPage.hideKeyboard(); createPage.setBright(false, false); }
    }

    /**
     * Top bar: [title] [Unlocked pill] [jump] [search] [scan]. Scan is on History, Create, and Settings; jump only on
     * History's All tab (Favorites are in manual order), search only on History. A hidden button is INVISIBLE (not GONE),
     * so the slots, and with them the unlock pill, stay exactly in place on every tab.
     */
    void renderTopButtons() {
        boolean history = tab == TAB_HISTORY;
        findViewById(R.id.btn_scan_top).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_jump).setVisibility(history && !historyPage.favOnly() ? View.VISIBLE : View.INVISIBLE);
        findViewById(R.id.btn_search).setVisibility(history ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * When light prefs show Create, paint chrome with night colours so title + tabs match the dark Create body.
     * Night mode already uses those colours globally — leave it alone. Leaving Create restores light chrome.
     */
    private void applyChrome() {
        if (isNight()) return;
        boolean create = tab == TAB_CREATE;
        Context colors = create ? nightColors() : this;
        int bg = colors.getColor(R.color.bg);
        int text = colors.getColor(R.color.text);
        int dim = colors.getColor(R.color.dim);
        int line = colors.getColor(R.color.line);
        int accent = colors.getColor(R.color.accent);

        root.setBackgroundColor(bg);
        topBar.setBackgroundColor(bg);
        tabBar.setBackgroundColor(bg);
        tabLine.setBackgroundColor(line);
        title.setTextColor(text);

        ColorStateList tabCs = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_selected}, new int[]{}},
                new int[]{accent, dim});
        for (TextView tv : tabs) {
            tv.setTextColor(tabCs);
            tv.setCompoundDrawableTintList(tabCs);
        }

        ColorStateList icon = ColorStateList.valueOf(text);
        for (int id : new int[]{R.id.btn_scan_top, R.id.btn_jump, R.id.btn_search}) {
            ((ImageButton) findViewById(id)).setImageTintList(icon);
        }

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        setLightSystemBars(!create);
    }

    /** Night-resource Context (same palette as {@code values-night}) without flipping the activity's uiMode. */
    private Context nightColors() {
        Configuration n = new Configuration(getResources().getConfiguration());
        n.uiMode = (n.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        return createConfigurationContext(n);
    }

    @SuppressWarnings("deprecation")
    private void setLightSystemBars(boolean light) {
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (light) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    @SuppressWarnings("deprecation")   // predictive back is not enabled; this only closes the search field first
    @Override public void onBackPressed() {
        if (tab == TAB_HISTORY && historyPage.searchOpen()) { historyPage.closeSearch(); return; }
        super.onBackPressed();
    }

    @Override protected void onStart() {
        super.onStart();
        historyPage.onStart();
    }

    @Override protected void onStop() {
        historyPage.onStop();
        super.onStop();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == Export.REQ_EXPORT) {
            App.get().holdForeground = false;
            if (res == RESULT_OK && data != null && data.getData() != null) Export.write(this, data.getData());
        }
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
        applyChrome();
    }

    @Override protected void onPause() {
        createPage.setBright(false, false);   // leaving the page or the app: normal brightness
        super.onPause();
    }

    /** Settings' hidden line: scan every barcode format for one session. */
    void scanNonQr() {
        startActivity(new Intent(this, ScannerActivity.class).putExtra(ScannerActivity.EXTRA_NON_QR, true));
        finish();
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

    /** Background colour currently painted on the top / tab bars (for tests). */
    int chromeBgForTest() {
        ColorDrawable d = (ColorDrawable) topBar.getBackground();
        return d != null ? d.getColor() : 0;
    }
}
