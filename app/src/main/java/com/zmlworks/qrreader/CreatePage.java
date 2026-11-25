package com.zmlworks.qrreader;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Create tab: the symbol at the top is re-encoded on every character (on a worker thread, newest text wins); the input
 * sits right under it, with a small "Save to history" button in between (adds a "Created" item; blank or invalid text
 * is ignored and saving the same text and format twice in a row only says "Already saved"). A small format switch under
 * the input chooses QR (default, {@link QrGen}), Code 128 or EAN-13 / JAN ({@link Recreate}).
 * Tapping the symbol asks before raising the screen brightness to maximum; tapping again restores normal brightness
 * immediately (no confirm). Leaving the page or the app restores normal brightness.
 */
final class CreatePage {
    /** Empty-field placeholder: a real QR that encodes this payload. */
    static final String PLACEHOLDER_TEXT = "no data";
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface NORMAL = Typeface.create("sans-serif", Typeface.NORMAL);
    private final MainActivity a;
    private final EditText input;
    private final QrView qr;
    private final CodeView code;
    private final TextView info, empty, hint, save, fmtQr, fmt128, fmtEan;
    private boolean bright;
    private String format = CreateCodec.QR;
    private final Runnable hideHint;
    private final HandlerThread worker = new HandlerThread("qr-encode");
    private final Handler bg, ui = new Handler(Looper.getMainLooper());
    private volatile int seq;
    private QrGen.Qr current;
    private Recreate.Code currentCode;
    /** Last brightness-confirm dialog (tests click OK / Cancel). */
    AlertDialog brightDialog;

