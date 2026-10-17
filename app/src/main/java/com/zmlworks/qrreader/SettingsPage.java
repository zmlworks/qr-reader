package com.zmlworks.qrreader;

import android.app.AlertDialog;
import android.graphics.Paint;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Settings tab: clear history (with or without favorites) at the top, then lefty, vibrate, dark mode, confirm before
 * browser, search URL, the all-codes row, about & licenses.
 */
final class SettingsPage {
    static final String REPO_URL = "https://github.com/zmlworks/qr-reader";
    private final MainActivity a;
    private final Prefs p;
    private final CheckBox cbLeft, cbVibrate, cbDark, cbConfirm, cbClearFav;
    /** Clear all history also deletes favorites (off at every start: the safe choice) */
    private boolean clearFavorites;
    private final TextView engineValue, clearValue, aboutValue;

    SettingsPage(MainActivity a, View root) {
        this.a = a;
        p = App.get().prefs();
        cbLeft = root.findViewById(R.id.cb_left);
        cbVibrate = root.findViewById(R.id.cb_vibrate);
        cbDark = root.findViewById(R.id.cb_dark);
        cbConfirm = root.findViewById(R.id.cb_confirm);
        cbClearFav = root.findViewById(R.id.cb_clear_fav);
        root.findViewById(R.id.set_clear_fav).setOnClickListener(v -> { clearFavorites = !clearFavorites; refresh(); });
        engineValue = root.findViewById(R.id.engine_value);
        clearValue = root.findViewById(R.id.clear_value);
        aboutValue = root.findViewById(R.id.about_value);
        root.findViewById(R.id.set_left).setOnClickListener(v -> { p.setLeftHanded(!p.leftHanded()); refresh(); });
        root.findViewById(R.id.set_vibrate).setOnClickListener(v -> { p.setVibrate(!p.vibrate()); refresh(); });
        root.findViewById(R.id.set_dark).setOnClickListener(v -> { p.setDark(!a.isNight()); a.recreate(); });
        root.findViewById(R.id.set_confirm).setOnClickListener(v -> { p.setConfirmBrowser(!p.confirmBrowser()); refresh(); });
        root.findViewById(R.id.set_engine).setOnClickListener(v -> editTemplate());
        root.findViewById(R.id.set_clear).setOnClickListener(v -> confirmClear());
        root.findViewById(R.id.set_about).setOnClickListener(v -> about());
        root.findViewById(R.id.set_once).setOnClickListener(v -> a.scanAllCodes());
    }

    void refresh() {
        cbLeft.setChecked(p.leftHanded());
        cbVibrate.setChecked(p.vibrate());
        cbDark.setChecked(a.isNight());
        cbConfirm.setChecked(p.confirmBrowser());
        engineValue.setText(p.searchTemplate());
        cbClearFav.setChecked(clearFavorites);
        int n = App.get().history().planClear(true).codes;
        clearValue.setText(a.getResources().getQuantityString(R.plurals.clear_history_sub, n, n));
        aboutValue.setText(a.getString(R.string.about_sub, version()));
    }

    private String version() {
        try { return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }

    private int dp(int v) { return (int) (v * a.getResources().getDisplayMetrics().density); }

    /** URL template editor: any http(s) URL with %s; empty = back to Google. */
    void editTemplate() {
        final EditText et = new EditText(a);
        et.setId(R.id.template_input);
        et.setSingleLine(true);
        et.setHint(SearchUrl.DEFAULT_TEMPLATE);
        et.setText(p.searchTemplate());
        et.setSelection(et.getText().length());
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout box = new LinearLayout(a);
        box.setPadding(dp(20), dp(10), dp(20), 0);
        box.addView(et, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog d = new AlertDialog.Builder(a).setTitle(R.string.search_engine).setMessage(R.string.custom_url_help).setView(box)
                .setPositiveButton(R.string.ok, null).setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.reset_google, (x, w) -> { p.setSearchTemplate(SearchUrl.DEFAULT_TEMPLATE); refresh(); })
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String t = et.getText().toString().trim();
            if (t.isEmpty()) t = SearchUrl.DEFAULT_TEMPLATE;
            if (!SearchUrl.validTemplate(t)) { et.setError(a.getString(R.string.custom_url_invalid)); return; }
            p.setSearchTemplate(t);
            refresh();
            d.dismiss();
        }));
        d.show();
        lastDialog = d;
    }

    /** the last dialog shown (tests) */
    AlertDialog lastDialog;

    private void confirmClear() {
        History h = App.get().history();
        if (h.size() == 0) return;
        final boolean all = clearFavorites;
        History.ClearPlan plan = h.planClear(all);
        if (plan.codes == 0) { Toast.makeText(a, R.string.clear_only_favorites, Toast.LENGTH_SHORT).show(); return; }
        android.content.res.Resources r = a.getResources();
        StringBuilder m = new StringBuilder(r.getQuantityString(R.plurals.clear_n_codes, plan.codes, plan.codes));
        if (plan.prot > 0) m.append(' ').append(r.getQuantityString(R.plurals.clear_n_protected, plan.prot, plan.prot));
        if (plan.keptFavorites > 0) m.append(' ').append(r.getQuantityString(R.plurals.clear_n_kept, plan.keptFavorites, plan.keptFavorites));
        m.append(' ').append(a.getString(R.string.cannot_undo));
        final int prot = plan.prot;
        lastDialog = new AlertDialog.Builder(a).setTitle(all ? R.string.clear_confirm_all : R.string.clear_confirm_keep_fav)
                .setMessage(m)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    Runnable go = () -> {
                        App.get().history().clear(all);
                        App.get().pendingUndo = null;
                        refresh();
                        Toast.makeText(a, R.string.cleared, Toast.LENGTH_SHORT).show();
                    };
                    // no UNDO here, so protected codes need the same check as opening them
                    if (prot > 0) Auth.require(a, true, a.getString(R.string.auth_clear_title), go, null);
                    else go.run();
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    void about() {
        String notices;
        try (InputStream in = a.getAssets().open("THIRD_PARTY_NOTICES.txt")) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) bo.write(buf, 0, n);
            notices = new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) { notices = ""; }
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(8), dp(20), dp(8));
        TextView head = new TextView(a);
        head.setText(a.getString(R.string.about_head, a.getString(R.string.app_label), version()));
        head.setTextSize(14);
        head.setTextColor(a.getColor(R.color.text));
        col.addView(head);
        TextView link = new TextView(a);
        link.setId(R.id.about_link);
        link.setText(REPO_URL);
        link.setTextSize(15);
        link.setTextColor(a.getColor(R.color.link));
        link.setPaintFlags(link.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        link.setPadding(0, dp(12), 0, dp(12));
        link.setOnClickListener(v -> Actions.openUrl(a, REPO_URL));
        col.addView(link);
        TextView tv = new TextView(a);
        tv.setText(notices);
        tv.setTextSize(12);
        tv.setTextColor(a.getColor(R.color.dim));
        tv.setTextIsSelectable(true);
        col.addView(tv);
        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        lastDialog = new AlertDialog.Builder(a).setTitle(R.string.about).setView(sv).setPositiveButton(R.string.ok, null).show();
    }
}
