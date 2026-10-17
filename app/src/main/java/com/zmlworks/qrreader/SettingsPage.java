package com.zmlworks.qrreader;

import android.app.AlertDialog;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Settings tab: left-handed, vibrate, theme, search engine, clear history, about & licenses. */
final class SettingsPage {
    private final MainActivity a;
    private final Prefs p;
    private final Switch swLeft, swVibrate;
    private final TextView themeValue, engineValue, clearValue, aboutValue;

    SettingsPage(MainActivity a, View root) {
        this.a = a;
        p = App.get().prefs();
        swLeft = root.findViewById(R.id.sw_left);
        swVibrate = root.findViewById(R.id.sw_vibrate);
        themeValue = root.findViewById(R.id.theme_value);
        engineValue = root.findViewById(R.id.engine_value);
        clearValue = root.findViewById(R.id.clear_value);
        aboutValue = root.findViewById(R.id.about_value);
        root.findViewById(R.id.set_left).setOnClickListener(v -> { p.setLeftHanded(!p.leftHanded()); refresh(); });
        root.findViewById(R.id.set_vibrate).setOnClickListener(v -> { p.setVibrate(!p.vibrate()); refresh(); });
        root.findViewById(R.id.set_theme).setOnClickListener(v -> chooseTheme());
        root.findViewById(R.id.set_engine).setOnClickListener(v -> chooseEngine());
        root.findViewById(R.id.set_clear).setOnClickListener(v -> confirmClear());
        root.findViewById(R.id.set_about).setOnClickListener(v -> about());
    }

    void refresh() {
        swLeft.setChecked(p.leftHanded());
        swVibrate.setChecked(p.vibrate());
        int t = p.theme();
        themeValue.setText(t == Prefs.THEME_LIGHT ? R.string.theme_light : t == Prefs.THEME_DARK ? R.string.theme_dark : R.string.theme_system);
        String e = p.engine();
        engineValue.setText(SearchUrl.CUSTOM.equals(e) ? a.getString(R.string.custom_url) + " · " + p.customTemplate() : SearchUrl.name(e));
        int n = App.get().history().topLevel().size();
        clearValue.setText(a.getResources().getQuantityString(R.plurals.clear_history_sub, n, n));
        aboutValue.setText(a.getString(R.string.about_sub, version()));
    }

    private String version() {
        try { return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }

    private void chooseTheme() {
        String[] names = {a.getString(R.string.theme_system), a.getString(R.string.theme_light), a.getString(R.string.theme_dark)};
        new AlertDialog.Builder(a).setTitle(R.string.theme).setSingleChoiceItems(names, p.theme(), (d, which) -> {
            d.dismiss();
            if (which != p.theme()) { p.setTheme(which); a.recreate(); }
        }).setNegativeButton(R.string.cancel, null).show();
    }

    private void chooseEngine() {
        int cur = 0;
        for (int i = 0; i < SearchUrl.KEYS.length; i++) if (SearchUrl.KEYS[i].equals(p.engine())) cur = i;
        new AlertDialog.Builder(a).setTitle(R.string.search_engine).setSingleChoiceItems(SearchUrl.NAMES, cur, (d, which) -> {
            d.dismiss();
            if (SearchUrl.CUSTOM.equals(SearchUrl.KEYS[which])) customTemplate();
            else { p.setEngine(SearchUrl.KEYS[which]); refresh(); }
        }).setNegativeButton(R.string.cancel, null).show();
    }

    private void customTemplate() {
        final EditText et = new EditText(a);
        et.setSingleLine(true);
        et.setHint(R.string.custom_url_hint);
        et.setText(p.customTemplate());
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout box = new LinearLayout(a);
        int pad = (int) (20 * a.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(et, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog d = new AlertDialog.Builder(a).setTitle(R.string.custom_url).setMessage(R.string.custom_url_help).setView(box)
                .setPositiveButton(R.string.ok, null).setNegativeButton(R.string.cancel, null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String t = et.getText().toString().trim();
            if (!SearchUrl.validTemplate(t)) { et.setError(a.getString(R.string.custom_url_invalid)); return; }
            p.setCustomTemplate(t);
            p.setEngine(SearchUrl.CUSTOM);
            refresh();
            d.dismiss();
        }));
        d.show();
    }

    private void confirmClear() {
        if (App.get().history().size() == 0) return;
        new AlertDialog.Builder(a).setTitle(R.string.clear_history).setMessage(R.string.clear_history_confirm)
                .setPositiveButton(R.string.clear, (d, w) -> {
                    App.get().history().clear();
                    App.get().pendingUndo = null;
                    refresh();
                    Toast.makeText(a, R.string.cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    private void about() {
        String notices;
        try (InputStream in = a.getAssets().open("THIRD_PARTY_NOTICES.txt")) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) bo.write(buf, 0, n);
            notices = new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) { notices = ""; }
        String text = a.getString(R.string.app_label) + " " + version() + "\n"
                + "Copyright (c) 2026 zmlworks · MIT License\n"
                + "No internet access, no analytics: scans stay on this device.\n"
                + "Made by the AI assistant Grok Bot for zmlworks.\n\n" + notices;
        TextView tv = new TextView(a);
        int pad = (int) (20 * a.getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad / 2, pad, pad / 2);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(a);
        sv.addView(tv);
        new AlertDialog.Builder(a).setTitle(R.string.about).setView(sv).setPositiveButton(R.string.ok, null).show();
    }
}
