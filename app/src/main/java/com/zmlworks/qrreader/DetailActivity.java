package com.zmlworks.qrreader;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Paint;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * One code: optional name (pen), the full text (a URL is a link: tap = browser, after a confirmation if that setting is
 * on), then one action per row: type-specific ones (call, mail, SMS, map, Wi-Fi) and share / copy / web search /
 * create QR. Lock (protection), name, favourite and delete (with UNDO in the history) are in the top bar.
 * A protected item shows nothing until the user authenticates ({@link Auth}); its window is FLAG_SECURE (no
 * screenshots, blank in Recents).
 */
public final class DetailActivity extends BaseActivity {
    static final String EXTRA_ID = "id";
    private History.Entry e;
    private ImageButton fav;
    private LinearLayout actions;
    private final List<View> extraRows = new ArrayList<>();
    private ImageButton lock;
    /** authenticated on this page (a protected item stays readable here until the page closes) */
    private boolean unlocked, prompted;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_detail);
        padForInsets(findViewById(R.id.root));
        fav = findViewById(R.id.btn_fav);
        actions = findViewById(R.id.actions);
        lock = findViewById(R.id.btn_lock);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_unlock).setOnClickListener(v -> unlock());
        bind();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        unlocked = prompted = false;
        bind();
    }

    private void bind() {
        History h = App.get().history();
        e = h.get(getIntent().getLongExtra(EXTRA_ID, 0));
        TextView title = findViewById(R.id.title), meta = findViewById(R.id.meta), text = findViewById(R.id.text), fields = findViewById(R.id.fields);
        for (View v : extraRows) actions.removeView(v);
        extraRows.clear();
        if (e == null || e.kind == History.GROUP) {
            title.setText("");
            text.setText(R.string.not_found);
            secure(false);
            findViewById(R.id.content).setVisibility(View.VISIBLE);
            findViewById(R.id.locked).setVisibility(View.GONE);
            findViewById(R.id.name).setVisibility(View.GONE);
            for (int id : new int[]{R.id.btn_lock, R.id.btn_name, R.id.btn_fav, R.id.btn_delete, R.id.actions})
                findViewById(id).setVisibility(View.GONE);
            return;
        }
        secure(e.prot);
        boolean locked = e.prot && !unlocked;
        if (locked && Auth.recentlyOk()) { unlocked = true; locked = false; }
        findViewById(R.id.content).setVisibility(locked ? View.GONE : View.VISIBLE);
        findViewById(R.id.locked).setVisibility(locked ? View.VISIBLE : View.GONE);
        for (int id : new int[]{R.id.btn_lock, R.id.btn_name, R.id.btn_fav, R.id.btn_delete})
            findViewById(id).setVisibility(locked ? View.GONE : View.VISIBLE);
        if (locked) {
            title.setText(R.string.protected_title);
            if (!prompted) { prompted = true; getWindow().getDecorView().post(this::unlock); }
            return;
        }
        renderLock();
        lock.setOnClickListener(v -> toggleProtection());
        final String s = e.text;
        final ContentType t = ContentType.parse(s);
        title.setText(e.format != null ? e.format : t.label());
        renderName();
        String m = Actions.longTime(e.time);
        if (e.kind == History.CHILD) {
            List<History.Entry> ch = h.children(e.parent);
            int idx = 0;
            for (int i = 0; i < ch.size(); i++) if (ch.get(i).id == e.id) idx = i + 1;
            m += "\n" + getString(R.string.code_n_of_m, idx, ch.size());
        }
        meta.setText(m);
        text.setText(s);
        boolean link = t.kind == ContentType.Kind.URL;
        text.setTextColor(getColor(link ? R.color.link : R.color.text));
        text.setPaintFlags(link ? text.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG : text.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
        text.setTextIsSelectable(!link);
        if (link) {
            text.setOnClickListener(v -> Actions.openUrl(this, t.uri));
            text.setBackgroundResource(R.drawable.card_bg_clickable);
        } else {
            text.setOnClickListener(null);
            text.setBackgroundResource(R.drawable.card_bg);
        }
        fields.setVisibility(View.GONE);
        switch (t.kind) {
            case TEL:
                row(R.drawable.ic_call, getString(R.string.call, t.number), v -> Actions.dial(this, t.number));
                break;
            case EMAIL:
                row(R.drawable.ic_mail, getString(R.string.send_email), v -> Actions.email(this, t));
                break;
            case SMS:
                row(R.drawable.ic_mail, getString(R.string.send_sms), v -> Actions.sms(this, t.number, t.body));
                break;
            case GEO:
                row(R.drawable.ic_open, getString(R.string.open_map), v -> Actions.view(this, t.uri));
                break;
            case WIFI:
                fields.setVisibility(View.VISIBLE);
                fields.setText(getString(R.string.wifi_fields, t.ssid, t.security == null ? getString(R.string.wifi_open) : t.security,
                        t.password == null ? getString(R.string.none) : t.password));
                row(R.drawable.ic_wifi, getString(R.string.wifi_connect), v -> connectWifi(t));
                if (t.password != null) row(R.drawable.ic_copy, getString(R.string.copy_password), v -> Actions.copy(this, t.password));
                break;
            default:
                break;
        }
        renderFav();
        fav.setOnClickListener(v -> { h.setFavorite(e.id, !e.fav); renderFav(); });
        findViewById(R.id.btn_name).setOnClickListener(v -> editName());
        findViewById(R.id.btn_delete).setOnClickListener(v -> delete());
        findViewById(R.id.act_copy).setOnClickListener(v -> Actions.copy(this, s));
        findViewById(R.id.act_share).setOnClickListener(v -> Actions.share(this, s));
        findViewById(R.id.act_search).setOnClickListener(v -> Actions.search(this, s));
        findViewById(R.id.act_qr).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_CREATE_TEXT, s)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
    }

    /** A type-specific action row, above the standard ones (in the order added). */
    private void row(int icon, String label, View.OnClickListener l) {
        TextView r = (TextView) LayoutInflater.from(this).inflate(R.layout.action_row, actions, false);
        r.setText(label);
        r.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        r.setOnClickListener(l);
        actions.addView(r, extraRows.size());
        extraRows.add(r);
    }

    private void renderName() {
        TextView n = findViewById(R.id.name);
        n.setVisibility(e.name == null ? View.GONE : View.VISIBLE);
        n.setText(e.name);
    }

    /** the last name dialog (tests) */
    AlertDialog nameDialog;

    private void editName() {
        final EditText et = new EditText(this);
        et.setId(R.id.name_input);
        et.setSingleLine(true);
        et.setHint(R.string.name_hint);
        et.setText(e.name == null ? "" : e.name);
        et.setSelection(et.getText().length());
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout box = new LinearLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(et, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        nameDialog = new AlertDialog.Builder(this).setTitle(R.string.rename).setMessage(R.string.name_help).setView(box)
                .setPositiveButton(R.string.ok, (d, w) -> { App.get().history().setName(e.id, et.getText().toString()); renderName(); })
                .setNegativeButton(R.string.cancel, null).show();
    }

    /** "Lock now" (the pill): an open protected page locks at once */
    private final Runnable onAuth = () -> {
        if (e != null && e.prot && unlocked && !Auth.recentlyOk() && !isFinishing()) {
            unlocked = false;
            prompted = true;   // no automatic prompt: the Unlock button is there
            bind();
        }
    };

    @Override protected void onStart() { super.onStart(); Auth.addListener(onAuth); onAuth.run(); }   // locked meanwhile?
    @Override protected void onStop() { Auth.removeListener(onAuth); super.onStop(); }

    private void unlock() {
        if (e == null || isFinishing()) return;
        Auth.require(this, true, getString(R.string.auth_open_title), () -> { unlocked = true; bind(); }, null);
    }

    /** ON: no prompt (needs a screen lock); OFF: asks unless inside the 1-minute window. */
    private void toggleProtection() {
        History h = App.get().history();
        if (!e.prot) {
            if (!Auth.deviceSecure(this)) {
                lastDialog = new AlertDialog.Builder(this).setTitle(R.string.no_lock_title).setMessage(R.string.no_lock_message)
                        .setPositiveButton(R.string.open_settings, (d, w) -> Actions.start(this, new Intent(Settings.ACTION_SECURITY_SETTINGS)))
                        .setNegativeButton(R.string.cancel, null).show();
                return;
            }
            h.setProtected(e.id, true);
            unlocked = true;
            secure(true);
            renderLock();
            android.widget.Toast.makeText(this, R.string.protected_on, android.widget.Toast.LENGTH_SHORT).show();
        } else {
            Auth.require(this, true, getString(R.string.auth_unprotect_title), () -> {
                h.setProtected(e.id, false);
                secure(false);
                renderLock();
                android.widget.Toast.makeText(this, R.string.protected_off, android.widget.Toast.LENGTH_SHORT).show();
            }, null);
        }
    }

    /** the last "no screen lock" dialog (tests) */
    AlertDialog lastDialog;

    private void renderLock() {
        lock.setImageResource(e.prot ? R.drawable.ic_lock_filled : R.drawable.ic_lock);
        lock.setContentDescription(getString(e.prot ? R.string.unprotect : R.string.protect));
        lock.setActivated(e.prot);
    }

    private void secure(boolean on) {
        if (on) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
    }

    boolean lockedForTest() { return findViewById(R.id.locked).getVisibility() == View.VISIBLE; }

    private void renderFav() {
        fav.setImageResource(e.fav ? R.drawable.ic_star_filled : R.drawable.ic_star);
        fav.setContentDescription(getString(e.fav ? R.string.unfavorite : R.string.favorite));
    }

    private void delete() {
        long id = e.id;
        App.get().pendingUndo = App.get().history().remove(id);
        startActivity(new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_HISTORY)
                .putExtra(MainActivity.EXTRA_UNDO, true)
                .putExtra(MainActivity.EXTRA_UNDO_DETAIL, id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
        overridePendingTransition(0, R.anim.fade_out_fast);
    }

    private void connectWifi(ContentType t) {
        if (Build.VERSION.SDK_INT >= 30) {
            String sec = t.security == null ? "" : t.security.toUpperCase(java.util.Locale.ROOT);
            WifiNetworkSuggestion.Builder b = new WifiNetworkSuggestion.Builder().setSsid(t.ssid);
            boolean ok = true;
            try {
                if (t.hidden) b.setIsHiddenSsid(true);
                if (sec.isEmpty()) { /* open network */ }
                else if (sec.contains("SAE") || sec.contains("WPA3")) b.setWpa3Passphrase(t.password == null ? "" : t.password);
                else if (sec.contains("WPA") && t.password != null) b.setWpa2Passphrase(t.password);
                else ok = false;   // WEP and others: not supported by suggestions
                if (ok) {
                    ArrayList<WifiNetworkSuggestion> list = new ArrayList<>();
                    list.add(b.build());
                    Actions.start(this, new Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, list));
                    return;
                }
            } catch (IllegalArgumentException | IllegalStateException ignored) { }
        }
        if (t.password != null) Actions.copy(this, t.password);
        Actions.start(this, new Intent(Settings.ACTION_WIFI_SETTINGS));
    }
}
