package com.zmlworks.qrreader;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.widget.Toast;

/**
 * Authentication for protected history items: the framework {@link BiometricPrompt} with the device credential
 * (PIN, pattern, password) as fallback on Android 10+, and the system "confirm your screen lock" screen on 8.0-9.
 * No AndroidX. After a success, no protected action (open, copy, remove protection, clear) asks again for
 * {@link #WINDOW_MS}, whatever the user does in between (also leaving and coming back). The window is measured on
 * {@link SystemClock#elapsedRealtime()} so time asleep counts. {@link #lockNow()} ends it early (the "Unlocked" pill).
 */
final class Auth {
    private Auth() {}

    static final long WINDOW_MS = 60_000;
    static final int REQ_AUTH = 0x4155;   // the confirm-credential screen (Android 8.0-9)

    /** tests: non-null = answer every prompt at once with this result */
    static Boolean testResult;
    static int testPrompts;

    private static Runnable pendingOk, pendingCancel;

    /** A screen lock (PIN, pattern, password; biometrics need one too) is set up. */
    static boolean deviceSecure(Context c) {
        KeyguardManager km = c.getSystemService(KeyguardManager.class);
        return km != null && km.isDeviceSecure();
    }

    static boolean recentlyOk() { return remainingMs() > 0; }

    /** ms left in the window (0 = locked) */
    static long remainingMs() {
        long left = App.get().authUntil - SystemClock.elapsedRealtime();
        return left > WINDOW_MS ? 0 : Math.max(0, left);   // > WINDOW_MS: the clock went backwards (reboot): locked
    }

    private static final java.util.List<Runnable> listeners = new java.util.ArrayList<>();
    static void addListener(Runnable r) { listeners.add(r); }
    static void removeListener(Runnable r) { listeners.remove(r); }
    private static void changed() { for (Runnable r : new java.util.ArrayList<>(listeners)) r.run(); }

    /** Ends the window now (tap on the "Unlocked" pill). */
    static void lockNow() {
        App.get().authUntil = 0;
        changed();
    }

    private static void success(Runnable ok) {
        App.get().authUntil = SystemClock.elapsedRealtime() + WINDOW_MS;
        changed();
        if (ok != null) ok.run();
    }

    /**
     * Runs `ok` after a successful authentication (or at once inside the window if `useWindow`; every protected action
     * passes true since 0.4.0). If the device has
     * no screen lock any more there is nothing to check against: `ok` runs (whoever holds the unlocked phone could
     * remove the lock anyway, and the user is not locked out of their own items).
     */
    static void require(Activity a, boolean useWindow, CharSequence title, Runnable ok, Runnable cancel) {
        if (useWindow && recentlyOk()) { ok.run(); return; }
        if (testResult != null) {
            testPrompts++;
            if (testResult) success(ok); else if (cancel != null) cancel.run();
            return;
        }
        if (!deviceSecure(a)) {
            Toast.makeText(a, R.string.auth_no_lock_open, Toast.LENGTH_LONG).show();
            ok.run();
            return;
        }
        if (Build.VERSION.SDK_INT >= 29) prompt(a, title, ok, cancel);
        else confirmCredential(a, title, ok, cancel);
    }

    @SuppressWarnings("deprecation")   // setDeviceCredentialAllowed: the Android 10 way (11+ uses authenticators)
    @android.annotation.TargetApi(29)  // called only on Android 10+ (see require)
    private static void prompt(Activity a, CharSequence title, Runnable ok, Runnable cancel) {
        BiometricPrompt.Builder b = new BiometricPrompt.Builder(a).setTitle(title).setSubtitle(a.getString(R.string.auth_subtitle));
        if (Build.VERSION.SDK_INT >= 30)
            b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        else b.setDeviceCredentialAllowed(true);
        b.setConfirmationRequired(false);
        App.get().holdForeground = true;   // the PIN / pattern screen may cover our activity
        try {
            b.build().authenticate(new CancellationSignal(), a.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                    App.get().holdForeground = false;
                    success(ok);
                }
                @Override public void onAuthenticationError(int code, CharSequence msg) {
                    App.get().holdForeground = false;
                    boolean user = code == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED || code == BiometricPrompt.BIOMETRIC_ERROR_CANCELED;
                    if (!user && msg != null) Toast.makeText(a, msg, Toast.LENGTH_SHORT).show();
                    if (cancel != null) cancel.run();
                }
            });
        } catch (RuntimeException e) {   // e.g. no authenticator usable
            App.get().holdForeground = false;
            confirmCredential(a, title, ok, cancel);
        }
    }

    @SuppressWarnings("deprecation")   // createConfirmDeviceCredentialIntent: the only framework way below Android 10
    private static void confirmCredential(Activity a, CharSequence title, Runnable ok, Runnable cancel) {
        KeyguardManager km = a.getSystemService(KeyguardManager.class);
        Intent i = km == null ? null : km.createConfirmDeviceCredentialIntent(title, a.getString(R.string.auth_subtitle));
        if (i == null) { ok.run(); return; }   // no screen lock: nothing to confirm
        pendingOk = ok;
        pendingCancel = cancel;
        App.get().holdForeground = true;   // the system screen is not "leaving the app"
        try {
            a.startActivityForResult(i, REQ_AUTH);
        } catch (RuntimeException e) {
            App.get().holdForeground = false;
            pendingOk = pendingCancel = null;
            if (cancel != null) cancel.run();
        }
    }

    /** Forwarded from {@link BaseActivity#onActivityResult}; true if it was ours. */
    static boolean onActivityResult(int req, int res) {
        if (req != REQ_AUTH) return false;
        App.get().holdForeground = false;
        Runnable ok = pendingOk, cancel = pendingCancel;
        pendingOk = pendingCancel = null;
        if (res == Activity.RESULT_OK) success(ok);
        else if (cancel != null) cancel.run();
        return true;
    }
}
