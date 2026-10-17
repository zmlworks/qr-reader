package com.zmlworks.qrreader;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Create tab: the QR above the input is re-encoded on every character (on a worker thread, newest text wins).
 * Mode, EC level (M) and version are chosen automatically by {@link QrGen}.
 */
final class CreatePage {
    private final MainActivity a;
    private final EditText input;
    private final QrView qr;
    private final TextView info, empty;
    private final View actions;
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
        actions = root.findViewById(R.id.qr_actions);
        worker.start();
        bg = new Handler(worker.getLooper());
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { encode(s.toString()); }
        });
        root.findViewById(R.id.input_clear).setOnClickListener(v -> input.setText(""));
        root.findViewById(R.id.qr_share).setOnClickListener(v -> shareImage());
        View save = root.findViewById(R.id.qr_save);
        if (Build.VERSION.SDK_INT >= 29) save.setOnClickListener(v -> saveImage());
        else save.setVisibility(View.GONE);   // Pictures/ without a storage permission needs API 29
        root.findViewById(R.id.qr_copy).setOnClickListener(v -> { String t = input.getText().toString(); if (!t.isEmpty()) Actions.copy(a, t); });
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
        actions.setVisibility(none ? View.INVISIBLE : View.VISIBLE);
    }

    void hideKeyboard() {
        InputMethodManager im = a.getSystemService(InputMethodManager.class);
        if (im != null) im.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    /** PNG at an integer scale of about 1000 px. */
    private Bitmap image() {
        QrGen.Qr q = current;
        if (q == null) return null;
        int n = q.size + 2 * QrGen.QUIET, scale = Math.max(4, 1000 / n);
        return Bitmap.createBitmap(QrGen.pixels(q, scale), n * scale, n * scale, Bitmap.Config.ARGB_8888);
    }

    private void shareImage() {
        Bitmap b = image();
        if (b == null) return;
        File f = new File(ShareProvider.dir(a), "qr.png");
        try (FileOutputStream os = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, os);
        } catch (IOException e) {
            Toast.makeText(a, R.string.save_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri u = ShareProvider.uriFor(a, f);
        Intent i = new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, u)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(android.content.ClipData.newRawUri("QR", u));
        Actions.start(a, Intent.createChooser(i, a.getString(R.string.share_image)));
    }

    private void saveImage() {
        if (Build.VERSION.SDK_INT < 29) return;
        Bitmap b = image();
        if (b == null) return;
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, "QR_" + System.currentTimeMillis() + ".png");
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Simple QR Reader");
        Uri u = null;
        try {
            u = a.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (u == null) throw new IOException("insert");
            try (OutputStream os = a.getContentResolver().openOutputStream(u)) {
                if (os == null || !b.compress(Bitmap.CompressFormat.PNG, 100, os)) throw new IOException("write");
            }
            Toast.makeText(a, R.string.saved, Toast.LENGTH_SHORT).show();
        } catch (IOException | RuntimeException e) {
            if (u != null) try { a.getContentResolver().delete(u, null, null); } catch (RuntimeException ignored) { }
            Toast.makeText(a, R.string.save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    void release() { worker.quitSafely(); }
}