    CreatePage(MainActivity a, View root) {
        this.a = a;
        input = root.findViewById(R.id.input);
        qr = root.findViewById(R.id.qr);
        code = root.findViewById(R.id.create_code);
        info = root.findViewById(R.id.qr_info);
        empty = root.findViewById(R.id.qr_empty);
        hint = root.findViewById(R.id.qr_hint);
        save = root.findViewById(R.id.save_history);
        fmtQr = root.findViewById(R.id.fmt_qr);
        fmt128 = root.findViewById(R.id.fmt_code128);
        fmtEan = root.findViewById(R.id.fmt_ean);
        save.setOnClickListener(v -> saveToHistory());
        fmtQr.setOnClickListener(v -> setFormat(CreateCodec.QR));
        fmt128.setOnClickListener(v -> setFormat(CreateCodec.CODE_128));
        fmtEan.setOnClickListener(v -> setFormat(CreateCodec.EAN13));
        hideHint = () -> hint.animate().alpha(0).setDuration(150).withEndAction(() -> hint.setVisibility(View.GONE)).start();
        root.findViewById(R.id.qr_box).setOnClickListener(v -> onQrTap());
        worker.start();
        bg = new Handler(worker.getLooper());
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { encode(s.toString()); renderSave(); }
        });
        renderFormats();
        render(null, null, "");
        renderSave();
    }

    void setFormat(String fmt) {
        if (fmt.equals(format)) return;
        format = fmt;
        input.setInputType(CreateCodec.EAN13.equals(fmt)
                ? InputType.TYPE_CLASS_NUMBER
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(CreateCodec.EAN13.equals(fmt) ? R.string.create_hint_ean : R.string.create_hint);
        a.findViewById(R.id.qr_box).setContentDescription(a.getString(
                CreateCodec.QR.equals(fmt) ? R.string.brightness_toggle : R.string.brightness_toggle_code));
        renderFormats();
        encode(input.getText().toString());
        renderSave();
    }

    private void renderFormats() {
        styleFmt(fmtQr, CreateCodec.QR.equals(format));
        styleFmt(fmt128, CreateCodec.CODE_128.equals(format));
        styleFmt(fmtEan, CreateCodec.EAN13.equals(format));
    }

    private void styleFmt(TextView v, boolean on) {
        v.setTextColor(a.getColor(on ? R.color.create_save : R.color.create_dim));
        v.setTypeface(on ? MEDIUM : NORMAL);
    }

    /** Confirm only when raising to max; restore to normal immediately. */
    private void onQrTap() {
        if (bright) {
            setBright(false, true);
            return;
        }
        brightDialog = new AlertDialog.Builder(a)
                .setTitle(R.string.brightness_confirm_title)
                .setPositiveButton(R.string.ok, (d, w) -> setBright(true, true))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void renderSave() {
        boolean on = canSave();
        save.setEnabled(on);
        save.setAlpha(on ? 1f : 0.4f);
    }

    private boolean canSave() {
        String t = input.getText().toString();
        if (t.trim().isEmpty()) return false;
        try { CreateCodec.payload(format, t); return true; }
        catch (Recreate.InvalidContent e) { return false; }
    }

    /** "Save to history": the current text as a Created item, in the chosen format. */
    int saveToHistory() {
        String text;
        try { text = CreateCodec.payload(format, input.getText().toString()); }
        catch (Recreate.InvalidContent e) { return History.SAVE_EMPTY; }
        int r = App.get().history().saveCreated(text, System.currentTimeMillis(), CreateCodec.historyFormat(format));
        if (r == History.SAVED) android.widget.Toast.makeText(a, R.string.saved_to_history, android.widget.Toast.LENGTH_SHORT).show();
        else if (r == History.SAVE_DUPLICATE) android.widget.Toast.makeText(a, R.string.already_saved, android.widget.Toast.LENGTH_SHORT).show();
        return r;
    }

    void setText(String t) {
        input.setText(t);
        input.setSelection(input.getText().length());
    }

    String textForTest() { return input.getText().toString(); }
    String formatForTest() { return format; }
    QrGen.Qr qrForTest() { return current; }
    Recreate.Code codeForTest() { return currentCode; }

    private void encode(final String text) {
        final int my = ++seq;
        final String fmt = format;
        bg.post(() -> {
            if (my != seq) return;
            QrGen.Qr q = null;
            Recreate.Code c = null;
            if (CreateCodec.QR.equals(fmt)) {
                if (text.isEmpty()) q = QrGen.encode(PLACEHOLDER_TEXT);
                else q = QrGen.encode(text);
            } else if (!text.isEmpty()) {
                try { c = Recreate.encode(fmt, CreateCodec.payload(fmt, text)); }
                catch (Recreate.InvalidContent ignored) { }
            }
            final QrGen.Qr qrOut = q;
            final Recreate.Code codeOut = c;
            ui.post(() -> { if (my == seq) render(qrOut, codeOut, text); });
        });
    }

    private void render(QrGen.Qr q, Recreate.Code c, String text) {
        boolean qrMode = CreateCodec.QR.equals(format);
        current = qrMode && !text.isEmpty() ? q : null;
        currentCode = qrMode ? null : c;
        qr.setVisibility(qrMode ? View.VISIBLE : View.GONE);
        code.setVisibility(qrMode ? View.GONE : View.VISIBLE);
        if (qrMode) {
            boolean emptyText = text.isEmpty();
            boolean tooLong = !emptyText && q == null;
            qr.setQr(emptyText ? QrGen.encode(PLACEHOLDER_TEXT) : q);
            code.setCode(null);
            info.setText(emptyText || q == null ? "" : a.getString(R.string.qr_info, q.version, q.size, q.bytes));
            empty.setVisibility(tooLong ? View.VISIBLE : View.GONE);
        } else {
            qr.setQr(null);
            code.setCode(c);
            empty.setVisibility(View.GONE);
            if (text.isEmpty() || c != null) info.setText("");
            else if (CreateCodec.EAN13.equals(format) && CreateCodec.ean13BadCheck(text))
                info.setText(R.string.create_invalid_ean_check);
            else if (CreateCodec.EAN13.equals(format)) info.setText(R.string.create_invalid_ean);
            else info.setText(R.string.create_invalid_code128);
        }
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
