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

    /** Right column, top to bottom: multi, image, flash, (wider gap) ×, with × at half the screen height. */
    static void checkColumn(ScannerActivity s) {
        View multi = s.findViewById(R.id.btn_multi), image = s.findViewById(R.id.btn_image), flash = s.findViewById(R.id.btn_flash),
                close = s.findViewById(R.id.btn_close);
        assertTrue(centerY(multi) < centerY(image));
        assertTrue(centerY(image) < centerY(flash));
        assertTrue(centerY(flash) < centerY(close));
        int g1 = topIn(flash) - (topIn(image) + image.getHeight()), g2 = topIn(close) - (topIn(flash) + flash.getHeight());
        assertTrue("flash sits a bit higher above ×: " + g1 + " / " + g2, g2 > g1 + dp(s, 12));
        assertTrue("× at half height: " + centerY(close), Math.abs(centerY(close) - H / 2) <= dp(s, 2));
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
}
