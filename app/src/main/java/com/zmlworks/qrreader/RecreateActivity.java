package com.zmlworks.qrreader;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * "Recreate this code" for a non-QR item: the code in its original format (white card, quiet zones, as wide as the
 * screen, the text below it). Tapping the code asks before maximum brightness, like the Create page; tapping again
 * restores normal brightness. Only opened for codes that
 * can be recreated (the Detail page greys the row out otherwise). "Show as QR code" is always offered.
 */
public final class RecreateActivity extends BaseActivity {
    static final String EXTRA_FORMAT = "format", EXTRA_TEXT = "text";
    private boolean bright;
    private TextView hint;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable hideHint = () -> { if (hint != null) hint.setVisibility(View.GONE); };
    /** last brightness dialog (tests) */
    AlertDialog brightDialog;
    Recreate.Code code;

    static Intent intent(android.content.Context c, String format, String text) {
        return new Intent(c, RecreateActivity.class).putExtra(EXTRA_FORMAT, format).putExtra(EXTRA_TEXT, text);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_recreate);
        padForInsets(findViewById(R.id.root));
        String format = getIntent().getStringExtra(EXTRA_FORMAT), text = getIntent().getStringExtra(EXTRA_TEXT);
        if (text == null) text = "";
        final String s = text;
        ((TextView) findViewById(R.id.title)).setText(format);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        hint = findViewById(R.id.code_hint);
        try {
            code = Recreate.encode(format, s);
        } catch (Recreate.InvalidContent ex) {
            code = null;
        }
        if (code == null) { finish(); return; }   // the Detail page greys the row out, so this is not reached
        CodeView cv = findViewById(R.id.code);
        cv.setCode(code);
        ((TextView) findViewById(R.id.code_text)).setText(s);
        findViewById(R.id.code_box).setOnClickListener(v -> onCodeTap());
        findViewById(R.id.act_show_qr).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_CREATE_TEXT, s)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
    }

    private void onCodeTap() {
        if (bright) { setBright(false, true); return; }
        brightDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.brightness_confirm_title)
                .setPositiveButton(R.string.ok, (d, w) -> setBright(true, true))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    void setBright(boolean on, boolean showHint) {
        bright = on;
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = on ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
        if (!showHint) return;
        hint.setText(on ? R.string.brightness_max : R.string.brightness_normal);
        hint.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideHint);
        ui.postDelayed(hideHint, 1000);
    }

    boolean brightForTest() { return bright; }

    @Override protected void onPause() {
        setBright(false, false);   // leaving: normal brightness
        super.onPause();
    }
}
