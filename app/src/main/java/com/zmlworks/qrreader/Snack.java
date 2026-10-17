package com.zmlworks.qrreader;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

/** Minimal snackbar (message + UNDO) on a layout's R.id.snack view. */
final class Snack {
    private final View root;
    private final TextView msg, action;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable hide = this::hide;

    Snack(View host) {
        root = host.findViewById(R.id.snack);
        msg = host.findViewById(R.id.snack_text);
        action = host.findViewById(R.id.snack_action);
    }

    void show(CharSequence text, Runnable undo) {
        msg.setText(text);
        action.setVisibility(undo == null ? View.GONE : View.VISIBLE);
        action.setOnClickListener(v -> { hide(); if (undo != null) undo.run(); });
        root.setVisibility(View.VISIBLE);
        root.setAlpha(0);
        root.animate().alpha(1).setDuration(120).start();
        h.removeCallbacks(hide);
        h.postDelayed(hide, 5000);
    }

    void hide() {
        h.removeCallbacks(hide);
        root.setVisibility(View.GONE);
    }
}
