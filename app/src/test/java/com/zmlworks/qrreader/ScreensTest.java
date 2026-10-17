package com.zmlworks.qrreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
        app.allCodes = false;
        app.holdForeground = false;
        Auth.testResult = null;
        Auth.testPrompts = 0;
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

    /** Multi and image at the top (below the status bar), flash a bit above ×, × at half the screen height; one side. */
    static void checkColumn(ScannerActivity s) {
        View multi = s.findViewById(R.id.btn_multi), image = s.findViewById(R.id.btn_image), flash = s.findViewById(R.id.btn_flash),
                close = s.findViewById(R.id.btn_close);
        assertTrue("multi near the top: " + topIn(multi), topIn(multi) < dp(s, 80));
        assertTrue(centerY(multi) < centerY(image));
        assertTrue("image near the top too", topIn(image) + image.getHeight() < dp(s, 160));
        assertTrue("a big gap down to flash", topIn(flash) - (topIn(image) + image.getHeight()) > dp(s, 150));
        assertTrue(centerY(flash) < centerY(close));
        int g2 = topIn(close) - (topIn(flash) + flash.getHeight());
        assertTrue("flash sits a bit above ×: " + g2, g2 >= dp(s, 24) && g2 <= dp(s, 48));
        assertTrue("× at half height: " + centerY(close), Math.abs(centerY(close) - H / 2) <= dp(s, 2));
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
        // one action per row, in this order
        android.widget.LinearLayout rows = d.findViewById(R.id.actions);
        String[] labels = {d.getString(R.string.share_text), d.getString(R.string.copy_clipboard), d.getString(R.string.search_web), d.getString(R.string.create_this_qr)};
        assertEquals(4, rows.getChildCount());
        for (int k = 0; k < 4; k++) assertEquals(labels[k], ((TextView) rows.getChildAt(k)).getText().toString());
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
        assertEquals(6, rows.getChildCount());   // connect, copy password + the four standard rows
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
        assertEquals(dp(m, 8), topIn(in) - (topIn(box) + box.getHeight()));
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
        assertEquals(dp(m, 8), topIn(in) - (topIn(box) + box.getHeight()));
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
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(d.isShowing());
        assertNotNull(et.getError());
        et.setText("https://duckduckgo.com/?q=%s");
        et.setError(null);   // typing clears it on a device
        shot(m, "settings-search-engine", null, d);
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals("https://duckduckgo.com/?q=%s", p.searchTemplate());
        assertEquals("https://duckduckgo.com/?q=%s", ((TextView) m.findViewById(R.id.engine_value)).getText().toString());
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
        clear.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
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
        // history list: only the date and time + a lock, no name / text / type / star
        MainActivity m = Robolectric.buildActivity(MainActivity.class).setup().get();
        layout(m);
        SwipeListView list = m.findViewById(R.id.list);
        View row = list.getChildAt(1);
        assertEquals(wifi, list.getAdapter().getItemId(1));
        assertEquals(Actions.listTime(h.get(wifi).time), rowTitle(list, 1));
        assertEquals(View.GONE, row.findViewById(R.id.row_sub).getVisibility());
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
        // the 1-minute window: opening right after needs no new prompt
        DetailActivity d2 = detail(wifi);
        assertTrue(!d2.lockedForTest());
        assertEquals(2, Auth.testPrompts);
        // after the window (or leaving the app) it asks; refused -> locked page, nothing shown
        App.get().authUntil = 0;
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
        // OFF always asks, even inside the window
        assertTrue(Auth.recentlyOk());
        Auth.testResult = false;
        d3.findViewById(R.id.btn_lock).performClick();
        assertEquals(5, Auth.testPrompts);
        assertTrue(h.get(wifi).prot);
        Auth.testResult = true;
        d3.findViewById(R.id.btn_lock).performClick();
        assertEquals(6, Auth.testPrompts);
        assertTrue(!h.get(wifi).prot);
        assertTrue(!secureFlag(d3));
    }

    @Test public void leavingTheAppEndsTheAuthWindow() {
        ActivityController<MainActivity> ac = Robolectric.buildActivity(MainActivity.class).setup();
        Auth.testResult = true;
        Auth.require(ac.get(), true, "t", () -> { }, null);
        assertTrue(Auth.recentlyOk());
        ac.pause().stop();
        assertTrue(!Auth.recentlyOk());
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
        assertEquals(Actions.listTime(ch.get(0).time), rowTitle(list, 0));
        assertEquals(View.GONE, list.getChildAt(0).findViewById(R.id.row_sub).getVisibility());
        for (int k = 0; k < list.getChildCount(); k++) {
            View r = list.getChildAt(k);
            assertTrue(!((TextView) r.findViewById(R.id.row_title)).getText().toString().contains("INV-2026-10-101"));
        }
        shot(c, "just-scanned-protected", null);
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
        Intent in = new Intent(RuntimeEnvironment.getApplication(), ScannerActivity.class).putExtra(ScannerActivity.EXTRA_ALL_CODES, true);
        ActivityController<ScannerActivity> ac = Robolectric.buildActivity(ScannerActivity.class, in).setup();
        ScannerActivity s = ac.get();
        s.findViewById(R.id.preview).setVisibility(View.INVISIBLE);
        ((View) s.findViewById(R.id.preview).getParent()).setBackground(null);
        s.getWindow().setBackgroundDrawable(null);
        for (int i = 0; i < 50; i++) { Thread.sleep(10); ShadowLooper.idleMainLooper(); }
        s.hideMessageForTest();
        s.setZoomForTest(1, 8, 2.4f);
        assertTrue(App.get().allCodes);
        assertTrue(s.allModeForTest());
        TextView badge = s.findViewById(R.id.mode_badge);
        assertEquals(View.VISIBLE, badge.getVisibility());
        assertEquals("All codes", badge.getText().toString());
        layout(s);
        checkColumn(s);
        assertTrue("badge at the top centre", Math.abs(centerX(badge) - W / 2) <= 2 && topIn(badge) < dp(s, 80));
        View multi = s.findViewById(R.id.btn_multi);
        int[] bl = new int[2], ml = new int[2];
        badge.getLocationInWindow(bl);
        multi.getLocationInWindow(ml);
        assertTrue("badge clear of the buttons", bl[0] + badge.getWidth() < ml[0]);
        shot(s, "scanner-all-codes", fakeCamera(new String[0], new float[0][], new ArrayList<>()));
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
        assertTrue(!App.get().allCodes);
        assertEquals(View.GONE, badge.getVisibility());
    }

    @Test public void allCodesSessionEndsWithCloseOrLeaving() {
        Intent in = new Intent(RuntimeEnvironment.getApplication(), ScannerActivity.class).putExtra(ScannerActivity.EXTRA_ALL_CODES, true);
        ActivityController<ScannerActivity> ac = Robolectric.buildActivity(ScannerActivity.class, in).setup();
        assertTrue(App.get().allCodes);
        ac.pause().stop();
        assertTrue("leaving the app ends it", !App.get().allCodes);
        ActivityController<ScannerActivity> ac2 = Robolectric.buildActivity(ScannerActivity.class,
                new Intent(RuntimeEnvironment.getApplication(), ScannerActivity.class).putExtra(ScannerActivity.EXTRA_ALL_CODES, true)).setup();
        assertTrue(App.get().allCodes);
        // the photo picker is not leaving the app
        ac2.get().findViewById(R.id.btn_image).performClick();
        assertTrue(App.get().holdForeground);
        ac2.pause().stop();
        assertTrue(App.get().allCodes);
        ac2.start().resume();
        App.get().holdForeground = false;
        ac2.get().findViewById(R.id.btn_close).performClick();
        assertTrue("× ends it", !App.get().allCodes);
    }

    @Test public void settingsOnceLineStartsAllCodes() throws Exception {
        Intent i = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_SETTINGS);
        MainActivity m = Robolectric.buildActivity(MainActivity.class, i).setup().get();
        TextView once = m.findViewById(R.id.set_once);
        assertEquals("Once in a blue moon, a code comes in stripes.", once.getText().toString());
        layout(m);
        // the last row of the page
        android.view.ViewGroup col = (android.view.ViewGroup) once.getParent();
        assertEquals(once, col.getChildAt(col.getChildCount() - 1));
        View page = (View) col.getParent();
        page.scrollTo(0, 100000);
        shot(m, "settings-once", null);
        once.performClick();
        Intent next = shadowOf(m).getNextStartedActivity();
        assertEquals(ScannerActivity.class.getName(), next.getComponent().getClassName());
        assertTrue(next.getBooleanExtra(ScannerActivity.EXTRA_ALL_CODES, false));
        assertTrue(m.isFinishing());
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
}
