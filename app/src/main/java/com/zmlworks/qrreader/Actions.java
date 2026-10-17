package com.zmlworks.qrreader;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Toast;

/** Small shared actions: copy, share, open in browser / other apps, web search, vibrate. */
final class Actions {
    private Actions() {}

    static void copy(Context c, String text) {
        ClipboardManager cm = c.getSystemService(ClipboardManager.class);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("QR", text));
        Toast.makeText(c, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    static void share(Activity a, String text) {
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        start(a, Intent.createChooser(i, a.getString(R.string.share)));
    }

    static void openUrl(Activity a, String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE);
        start(a, i);
    }

    static void view(Activity a, String uri) { start(a, new Intent(Intent.ACTION_VIEW, Uri.parse(uri))); }

    static void dial(Activity a, String number) { start(a, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))); }

    static void sms(Activity a, String number, String body) {
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)));
        if (body != null) i.putExtra("sms_body", body);
        start(a, i);
    }

    static void email(Activity a, ContentType t) {
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + (t.address == null ? "" : Uri.encode(t.address, "@"))));
        if (t.address != null && !t.address.isEmpty()) i.putExtra(Intent.EXTRA_EMAIL, new String[]{t.address});
        if (t.subject != null) i.putExtra(Intent.EXTRA_SUBJECT, t.subject);
        if (t.body != null) i.putExtra(Intent.EXTRA_TEXT, t.body);
        start(a, i);
    }

    static void search(Activity a, String text) {
        Prefs p = App.get().prefs();
        openUrl(a, SearchUrl.build(p.engine(), p.customTemplate(), text));
    }

    static void start(Activity a, Intent i) {
        try { a.startActivity(i); } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(a, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }

    static void vibrate(Context c, int ms) {
        if (!App.get().prefs().vibrate()) return;
        Vibrator v = c.getSystemService(Vibrator.class);
        if (v == null || !v.hasVibrator()) return;
        try {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (RuntimeException ignored) { }
    }

    /** Short relative time for lists: "14:05" today, "Oct 3" this year, else "2025-10-03". */
    static String shortTime(long t, long now) {
        java.util.Calendar a = java.util.Calendar.getInstance(), b = java.util.Calendar.getInstance();
        a.setTimeInMillis(t); b.setTimeInMillis(now);
        java.util.Locale l = java.util.Locale.getDefault();
        if (a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR)) {
            if (a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR))
                return android.text.format.DateFormat.format("HH:mm", a).toString();
            return android.text.format.DateFormat.format(android.text.format.DateFormat.getBestDateTimePattern(l, "MMMd"), a).toString();
        }
        return android.text.format.DateFormat.format("yyyy-MM-dd", a).toString();
    }

    static String longTime(long t) {
        java.util.Locale l = java.util.Locale.getDefault();
        return new java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(l, "yyyyMMMdHHmmss"), l).format(new java.util.Date(t));
    }

    static boolean atLeast(int api) { return Build.VERSION.SDK_INT >= api; }
}
