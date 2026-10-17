package com.zmlworks.qrreader;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/** Applies the dark-mode setting (night resources via an override configuration) and system insets. */
public abstract class BaseActivity extends Activity {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Configuration sys = base.getResources().getConfiguration();
        boolean dark = new Prefs(base).dark(sys);
        Configuration c = new Configuration();
        c.uiMode = (sys.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        applyOverrideConfiguration(c);
    }

    boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Pads `root` by the status/navigation bars and the keyboard (targetSdk 35 draws edge to edge). */
    static void padForInsets(View root) {
        // below Android 15 these windows are not edge to edge (the system pads them and resizes for the keyboard)
        if (Build.VERSION.SDK_INT < 35) return;
        final int l = root.getPaddingLeft(), t = root.getPaddingTop(), r = root.getPaddingRight(), b = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, in) -> {
            Insets s = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
            v.setPadding(l + s.left, t + s.top, r + s.right, b + s.bottom);
            return in;
        });
        root.requestApplyInsets();
    }

    @Override protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        Auth.onActivityResult(req, res);
    }
}
