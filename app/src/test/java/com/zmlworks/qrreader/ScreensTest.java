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
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA);
        Prefs p = app.prefs();
        p.setLeftHanded(false);
        p.setMulti(false);
        p.setTheme(Prefs.THEME_SYSTEM);
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
        url = h.addSingle("https://github.com/zmlworks/qr-reader", now - 60_000).id;
        OUT.mkdirs();
    }

    static void layout(Activity a) {
        View root = a.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, W, H);
        ShadowLooper.idleMainLooper();
        root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, W, H);
    }

    static void shot(Activity a, String name, Bitmap background) throws Exception {
        layout(a);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        if (background != null) c.drawBitmap(background, 0, 0, null);
        a.getWindow().getDecorView().draw(c);
        try (FileOutputStream os = new FileOutputStream(new File(OUT, name + ".png"))) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
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

    @Test public void scannerRightHanded() throws Exception {
        ScannerActivity s = scanner();
        layout(s);
        View buttons = s.findViewById(R.id.buttons);
        assertEquals(Gravity.BOTTOM | Gravity.END, ((FrameLayout.LayoutParams) buttons.getLayoutParams()).gravity);
        assertTrue("buttons on the right", buttons.getLeft() > W / 2);
        assertTrue("zoom bar on the left", s.zoomBarForTest().getRight() < W / 2);
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
        assertEquals("3", ((TextView) s.findViewById(R.id.multi_badge)).getText().toString());
        List<float[]> corners = new ArrayList<>();
        Bitmap cam = fakeCamera(new String[]{"INV-1", "INV-2", "INV-3"},
                new float[][]{{W * 0.33f, H * 0.33f, 420, 4}, {W * 0.70f, H * 0.42f, 400, -8}, {W * 0.45f, H * 0.62f, 440, 2}}, corners);
        List<OverlayView.Shape> shapes = new ArrayList<>();
        for (int i = 0; i < corners.size(); i++) shapes.add(new OverlayView.Shape(corners.get(i), i == 2));
        ((OverlayView) s.findViewById(R.id.overlay)).setShapes(shapes);
        s.findViewById(R.id.zoom_bar);
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

    @Test public void history() throws Exception {
        ActivityController<MainActivity> ac = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity m = ac.get();
        layout(m);
        assertEquals(MainActivity.TAB_HISTORY, m.tabForTest());
        SwipeListView list = m.findViewById(R.id.list);
        assertEquals(6, list.getAdapter().getCount());   // 5 singles + the multi scan (one entry), newest first
        assertEquals(url, list.getAdapter().getItemId(0));
        shot(m, "history", null);
        m.findViewById(R.id.seg_fav).performClick();
        assertEquals(1, list.getAdapter().getCount());
        assertEquals(jp, list.getAdapter().getItemId(0));
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
        // swipe left -> copied to the clipboard
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

    @Test public void detailDeleteUndoReturnsToDetail() throws Exception {
        Intent i = new Intent(RuntimeEnvironment.getApplication(), DetailActivity.class).putExtra(DetailActivity.EXTRA_ID, url);
        ActivityController<DetailActivity> dc = Robolectric.buildActivity(DetailActivity.class, i).setup();
        DetailActivity d = dc.get();
        assertEquals(View.VISIBLE, d.findViewById(R.id.primary).getVisibility());
        assertEquals(d.getString(R.string.open_in_browser), ((TextView) d.findViewById(R.id.primary)).getText().toString());
        shot(d, "detail-url", null);
        d.findViewById(R.id.primary).performClick();
        Intent browse = shadowOf(d).getNextStartedActivity();
        assertEquals(Intent.ACTION_VIEW, browse.getAction());
        assertEquals("https://github.com/zmlworks/qr-reader", browse.getDataString());
        d.findViewById(R.id.act_search).performClick();
        assertEquals("https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fzmlworks%2Fqr-reader",
                shadowOf(d).getNextStartedActivity().getDataString());
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

    @Test public void detailWifiAndDark() throws Exception {
        App.get().prefs().setTheme(Prefs.THEME_DARK);
        Intent i = new Intent(RuntimeEnvironment.getApplication(), DetailActivity.class).putExtra(DetailActivity.EXTRA_ID, wifi);
        DetailActivity d = Robolectric.buildActivity(DetailActivity.class, i).setup().get();
        assertTrue(d.isNight());
        assertEquals(View.VISIBLE, d.findViewById(R.id.fields).getVisibility());
        assertEquals(View.VISIBLE, d.findViewById(R.id.secondary).getVisibility());
        shot(d, "detail-wifi-dark", null);
        App.get().prefs().setTheme(Prefs.THEME_SYSTEM);
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
        shot(m, "create", null);
    }

    @Test public void settings() throws Exception {
        Intent i = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_SETTINGS);
        MainActivity m = Robolectric.buildActivity(MainActivity.class, i).setup().get();
        m.findViewById(R.id.set_left).performClick();
        assertTrue(App.get().prefs().leftHanded());
        m.findViewById(R.id.set_left).performClick();
        shot(m, "settings", null);
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
}
