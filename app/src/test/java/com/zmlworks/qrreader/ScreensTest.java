package com.zmlworks.qrreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import android.app.AlertDialog;
import android.content.res.Configuration;
import android.net.Uri;
import android.widget.CheckBox;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The real activities on the JVM (Robolectric, native graphics): behaviour checks + PNG screenshots in
 * build/reports/screens/. The camera preview is a drawn stand-in (Robolectric has no camera).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreensTest {
    static final int W = 1233, H = 2673;
    static final File OUT = new File("build/reports/screens");
    History h;
    long url, wifi, jp, group;

    @Before public void setUp() {
        App app = (App) RuntimeEnvironment.getApplication();
        app.wasInBackground = false;
        app.authUntil = 0;
        app.nonQr = false;
        app.holdForeground = false;
        Auth.testResult = null;
        Auth.testPrompts = 0;
        Obscured.forceBitmapForTest = true;   // screenshots: see Obscured
        shadowOf(app.getSystemService(android.app.KeyguardManager.class)).setIsDeviceSecure(true);
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA);
        Prefs p = app.prefs();
        p.setLeftHanded(false);
        p.setMulti(false);
        p.setDark(false);
        p.setVibrate(true);
        p.setConfirmBrowser(true);
        p.setSearchTemplate(SearchUrl.DEFAULT_TEMPLATE);
        h = app.history();
        h.clear();
        long now = System.currentTimeMillis();
        h.addSingle("Meeting room B, 3rd floor — bring the badge", now - 86_400_000L * 3);
        jp = h.addSingle("こんにちは、世界！ QR コードのテスト", now - 86_400_000L).id;
        h.setFavorite(jp, true);
        h.addSingle("tel:+81-3-1234-5678", now - 7_200_000);
        History.Entry g = h.newGroup(now - 3_600_000);
        for (int i = 1; i <= 5; i++) h.addChild(g.id, "INV-2026-10-" + (100 + i), now - 3_600_000 + i);
        group = g.id;
        wifi = h.addSingle("WIFI:T:WPA;S:Cafe-Guest;P:espresso2026;;", now - 600_000).id;
        h.setName(wifi, "Café guest Wi-Fi");
        url = h.addSingle("https://github.com/zmlworks/qr-reader", now - 60_000).id;
        OUT.mkdirs();
    }

    static void layout(Activity a) {
        View root = a.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, W, H);
        ShadowLooper.idleMainLooper();
        root.jumpDrawablesToCurrentState();   // checkbox animations: draw the end state
        root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, W, H);
    }

    static void shot(Activity a, String name, Bitmap background) throws Exception { shot(a, name, background, null); }

    /** Screenshot of the activity, with `dialog` (if any) drawn on top over a dim layer. */
    static void shot(Activity a, String name, Bitmap background, android.app.Dialog dialog) throws Exception {
        layout(a);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        if (background != null) c.drawBitmap(background, 0, 0, null);
        a.getWindow().getDecorView().draw(c);
        if (dialog != null) {
            ShadowLooper.idleMainLooper();
            c.drawColor(0x99000000);
            View dv = dialog.getWindow().getDecorView();
            dv.jumpDrawablesToCurrentState();
            int dw = (int) (W * 0.86f);
            dv.measure(View.MeasureSpec.makeMeasureSpec(dw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((int) (H * 0.8f), View.MeasureSpec.AT_MOST));
            dv.layout(0, 0, dw, dv.getMeasuredHeight());
            c.save();
            c.translate((W - dw) / 2f, (H - dv.getMeasuredHeight()) / 2f);
            dv.draw(c);
            c.restore();
        }
        try (FileOutputStream os = new FileOutputStream(new File(OUT, name + ".png"))) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
    }

    static int dp(Activity a, float v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }

    static int centerY(View v) {
        int[] loc = new int[2];
        v.getLocationInWindow(loc);
        return loc[1] + v.getHeight() / 2;
    }

    static int topIn(View v) { int[] l = new int[2]; v.getLocationInWindow(l); return l[1]; }

    /** A PNG "photo" with the given codes, for Read from image. */
    static Uri photo(String name, String[] texts) throws Exception {
        int w = 1400, h = 1000;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFFCFC8BE);
        for (int i = 0; i < texts.length; i++) {
            QrGen.Qr q = QrGen.encode(texts[i]);
            int n = q.size + 2 * QrGen.QUIET, scale = 300 / n + 1;
            Bitmap qb = Bitmap.createBitmap(QrGen.pixels(q, scale), n * scale, n * scale, Bitmap.Config.ARGB_8888);
            c.drawBitmap(qb, 60 + i * 450, 200 + (i % 2) * 220, null);
        }
        File f = new File(OUT.getParentFile(), name + ".png");
        try (FileOutputStream os = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.PNG, 100, os); }
        return Uri.fromFile(f);
    }

    static void waitFor(java.util.function.BooleanSupplier ok) throws InterruptedException {
        for (int i = 0; i < 1000 && !ok.getAsBoolean(); i++) { Thread.sleep(5); ShadowLooper.idleMainLooper(); }
    }

    /** Stand-in for the camera image: a desk-like gradient with one or three printed QR codes. */
    static Bitmap fakeCamera(String[] texts, float[][] place, List<float[]> outCorners) {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, W, H, 0xFF6B6259, 0xFF2E2A27, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        for (int i = 0; i < texts.length; i++) {
            QrGen.Qr q = QrGen.encode(texts[i]);
            int n = q.size + 2 * QrGen.QUIET;
            Bitmap qb = Bitmap.createBitmap(QrGen.pixels(q, 1), n, n, Bitmap.Config.ARGB_8888);
            float cx = place[i][0], cy = place[i][1], side = place[i][2], rot = place[i][3];
            Matrix m = new Matrix();
            m.setScale(side / n, side / n);
            m.postTranslate(-side / 2, -side / 2);
            m.postRotate(rot);
            m.postTranslate(cx, cy);
            Paint bp = new Paint();
            bp.setFilterBitmap(true);
            bp.setColorFilter(new android.graphics.LightingColorFilter(0xFFE8E2DA, 0x00080808));   // paper, not pure white
            c.drawBitmap(qb, m, bp);
            float q0 = QrGen.QUIET, q1 = n - QrGen.QUIET;   // bitmap px (1 px per module)
            float[] pts = {q0, q0, q1, q0, q1, q1, q0, q1};
            m.mapPoints(pts);
            outCorners.add(pts);
        }
        return b;
    }

    private ScannerActivity scanner() {
        ActivityController<ScannerActivity> ac = Robolectric.buildActivity(ScannerActivity.class).setup();
        ScannerActivity s = ac.get();
        // the stand-in camera image is drawn below the window
        s.findViewById(R.id.preview).setVisibility(View.INVISIBLE);
        ((View) s.findViewById(R.id.preview).getParent()).setBackground(null);
        s.getWindow().setBackgroundDrawable(null);
        for (int i = 0; i < 50; i++) {   // let the camera thread report "no camera" (Robolectric has none), then hide it
            try { Thread.sleep(10); } catch (InterruptedException ignored) { }
            ShadowLooper.idleMainLooper();
        }
        s.hideMessageForTest();
        s.setZoomForTest(1, 8, 2.4f);
        return s;
    }

    /** Multi and image at the top (below the status bar), flash a bit above ×, × (big, framed) a bit above half height; one side. */
    static void checkColumn(ScannerActivity s) {
        View multi = s.findViewById(R.id.btn_multi), image = s.findViewById(R.id.btn_image), flash = s.findViewById(R.id.btn_flash),
                close = s.findViewById(R.id.btn_close);
        assertTrue("multi near the top: " + topIn(multi), topIn(multi) < dp(s, 80));
        assertTrue(centerY(multi) < centerY(image));
        assertTrue("image near the top too", topIn(image) + image.getHeight() < dp(s, 160));
        assertTrue("a big gap down to flash: " + (topIn(flash) - (topIn(image) + image.getHeight())) / s.getResources().getDisplayMetrics().density, topIn(flash) - (topIn(image) + image.getHeight()) > dp(s, 80));
        assertTrue(centerY(flash) < centerY(close));
        int g2 = topIn(close) - (topIn(flash) + flash.getHeight());
        assertTrue("flash sits a bit above ×: " + g2, g2 >= dp(s, 24) && g2 <= dp(s, 48));
        assertTrue("× a bit above half height: " + centerY(close), Math.abs(centerY(close) - Math.round(H * 0.44f)) <= dp(s, 2));
        assertTrue("big × button: " + close.getWidth(), close.getWidth() >= dp(s, 72) && close.getHeight() >= dp(s, 72));
        assertNotNull("rounded-square frame", close.getBackground());
        for (View v : new View[]{image, flash, close}) assertEquals("same column", centerX(multi), centerX(v));
    }

    static int centerX(View v) { int[] l = new int[2]; v.getLocationInWindow(l); return l[0] + v.getWidth() / 2; }

    static String rowTitle(SwipeListView list, int i) { return ((TextView) list.getChildAt(i).findViewById(R.id.row_title)).getText().toString(); }

    static void swipeLeft(SwipeListView list, View row) {
        int y = row.getTop() + row.getHeight() / 2;
        long t = android.os.SystemClock.uptimeMillis();
        list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, 1100, y, 0));
        for (int i = 1; i <= 10; i++) list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + i * 16, android.view.MotionEvent.ACTION_MOVE, 1100 - i * 90, y, 0));
        list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 200, android.view.MotionEvent.ACTION_UP, 200, y, 0));
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test public void scannerRightHanded() throws Exception {
        ScannerActivity s = scanner();
        layout(s);
        View buttons = s.findViewById(R.id.buttons);
        assertEquals(Gravity.TOP | Gravity.END, ((FrameLayout.LayoutParams) buttons.getLayoutParams()).gravity);
        assertTrue("buttons on the right", buttons.getLeft() > W / 2);
        assertTrue("zoom bar on the left", s.zoomBarForTest().getRight() < W / 2);
        checkColumn(s);
        List<float[]> corners = new ArrayList<>();
        Bitmap cam = fakeCamera(new String[]{"https://github.com/zmlworks/qr-reader"}, new float[][]{{W * 0.5f, H * 0.45f, 620, -6}}, corners);
        shot(s, "scanner-right-handed", cam);
    }

    @Test public void scannerLeftHandedMulti() throws Exception {
        App.get().prefs().setLeftHanded(true);
        App.get().prefs().setMulti(true);
        ScannerActivity s = scanner();
        for (String t : new String[]{"INV-1", "INV-2", "INV-3"}) s.collectForTest(t);
        ShadowLooper.idleMainLooper();
        layout(s);
        View buttons = s.findViewById(R.id.buttons);
        assertTrue("buttons on the left", buttons.getRight() < W / 2);
        assertTrue("zoom bar on the right", s.zoomBarForTest().getLeft() > W / 2);
        checkColumn(s);
        assertEquals("3", ((TextView) s.findViewById(R.id.multi_badge)).getText().toString());
        List<float[]> corners = new ArrayList<>();
        Bitmap cam = fakeCamera(new String[]{"INV-1", "INV-2", "INV-3"},
                new float[][]{{W * 0.33f, H * 0.33f, 420, 4}, {W * 0.70f, H * 0.42f, 400, -8}, {W * 0.45f, H * 0.62f, 440, 2}}, corners);
        List<OverlayView.Shape> shapes = new ArrayList<>();
        for (int i = 0; i < corners.size(); i++) shapes.add(new OverlayView.Shape(corners.get(i), i == 2));
        ((OverlayView) s.findViewById(R.id.overlay)).setShapes(shapes);
        ((ZoomBarView) s.zoomBarForTest()).setActive(true);
        shot(s, "scanner-left-handed-multi", cam);
        // × with collected codes: History underneath, "Just scanned" on top
        s.findViewById(R.id.btn_close).performClick();
        Intent i1 = shadowOf(s).getNextStartedActivity(), i2 = shadowOf(s).getNextStartedActivity();
        Intent codes = i1.getComponent().getClassName().equals(CodesActivity.class.getName()) ? i1 : i2;
        Intent main = codes == i1 ? i2 : i1;
        assertEquals(MainActivity.class.getName(), main.getComponent().getClassName());
        assertEquals(CodesActivity.class.getName(), codes.getComponent().getClassName());
        assertTrue(codes.getBooleanExtra(CodesActivity.EXTRA_JUST_SCANNED, false));
        long gid = codes.getLongExtra(CodesActivity.EXTRA_GROUP, 0);
        assertEquals(3, App.get().history().childCount(gid));
        ActivityController<CodesActivity> cc = Robolectric.buildActivity(CodesActivity.class, codes).setup();
        shot(cc.get(), "just-scanned", null);
    }

    @Test public void readFromImage() throws Exception {
        ScannerActivity s = scanner();
        // the button opens the system photo picker
        s.findViewById(R.id.btn_image).performClick();
        org.robolectric.shadows.ShadowActivity.IntentForResult pick = shadowOf(s).getNextStartedActivityForResult();
        assertEquals("android.provider.action.PICK_IMAGES", pick.intent.getAction());
        // several codes -> one "N codes" entry, Just scanned list on top of History
        String[] three = {"https://example.com/a", "SN-0042", "WIFI:T:WPA;S:Lab;P:pw123456;;"};
        int before = h.topLevel().size();
        shadowOf(s).receiveResult(pick.intent, Activity.RESULT_OK, new Intent().setData(photo("photo-three", three)));
        waitFor(() -> h.topLevel().size() > before);
        History.Entry g = h.topLevel().get(0);
        assertEquals(History.GROUP, g.kind);
        assertEquals(3, h.childCount(g.id));
        Intent i1 = shadowOf(s).getNextStartedActivity(), i2 = shadowOf(s).getNextStartedActivity();
        Intent codes = i1.getComponent().getClassName().equals(CodesActivity.class.getName()) ? i1 : i2;
        assertEquals(g.id, codes.getLongExtra(CodesActivity.EXTRA_GROUP, 0));
        assertTrue(codes.getBooleanExtra(CodesActivity.EXTRA_JUST_SCANNED, false));
        // one code -> saved and its detail page opens
        s.findViewById(R.id.btn_image).performClick();
        pick = shadowOf(s).getNextStartedActivityForResult();
        shadowOf(s).receiveResult(pick.intent, Activity.RESULT_OK, new Intent().setData(photo("photo-one", new String[]{"Hello from a photo"})));
        waitFor(() -> "Hello from a photo".equals(h.topLevel().get(0).text));
        Intent d = shadowOf(s).getNextStartedActivity();
        assertEquals(DetailActivity.class.getName(), d.getComponent().getClassName());
        assertEquals(h.topLevel().get(0).id, d.getLongExtra(DetailActivity.EXTRA_ID, 0));
        // none -> a short message, nothing saved
        int n = h.size();
        s.findViewById(R.id.btn_image).performClick();
        pick = shadowOf(s).getNextStartedActivityForResult();
        shadowOf(s).receiveResult(pick.intent, Activity.RESULT_OK, new Intent().setData(photo("photo-none", new String[0])));
        waitFor(() -> s.pillTextForTest() != null && s.pillTextForTest().toString().equals(s.getString(R.string.image_no_code)));
        assertEquals(s.getString(R.string.image_no_code), String.valueOf(s.pillTextForTest()));
        assertEquals(n, h.size());
        layout(s);
        List<float[]> corners = new ArrayList<>();
        shot(s, "scanner-image-none", fakeCamera(new String[0], new float[0][], corners));
    }

    @Test public void history() throws Exception {
        ActivityController<MainActivity> ac = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity m = ac.get();
        layout(m);
        assertEquals(MainActivity.TAB_HISTORY, m.tabForTest());
        assertEquals(0, m.getResources().getIdentifier("fab_scan", "id", m.getPackageName()));
        assertEquals(0, m.getResources().getIdentifier("swipe_hint", "id", m.getPackageName()));
        SwipeListView list = m.findViewById(R.id.list);
        assertEquals(6, list.getAdapter().getCount());   // 5 singles + the multi scan (one entry), newest first
        assertEquals(url, list.getAdapter().getItemId(0));
        // the named Wi-Fi code shows its name; every row has the full date with the year
        View wifiRow = list.getChildAt(1);
        assertEquals("Café guest Wi-Fi", ((TextView) wifiRow.findViewById(R.id.row_title)).getText().toString());
        String year = String.valueOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR));
        for (int i = 0; i < list.getChildCount(); i++)
            assertTrue(((TextView) list.getChildAt(i).findViewById(R.id.row_sub)).getText().toString().contains(year));
        shot(m, "history", null);
        m.findViewById(R.id.seg_fav).performClick();
        assertEquals(1, list.getAdapter().getCount());
        assertEquals(jp, list.getAdapter().getItemId(0));
    }

    @Test public void historyDark() throws Exception {
        App.get().prefs().setDark(true);
        MainActivity m = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertTrue(m.isNight());
        assertEquals(0xFF1E2028, m.getColor(R.color.bg));
        shot(m, "history-dark", null);
    }

    @Test public void historySwipeDeleteUndoAndCopy() throws Exception {
        ActivityController<MainActivity> ac = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity m = ac.get();
        layout(m);
        SwipeListView list = m.findViewById(R.id.list);
        View row = list.getChildAt(0);
        int y = row.getTop() + row.getHeight() / 2;
        long t = android.os.SystemClock.uptimeMillis();
        // swipe right on the first row -> deleted, UNDO shown
        list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, 100, y, 0));
        for (int i = 1; i <= 10; i++) list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + i * 16, android.view.MotionEvent.ACTION_MOVE, 100 + i * 90, y, 0));
        list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 200, android.view.MotionEvent.ACTION_UP, 1000, y, 0));
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(null, App.get().history().get(url));
        assertEquals(View.VISIBLE, m.findViewById(R.id.snack).getVisibility());
        shot(m, "history-undo", null);
        m.findViewById(R.id.snack_action).performClick();
        assertNotNull(App.get().history().get(url));
        assertEquals(6, list.getAdapter().getCount());
        // swipe left -> copied to the clipboard (the text, not the name)
        layout(m);
        row = list.getChildAt(1);
        y = row.getTop() + row.getHeight() / 2;
        t = android.os.SystemClock.uptimeMillis();
        list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, 1100, y, 0));
        for (int i = 1; i <= 10; i++) list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + i * 16, android.view.MotionEvent.ACTION_MOVE, 1100 - i * 90, y, 0));
        list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 200, android.view.MotionEvent.ACTION_UP, 200, y, 0));
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.content.ClipboardManager cm = m.getSystemService(android.content.ClipboardManager.class);
        assertEquals("WIFI:T:WPA;S:Cafe-Guest;P:espresso2026;;", cm.getPrimaryClip().getItemAt(0).getText().toString());
        assertEquals(6, list.getAdapter().getCount());
    }

    @Test public void detailUrlLinkConfirmDeleteUndo() throws Exception {
        Intent i = new Intent(RuntimeEnvironment.getApplication(), DetailActivity.class).putExtra(DetailActivity.EXTRA_ID, url);
        ActivityController<DetailActivity> dc = Robolectric.buildActivity(DetailActivity.class, i).setup();
        DetailActivity d = dc.get();
        TextView text = d.findViewById(R.id.text);
        assertEquals(d.getColor(R.color.link), text.getCurrentTextColor());
        assertTrue((text.getPaintFlags() & Paint.UNDERLINE_TEXT_FLAG) != 0);
        // one action per row, in this order (0.7.0: Copy is no longer a row)
        android.widget.LinearLayout rows = d.findViewById(R.id.actions);
        String[] labels = {d.getString(R.string.share_text), d.getString(R.string.search_web), d.getString(R.string.create_this_qr)};
        assertEquals(3, rows.getChildCount());
        for (int k = 0; k < 3; k++) assertEquals(labels[k], ((TextView) rows.getChildAt(k)).getText().toString());
        shot(d, "detail-url", null);
        // tap the link -> confirmation -> Open -> browser
        text.performClick();
        AlertDialog c = Actions.lastConfirm;
        assertTrue(c.isShowing());
        assertEquals(null, shadowOf(d).getNextStartedActivity());
        shot(d, "detail-url-confirm", null, c);
        c.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        Intent browse = shadowOf(d).getNextStartedActivity();
        assertEquals(Intent.ACTION_VIEW, browse.getAction());
        assertEquals("https://github.com/zmlworks/qr-reader", browse.getDataString());
        // web search asks too
        d.findViewById(R.id.act_search).performClick();
        Actions.lastConfirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals("https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fzmlworks%2Fqr-reader",
                shadowOf(d).getNextStartedActivity().getDataString());
        // setting off -> straight to the browser; Cancel keeps the user here
        App.get().prefs().setConfirmBrowser(false);
        Actions.lastConfirm = null;
        text.performClick();
        assertEquals(null, Actions.lastConfirm);
        assertEquals("https://github.com/zmlworks/qr-reader", shadowOf(d).getNextStartedActivity().getDataString());
        App.get().prefs().setConfirmBrowser(true);
        d.findViewById(R.id.btn_fav).performClick();
        assertTrue(App.get().history().get(url).fav);
        // delete -> History with UNDO -> UNDO restores and reopens this detail page
        d.findViewById(R.id.btn_delete).performClick();
        assertEquals(null, App.get().history().get(url));
        Intent toMain = shadowOf(d).getNextStartedActivity();
        assertEquals(MainActivity.class.getName(), toMain.getComponent().getClassName());
        assertTrue(d.isFinishing());
        MainActivity m = Robolectric.buildActivity(MainActivity.class, toMain).setup().get();
        layout(m);
        assertEquals(View.VISIBLE, m.findViewById(R.id.snack).getVisibility());
        m.findViewById(R.id.snack_action).performClick();
        assertNotNull(App.get().history().get(url));
        assertTrue(App.get().history().get(url).fav);
        Intent back = shadowOf(m).getNextStartedActivity();
        assertEquals(DetailActivity.class.getName(), back.getComponent().getClassName());
        assertEquals(url, back.getLongExtra(DetailActivity.EXTRA_ID, 0));
    }

    @Test public void detailRename() throws Exception {
        Intent i = new Intent(RuntimeEnvironment.getApplication(), DetailActivity.class).putExtra(DetailActivity.EXTRA_ID, jp);
        DetailActivity d = Robolectric.buildActivity(DetailActivity.class, i).setup().get();
        assertEquals(View.GONE, d.findViewById(R.id.name).getVisibility());
        d.findViewById(R.id.btn_name).performClick();
        EditText et = d.nameDialog.findViewById(R.id.name_input);
        et.setText("Greeting card");
        shot(d, "detail-rename", null, d.nameDialog);
        d.nameDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals("Greeting card", h.get(jp).name);
        assertEquals("Greeting card", ((TextView) d.findViewById(R.id.name)).getText().toString());
        assertEquals(View.VISIBLE, d.findViewById(R.id.name).getVisibility());
        MainActivity m = Robolectric.buildActivity(MainActivity.class).setup().get();
        layout(m);
        SwipeListView list = m.findViewById(R.id.list);
        boolean shown = false;
        for (int k = 0; k < list.getChildCount(); k++)
            shown |= "Greeting card".equals(((TextView) list.getChildAt(k).findViewById(R.id.row_title)).getText().toString());
        assertTrue("name in the history list", shown);
        // empty clears it
        d.findViewById(R.id.btn_name).performClick();
        ((EditText) d.nameDialog.findViewById(R.id.name_input)).setText("");
        d.nameDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(null, h.get(jp).name);
        assertEquals(View.GONE, d.findViewById(R.id.name).getVisibility());
    }

    @Test public void detailWifiAndDark() throws Exception {
        App.get().prefs().setDark(true);
        Intent i = new Intent(RuntimeEnvironment.getApplication(), DetailActivity.class).putExtra(DetailActivity.EXTRA_ID, wifi);
        DetailActivity d = Robolectric.buildActivity(DetailActivity.class, i).setup().get();
        assertTrue(d.isNight());
        assertEquals(View.VISIBLE, d.findViewById(R.id.fields).getVisibility());
        android.widget.LinearLayout rows = d.findViewById(R.id.actions);
        assertEquals(5, rows.getChildCount());   // connect, copy password + the three standard rows
        assertEquals(d.getString(R.string.wifi_connect), ((TextView) rows.getChildAt(0)).getText().toString());
        assertEquals(d.getString(R.string.copy_password), ((TextView) rows.getChildAt(1)).getText().toString());
        assertEquals("Café guest Wi-Fi", ((TextView) d.findViewById(R.id.name)).getText().toString());
        shot(d, "detail-wifi-dark", null);
    }

    @Test public void detailCreateQrOpensCreatePrefilled() throws Exception {
        Intent i = new Intent(RuntimeEnvironment.getApplication(), DetailActivity.class).putExtra(DetailActivity.EXTRA_ID, jp);
        DetailActivity d = Robolectric.buildActivity(DetailActivity.class, i).setup().get();
        d.findViewById(R.id.act_qr).performClick();
        Intent toMain = shadowOf(d).getNextStartedActivity();
        MainActivity m = Robolectric.buildActivity(MainActivity.class, toMain).setup().get();
        assertEquals(MainActivity.TAB_CREATE, m.tabForTest());
        assertEquals("こんにちは、世界！ QR コードのテスト", m.createPage.textForTest());
    }

    @Test public void create() throws Exception {
        Intent i = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_CREATE);
        MainActivity m = Robolectric.buildActivity(MainActivity.class, i).setup().get();
        EditText in = m.findViewById(R.id.input);
        String target = "https://github.com/zmlworks/qr-reader";
        int lastVersion = 0;
        for (int k = 1; k <= target.length(); k++) {   // one character at a time, like typing
            in.getText().append(target.charAt(k - 1));
            for (int w = 0; w < 200 && (m.createPage.qrForTest() == null || m.createPage.qrForTest().bytes != k); w++) {
                Thread.sleep(2);
                ShadowLooper.idleMainLooper();
            }
            QrGen.Qr q = m.createPage.qrForTest();
            assertNotNull("after " + k + " chars", q);
            assertEquals(k, q.bytes);
            assertTrue(q.version >= lastVersion);
            lastVersion = q.version;
        }
        assertEquals(target, QrGenTest.decode(m.createPage.qrForTest()).getText());
        layout(m);
        // no title bar, no scan / share / save / copy / clear buttons; the square QR on top, the input right under it
        assertEquals(View.GONE, m.findViewById(R.id.top_bar).getVisibility());
        for (String gone : new String[]{"qr_share", "qr_save", "qr_copy", "input_clear", "fab_scan"})
            assertEquals(gone, 0, m.getResources().getIdentifier(gone, "id", m.getPackageName()));
        View box = m.findViewById(R.id.qr_box);
        assertEquals(box.getWidth(), box.getHeight());
        assertTrue(topIn(box) < dp(m, 80));
        // 0.7.0: the small "Save to history" button sits between the QR and the input
        View save = m.findViewById(R.id.save_history);
        assertEquals(dp(m, 8), topIn(save) - (topIn(box) + box.getHeight()));
        assertEquals(dp(m, 8), topIn(in) - (topIn(save) + save.getHeight()));
        shot(m, "create", null);
        // keyboard open: the window is shorter and the tabs hide; QR and input both stay on screen
        m.setKeyboardForTest(true);
        assertEquals(View.GONE, m.findViewById(R.id.tab_bar).getVisibility());
        int kh = (int) (H * 0.55f);
        View root = m.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(kh, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, W, kh);
        assertEquals(box.getWidth(), box.getHeight());
        assertTrue("input visible above the keyboard", topIn(in) + in.getHeight() <= kh);
        assertEquals(dp(m, 8), topIn(in) - (topIn(save) + save.getHeight()));
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        root.draw(c);
        Paint kb = new Paint();
        kb.setColor(0xFFD5D3DC);
        c.drawRect(0, kh, W, H, kb);   // where the keyboard would be
        kb.setColor(0xFF8C8996);
        kb.setTextSize(54);
        kb.setTextAlign(Paint.Align.CENTER);
        c.drawText("(keyboard)", W / 2f, kh + (H - kh) / 2f, kb);
        try (FileOutputStream os = new FileOutputStream(new File(OUT, "create-keyboard.png"))) { bmp.compress(Bitmap.CompressFormat.PNG, 100, os); }
        m.setKeyboardForTest(false);
        assertEquals(View.VISIBLE, m.findViewById(R.id.tab_bar).getVisibility());
    }

    @Test public void settings() throws Exception {
        Intent i = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_SETTINGS);
        MainActivity m = Robolectric.buildActivity(MainActivity.class, i).setup().get();
        Prefs p = App.get().prefs();
        m.findViewById(R.id.set_left).performClick();
        assertTrue(p.leftHanded());
        assertTrue(((CheckBox) m.findViewById(R.id.cb_left)).isChecked());
        m.findViewById(R.id.set_left).performClick();
        assertTrue(((CheckBox) m.findViewById(R.id.cb_vibrate)).isChecked());
        m.findViewById(R.id.set_vibrate).performClick();
        assertTrue(!p.vibrate());
        m.findViewById(R.id.set_vibrate).performClick();
        assertTrue(((CheckBox) m.findViewById(R.id.cb_confirm)).isChecked());
        m.findViewById(R.id.set_confirm).performClick();
        assertTrue(!p.confirmBrowser());
        m.findViewById(R.id.set_confirm).performClick();
        assertTrue(p.confirmBrowser());
        assertTrue(!((CheckBox) m.findViewById(R.id.cb_dark)).isChecked());
        assertEquals(SearchUrl.DEFAULT_TEMPLATE, ((TextView) m.findViewById(R.id.engine_value)).getText().toString());
        shot(m, "settings", null);
        // search engine row -> template editor; invalid is refused, valid is saved and shown
        m.findViewById(R.id.set_engine).performClick();
        ShadowLooper.idleMainLooper();
        AlertDialog d = m.settingsPage.lastDialog;
        EditText et = d.findViewById(R.id.template_input);
        et.setText("https://duckduckgo.com/?q=");
        assertEquals("pasted scheme removed", "duckduckgo.com/?q=", et.getText().toString());
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(d.isShowing());
        assertNotNull(et.getError());
        et.setText("https://duckduckgo.com/?q=%s");
        et.setError(null);   // typing clears it on a device
        assertEquals("duckduckgo.com/?q=%s", et.getText().toString());
        shot(m, "settings-search-engine", null, d);
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals("duckduckgo.com/?q=%s", p.searchTemplate());
        assertEquals("duckduckgo.com/?q=%s", ((TextView) m.findViewById(R.id.engine_value)).getText().toString());
        // about: link to the repository (opens after the confirmation)
        m.settingsPage.about();
        AlertDialog about = m.settingsPage.lastDialog;
        TextView link = about.findViewById(R.id.about_link);
        assertEquals("https://github.com/zmlworks/qr-reader", link.getText().toString());
        shot(m, "settings-about", null, about);
        link.performClick();
        Actions.lastConfirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals("https://github.com/zmlworks/qr-reader", shadowOf(m).getNextStartedActivity().getDataString());
        // clear all history asks first
        m.findViewById(R.id.set_clear).performClick();
        AlertDialog clear = m.settingsPage.lastDialog;
        assertTrue(clear.isShowing());
        clear.cancel();
        assertEquals(6, h.topLevel().size());
        // dark mode: on -> remembered
        m.findViewById(R.id.set_dark).performClick();
        assertTrue(p.dark(m.getResources().getConfiguration()));
    }

    @Test public void settingsDark() throws Exception {
        App.get().prefs().setDark(true);
        Intent i = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_SETTINGS);
        MainActivity m = Robolectric.buildActivity(MainActivity.class, i).setup().get();
        assertTrue(((CheckBox) m.findViewById(R.id.cb_dark)).isChecked());
        shot(m, "settings-dark", null);
    }

    @Test public void darkModeDefaultsToTheSystemOnceThenRemembers() {
        Prefs p = App.get().prefs();
        Configuration night = new Configuration(), day = new Configuration();
        night.uiMode = Configuration.UI_MODE_NIGHT_YES | Configuration.UI_MODE_TYPE_NORMAL;
        day.uiMode = Configuration.UI_MODE_NIGHT_NO | Configuration.UI_MODE_TYPE_NORMAL;
        p.resetDark();
        assertTrue(p.dark(night));    // first run: the system is dark
        assertTrue(p.dark(day));      // then remembered, whatever the system does
        p.setDark(false);
        assertTrue(!p.dark(night));
        p.resetDark();
        assertTrue(!p.dark(day));
        assertTrue(!p.dark(night));
    }

    @Test public void groupList() throws Exception {
        Intent i = new Intent(RuntimeEnvironment.getApplication(), CodesActivity.class).putExtra(CodesActivity.EXTRA_GROUP, group);
        CodesActivity c = Robolectric.buildActivity(CodesActivity.class, i).setup().get();
        layout(c);
        assertEquals("5 codes", ((TextView) c.findViewById(R.id.title)).getText().toString());
        assertEquals(5, ((SwipeListView) c.findViewById(R.id.list)).getAdapter().getCount());
    }

    @Test public void backFromBackgroundGoesToScanner() {
        App app = App.get();
        ActivityController<MainActivity> ac = Robolectric.buildActivity(MainActivity.class).setup();
        ac.pause().stop();          // app goes to the background
        assertTrue(app.wasInBackground);
        ac.start();                 // and comes back
        Intent next = shadowOf(ac.get()).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(ScannerActivity.class.getName(), next.getComponent().getClassName());
    }

    // ---- 0.3.0 ----

    static DetailActivity detail(long id) {
        Intent i = new Intent(RuntimeEnvironment.getApplication(), DetailActivity.class).putExtra(DetailActivity.EXTRA_ID, id);
        DetailActivity d = Robolectric.buildActivity(DetailActivity.class, i).setup().get();
        ShadowLooper.idleMainLooper();
        return d;
    }

    static boolean secureFlag(Activity a) {
        return (a.getWindow().getAttributes().flags & android.view.WindowManager.LayoutParams.FLAG_SECURE) != 0;
    }

    @Test public void protectionLockDetailAndList() throws Exception {
        // ON: no prompt, the lock button turns filled, the page becomes FLAG_SECURE
        DetailActivity d = detail(wifi);
        android.widget.ImageButton lock = d.findViewById(R.id.btn_lock);
        assertEquals(d.getString(R.string.protect), lock.getContentDescription().toString());
        assertTrue(!secureFlag(d));
        lock.performClick();
        assertTrue(h.get(wifi).prot);
        assertEquals(0, Auth.testPrompts);
        assertEquals(d.getString(R.string.unprotect), lock.getContentDescription().toString());
        assertTrue(secureFlag(d));
        assertTrue(!d.lockedForTest());
        shot(d, "detail-protected", null);
        h.setFavorite(wifi, true);
        // history list: lock badge, a blurred placeholder (never the real text) and the usual date line; no star
        MainActivity m = Robolectric.buildActivity(MainActivity.class).setup().get();
        layout(m);
        SwipeListView list = m.findViewById(R.id.list);
        View row = list.getChildAt(1);
        assertEquals(wifi, list.getAdapter().getItemId(1));
        checkObscured(row, h.get(wifi), "bitmap");
        assertEquals(Actions.listTime(h.get(wifi).time), ((TextView) row.findViewById(R.id.row_sub)).getText().toString());
        View normal = list.getChildAt(0);
        assertEquals("date line in the same place as a normal row", topIn(normal.findViewById(R.id.row_sub)) - topIn(normal),
                topIn(row.findViewById(R.id.row_sub)) - topIn(row));
        assertEquals(((TextView) normal.findViewById(R.id.row_sub)).getTextSize(), ((TextView) row.findViewById(R.id.row_sub)).getTextSize(), 0f);
        assertEquals(View.GONE, row.findViewById(R.id.row_star).getVisibility());
        assertEquals(" ", ((TextView) row.findViewById(R.id.row_badge)).getText().toString());
        shot(m, "history-protected", null);
        // swipe-left copy needs authentication: refused -> clipboard untouched
        android.content.ClipboardManager cm = m.getSystemService(android.content.ClipboardManager.class);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("x", "before"));
        Auth.testResult = false;
        swipeLeft(list, row);
        assertEquals(1, Auth.testPrompts);
        assertEquals("before", cm.getPrimaryClip().getItemAt(0).getText().toString());
        Auth.testResult = true;
        layout(m);
        swipeLeft(list, list.getChildAt(1));
        assertEquals("WIFI:T:WPA;S:Cafe-Guest;P:espresso2026;;", cm.getPrimaryClip().getItemAt(0).getText().toString());
        assertEquals(2, Auth.testPrompts);
        // the 1-minute window: opening right after needs no new prompt; the pill counts down in both top bars
        DetailActivity d2 = detail(wifi);
        assertTrue(!d2.lockedForTest());
        assertEquals(2, Auth.testPrompts);
        TextView dp = d2.findViewById(R.id.unlock_pill), mp = m.findViewById(R.id.unlock_pill);
        assertEquals(View.VISIBLE, dp.getVisibility());
        int sec = (int) ((Auth.remainingMs() + 999) / 1000);
        assertTrue(sec > 50 && sec <= 60);
        assertEquals(sec + "s", dp.getText().toString());
        assertEquals("Unlocked · " + sec + "s", mp.getText().toString());
        ShadowLooper.idleMainLooper(8, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals((sec - 8) + "s", dp.getText().toString());
        assertEquals("Unlocked · " + (sec - 8) + "s", mp.getText().toString());
        shot(m, "history-unlocked-pill", null);
        shot(d2, "detail-unlocked-pill", null);
        // after the window it asks; refused -> locked page, nothing shown
        ShadowLooper.idleMainLooper(53, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(!Auth.recentlyOk());
        assertEquals(View.GONE, dp.getVisibility());
        assertEquals(View.GONE, mp.getVisibility());
        Auth.testResult = false;
        DetailActivity d3 = detail(wifi);
        assertEquals(3, Auth.testPrompts);
        assertTrue(d3.lockedForTest());
        assertTrue(secureFlag(d3));
        assertEquals(View.GONE, d3.findViewById(R.id.content).getVisibility());
        for (int id : new int[]{R.id.btn_lock, R.id.btn_name, R.id.btn_fav, R.id.btn_delete})
            assertEquals(View.GONE, d3.findViewById(id).getVisibility());
        assertEquals(d3.getString(R.string.protected_title), ((TextView) d3.findViewById(R.id.title)).getText().toString());
        shot(d3, "detail-locked", null);
        // Unlock -> accepted -> content
        Auth.testResult = true;
        d3.findViewById(R.id.btn_unlock).performClick();
        assertTrue(!d3.lockedForTest());
        assertEquals("Café guest Wi-Fi", ((TextView) d3.findViewById(R.id.name)).getText().toString());
        int prompts = Auth.testPrompts;
        // tap on the pill: locked at once, the open page locks too (no automatic prompt)
        d3.findViewById(R.id.unlock_pill).performClick();
        assertTrue(!Auth.recentlyOk());
        assertTrue(d3.lockedForTest());
        assertEquals(prompts, Auth.testPrompts);
        assertEquals(View.GONE, d3.findViewById(R.id.unlock_pill).getVisibility());
        d3.findViewById(R.id.btn_unlock).performClick();
        assertEquals(prompts + 1, Auth.testPrompts);
        assertTrue(!d3.lockedForTest());
        // inside the window even OFF needs no new prompt
        Auth.testResult = false;
        d3.findViewById(R.id.btn_lock).performClick();
        assertEquals(prompts + 1, Auth.testPrompts);
        assertTrue(!h.get(wifi).prot);
        assertTrue(!secureFlag(d3));
        // OFF after the window asks
        h.setProtected(wifi, true);
        Auth.lockNow();
        DetailActivity d4 = detail(wifi);
        assertTrue(d4.lockedForTest());
        Auth.testResult = true;
        d4.findViewById(R.id.btn_unlock).performClick();
        Auth.lockNow();
        assertTrue(d4.lockedForTest());
    }

    static void checkObscured(View row, History.Entry e, String how) {
        TextView title = row.findViewById(R.id.row_title);
        assertEquals(how, title.getTag(R.id.obscured));
        String shown = title.getText().toString();
        if (how.equals("render-effect")) assertEquals(" * * * * * * * * * * *", shown);
        assertTrue("no real text: " + shown, !shown.contains(e.text) && (e.name == null || !shown.contains(e.name)));
        assertEquals(View.VISIBLE, row.findViewById(R.id.row_sub).getVisibility());
    }

    @Test public void authWindowSurvivesLeavingTheApp() {
        ActivityController<MainActivity> ac = Robolectric.buildActivity(MainActivity.class).setup();
        Auth.testResult = true;
        Auth.require(ac.get(), true, "t", () -> { }, null);
        assertTrue(Auth.recentlyOk());
        ac.pause().stop();
        ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.SECONDS);
        ac.start();
        assertTrue("back within 60 s: still unlocked", Auth.recentlyOk());
        int n = Auth.testPrompts;
        Auth.require(ac.get(), true, "t", () -> { }, null);
        assertEquals(n, Auth.testPrompts);
        ShadowLooper.idleMainLooper(41, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(!Auth.recentlyOk());
    }

    /** Android 12+: placeholder glyphs with a RenderEffect blur (not visible in software-drawn screenshots). */
    @Test public void protectedRowsUseRenderEffectOnAndroid12() throws Exception {
        Obscured.forceBitmapForTest = false;
        h.setProtected(wifi, true);
        MainActivity m = Robolectric.buildActivity(MainActivity.class).setup().get();
        layout(m);
        SwipeListView list = m.findViewById(R.id.list);
        checkObscured(list.getChildAt(1), h.get(wifi), "render-effect");
        // a recycled row turning normal again loses the blur
        h.setProtected(wifi, false);
        m.historyPage.refresh();
        layout(m);
        assertEquals(null, list.getChildAt(1).findViewById(R.id.row_title).getTag(R.id.obscured));
        assertEquals("Café guest Wi-Fi", rowTitle(list, 1));
    }

    /** Android 11 and older: the blurred line is a pre-blurred bitmap of placeholder glyphs. */
    @Config(sdk = 30)
    @Test public void protectedRowsBlurredBelowAndroid12() throws Exception {
        Obscured.forceBitmapForTest = false;
        h.setProtected(wifi, true);
        List<History.Entry> ch = h.children(group);
        h.setProtected(ch.get(0).id, true);
        MainActivity m = Robolectric.buildActivity(MainActivity.class).setup().get();
        layout(m);
        SwipeListView list = m.findViewById(R.id.list);
        checkObscured(list.getChildAt(1), h.get(wifi), "bitmap");
        CharSequence t = ((TextView) list.getChildAt(1).findViewById(R.id.row_title)).getText();
        assertTrue(t instanceof android.text.Spanned && ((android.text.Spanned) t).getSpans(0, t.length(), Obscured.BlurSpan.class).length == 1);
        Intent i = new Intent(RuntimeEnvironment.getApplication(), CodesActivity.class).putExtra(CodesActivity.EXTRA_GROUP, group)
                .putExtra(CodesActivity.EXTRA_JUST_SCANNED, true);
        CodesActivity c = Robolectric.buildActivity(CodesActivity.class, i).setup().get();
        layout(c);
        checkObscured(((SwipeListView) c.findViewById(R.id.list)).getChildAt(0), ch.get(0), "bitmap");
    }

    @Test public void protectionNeedsAScreenLock() throws Exception {
        shadowOf(App.get().getSystemService(android.app.KeyguardManager.class)).setIsDeviceSecure(false);
        DetailActivity d = detail(url);
        d.findViewById(R.id.btn_lock).performClick();
        assertTrue(!h.get(url).prot);
        assertTrue(d.lastDialog.isShowing());
        shot(d, "detail-no-screen-lock", null, d.lastDialog);
        d.lastDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(android.provider.Settings.ACTION_SECURITY_SETTINGS, shadowOf(d).getNextStartedActivity().getAction());
        // a lock removed later: protected items still open (with a message), nothing is lost
        h.setProtected(jp, true);
        DetailActivity d2 = detail(jp);
        assertTrue(!d2.lockedForTest());
    }

    @Test public void protectedCodesStayHiddenInGroupLists() throws Exception {
        List<History.Entry> ch = h.children(group);
        h.setProtected(ch.get(0).id, true);
        // the history row previews the first unprotected code
        MainActivity m = Robolectric.buildActivity(MainActivity.class).setup().get();
        layout(m);
        SwipeListView hl = m.findViewById(R.id.list);
        for (int k = 0; k < hl.getChildCount(); k++)
            if (hl.getAdapter().getItemId(k) == group) {
                String sub = ((TextView) hl.getChildAt(k).findViewById(R.id.row_sub)).getText().toString();
                assertTrue(sub, sub.contains("INV-2026-10-102") && !sub.contains("INV-2026-10-101"));
            }
        Intent i = new Intent(RuntimeEnvironment.getApplication(), CodesActivity.class).putExtra(CodesActivity.EXTRA_GROUP, group)
                .putExtra(CodesActivity.EXTRA_JUST_SCANNED, true);
        CodesActivity c = Robolectric.buildActivity(CodesActivity.class, i).setup().get();
        layout(c);
        SwipeListView list = c.findViewById(R.id.list);
        checkObscured(list.getChildAt(0), ch.get(0), "bitmap");
        shot(c, "just-scanned-protected", null);
        for (int k = 0; k < list.getChildCount(); k++) {
            View r = list.getChildAt(k);
            assertTrue(!((TextView) r.findViewById(R.id.row_title)).getText().toString().contains("INV-2026-10-101"));
        }
        // Copy all leaves it out
        c.findViewById(R.id.btn_copy_all).performClick();
        android.content.ClipboardManager cm = c.getSystemService(android.content.ClipboardManager.class);
        String all = cm.getPrimaryClip().getItemAt(0).getText().toString();
        assertTrue(!all.contains("INV-2026-10-101"));
        assertTrue(all.contains("INV-2026-10-105"));
        // swipe copy of the protected code asks
        Auth.testResult = false;
        swipeLeft(list, list.getChildAt(0));
        assertEquals(1, Auth.testPrompts);
        assertEquals(all, cm.getPrimaryClip().getItemAt(0).getText().toString());
    }

    @Test public void scannerAllCodesModeFreezeAndFormats() throws Exception {
        Intent in = new Intent(RuntimeEnvironment.getApplication(), ScannerActivity.class).putExtra(ScannerActivity.EXTRA_NON_QR, true);
        ActivityController<ScannerActivity> ac = Robolectric.buildActivity(ScannerActivity.class, in).setup();
        ScannerActivity s = ac.get();
        s.findViewById(R.id.preview).setVisibility(View.INVISIBLE);
        ((View) s.findViewById(R.id.preview).getParent()).setBackground(null);
        s.getWindow().setBackgroundDrawable(null);
        for (int i = 0; i < 50; i++) { Thread.sleep(10); ShadowLooper.idleMainLooper(); }
        s.hideMessageForTest();
        s.setZoomForTest(1, 8, 2.4f);
        assertTrue(App.get().nonQr);
        assertTrue(s.nonQrModeForTest());
        TextView badge = s.findViewById(R.id.mode_badge);
        assertEquals(View.VISIBLE, badge.getVisibility());
        assertEquals("Non-QR", badge.getText().toString());
        layout(s);
        checkColumn(s);
        assertTrue("badge at the top centre", Math.abs(centerX(badge) - W / 2) <= 2 && topIn(badge) < dp(s, 80));
        View multi = s.findViewById(R.id.btn_multi);
        int[] bl = new int[2], ml = new int[2];
        badge.getLocationInWindow(bl);
        multi.getLocationInWindow(ml);
        assertTrue("badge clear of the buttons", bl[0] + badge.getWidth() < ml[0]);
        shot(s, "scanner-non-qr", fakeCamera(new String[0], new float[0][], new ArrayList<>()));
        // a read: saved at once with its format, the detail opens after the freeze
        float[] corners = {300, 1100, 900, 1100, 900, 1400, 300, 1400};
        s.readForTest(new QrEngine.Code("4006381333931", corners, "EAN-13"));
        assertEquals("4006381333931", h.topLevel().get(0).text);
        assertEquals("EAN-13", h.topLevel().get(0).format);
        assertEquals(null, shadowOf(s).getNextStartedActivity());
        ShadowLooper.idleMainLooper(ScannerActivity.FREEZE_MS - 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("still frozen", null, shadowOf(s).getNextStartedActivity());
        ShadowLooper.idleMainLooper(60, java.util.concurrent.TimeUnit.MILLISECONDS);
        Intent di = shadowOf(s).getNextStartedActivity();
        assertEquals(DetailActivity.class.getName(), di.getComponent().getClassName());
        // format on the detail page and in the list
        DetailActivity d = detail(di.getLongExtra(DetailActivity.EXTRA_ID, 0));
        assertEquals("EAN-13", ((TextView) d.findViewById(R.id.title)).getText().toString());
        shot(d, "detail-ean13", null);
        MainActivity m = Robolectric.buildActivity(MainActivity.class).setup().get();
        layout(m);
        SwipeListView list = m.findViewById(R.id.list);
        assertEquals("EAN-13", ((TextView) list.getChildAt(0).findViewById(R.id.row_badge)).getText().toString());
        shot(m, "history-formats", null);
        // tapping the badge ends the mode
        badge.performClick();
        assertTrue(!App.get().nonQr);
        assertEquals(View.GONE, badge.getVisibility());
    }

    @Test public void nonQrSessionEndsWithCloseOrLeaving() {
        Intent in = new Intent(RuntimeEnvironment.getApplication(), ScannerActivity.class).putExtra(ScannerActivity.EXTRA_NON_QR, true);
        ActivityController<ScannerActivity> ac = Robolectric.buildActivity(ScannerActivity.class, in).setup();
        assertTrue(App.get().nonQr);
        ac.pause().stop();
        assertTrue("leaving the app ends it", !App.get().nonQr);
        ActivityController<ScannerActivity> ac2 = Robolectric.buildActivity(ScannerActivity.class,
                new Intent(RuntimeEnvironment.getApplication(), ScannerActivity.class).putExtra(ScannerActivity.EXTRA_NON_QR, true)).setup();
        assertTrue(App.get().nonQr);
        // the photo picker is not leaving the app
        ac2.get().findViewById(R.id.btn_image).performClick();
        assertTrue(App.get().holdForeground);
        ac2.pause().stop();
        assertTrue(App.get().nonQr);
        ac2.start().resume();
        App.get().holdForeground = false;
        ac2.get().findViewById(R.id.btn_close).performClick();
        assertTrue("× ends it", !App.get().nonQr);
    }

    @Test public void settingsOnceLineStartsAllCodes() throws Exception {
        Intent i = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_SETTINGS);
        MainActivity m = Robolectric.buildActivity(MainActivity.class, i).setup().get();
        TextView once = m.findViewById(R.id.set_once);
        assertEquals("[Scan non-QR once]", once.getText().toString());
        assertEquals("normal text colour, not dimmed", m.getColor(R.color.text), once.getCurrentTextColor());
        layout(m);
        // its row stays right above About (where it was); the text is centred and only it (+ ~10 dp) is tappable
        View row = m.findViewById(R.id.set_once_row);
        android.view.ViewGroup col = (android.view.ViewGroup) row.getParent();
        assertEquals(m.findViewById(R.id.set_about), col.getChildAt(col.indexOfChild(row) + 1));
        assertTrue(Math.abs(centerX(once) - W / 2) <= dp(m, 2));
        assertTrue("wrap_content, not the row width: " + once.getWidth(), once.getWidth() < W / 2);
        assertTrue(once.getPaddingLeft() >= dp(m, 8) && once.getPaddingLeft() <= dp(m, 12));
        assertTrue(once.getPaddingTop() >= dp(m, 8) && once.getPaddingTop() <= dp(m, 12));
        assertTrue(!row.isClickable());
        // a tap at the left of the row (away from the text) does nothing
        int[] loc = new int[2];
        row.getLocationInWindow(loc);
        long t = android.os.SystemClock.uptimeMillis();
        float x = dp(m, 30), y = loc[1] + row.getHeight() / 2f;
        m.getWindow().getDecorView().dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, x, y, 0));
        m.getWindow().getDecorView().dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 50, android.view.MotionEvent.ACTION_UP, x, y, 0));
        ShadowLooper.idleMainLooper();
        assertNull(shadowOf(m).getNextStartedActivity());
        assertTrue(once.isClickable());
        once.performClick();
        Intent next = shadowOf(m).getNextStartedActivity();
        assertEquals(ScannerActivity.class.getName(), next.getComponent().getClassName());
        assertTrue(next.getBooleanExtra(ScannerActivity.EXTRA_NON_QR, false));
        assertTrue(m.isFinishing());
    }

    // ---- 0.6.0 ----

    @Test public void dateFormatSetting() throws Exception {
        java.time.ZoneId z = java.time.ZoneId.systemDefault();
        long t = java.time.LocalDateTime.of(2026, 10, 7, 9, 5, 3).atZone(z).toInstant().toEpochMilli();
        h.clear();
        h.addSingle("older", t - 86_400_000L * 40);
        long id = h.addSingle("dated", t).id;
        Intent i = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_SETTINGS);
        MainActivity m = Robolectric.buildActivity(MainActivity.class, i).setup().get();
        layout(m);
        View row = m.findViewById(R.id.set_date_format);
        TextView value = m.findViewById(R.id.date_format_value);
        assertEquals("default", "DD/MM/YYYY", value.getText().toString());
        // one line: title on the left, value on the right, a normal row height
        TextView title = (TextView) ((ViewGroup) row).getChildAt(0);
        assertEquals("Date format", title.getText().toString());
        assertEquals(1, title.getLineCount());
        assertEquals(1, value.getLineCount());
        assertTrue(centerX(value) > W / 2 && centerX(title) < W / 2);
        assertTrue(Math.abs(centerY(value) - centerY(title)) <= dp(m, 2));
        assertTrue(row.getHeight() <= dp(m, 64));
        m.select(MainActivity.TAB_HISTORY);
        layout(m);
        SwipeListView list = m.findViewById(R.id.list);
        assertEquals("07/10/2026 09:05", ((TextView) list.getChildAt(0).findViewById(R.id.row_sub)).getText().toString());
        // tap cycles DD/MM/YYYY -> MM/DD/YYYY -> YYYY/MM/DD -> DD/MM/YYYY; everything follows
        m.select(MainActivity.TAB_SETTINGS);
        row.performClick();
        assertEquals("MM/DD/YYYY", value.getText().toString());
        assertEquals("10/07/2026 09:05", Actions.listTime(t));
        row.performClick();
        assertEquals("YYYY/MM/DD", value.getText().toString());
        layout(m);
        shot(m, "settings-date-format", null);
        m.select(MainActivity.TAB_HISTORY);
        layout(m);
        assertEquals("2026/10/07 09:05", ((TextView) list.getChildAt(0).findViewById(R.id.row_sub)).getText().toString());
        shot(m, "history-yyyymmdd", null);
        // jump wheels: year, month, day
        m.findViewById(R.id.btn_jump).performClick();
        ShadowLooper.idleMainLooper();
        AlertDialog d = m.historyPage.jumpDialog;
        ViewGroup wheels = (ViewGroup) ((View) d.findViewById(R.id.jump_day).getParent()).getParent();
        assertEquals(d.findViewById(R.id.jump_year).getParent(), wheels.getChildAt(0));
        assertEquals(d.findViewById(R.id.jump_month).getParent(), wheels.getChildAt(1));
        assertEquals(d.findViewById(R.id.jump_day).getParent(), wheels.getChildAt(2));
        shot(m, "history-jump-yyyymmdd", null, d);
        d.dismiss();
        // detail page, with seconds
        Intent di = new Intent(RuntimeEnvironment.getApplication(), DetailActivity.class).putExtra(DetailActivity.EXTRA_ID, id);
        DetailActivity da = Robolectric.buildActivity(DetailActivity.class, di).setup().get();
        assertTrue(textOf(da.getWindow().getDecorView()).contains("2026/10/07 09:05:03"));
        row.performClick();
        assertEquals("DD/MM/YYYY", value.getText().toString());
        assertEquals("07/10/2026 09:05:03", Actions.longTime(t));
    }

    /** all the text of a view tree, for "is it shown somewhere" checks */
    static String textOf(View v) {
        StringBuilder b = new StringBuilder();
        if (v instanceof TextView) b.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup) for (int k = 0; k < ((ViewGroup) v).getChildCount(); k++) b.append(textOf(((ViewGroup) v).getChildAt(k)));
        return b.toString();
    }

    /** the longest date + time ("28/12/2026 23:59", any order) fits on one line of a history row at 360 dp */
    @Test @Config(qualifiers = "w360dp-h740dp-port-xxhdpi") public void historyDateFitsAt360dp() throws Exception {
        java.time.ZoneId z = java.time.ZoneId.systemDefault();
        long t = java.time.LocalDateTime.of(2026, 12, 28, 23, 59).atZone(z).toInstant().toEpochMilli();
        h.clear();
        h.addSingle("a long text that certainly does not fit on one line of a narrow phone screen at all", t);
        MainActivity m = Robolectric.buildActivity(MainActivity.class).setup().get();
        for (DateFmt f : DateFmt.values()) {
            App.get().prefs().setDateFmt(f);
            m.historyPage.refresh();
            int w = dp(m, 360), hh = dp(m, 740);
            View root = m.getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hh, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, w, hh);
            SwipeListView list = m.findViewById(R.id.list);
            TextView sub = list.getChildAt(0).findViewById(R.id.row_sub);
            assertEquals(f.dateTime(t, z, false), sub.getText().toString());
            assertEquals(1, sub.getLineCount());
            assertEquals(f + " not cut", 0, sub.getLayout().getEllipsisCount(0));
            assertTrue("plenty of room: " + sub.getPaint().measureText(sub.getText().toString()) + " of " + sub.getWidth(),
                    sub.getPaint().measureText(sub.getText().toString()) < sub.getWidth() * 0.7f);
        }
    }

    @Test public void createTapTogglesBrightness() throws Exception {
        Intent i = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_CREATE);
        ActivityController<MainActivity> ac = Robolectric.buildActivity(MainActivity.class, i).setup();
        MainActivity m = ac.get();
        ((EditText) m.findViewById(R.id.input)).setText("https://github.com/zmlworks/qr-reader");
        waitFor(() -> m.createPage.qrForTest() != null);
        View box = m.findViewById(R.id.qr_box);
        TextView hint = m.findViewById(R.id.qr_hint);
        android.view.WindowManager.LayoutParams lp = m.getWindow().getAttributes();
        assertEquals(android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE, lp.screenBrightness, 0f);
        box.performClick();
        assertEquals(1f, m.getWindow().getAttributes().screenBrightness, 0f);
        assertEquals(View.VISIBLE, hint.getVisibility());
        assertEquals("Brightness max", hint.getText().toString());
        shot(m, "create-brightness-max", null);
        ShadowLooper.idleMainLooper(1300, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("hint gone after about a second", View.GONE, hint.getVisibility());
        box.performClick();
        assertEquals(android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE, m.getWindow().getAttributes().screenBrightness, 0f);
        assertEquals("Brightness normal", hint.getText().toString());
        // leaving the page restores it
        box.performClick();
        assertTrue(m.createPage.brightForTest());
        m.select(MainActivity.TAB_HISTORY);
        assertEquals(android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE, m.getWindow().getAttributes().screenBrightness, 0f);
        // and so does leaving the app
        m.select(MainActivity.TAB_CREATE);
        box.performClick();
        assertEquals(1f, m.getWindow().getAttributes().screenBrightness, 0f);
        ac.pause();
        assertEquals(android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE, m.getWindow().getAttributes().screenBrightness, 0f);
        assertTrue(!m.createPage.brightForTest());
    }

    // ---- 0.4.0 ----

    /** drags the slider's thumb from the start to frac of the track and lets go */
    static void slide(SlideToConfirm sl, float frac) {
        if (sl.getWidth() == 0) {
            sl.measure(View.MeasureSpec.makeMeasureSpec(dp(sl, 300), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dp(sl, 56), View.MeasureSpec.EXACTLY));
            sl.layout(0, 0, dp(sl, 300), dp(sl, 56));
        }
        float h = sl.getHeight() - sl.getPaddingTop() - sl.getPaddingBottom(), x0 = sl.getPaddingLeft() + h / 2, y = sl.getHeight() / 2f;
        float x1 = x0 + frac * (sl.getWidth() - sl.getPaddingLeft() - sl.getPaddingRight() - h);
        long t = android.os.SystemClock.uptimeMillis();
        sl.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, x0, y, 0));
        sl.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 50, android.view.MotionEvent.ACTION_MOVE, (x0 + x1) / 2, y, 0));
        sl.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 100, android.view.MotionEvent.ACTION_MOVE, x1, y, 0));
        sl.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 150, android.view.MotionEvent.ACTION_UP, x1, y, 0));
        ShadowLooper.idleMainLooper();
    }

    static int dp(View v, int d) { return Math.round(d * v.getResources().getDisplayMetrics().density); }

    static String message(AlertDialog d) { return ((TextView) d.findViewById(R.id.clear_message)).getText().toString(); }

    // ---- 0.5.0 ----

    @Test public void settingsClearAllWithFavoritesAndProtectedChoices() throws Exception {
        Intent i = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_SETTINGS);
        MainActivity m = Robolectric.buildActivity(MainActivity.class, i).setup().get();
        layout(m);
        // first row of the page; 0.7.0: the scan button is back in the Settings top bar, jump and search keep their slots
        View clear = m.findViewById(R.id.set_clear), clearRow = m.findViewById(R.id.set_clear_row);
        assertTrue(topIn(clear) < topIn(m.findViewById(R.id.set_left)));
        assertTrue(topIn((View) clearRow.getParent()) - (topIn(m.findViewById(R.id.top_bar)) + m.findViewById(R.id.top_bar).getHeight()) < dp(m, 4));
        assertEquals(View.VISIBLE, m.findViewById(R.id.btn_scan_top).getVisibility());
        assertEquals(View.INVISIBLE, m.findViewById(R.id.btn_jump).getVisibility());
        assertEquals(View.INVISIBLE, m.findViewById(R.id.btn_search).getVisibility());
        CheckBox fav = m.findViewById(R.id.cb_clear_fav), prot = m.findViewById(R.id.cb_clear_prot);
        assertTrue("both off by default", !fav.isChecked() && !prot.isChecked());
        // stacked: incl. favorites above incl. protected, on the right of the row
        View rf = m.findViewById(R.id.set_clear_fav), rp = m.findViewById(R.id.set_clear_prot);
        assertTrue(topIn(rf) < topIn(rp));
        assertTrue(rf.getLeft() >= 0 && centerX(rf) > W / 2 && centerX(rp) > W / 2);
        assertEquals("incl. favorites", ((TextView) ((ViewGroup) rf).getChildAt(0)).getText().toString());
        assertEquals("incl. protected", ((TextView) ((ViewGroup) rp).getChildAt(0)).getText().toString());
        assertEquals("10 codes", ((TextView) m.findViewById(R.id.clear_value)).getText().toString());
        h.setProtected(url, true);
        // default: everything but favorites (jp) and protected codes (url); no question about the protected one
        clear.performClick();
        AlertDialog d = m.settingsPage.lastDialog;
        assertEquals("Delete all history?", shadowOf(d).getTitle().toString());
        assertEquals("8 codes will be deleted.", message(d));
        for (int b : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE})
            assertTrue("no buttons, only the slider", d.getButton(b) == null || d.getButton(b).getVisibility() != View.VISIBLE);
        shot(m, "settings-clear-default", null, d);
        // a short drag springs back, nothing deleted; back cancels
        slide(m.settingsPage.lastSlider, 0.5f);
        assertTrue(d.isShowing());
        assertEquals(6, h.topLevel().size());
        d.onBackPressed();
        assertTrue(!d.isShowing());
        assertEquals(6, h.topLevel().size());
        // all the way: deleted, favorites and protected kept, no authentication
        clear.performClick();
        slide(m.settingsPage.lastSlider, 1f);
        assertEquals(0, Auth.testPrompts);
        assertTrue(!m.settingsPage.lastDialog.isShowing());
        assertEquals(2, h.topLevel().size());
        // incl. protected: asks first (refused -> nothing deleted), singular texts
        m.findViewById(R.id.set_clear_prot).performClick();
        assertTrue(prot.isChecked());
        clear.performClick();
        d = m.settingsPage.lastDialog;
        assertEquals("1 code will be deleted.\nThis includes 1 protected code.", message(d));
        shot(m, "settings-clear-including-protected", null, d);
        Auth.testResult = false;
        slide(m.settingsPage.lastSlider, 1f);
        assertEquals(1, Auth.testPrompts);
        assertEquals(2, h.topLevel().size());
        Auth.testResult = true;
        clear.performClick();
        slide(m.settingsPage.lastSlider, 1f);
        assertEquals(2, Auth.testPrompts);
        assertEquals(1, h.topLevel().size());
        assertEquals(jp, h.topLevel().get(0).id);
        // only the favorite left and incl. favorites off: nothing to delete, no dialog
        AlertDialog before = m.settingsPage.lastDialog;
        clear.performClick();
        assertSame(before, m.settingsPage.lastDialog);
        assertEquals("Nothing to delete", org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        // incl. favorites: a tap outside cancels, then sliding deletes it
        m.findViewById(R.id.set_clear_fav).performClick();
        assertTrue(fav.isChecked());
        clear.performClick();
        d = m.settingsPage.lastDialog;
        assertEquals("1 code will be deleted.", message(d));
        assertTrue("tap outside cancels", shadowOf(d).isCancelableOnTouchOutside());
        slide(m.settingsPage.lastSlider, 1f);
        assertEquals(0, h.size());
        // the History tab still has its scan button
        m.select(MainActivity.TAB_HISTORY);
        assertEquals(View.VISIBLE, m.findViewById(R.id.btn_scan_top).getVisibility());
        assertEquals(View.VISIBLE, m.findViewById(R.id.btn_jump).getVisibility());
        // not remembered: a new start has both off
        MainActivity m2 = Robolectric.buildActivity(MainActivity.class, i).setup().get();
        assertTrue(!((CheckBox) m2.findViewById(R.id.cb_clear_fav)).isChecked() && !((CheckBox) m2.findViewById(R.id.cb_clear_prot)).isChecked());
    }

    @Test public void clearCountsPlural() throws Exception {
        h.setProtected(url, true);
        h.setProtected(wifi, true);
        Intent i = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_SETTINGS);
        MainActivity m = Robolectric.buildActivity(MainActivity.class, i).setup().get();
        m.findViewById(R.id.set_clear_prot).performClick();
        m.findViewById(R.id.set_clear_fav).performClick();
        m.findViewById(R.id.set_clear).performClick();
        assertEquals("10 codes will be deleted.\nThis includes 2 protected codes.", message(m.settingsPage.lastDialog));
    }

    @Test public void unlockPillOnSettingsToo() throws Exception {
        h.setProtected(url, true);
        MainActivity m = Robolectric.buildActivity(MainActivity.class).setup().get();
        Auth.testResult = true;
        Auth.require(m, true, "test", () -> { }, null);
        ShadowLooper.idleMainLooper();
        View pill = m.findViewById(R.id.unlock_pill);
        layout(m);
        assertEquals(View.VISIBLE, pill.getVisibility());
        int[] onHistory = new int[2];
        pill.getLocationInWindow(onHistory);
        int wHistory = pill.getWidth();
        m.select(MainActivity.TAB_SETTINGS);
        layout(m);
        assertEquals("pill in the Settings top bar", View.VISIBLE, pill.getVisibility());
        int[] onSettings = new int[2];
        pill.getLocationInWindow(onSettings);
        assertEquals("same slot as on History, no jump: x", onHistory[0], onSettings[0]);
        assertEquals("y", onHistory[1], onSettings[1]);
        assertEquals(wHistory, pill.getWidth());
        shot(m, "settings-unlock-pill", null);
        m.select(MainActivity.TAB_CREATE);
        assertEquals(View.GONE, pill.getVisibility());
        m.select(MainActivity.TAB_SETTINGS);
        pill.performClick();   // tap locks
        assertEquals(0, Auth.remainingMs());
        assertEquals(View.GONE, pill.getVisibility());
    }

    @Test public void jumpToDate() throws Exception {
        java.time.ZoneId z = java.time.ZoneId.systemDefault();
        h.clear();
        java.time.LocalDate today = java.time.LocalDate.now(z), base = today.minusDays(2);
        java.time.LocalDate gap = base.minusDays(16);
        for (int k = 0; k < 40; k++) {   // one code per day, 40 days back from 2 days ago, skipping one day
            java.time.LocalDate d = base.minusDays(k);
            if (d.equals(gap)) continue;
            h.addSingle("code of " + d, d.atTime(12, 0).atZone(z).toInstant().toEpochMilli());
        }
        java.time.LocalDate oldest = base.minusDays(39);
        MainActivity m = Robolectric.buildActivity(MainActivity.class).setup().get();
        layout(m);
        m.findViewById(R.id.btn_jump).performClick();
        ShadowLooper.idleMainLooper();
        AlertDialog d = m.historyPage.jumpDialog;
        android.widget.NumberPicker day = d.findViewById(R.id.jump_day), month = d.findViewById(R.id.jump_month), year = d.findViewById(R.id.jump_year);
        assertNotNull(day);
        assertSame(m.historyPage.jumpPickers[0], day);
        // order day, month, year; starts at the newest code's day
        ViewGroup row = (ViewGroup) ((View) day.getParent()).getParent();
        assertEquals(day.getParent(), row.getChildAt(0));
        assertEquals(month.getParent(), row.getChildAt(1));
        assertEquals(year.getParent(), row.getChildAt(2));
        assertEquals(base.getDayOfMonth(), day.getValue());
        assertEquals(base.getMonthValue(), month.getValue());
        assertEquals(base.getYear(), year.getValue());
        assertEquals(oldest.getYear(), year.getMinValue());
        assertEquals(today.getYear(), year.getMaxValue());
        if (base.getMonthValue() == today.getMonthValue()) assertEquals("no later month than today's", today.getMonthValue(), month.getMaxValue());
        shot(m, "history-jump-to-date", null, d);
        // the range is oldest .. today: turning past today stops at today, before the oldest stops there
        spin(year, year.getMaxValue());
        spin(month, 12);
        spin(day, 31);
        assertEquals(today, date(day, month, year));
        spin(year, year.getMinValue());
        spin(month, 1);
        spin(day, 1);
        assertEquals(oldest, date(day, month, year));
        // the gap day: the nearest earlier one
        spin(year, gap.getYear());
        spin(month, gap.getMonthValue());
        spin(day, gap.getDayOfMonth());
        assertEquals(gap, date(day, month, year));
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        layout(m);
        SwipeListView list = m.findViewById(R.id.list);
        assertEquals("code of " + gap.minusDays(1), h.get(list.getAdapter().getItemId(list.getFirstVisiblePosition())).text);
        assertEquals(15, m.historyPage.jumpTo(gap.plusDays(1)));
        assertEquals(-1, m.historyPage.jumpTo(oldest.minusDays(1)));
        assertEquals(0, m.historyPage.jumpTo(today));
    }

    /** turns a wheel to v as a user would (the change listener runs) */
    static void spin(android.widget.NumberPicker p, int v) {
        int old = p.getValue();
        p.setValue(Math.max(p.getMinValue(), Math.min(p.getMaxValue(), v)));
        try {
            java.lang.reflect.Method m = android.widget.NumberPicker.class.getDeclaredMethod("notifyChange", int.class, int.class);
            m.setAccessible(true);
            m.invoke(p, old, p.getValue());
        } catch (Exception e) { throw new AssertionError(e); }
    }

    static java.time.LocalDate date(android.widget.NumberPicker d, android.widget.NumberPicker m, android.widget.NumberPicker y) {
        return java.time.LocalDate.of(y.getValue(), m.getValue(), d.getValue());
    }

    // -- "Already read" regression: only for codes seen again in a frame of the current scanning session --

    static List<QrEngine.Code> frame(String... texts) {
        List<QrEngine.Code> l = new ArrayList<>();
        for (String t : texts) l.add(new QrEngine.Code(t, new float[]{400, 400, 600, 400, 600, 600, 400, 600}));
        return l;
    }

    static boolean alreadyRead(ScannerActivity s) {
        CharSequence t = s.pillTextForTest();
        return t != null && t.toString().startsWith("Already read");
    }

    static void scanFrames(ScannerActivity s, long ms, String... texts) {
        for (long t = 0; t < ms; t += 100) {
            s.handleFrame(frame(texts), 1000, 1000, android.os.SystemClock.uptimeMillis());
            ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    @Test public void alreadyReadNotAtLaunchOrResume() throws Exception {
        // launch with a history whose newest item is "https://github.com/zmlworks/qr-reader"
        ActivityController<ScannerActivity> ac = Robolectric.buildActivity(ScannerActivity.class).setup();
        ScannerActivity s = ac.get();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(!alreadyRead(s));
        // a read: no pill during the freeze, even with more frames of it
        s.handleFrame(frame("A"), 1000, 1000, android.os.SystemClock.uptimeMillis());
        ShadowLooper.idleMainLooper();
        scanFrames(s, ScannerActivity.FREEZE_MS - 50, "A");
        assertTrue("not during the freeze", !alreadyRead(s));
        ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS);
        Intent di = shadowOf(s).getNextStartedActivity();
        assertEquals(DetailActivity.class.getName(), di.getComponent().getClassName());
        assertEquals(1, countText("A"));
        // the detail page covers the scanner (still in the app)
        ActivityController<DetailActivity> dc = Robolectric.buildActivity(DetailActivity.class, di).setup();
        ac.pause().stop();
        // back from the detail page: nothing shown until a frame shows the code again
        dc.pause();
        ac.restart().start().resume();
        dc.stop();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue("not on return from the detail page", !alreadyRead(s));
        scanFrames(s, 300, "A");                       // still pointing at it: not reopened, and now the pill
        assertEquals(null, shadowOf(s).getNextStartedActivity());
        assertTrue(alreadyRead(s));
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue("gone when the code leaves the frames", !alreadyRead(s));
        // away from it for a while, back at it: a fresh read
        scanFrames(s, ScanLogic.FORGET_MS + 200);
        scanFrames(s, 100, "A");
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(DetailActivity.class.getName(), shadowOf(s).getNextStartedActivity().getComponent().getClassName());
    }

    @Test public void alreadyReadNotAfterLeavingTheApp() throws Exception {
        ActivityController<ScannerActivity> ac = Robolectric.buildActivity(ScannerActivity.class).setup();
        ScannerActivity s = ac.get();
        s.handleFrame(frame("A"), 1000, 1000, android.os.SystemClock.uptimeMillis());
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        shadowOf(s).getNextStartedActivity();
        ac.pause().stop();                              // to the home screen: a new visit next time
        ShadowLooper.idleMainLooper(30, java.util.concurrent.TimeUnit.SECONDS);
        ac.restart().start().resume();                  // "launch" again
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue("not at launch", !alreadyRead(s));
        scanFrames(s, 100, "A");                        // a new visit reads it as new
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(!alreadyRead(s));
        assertEquals(DetailActivity.class.getName(), shadowOf(s).getNextStartedActivity().getComponent().getClassName());
    }

    @Test public void alreadyReadNotAfterThePhotoPicker() throws Exception {
        ActivityController<ScannerActivity> ac = Robolectric.buildActivity(ScannerActivity.class).setup();
        ScannerActivity s = ac.get();
        s.findViewById(R.id.btn_image).performClick();
        org.robolectric.shadows.ShadowActivity.IntentForResult pick = shadowOf(s).getNextStartedActivityForResult();
        ac.pause().stop();
        shadowOf(s).receiveResult(pick.intent, Activity.RESULT_CANCELED, null);
        ac.restart().start().resume();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(!alreadyRead(s));
        assertTrue(!App.get().holdForeground);
        // a picture with one code: its detail opens; back on the scanner no pill without frames
        s.findViewById(R.id.btn_image).performClick();
        pick = shadowOf(s).getNextStartedActivityForResult();
        ac.pause().stop();
        shadowOf(s).receiveResult(pick.intent, Activity.RESULT_OK, new Intent().setData(photo("photo-pill", new String[]{"From a photo"})));
        ac.restart().start().resume();
        waitFor(() -> "From a photo".equals(h.topLevel().get(0).text));
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(!alreadyRead(s));
    }

    int countText(String t) { int n = 0; for (History.Entry e : h.topLevel()) if (t.equals(e.text)) n++; return n; }

    // ---- 0.7.0 ----

    static MainActivity main(int tab) {
        Intent i = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class).putExtra(MainActivity.EXTRA_TAB, tab);
        MainActivity m = Robolectric.buildActivity(MainActivity.class, i).setup().get();
        layout(m);
        return m;
    }

    static List<Long> ids(SwipeListView list) {
        List<Long> r = new ArrayList<>();
        for (int k = 0; k < list.getAdapter().getCount(); k++) r.add(list.getAdapter().getItemId(k));
        return r;
    }

    static View rowOf(SwipeListView list, long id) {
        for (int k = 0; k < list.getChildCount(); k++)
            if (list.getAdapter().getItemId(list.getFirstVisiblePosition() + k) == id) return list.getChildAt(k);
        return null;
    }

    /** a horizontal drag of `dx` px on `row`, released without a fling (slow steps) */
    static void swipeBy(SwipeListView list, View row, int dx) {
        int y = row.getTop() + row.getHeight() / 2, x0 = dx > 0 ? 60 : W - 200;   // (left: clear of a ≡ handle)
        long t = android.os.SystemClock.uptimeMillis();
        list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, x0, y, 0));
        int steps = 30;
        for (int i = 1; i <= steps; i++)
            list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + i * 60L, android.view.MotionEvent.ACTION_MOVE, x0 + dx * i / (float) steps, y, 0));
        list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + steps * 60L + 400, android.view.MotionEvent.ACTION_MOVE, x0 + dx, y, 0));
        list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + steps * 60L + 800, android.view.MotionEvent.ACTION_UP, x0 + dx, y, 0));
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
    }

    static void type(EditText et, String text) {
        et.setText(text);
        ShadowLooper.idleMainLooper();
    }

    static boolean bold(View row) {
        android.graphics.Typeface tf = ((TextView) row.findViewById(R.id.row_title)).getTypeface();
        return tf != null && tf.isBold();
    }

    @Test public void historySearchLockedUnlockedAndBothTabs() throws Exception {
        h.setProtected(url, true);
        h.setMemo(jp, "Card from Tanaka-san");
        long ean = h.addSingle("4006381333931", System.currentTimeMillis() - 30 * 86_400_000L, "EAN-13").id;
        MainActivity m = main(MainActivity.TAB_HISTORY);
        SwipeListView list = m.findViewById(R.id.list);
        View bar = m.findViewById(R.id.search_bar);
        TextView hidden = m.findViewById(R.id.search_hidden);
        assertEquals(View.GONE, bar.getVisibility());
        assertEquals(View.VISIBLE, m.findViewById(R.id.btn_search).getVisibility());
        m.findViewById(R.id.btn_search).performClick();
        assertEquals(View.VISIBLE, bar.getVisibility());
        EditText in = m.findViewById(R.id.search_input);
        // locked: the protected URL is left out, a line says so
        type(in, "github");
        assertEquals(List.of(), ids(list));
        assertEquals(View.VISIBLE, hidden.getVisibility());
        assertEquals("1 protected item hidden · Unlock", hidden.getText().toString());
        assertEquals(m.getString(R.string.no_matches), ((TextView) m.findViewById(R.id.empty)).getText().toString());
        layout(m);
        shot(m, "history-search-locked", null);
        // matches on text, name, memo and format, case-insensitive, as you type
        type(in, "CAFÉ");
        assertEquals(List.of(wifi), ids(list));
        type(in, "tanaka");
        assertEquals(List.of(jp), ids(list));
        type(in, "ean-13");
        assertEquals(List.of(ean), ids(list));
        type(in, "inv-2026-10-103");
        assertEquals("a multi scan through its codes", List.of(group), ids(list));
        // tap the line: authenticate -> the protected code is searched and shown normally (lock badge only)
        type(in, "github");
        Auth.testResult = true;
        hidden.performClick();
        assertEquals(1, Auth.testPrompts);
        assertEquals(List.of(url), ids(list));
        assertEquals(View.GONE, hidden.getVisibility());
        layout(m);
        assertEquals("https://github.com/zmlworks/qr-reader", rowTitle(list, 0));
        assertNull(list.getChildAt(0).findViewById(R.id.row_title).getTag(R.id.obscured));
        shot(m, "history-search-unlocked", null);
        // the window runs out: filtered again at once
        ShadowLooper.idleMainLooper(61, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(List.of(), ids(list));
        assertEquals(View.VISIBLE, hidden.getVisibility());
        // Favorites tab: the same query on the favourites only
        h.setFavorite(url, true);
        h.setFavorite(wifi, true);
        m.findViewById(R.id.seg_fav).performClick();
        assertEquals(List.of(), ids(list));
        assertEquals("1 protected item hidden · Unlock", hidden.getText().toString());
        type(in, "caf");
        assertEquals(List.of(wifi), ids(list));   // "Café …" (the name) and not the protected URL
        type(in, "tanaka");
        assertEquals(List.of(jp), ids(list));
        type(in, "");
        assertEquals(View.GONE, hidden.getVisibility());
        assertEquals(3, list.getAdapter().getCount());
        // back closes the search first
        type(in, "tanaka");
        m.onBackPressed();
        assertEquals(View.GONE, bar.getVisibility());
        assertTrue(!m.isFinishing());
        assertEquals(3, list.getAdapter().getCount());
    }

    @Test public void boldNamesInTheList() throws Exception {
        MainActivity m = main(MainActivity.TAB_HISTORY);
        SwipeListView list = m.findViewById(R.id.list);
        assertTrue("named: bold", bold(rowOf(list, wifi)));
        assertEquals("Café guest Wi-Fi", ((TextView) rowOf(list, wifi).findViewById(R.id.row_title)).getText().toString());
        assertTrue("not named: normal", !bold(rowOf(list, url)));
        assertTrue(!bold(rowOf(list, group)));
        // a recycled row loses the bold when the name goes
        h.setName(wifi, null);
        m.historyPage.refresh();
        layout(m);
        assertTrue(!bold(rowOf(list, wifi)));
        h.setName(wifi, "Café guest Wi-Fi");
        m.historyPage.refresh();
        layout(m);
        shot(m, "history-bold-names", null);
    }

    @Test public void graceShowsProtectedRowsAndReblursLive() throws Exception {
        h.setProtected(wifi, true);
        h.setFavorite(wifi, true);
        List<History.Entry> ch = h.children(group);
        h.setProtected(ch.get(0).id, true);
        MainActivity m = main(MainActivity.TAB_HISTORY);
        SwipeListView list = m.findViewById(R.id.list);
        checkObscured(rowOf(list, wifi), h.get(wifi), "bitmap");
        Auth.testResult = true;
        Auth.require(m, true, "t", () -> { }, null);
        layout(m);
        View row = rowOf(list, wifi);
        TextView title = row.findViewById(R.id.row_title);
        assertNull("no blur in the window", title.getTag(R.id.obscured));
        assertEquals("Café guest Wi-Fi", title.getText().toString());
        assertTrue(bold(row));
        assertEquals("the small lock stays", " ", ((TextView) row.findViewById(R.id.row_badge)).getText().toString());
        assertEquals(View.VISIBLE, row.findViewById(R.id.row_star).getVisibility());
        shot(m, "history-grace", null);
        // multi-scan list too
        Intent i = new Intent(RuntimeEnvironment.getApplication(), CodesActivity.class).putExtra(CodesActivity.EXTRA_GROUP, group)
                .putExtra(CodesActivity.EXTRA_JUST_SCANNED, true);
        CodesActivity c = Robolectric.buildActivity(CodesActivity.class, i).setup().get();
        layout(c);
        SwipeListView cl = c.findViewById(R.id.list);
        assertEquals("INV-2026-10-101", rowTitle(cl, 0));
        shot(c, "just-scanned-grace", null);
        // the window ends by itself: blurred again without any tap
        ShadowLooper.idleMainLooper(61, java.util.concurrent.TimeUnit.SECONDS);
        layout(m);
        checkObscured(rowOf(list, wifi), h.get(wifi), "bitmap");
        assertTrue(!bold(rowOf(list, wifi)));
        layout(c);
        checkObscured(cl.getChildAt(0), ch.get(0), "bitmap");
        // the pill ends it early: blurred at once
        Auth.require(m, true, "t", () -> { }, null);
        layout(m);
        assertNull(rowOf(list, wifi).findViewById(R.id.row_title).getTag(R.id.obscured));
        m.findViewById(R.id.unlock_pill).performClick();
        layout(m);
        checkObscured(rowOf(list, wifi), h.get(wifi), "bitmap");
    }

    @Test public void swipeThresholdAndProtectedDeleteNeedsAuth() throws Exception {
        assertEquals(0.55f, SwipeListView.SWIPE_FRACTION, 0f);
        assertEquals(0.3f, SwipeListView.FLING_FRACTION, 0f);
        int w = 1000, fling = 1000;
        assertTrue("0.35 of the width was enough before 0.7.0", !SwipeListView.isSwipe(350, 0, w, fling));
        assertTrue(!SwipeListView.isSwipe(540, 0, w, fling));
        assertTrue(SwipeListView.isSwipe(560, 0, w, fling));
        assertTrue("a fling needs 0.3 of the width now", !SwipeListView.isSwipe(250, 5000, w, fling));
        assertTrue(SwipeListView.isSwipe(310, 5000, w, fling));
        MainActivity m = main(MainActivity.TAB_HISTORY);
        SwipeListView list = m.findViewById(R.id.list);
        // half the width: neither delete nor copy any more
        swipeBy(list, rowOf(list, url), (int) (W * 0.45f));
        assertNotNull(h.get(url));
        android.content.ClipboardManager cm = m.getSystemService(android.content.ClipboardManager.class);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("x", "before"));
        layout(m);
        swipeBy(list, rowOf(list, url), -(int) (W * 0.45f));
        assertEquals("before", cm.getPrimaryClip().getItemAt(0).getText().toString());
        layout(m);
        swipeBy(list, rowOf(list, url), -(int) (W * 0.6f));
        assertEquals("https://github.com/zmlworks/qr-reader", cm.getPrimaryClip().getItemAt(0).getText().toString());
        // protected: swipe-right delete asks; refused -> still there, the row springs back
        h.setProtected(url, true);
        m.historyPage.refresh();
        layout(m);
        Auth.testResult = false;
        swipeBy(list, rowOf(list, url), (int) (W * 0.7f));
        assertEquals(1, Auth.testPrompts);
        assertNotNull(h.get(url));
        layout(m);
        assertEquals(0f, rowOf(list, url).findViewById(R.id.row_content).getTranslationX(), 0f);
        // accepted -> deleted with UNDO
        Auth.testResult = true;
        swipeBy(list, rowOf(list, url), (int) (W * 0.7f));
        assertEquals(2, Auth.testPrompts);
        assertNull(h.get(url));
        assertEquals(View.VISIBLE, m.findViewById(R.id.snack).getVisibility());
        m.findViewById(R.id.snack_action).performClick();
        assertNotNull(h.get(url));
        // inside the window: no new prompt
        layout(m);
        swipeBy(list, rowOf(list, url), (int) (W * 0.7f));
        assertEquals(2, Auth.testPrompts);
        assertNull(h.get(url));
        // a multi scan holding a protected code asks too (deleting it deletes that code)
        Auth.lockNow();
        h.setProtected(h.children(group).get(1).id, true);
        m.historyPage.refresh();
        layout(m);
        Auth.testResult = false;
        swipeBy(list, rowOf(list, group), (int) (W * 0.7f));
        assertEquals(3, Auth.testPrompts);
        assertNotNull(h.get(group));
        // and the protected code inside the "n codes" list
        Intent i = new Intent(RuntimeEnvironment.getApplication(), CodesActivity.class).putExtra(CodesActivity.EXTRA_GROUP, group);
        CodesActivity c = Robolectric.buildActivity(CodesActivity.class, i).setup().get();
        layout(c);
        SwipeListView cl = c.findViewById(R.id.list);
        swipeBy(cl, cl.getChildAt(1), (int) (W * 0.7f));
        assertEquals(4, Auth.testPrompts);
        assertEquals(5, h.childCount(group));
        swipeBy(cl, cl.getChildAt(0), (int) (W * 0.7f));   // an unprotected one: no prompt
        assertEquals(4, Auth.testPrompts);
        assertEquals(4, h.childCount(group));
    }

    /** presses the ≡ of `row` and drags it by `dy` px */
    static void dragHandle(SwipeListView list, View row, int dy) {
        View hnd = row.findViewById(R.id.row_handle);
        int[] l = new int[2], hl = new int[2];
        list.getLocationInWindow(l);
        hnd.getLocationInWindow(hl);
        float x = hl[0] - l[0] + hnd.getWidth() / 2f, y = hl[1] - l[1] + hnd.getHeight() / 2f;
        long t = android.os.SystemClock.uptimeMillis();
        list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, x, y, 0));
        for (int i = 1; i <= 20; i++)
            list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + i * 20L, android.view.MotionEvent.ACTION_MOVE, x, y + dy * i / 20f, 0));
        list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 500, android.view.MotionEvent.ACTION_UP, x, y + dy, 0));
        ShadowLooper.idleMainLooper();
    }

    @Test public void favoritesReorderByHandleOnlyAndJumpHidden() throws Exception {
        h.setFavorite(url, true);
        h.setFavorite(wifi, true);   // with jp: three favourites, newest first url, wifi, jp
        MainActivity m = main(MainActivity.TAB_HISTORY);
        SwipeListView list = m.findViewById(R.id.list);
        View pill = m.findViewById(R.id.unlock_pill);
        assertEquals(View.VISIBLE, m.findViewById(R.id.btn_jump).getVisibility());
        assertEquals(View.GONE, list.getChildAt(0).findViewById(R.id.row_handle).getVisibility());
        Auth.testResult = true;
        Auth.require(m, true, "t", () -> { }, null);
        layout(m);
        int[] pAll = new int[2];
        pill.getLocationInWindow(pAll);
        m.findViewById(R.id.seg_fav).performClick();
        layout(m);
        // Favorites: no jump to date (its slot stays: the pill does not move), a ≡ on every row
        assertEquals(View.INVISIBLE, m.findViewById(R.id.btn_jump).getVisibility());
        int[] pFav = new int[2];
        pill.getLocationInWindow(pFav);
        assertEquals(pAll[0], pFav[0]);
        Auth.lockNow();
        layout(m);
        assertEquals(List.of(url, wifi, jp), ids(list));
        for (int k = 0; k < 3; k++) {
            View hnd = list.getChildAt(k).findViewById(R.id.row_handle);
            assertEquals(View.VISIBLE, hnd.getVisibility());
            assertTrue("on the right", centerX(hnd) > W * 0.85f);
        }
        shot(m, "history-favorites-reorder", null);
        // drag url's handle two rows down
        int rowH = list.getChildAt(1).getTop() - list.getChildAt(0).getTop();
        dragHandle(list, list.getChildAt(0), rowH * 2);
        assertEquals(List.of(wifi, jp, url), ids(list));
        assertEquals("persisted", List.of(wifi, jp, url), h.favoriteOrder());
        assertEquals(List.of(wifi, jp, url), History.parse(h.serialize()).favoriteOrder());
        layout(m);
        // a vertical drag elsewhere on the row is not a reorder (just the list)
        View r0 = list.getChildAt(0);
        int y = r0.getTop() + r0.getHeight() / 2;
        long t = android.os.SystemClock.uptimeMillis();
        list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, W / 2f, y, 0));
        for (int i = 1; i <= 10; i++) list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + i * 20L, android.view.MotionEvent.ACTION_MOVE, W / 2f, y + rowH * i / 5f, 0));
        list.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 300, android.view.MotionEvent.ACTION_UP, W / 2f, y + rowH * 2, 0));
        ShadowLooper.idleMainLooper();
        assertEquals(List.of(wifi, jp, url), h.favoriteOrder());
        // swipes still work on the rest of the row: left = copy
        layout(m);
        swipeBy(list, rowOf(list, jp), -(int) (W * 0.7f));
        android.content.ClipboardManager cm = m.getSystemService(android.content.ClipboardManager.class);
        assertEquals("こんにちは、世界！ QR コードのテスト", cm.getPrimaryClip().getItemAt(0).getText().toString());
        assertEquals(List.of(wifi, jp, url), ids(list));
        // a new start shows the saved order; searching hides the handles (no reordering of a filtered list)
        MainActivity m2 = main(MainActivity.TAB_HISTORY);
        m2.findViewById(R.id.seg_fav).performClick();
        SwipeListView l2 = m2.findViewById(R.id.list);
        assertEquals(List.of(wifi, jp, url), ids(l2));
        m2.findViewById(R.id.btn_search).performClick();
        type(m2.findViewById(R.id.search_input), "http");
        layout(m2);
        assertEquals(View.GONE, l2.getChildAt(0).findViewById(R.id.row_handle).getVisibility());
        // back to All: jump is back, no handles
        m2.findViewById(R.id.seg_all).performClick();
        assertEquals(View.VISIBLE, m2.findViewById(R.id.btn_jump).getVisibility());
    }

    /** decodes what a QrView shows */
    static String decodeView(View v) throws Exception {
        Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFFFFFFFF);
        v.draw(c);
        int[] px = new int[b.getWidth() * b.getHeight()];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        java.util.Map<com.google.zxing.DecodeHintType, Object> hints = new java.util.EnumMap<>(com.google.zxing.DecodeHintType.class);
        hints.put(com.google.zxing.DecodeHintType.TRY_HARDER, Boolean.TRUE);
        return new com.google.zxing.qrcode.QRCodeReader().decode(new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(
                new com.google.zxing.RGBLuminanceSource(b.getWidth(), b.getHeight(), px))), hints).getText();
    }

    @Test public void createSaveToHistoryAndCreatedDetail() throws Exception {
        MainActivity m = main(MainActivity.TAB_CREATE);
        TextView save = m.findViewById(R.id.save_history);
        assertEquals("Save to history", save.getText().toString());
        assertTrue("small, not a big button", save.getHeight() <= dp(m, 36) && save.getWidth() < W / 2);
        // empty text: ignored
        assertTrue(!save.isEnabled());
        int n = h.size();
        assertEquals(History.SAVE_EMPTY, m.createPage.saveToHistory());
        assertEquals(n, h.size());
        String text = "https://example.com/menu?table=12";
        ((EditText) m.findViewById(R.id.input)).setText(text);
        waitFor(() -> m.createPage.qrForTest() != null);
        assertTrue(save.isEnabled());
        layout(m);
        shot(m, "create-save-to-history", null);
        save.performClick();
        assertEquals(n + 1, h.size());
        History.Entry c = h.topLevel().get(0);
        assertTrue(c.created);
        assertEquals(text, c.text);
        assertEquals("Saved to history", org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        // twice: not added again
        save.performClick();
        assertEquals(n + 1, h.size());
        assertEquals("Already saved", org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        // History: a small "Created" marker on the date line
        m.select(MainActivity.TAB_HISTORY);
        layout(m);
        SwipeListView list = m.findViewById(R.id.list);
        assertEquals(c.id, list.getAdapter().getItemId(0));
        String sub = ((TextView) list.getChildAt(0).findViewById(R.id.row_sub)).getText().toString();
        assertTrue(sub, sub.endsWith(" · Created"));
        shot(m, "history-created", null);
        // its detail page starts with the QR code, then the text
        DetailActivity d = detail(c.id);
        layout(d);
        QrView qr = d.qrForTest();
        assertEquals(View.VISIBLE, qr.getVisibility());
        assertTrue(qr.hasQr());
        assertTrue("QR above the text", topIn(qr) + qr.getHeight() <= topIn(d.findViewById(R.id.text)));
        assertEquals(text, decodeView(qr));
        assertTrue(((TextView) d.findViewById(R.id.meta)).getText().toString().contains("Created"));
        shot(d, "detail-created", null);
        // a scanned code has no QR on its page
        DetailActivity d2 = detail(url);
        assertEquals(View.GONE, d2.qrForTest().getVisibility());
    }

    @Test public void detailCopyUnderTheTextAndMemo() throws Exception {
        DetailActivity d = detail(jp);
        layout(d);
        TextView copy = d.findViewById(R.id.act_copy), text = d.findViewById(R.id.text);
        // Copy is its own small control right under the content, not one of the rows
        android.widget.LinearLayout rows = d.findViewById(R.id.actions);
        assertTrue(copy.getParent() != rows);
        int gap = topIn(copy) - (topIn(text) + text.getHeight());
        assertTrue("right under the text: " + gap, gap >= 0 && gap <= dp(d, 4));
        assertTrue(topIn(copy) < topIn(rows));
        assertTrue("small", copy.getHeight() <= dp(d, 40) && copy.getWidth() < W / 3);
        copy.performClick();
        android.content.ClipboardManager cm = d.getSystemService(android.content.ClipboardManager.class);
        assertEquals("こんにちは、世界！ QR コードのテスト", cm.getPrimaryClip().getItemAt(0).getText().toString());
        // memo below the actions, saved as you type
        EditText memo = d.findViewById(R.id.memo);
        assertTrue(topIn(memo) > topIn(rows) + rows.getHeight());
        assertTrue((memo.getInputType() & android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0);
        memo.setText("Card from Tanaka-san\nreply by Friday");
        assertNull("not at every key", h.get(jp).memo);
        ShadowLooper.idleMainLooper(DetailActivity.MEMO_SAVE_DELAY_MS + 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("Card from Tanaka-san\nreply by Friday", h.get(jp).memo);
        layout(d);
        shot(d, "detail-memo", null);
        // leaving the page saves at once
        memo.setText("Card from Tanaka-san");
        d.flushMemo();
        assertEquals("Card from Tanaka-san", h.get(jp).memo);
        // written to the history file
        java.io.File f = new java.io.File(d.getFilesDir(), "history.tsv");
        waitFor(() -> f.isFile() && History.parse(read(f)).get(jp) != null && "Card from Tanaka-san".equals(History.parse(read(f)).get(jp).memo));
        assertEquals("Card from Tanaka-san", History.parse(read(f)).get(jp).memo);
        // reopened: shown again; cleared = no memo
        DetailActivity d2 = detail(jp);
        assertEquals("Card from Tanaka-san", ((EditText) d2.findViewById(R.id.memo)).getText().toString());
        ((EditText) d2.findViewById(R.id.memo)).setText("");
        d2.flushMemo();
        assertNull(h.get(jp).memo);
    }

    static String read(java.io.File f) {
        try { return new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8); }
        catch (java.io.IOException e) { return ""; }
    }

    @Test public void protectedMemoOnlyAfterAuth() throws Exception {
        h.setMemo(wifi, "door code 4711");
        h.setProtected(wifi, true);
        Auth.testResult = false;
        DetailActivity d = detail(wifi);
        assertTrue(d.lockedForTest());
        assertEquals(View.GONE, d.findViewById(R.id.content).getVisibility());
        assertEquals("nothing filled in while locked", "", ((EditText) d.findViewById(R.id.memo)).getText().toString());
        Auth.testResult = true;
        d.findViewById(R.id.btn_unlock).performClick();
        EditText memo = d.findViewById(R.id.memo);
        assertEquals(View.VISIBLE, d.findViewById(R.id.content).getVisibility());
        assertEquals("door code 4711", memo.getText().toString());
        assertTrue("kept out of keyboard learning", (memo.getInputType() & android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0);
        assertTrue((memo.getImeOptions() & android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0);
        assertTrue(secureFlag(d));
        // locking with the pill saves what was typed and hides it
        memo.setText("door code 4712");
        d.findViewById(R.id.unlock_pill).performClick();
        assertTrue(d.lockedForTest());
        assertEquals("door code 4712", h.get(wifi).memo);
    }

    @Test public void settingsClearTapAreaAndScanButton() throws Exception {
        MainActivity m = main(MainActivity.TAB_SETTINGS);
        View title = m.findViewById(R.id.set_clear), row = m.findViewById(R.id.set_clear_row), sub = m.findViewById(R.id.clear_value);
        assertTrue("only the title is tappable", title.isClickable() && !row.isClickable() && !sub.isClickable());
        assertTrue("wrap_content title: " + title.getWidth(), title.getWidth() < W / 2);
        android.view.ViewGroup outer = (android.view.ViewGroup) row.getParent();
        assertTrue(!outer.isClickable());
        // taps on the date line, right of the title and between title and checkboxes do nothing
        AlertDialog before = m.settingsPage.lastDialog;
        View root = m.getWindow().getDecorView();
        int[] tl = new int[2];
        title.getLocationInWindow(tl);
        float[][] misses = {
                {tl[0] + title.getWidth() + dp(m, 24), tl[1] + title.getHeight() / 2f},
                {centerX(sub), centerY(sub)},
                {dp(m, 4), centerY(title)}};
        for (float[] xy : misses) {
            long t = android.os.SystemClock.uptimeMillis();
            root.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, xy[0], xy[1], 0));
            root.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 50, android.view.MotionEvent.ACTION_UP, xy[0], xy[1], 0));
            ShadowLooper.idleMainLooper();
            assertSame("no dialog for a tap at " + xy[0] + "," + xy[1], before, m.settingsPage.lastDialog);
        }
        // a tap on the title opens it
        long t = android.os.SystemClock.uptimeMillis();
        root.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, centerX(title), centerY(title), 0));
        root.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 50, android.view.MotionEvent.ACTION_UP, centerX(title), centerY(title), 0));
        ShadowLooper.idleMainLooper();
        assertTrue(m.settingsPage.lastDialog != before && m.settingsPage.lastDialog.isShowing());
        m.settingsPage.lastDialog.cancel();
        // the checkboxes are still separate taps
        CheckBox fav = m.findViewById(R.id.cb_clear_fav), prot = m.findViewById(R.id.cb_clear_prot);
        AlertDialog d0 = m.settingsPage.lastDialog;
        for (View v : new View[]{m.findViewById(R.id.set_clear_fav), m.findViewById(R.id.set_clear_prot)}) {
            t = android.os.SystemClock.uptimeMillis();
            root.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, centerX(v), centerY(v), 0));
            root.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 50, android.view.MotionEvent.ACTION_UP, centerX(v), centerY(v), 0));
            ShadowLooper.idleMainLooper();
        }
        assertTrue(fav.isChecked() && prot.isChecked());
        assertSame(d0, m.settingsPage.lastDialog);
        // the scan button is back in the Settings top bar, and the pill keeps the History slot
        View scan = m.findViewById(R.id.btn_scan_top);
        assertEquals(View.VISIBLE, scan.getVisibility());
        assertTrue("top right", centerX(scan) > W - dp(m, 40));
        Auth.testResult = true;
        Auth.require(m, true, "t", () -> { }, null);
        layout(m);
        View pill = m.findViewById(R.id.unlock_pill);
        int[] onSettings = new int[2];
        pill.getLocationInWindow(onSettings);
        int[] scanSettings = new int[2];
        scan.getLocationInWindow(scanSettings);
        shot(m, "settings-scan-button-and-pill", null);
        m.select(MainActivity.TAB_HISTORY);
        layout(m);
        int[] onHistory = new int[2], scanHistory = new int[2];
        pill.getLocationInWindow(onHistory);
        scan.getLocationInWindow(scanHistory);
        assertEquals(onHistory[0], onSettings[0]);
        assertEquals(onHistory[1], onSettings[1]);
        assertEquals(scanHistory[0], scanSettings[0]);
        m.select(MainActivity.TAB_SETTINGS);
        scan.performClick();
        assertEquals(ScannerActivity.class.getName(), shadowOf(m).getNextStartedActivity().getComponent().getClassName());
        assertTrue(m.isFinishing());
    }

    @Test public void searchEngineWithoutSchemeAndMigration() throws Exception {
        android.content.SharedPreferences sp = RuntimeEnvironment.getApplication().getSharedPreferences("settings", android.content.Context.MODE_PRIVATE);
        sp.edit().putString("search_template", "https://duckduckgo.com/?q=%s").commit();   // as 0.6.0 stored it
        MainActivity m = main(MainActivity.TAB_SETTINGS);
        assertEquals("duckduckgo.com/?q=%s", ((TextView) m.findViewById(R.id.engine_value)).getText().toString());
        assertEquals("migrated once", "duckduckgo.com/?q=%s", sp.getString("search_template", null));
        assertEquals("https://duckduckgo.com/?q=a%20b", SearchUrl.build(App.get().prefs().searchTemplate(), "a b"));
        m.findViewById(R.id.set_engine).performClick();
        ShadowLooper.idleMainLooper();
        AlertDialog d = m.settingsPage.lastDialog;
        assertEquals("%s is replaced by the text", ((TextView) d.findViewById(android.R.id.message)).getText().toString());
        EditText et = d.findViewById(R.id.template_input);
        assertEquals("duckduckgo.com/?q=%s", et.getText().toString());
        assertEquals("www.google.com/search?q=%s", et.getHint().toString());
        // empty: OK disabled (no "empty = Google" any more)
        et.setText("");
        assertTrue(!d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        // a pasted scheme is removed
        et.setText("http://search.example.org/find?q=%s");
        assertEquals("search.example.org/find?q=%s", et.getText().toString());
        assertTrue(d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        shot(m, "settings-search-engine-no-scheme", null, d);
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals("search.example.org/find?q=%s", sp.getString("search_template", null));
        assertEquals("search.example.org/find?q=%s", ((TextView) m.findViewById(R.id.engine_value)).getText().toString());
        assertEquals("always https", "https://search.example.org/find?q=x", SearchUrl.build(App.get().prefs().searchTemplate(), "x"));
        // the Google button
        m.findViewById(R.id.set_engine).performClick();
        ShadowLooper.idleMainLooper();
        m.settingsPage.lastDialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals("www.google.com/search?q=%s", ((TextView) m.findViewById(R.id.engine_value)).getText().toString());
        // nothing on screen says https
        assertTrue(!textOf(m.findViewById(R.id.page_settings)).contains("https"));
        // a 0.1.0 engine choice still migrates
        sp.edit().remove("search_template").putString("search_engine", "bing").commit();
        assertEquals("www.bing.com/search?q=%s", App.get().prefs().searchTemplate());
    }

    @Test public void exportJsonContent() throws Exception {
        h.setMemo(jp, "memo ✓");
        h.setProtected(wifi, true);
        h.setFavorite(url, true);
        h.setFavoriteOrder(List.of(jp, url));
        h.saveCreated("made here", System.currentTimeMillis());
        org.json.JSONObject o = Export.json(h, "0.7.0", 1_760_000_000_000L, java.time.ZoneId.of("Asia/Tokyo"));
        assertEquals("com.zmlworks.qrreader", o.getString("package"));
        assertEquals("0.7.0", o.getString("appVersion"));
        assertEquals(1, o.getInt("formatVersion"));
        assertEquals("2025-10-09T17:53:20+09:00", o.getString("exportedAt"));
        org.json.JSONArray items = o.getJSONArray("items");
        assertEquals(11, items.length());   // 5 singles + 5 codes of the multi scan + the created one
        java.util.Map<Long, org.json.JSONObject> byId = new java.util.HashMap<>();
        for (int k = 0; k < items.length(); k++) byId.put(items.getJSONObject(k).getLong("id"), items.getJSONObject(k));
        org.json.JSONObject w = byId.get(wifi);
        assertEquals("WIFI:T:WPA;S:Cafe-Guest;P:espresso2026;;", w.getString("text"));
        assertEquals("Café guest Wi-Fi", w.getString("name"));
        assertTrue(w.getBoolean("protected"));
        assertEquals("QR Code", w.getString("format"));
        assertEquals(h.get(wifi).time, w.getLong("time"));
        assertTrue(w.getString("timeIso").matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d.*"));
        assertTrue(w.isNull("memo"));
        assertTrue(w.isNull("group"));
        org.json.JSONObject j = byId.get(jp);
        assertEquals("memo ✓", j.getString("memo"));
        assertTrue(j.getBoolean("favorite"));
        assertTrue(!j.getBoolean("created"));
        long created = h.topLevel().get(0).id;
        assertTrue(byId.get(created).getBoolean("created"));
        org.json.JSONArray groups = o.getJSONArray("groups");
        assertEquals(1, groups.length());
        assertEquals(group, groups.getJSONObject(0).getLong("id"));
        assertEquals(5, groups.getJSONObject(0).getJSONArray("codes").length());
        long first = groups.getJSONObject(0).getJSONArray("codes").getLong(0);
        assertEquals(group, byId.get(first).getLong("group"));
        assertEquals("INV-2026-10-101", byId.get(first).getString("text"));
        org.json.JSONArray order = o.getJSONArray("favoritesOrder");
        assertEquals(2, order.length());
        assertEquals(jp, order.getLong(0));
        assertEquals(url, order.getLong(1));
        assertEquals("qr-reader-history-20261018.json", Export.fileName(java.time.LocalDate.of(2026, 10, 18)));
    }

    @Test public void exportFlowAuthGateAndPicker() throws Exception {
        h.setProtected(wifi, true);
        h.setMemo(url, "repo");
        MainActivity m = main(MainActivity.TAB_SETTINGS);
        // a long press on About & licenses (a normal tap still opens About)
        assertTrue(m.findViewById(R.id.set_about).performLongClick());
        AlertDialog d = Export.lastDialog;
        assertTrue(d.isShowing());
        assertEquals("Export history?", shadowOf(d).getTitle().toString());
        String msg = ((TextView) d.findViewById(android.R.id.message)).getText().toString();
        assertTrue(msg, msg.startsWith("10 codes will be saved") && msg.contains("1 protected code"));
        shot(m, "settings-export-confirm", null, d);
        // protected codes: authentication first; refused -> no picker
        Auth.testResult = false;
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(1, Auth.testPrompts);
        assertNull(shadowOf(m).getNextStartedActivityForResult());
        // accepted -> the system "create document" picker with the file name; no storage permission
        m.findViewById(R.id.set_about).performLongClick();
        Auth.testResult = true;
        Export.lastDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(2, Auth.testPrompts);
        org.robolectric.shadows.ShadowActivity.IntentForResult pick = shadowOf(m).getNextStartedActivityForResult();
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, pick.intent.getAction());
        assertEquals("application/json", pick.intent.getType());
        assertEquals(Export.fileName(java.time.LocalDate.now()), pick.intent.getStringExtra(Intent.EXTRA_TITLE));
        assertTrue(pick.intent.getStringExtra(Intent.EXTRA_TITLE).matches("qr-reader-history-\\d{8}\\.json"));
        assertTrue("the picker is not leaving the app", App.get().holdForeground);
        String[] perms = m.getPackageManager().getPackageInfo(m.getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
        for (String p : perms) assertTrue(p, !p.contains("STORAGE") && !p.contains("INTERNET"));
        // inside the window a second export asks nothing
        m.findViewById(R.id.set_about).performLongClick();
        Export.lastDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(2, Auth.testPrompts);
        assertNotNull(shadowOf(m).getNextStartedActivityForResult());
        // the picked file gets the JSON
        java.io.File out = new java.io.File(OUT.getParentFile(), "export-test.json");
        out.delete();
        Uri uri = Uri.fromFile(out);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        shadowOf(m.getContentResolver()).registerOutputStream(uri, bytes);
        shadowOf(m).receiveResult(pick.intent, Activity.RESULT_OK, new Intent().setData(uri));
        ShadowLooper.idleMainLooper();
        assertTrue(!App.get().holdForeground);
        org.json.JSONObject o = new org.json.JSONObject(bytes.toString("UTF-8"));
        assertEquals(10, o.getJSONArray("items").length());
        assertEquals("0.7.0", o.getString("appVersion"));
        assertEquals("Exported 10 codes", org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        // nothing protected: no prompt at all
        h.setProtected(wifi, false);
        Auth.lockNow();
        m.findViewById(R.id.set_about).performLongClick();
        Export.lastDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(2, Auth.testPrompts);
        assertNotNull(shadowOf(m).getNextStartedActivityForResult());
        // an empty history: nothing to export
        h.clear();
        Export.lastDialog = null;
        m.findViewById(R.id.set_about).performLongClick();
        assertNull(Export.lastDialog);
        assertEquals("Nothing to export", org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test public void historyKeepsLoadingOldFiles() throws Exception {
        // the fixture's history written in the 0.6.0 format (QRH3) loads with the new fields at their defaults
        String v060 = "QRH3\t4\n1\t0\t0\t100\t3\t\tName\tsecret\n2\t0\t0\t200\t0\tEAN-13\t\t4006381333931\n3\t0\t0\t300\t1\t\t\tfav\n";
        History p = History.parse(v060);
        assertEquals(3, p.size());
        assertTrue(p.get(1).prot && p.get(1).fav);
        assertEquals("Name", p.get(1).name);
        assertNull(p.get(1).memo);
        assertTrue(!p.get(3).created);
        assertEquals(List.of(3L, 1L), p.favoriteOrder());
    }
}
