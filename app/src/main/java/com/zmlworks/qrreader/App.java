package com.zmlworks.qrreader;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

/**
 * Holds the history and implements "back from the background = scanning": when the app comes to the foreground again
 * (no activity of ours was started) and the top activity is not the scanner, the scanner is brought back
 * (singleTask clears the screens above it). This also applies after the browser or a share target that we opened.
 */
public final class App extends Application implements Application.ActivityLifecycleCallbacks {
    private static App instance;
    private HistoryStore store;
    private Prefs prefs;
    private int started;
    /** true at process start too, so a task restored after the process was killed also opens on the scanner */
    boolean wasInBackground = true;
    /** the last removal that can still be undone (one level, like the snackbar) */
    History.Removal pendingUndo;

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = new Prefs(this);
        registerActivityLifecycleCallbacks(this);
    }

    static App get() { return instance; }

    History history() {
        if (store == null) store = new HistoryStore(this);
        return store.history;
    }

    Prefs prefs() { return prefs; }

    @Override public void onActivityStarted(Activity a) {
        if (started++ == 0 && wasInBackground) {
            wasInBackground = false;
            if (!(a instanceof ScannerActivity)) {
                a.startActivity(new Intent(a, ScannerActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                a.overridePendingTransition(0, 0);
            }
        }
    }

    @Override public void onActivityStopped(Activity a) {
        if (--started == 0 && !a.isChangingConfigurations()) wasInBackground = true;
    }

    @Override public void onActivityCreated(Activity a, Bundle b) { }
    @Override public void onActivityResumed(Activity a) { }
    @Override public void onActivityPaused(Activity a) { }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
    @Override public void onActivityDestroyed(Activity a) { }
}
