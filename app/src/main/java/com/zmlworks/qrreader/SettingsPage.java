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

/** Settings tab: lefty, vibrate, dark mode, confirm before browser, search URL, clear history, about & licenses. */
final class SettingsPage {
    static final String REPO_URL = "https://github.com/zmlworks/qr-reader";
    private final MainActivity a;
    private final Prefs p;
    private final CheckBox cbLeft, cbVibrate, cbDark, cbConfirm;
    private final TextView engineValue, clearValue, aboutValue;

    SettingsPage(MainActivity a, View root) {
        this.a = a;
        p = App.get().prefs();
        cbLeft = root.findViewById(R.id.cb_left);
        cbVibrate = root.findViewById(R.id.cb_vibrate);
        cbDark = root.findViewById(R.id.cb_dark);
        cbConfirm = root.findViewById(R.id.cb_confirm);
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
        int n = App.get().history().topLevel().size();
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
        if (App.get().history().size() == 0) return;
        lastDialog = new AlertDialog.Builder(a).setTitle(R.string.clear_history).setMessage(R.string.clear_history_confirm)
                .setPositiveButton(R.string.clear, (d, w) -> {
                    App.get().history().clear();
                    App.get().pendingUndo = null;
                    refresh();
                    Toast.makeText(a, R.string.cleared, Toast.LENGTH_SHORT).show();
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
