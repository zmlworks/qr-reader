package com.zmlworks.qrreader;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Create tab: the QR at the top is re-encoded on every character (on a worker thread, newest text wins); the input
 * sits right under it. Mode, EC level (M) and version are chosen automatically by {@link QrGen}.
 * Tapping the QR toggles the screen brightness between maximum and normal (with a one-second hint); leaving the page
 * or the app restores normal brightness.
 */
final class CreatePage {
    private final MainActivity a;
    private final EditText input;
    private final QrView qr;
    private final TextView info, empty, hint;
    private boolean bright;
    private final Runnable hideHint;
    private final HandlerThread worker = new HandlerThread("qr-encode");
    private final Handler bg, ui = new Handler(Looper.getMainLooper());
    private volatile int seq;
    private QrGen.Qr current;

    CreatePage(MainActivity a, View root) {
        this.a = a;
        input = root.findViewById(R.id.input);
        qr = root.findViewById(R.id.qr);
        info = root.findViewById(R.id.qr_info);
        empty = root.findViewById(R.id.qr_empty);
        hint = root.findViewById(R.id.qr_hint);
        hideHint = () -> hint.animate().alpha(0).setDuration(150).withEndAction(() -> hint.setVisibility(View.GONE)).start();
        root.findViewById(R.id.qr_box).setOnClickListener(v -> setBright(!bright, true));
        worker.start();
        bg = new Handler(worker.getLooper());
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { encode(s.toString()); }
        });
        render(null, "");
    }

    void setText(String t) {
        input.setText(t);
        input.setSelection(input.getText().length());
    }

    String textForTest() { return input.getText().toString(); }
    QrGen.Qr qrForTest() { return current; }

    private void encode(final String text) {
        final int my = ++seq;
        if (text.isEmpty()) { render(null, text); return; }
        bg.post(() -> {
            if (my != seq) return;   // a newer character is already queued
            final QrGen.Qr q = QrGen.encode(text);
            ui.post(() -> { if (my == seq) render(q, text); });
        });
    }

    private void render(QrGen.Qr q, String text) {
        current = q;
        qr.setQr(q);
        boolean none = q == null;
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        empty.setText(text.isEmpty() ? R.string.create_empty : R.string.too_long);
        info.setText(none ? "" : a.getString(R.string.qr_info, q.version, q.size, q.bytes));
    }

    /** @param showHint briefly say what happened (taps); silent when the page or the app is left */
    void setBright(boolean on, boolean showHint) {
        if (on == bright && !showHint) return;
        bright = on;
        android.view.WindowManager.LayoutParams lp = a.getWindow().getAttributes();
        lp.screenBrightness = on ? android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                : android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        a.getWindow().setAttributes(lp);
        if (!showHint) return;
        hint.setText(on ? R.string.brightness_max : R.string.brightness_normal);
        hint.animate().cancel();
        hint.setAlpha(1);
        hint.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideHint);
        ui.postDelayed(hideHint, 1000);
    }

    boolean brightForTest() { return bright; }

    void hideKeyboard() {
        InputMethodManager im = a.getSystemService(InputMethodManager.class);
        if (im != null) im.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    void release() { worker.quitSafely(); }
}
