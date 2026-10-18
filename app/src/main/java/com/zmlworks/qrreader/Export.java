package com.zmlworks.qrreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * History export (long press on About &amp; licenses): "Export history?" → authentication if there are protected
 * codes (the 60 s window applies) → the system "create document" picker (no storage permission) → one JSON file
 * with every field. There is no import.
 */
final class Export {
    private Export() {}

    static final int REQ_EXPORT = 0x4558;
    static final int FORMAT_VERSION = 1;

    /** the last confirmation dialog (tests) */
    static AlertDialog lastDialog;

    /** e.g. qr-reader-history-20261018.json */
    static String fileName(LocalDate day) { return "qr-reader-history-" + day.format(DateTimeFormatter.BASIC_ISO_DATE) + ".json"; }

    private static Object orNull(String s) { return s == null ? JSONObject.NULL : s; }

    /** Every code (singles and codes of multi scans), the multi scans and the favourites order. */
    static JSONObject json(History h, String appVersion, long now, ZoneId zone) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", "Simple QR Reader");
        root.put("package", "com.zmlworks.qrreader");
        root.put("appVersion", appVersion);
        root.put("formatVersion", FORMAT_VERSION);
        root.put("exportedAt", iso(now, zone));
        JSONArray items = new JSONArray(), groups = new JSONArray();
        List<History.Entry> all = new ArrayList<>(h.rawEntries());
        all.sort((x, y) -> Long.compare(x.id, y.id));
        for (History.Entry e : all) {
            if (e.kind == History.GROUP) {
                List<History.Entry> ch = h.children(e.id);
                if (ch.isEmpty()) continue;
                JSONObject g = new JSONObject();
                g.put("id", e.id);
                g.put("time", e.time);
                g.put("timeIso", iso(e.time, zone));
                JSONArray ids = new JSONArray();
                for (History.Entry c : ch) ids.put(c.id);
                g.put("codes", ids);
                groups.put(g);
                continue;
            }
            JSONObject o = new JSONObject();
            o.put("id", e.id);
            o.put("text", e.text);
            o.put("format", e.format == null ? "QR Code" : e.format);
            o.put("time", e.time);
            o.put("timeIso", iso(e.time, zone));
            o.put("name", orNull(e.name));
            o.put("memo", orNull(e.memo));
            o.put("favorite", e.fav);
            o.put("protected", e.prot);
            o.put("created", e.created);
            o.put("group", e.kind == History.CHILD ? (Object) e.parent : JSONObject.NULL);
            items.put(o);
        }
        root.put("items", items);
        root.put("groups", groups);
        JSONArray order = new JSONArray();
        for (Long id : h.favoriteOrder()) order.put(id);
        root.put("favoritesOrder", order);
        return root;
    }

    private static String iso(long t, ZoneId z) { return Instant.ofEpochMilli(t).atZone(z).toOffsetDateTime().toString(); }

    static int codes(History h) {
        int n = 0;
        for (History.Entry e : h.rawEntries()) if (e.kind != History.GROUP) n++;
        return n;
    }

    static int protectedCodes(History h) {
        int n = 0;
        for (History.Entry e : h.rawEntries()) if (e.kind != History.GROUP && e.prot) n++;
        return n;
    }

    /** Step 1: "Export history?" */
    static void start(Activity a) {
        History h = App.get().history();
        int n = codes(h), prot = protectedCodes(h);
        if (n == 0) { Toast.makeText(a, R.string.export_nothing, Toast.LENGTH_SHORT).show(); return; }
        String msg = a.getResources().getQuantityString(R.plurals.export_message, n, n);
        if (prot > 0) msg += "\n" + a.getResources().getQuantityString(R.plurals.export_message_protected, prot, prot);
        lastDialog = new AlertDialog.Builder(a).setTitle(R.string.export_title).setMessage(msg)
                .setPositiveButton(R.string.export, (d, w) -> {
                    // protected codes go into the file in plain text: the same check as opening them (60 s window)
                    if (protectedCodes(App.get().history()) > 0) Auth.require(a, true, a.getString(R.string.auth_export_title), () -> pick(a), null);
                    else pick(a);
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    /** Step 2: the system picker chooses where the file goes. */
    static void pick(Activity a) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, fileName(LocalDate.now()));
        App.get().holdForeground = true;   // the picker is not "leaving the app"
        try {
            a.startActivityForResult(i, REQ_EXPORT);
        } catch (ActivityNotFoundException | SecurityException e) {
            App.get().holdForeground = false;
            Toast.makeText(a, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }

    /** Step 3: write the file the user picked. */
    static boolean write(Activity a, Uri uri) {
        History h = App.get().history();
        try (OutputStream os = a.getContentResolver().openOutputStream(uri, "wt")) {
            if (os == null) throw new IOException("no stream");
            os.write(json(h, version(a), System.currentTimeMillis(), ZoneId.systemDefault()).toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (IOException | JSONException | SecurityException | IllegalArgumentException e) {
            Toast.makeText(a, R.string.export_failed, Toast.LENGTH_LONG).show();
            return false;
        }
        int n = codes(h);
        Toast.makeText(a, a.getResources().getQuantityString(R.plurals.exported, n, n), Toast.LENGTH_SHORT).show();
        return true;
    }

    static String version(Activity a) {
        try { return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }
}
