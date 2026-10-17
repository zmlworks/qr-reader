package com.zmlworks.qrreader;

import android.content.Intent;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** One code: full text, a prominent action for URLs (and tel/mailto/sms/geo/Wi-Fi), copy/share/search/create QR. */
public final class DetailActivity extends BaseActivity {
    static final String EXTRA_ID = "id";
    private History.Entry e;
    private ImageButton fav;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_detail);
        padForInsets(findViewById(R.id.root));
        fav = findViewById(R.id.btn_fav);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        bind();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        bind();
    }

    private void bind() {
        History h = App.get().history();
        e = h.get(getIntent().getLongExtra(EXTRA_ID, 0));
        TextView title = findViewById(R.id.title), meta = findViewById(R.id.meta), text = findViewById(R.id.text), fields = findViewById(R.id.fields);
        Button primary = findViewById(R.id.primary), secondary = findViewById(R.id.secondary);
        if (e == null || e.kind == History.GROUP) {
            title.setText("");
            text.setText(R.string.not_found);
            for (int id : new int[]{R.id.btn_fav, R.id.btn_delete, R.id.act_copy, R.id.act_share, R.id.act_search, R.id.act_qr})
                findViewById(id).setVisibility(View.GONE);
            return;
        }
        final String s = e.text;
        final ContentType t = ContentType.parse(s);
        title.setText(t.label());
        String m = Actions.longTime(e.time);
        if (e.kind == History.CHILD) {
            List<History.Entry> ch = h.children(e.parent);
            int idx = 0;
            for (int i = 0; i < ch.size(); i++) if (ch.get(i).id == e.id) idx = i + 1;
            m += "\n" + getString(R.string.code_n_of_m, idx, ch.size());
        }
        meta.setText(m);
        text.setText(s);
        fields.setVisibility(View.GONE);
        primary.setVisibility(View.GONE);
        secondary.setVisibility(View.GONE);
        switch (t.kind) {
            case URL:
                show(primary, getString(R.string.open_in_browser), v -> Actions.openUrl(this, t.uri));
                break;
            case TEL:
                show(primary, getString(R.string.call, t.number), v -> Actions.dial(this, t.number));
                break;
            case EMAIL:
                show(primary, getString(R.string.send_email), v -> Actions.email(this, t));
                break;
            case SMS:
                show(primary, getString(R.string.send_sms), v -> Actions.sms(this, t.number, t.body));
                break;
            case GEO:
                show(primary, getString(R.string.open_map), v -> Actions.view(this, t.uri));
                break;
            case WIFI:
                fields.setVisibility(View.VISIBLE);
                fields.setText(getString(R.string.wifi_fields, t.ssid, t.security == null ? getString(R.string.wifi_open) : t.security,
                        t.password == null ? getString(R.string.none) : t.password));
                show(primary, getString(R.string.wifi_connect), v -> connectWifi(t));
                if (t.password != null) show(secondary, getString(R.string.copy_password), v -> Actions.copy(this, t.password));
                break;
            default:
                break;
        }
        renderFav();
        fav.setOnClickListener(v -> { h.setFavorite(e.id, !e.fav); renderFav(); });
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

    private static void show(Button b, String label, View.OnClickListener l) {
        b.setVisibility(View.VISIBLE);
        b.setText(label);
        b.setOnClickListener(l);
    }

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
