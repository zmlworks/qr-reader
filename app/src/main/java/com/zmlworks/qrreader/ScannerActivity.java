package com.zmlworks.qrreader;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Range;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Scanner: Camera2 preview (centre-cropped TextureView) + zxing-cpp on every frame's Y plane.
 * Opens straight into scanning on launch and whenever the activity resumes. Vertical swipe anywhere = zoom,
 * tap = focus there. Right column (left when left-handed), top to bottom: multi, read from image, flash, × (at half
 * the screen height). "Read from image" decodes every QR code in a picked picture.
 */
public final class ScannerActivity extends BaseActivity implements TextureView.SurfaceTextureListener {
    private static final int REQ_CAM = 1, REQ_IMAGE = 2;

    private TextureView preview;
    private OverlayView overlay;
    private ZoomBarView zoomBar;
    private ImageButton btnClose, btnFlash, btnMulti, btnImage;
    private TextView pill, multiBadge, message;
    private Button messageBtn;
    private View controls;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private HandlerThread camThread;
    private Handler cam;

    // camera (camera thread, except where noted)
    private volatile boolean wantCamera;
    private volatile CameraDevice device;
    private CameraCaptureSession session;
    private CaptureRequest.Builder rb;
    private ImageReader reader;
    private volatile Size size;
    private volatile int sensorOrientation = 90;
    private volatile boolean hasFlash, useZoomRatio;
    private Rect activeArray;
    private int afMode = CaptureRequest.CONTROL_AF_MODE_OFF, maxAfRegions;
    private boolean surfaceReady, opening;
    private int camGen;              // bumped by closeCamera(): late callbacks of an older open are dropped
    private volatile boolean camInfoKnown;
    private byte[] yBuf;
    private DecodePipeline pipe;

    // UI state (UI thread)
    private volatile boolean torch, multi;
    private volatile float zoom = 1, zoomMin = 1, zoomMax = 1;
    private static float lastZoom = 1;   // kept while the process lives
    private boolean handled;             // single mode: a read is being opened
    private long groupId;                // multi mode: history group of this session (0 = none yet)
    private final ScanLogic logic = new ScanLogic();
    private boolean asking, denied;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_scanner);
        preview = findViewById(R.id.preview);
        overlay = findViewById(R.id.overlay);
        zoomBar = findViewById(R.id.zoom_bar);
        btnClose = findViewById(R.id.btn_close);
        btnFlash = findViewById(R.id.btn_flash);
        btnMulti = findViewById(R.id.btn_multi);
        btnImage = findViewById(R.id.btn_image);
        pill = findViewById(R.id.pill);
        multiBadge = findViewById(R.id.multi_badge);
        message = findViewById(R.id.message);
        messageBtn = findViewById(R.id.message_btn);
        controls = findViewById(R.id.controls);
        preview.setSurfaceTextureListener(this);
        preview.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> configureTransform());
        btnClose.setOnClickListener(v -> close());
        btnFlash.setOnClickListener(v -> setTorch(!torch));
        btnMulti.setOnClickListener(v -> setMulti(!multi));
        btnImage.setOnClickListener(v -> pickImage());
        controls.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> positionButtons());
        pill.setOnClickListener(v -> onPill());
        messageBtn.setOnClickListener(v -> onMessageButton());
        overlay.setOnTouchListener(new Gestures());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        edgeToEdge();
        camThread = new HandlerThread("camera");
        camThread.start();
        cam = new Handler(camThread.getLooper());
        cam.post(() -> {   // load the native decoder off the UI thread
            QrEngine e = CppEngine.create();
            pipe = new DecodePipeline(e != null ? e : new JavaEngine(), true);
        });
        multi = App.get().prefs().multi();
        if (b == null && App.get().prefs().zoomHints() < 3) {
            App.get().prefs().countZoomHint();
            hintUntil = SystemClock.uptimeMillis() + 3500;
        }
    }

    private long hintUntil;

    @SuppressWarnings("deprecation")
    private void edgeToEdge() {
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        final int l = controls.getPaddingLeft(), t = controls.getPaddingTop(), r = controls.getPaddingRight(), bt = controls.getPaddingBottom();
        controls.setOnApplyWindowInsetsListener((v, in) -> {
            int il, it, ir, ib;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets s = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                il = s.left; it = s.top; ir = s.right; ib = s.bottom;
            } else {
                il = in.getSystemWindowInsetLeft(); it = in.getSystemWindowInsetTop(); ir = in.getSystemWindowInsetRight(); ib = in.getSystemWindowInsetBottom();
            }
            v.setPadding(l + il, t + it, r + ir, bt + ib);
            return in;
        });
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
    }

    @Override protected void onResume() {
        super.onResume();
        // always back to the scanning state
        handled = false;
        logic.touch(SystemClock.uptimeMillis());   // a code still in view after coming back stays suppressed
        applyHandedness();
        renderButtons();
        renderPill();
        zoom = lastZoom;
        zoomBar.setZoom(zoom);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (!asking && !denied) { asking = true; requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAM); }
            else if (denied) showMessage(getString(R.string.camera_denied), getString(R.string.grant));
        } else {
            showMessage(null, null);
            startCamera();
        }
        ui.postDelayed(pillLoop, 300);
    }

    @Override protected void onPause() {
        ui.removeCallbacks(pillLoop);
        stopCamera();
        lastZoom = zoom;
        torch = false;
        super.onPause();
    }

    @Override protected void onStop() {
        super.onStop();
        endMultiSession();   // whatever was collected is already in the history
        renderButtons();
    }

    @Override protected void onDestroy() {
        camThread.quitSafely();
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        if (req != REQ_CAM) return;
        asking = false;
        if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) { denied = false; showMessage(null, null); startCamera(); }
        else { denied = true; showMessage(getString(R.string.camera_denied), getString(R.string.grant)); }
    }

    private void onMessageButton() {
        if (denied) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                denied = false; asking = true;
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAM);
            } else {
                Actions.start(this, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null)));
            }
        }
    }

    private void showMessage(String text, String button) {
        message.setVisibility(text == null ? View.GONE : View.VISIBLE);
        message.setText(text);
        messageBtn.setVisibility(button == null ? View.GONE : View.VISIBLE);
        messageBtn.setText(button);
    }

    // ---------------- buttons ----------------
    private void applyHandedness() {
        boolean left = App.get().prefs().leftHanded();
        FrameLayout.LayoutParams bl = (FrameLayout.LayoutParams) findViewById(R.id.buttons).getLayoutParams();
        bl.gravity = Gravity.TOP | (left ? Gravity.START : Gravity.END);
        findViewById(R.id.buttons).setLayoutParams(bl);
        positionButtons();
        FrameLayout.LayoutParams zl = (FrameLayout.LayoutParams) zoomBar.getLayoutParams();
        zl.gravity = Gravity.CENTER_VERTICAL | (left ? Gravity.END : Gravity.START);
        zoomBar.setLayoutParams(zl);
    }

    /** Keeps the centre of × at half the screen height (the column hangs above it). */
    private void positionButtons() {
        View col = findViewById(R.id.buttons);
        if (controls.getHeight() == 0 || btnClose.getHeight() == 0) return;
        int closeCenter = btnClose.getTop() + btnClose.getHeight() / 2;   // within the column
        int top = Math.max(0, controls.getHeight() / 2 - closeCenter - controls.getPaddingTop());
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) col.getLayoutParams();
        if (lp.topMargin != top) { lp.topMargin = top; col.setLayoutParams(lp); }
    }

    // ---------------- read from image ----------------
    private void pickImage() {
        Intent i;
        if (Build.VERSION.SDK_INT >= 33 || (Build.VERSION.SDK_INT >= 30 && android.os.ext.SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2))
            i = new Intent("android.provider.action.PICK_IMAGES").setType("image/*");   // the system photo picker
        else
            i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(i, REQ_IMAGE);
        } catch (android.content.ActivityNotFoundException e) {
            try {
                startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), REQ_IMAGE);
            } catch (android.content.ActivityNotFoundException e2) {
                flash(getString(R.string.no_app));
            }
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_IMAGE || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        readingImage = true;
        flash(getString(R.string.reading_image));
        new Thread(() -> {
            final List<String> codes = PhotoReader.read(getApplicationContext(), uri);
            ui.post(() -> onImageCodes(codes));
        }, "image-decode").start();
    }

    /** @param codes null = the picture could not be read */
    void onImageCodes(List<String> codes) {
        readingImage = false;
        if (isFinishing() || isDestroyed()) return;
        if (codes == null) { flash(getString(R.string.image_unreadable)); return; }
        if (codes.isEmpty()) { flash(getString(R.string.image_no_code)); return; }
        flashUntil = 0;
        History h = App.get().history();
        long now = System.currentTimeMillis();
        Actions.vibrate(this, 40);
        if (codes.size() == 1) {
            History.Entry e = h.addSingle(codes.get(0), now);
            startActivity(new Intent(this, DetailActivity.class).putExtra(DetailActivity.EXTRA_ID, e.id));
            return;
        }
        History.Entry g = h.newGroup(now);
        for (String t : codes) h.addChild(g.id, t, now);
        startActivities(new Intent[]{new Intent(this, MainActivity.class), new Intent(this, CodesActivity.class)
                .putExtra(CodesActivity.EXTRA_GROUP, g.id).putExtra(CodesActivity.EXTRA_JUST_SCANNED, true)});
    }

    private boolean readingImage;   // a picked picture is being decoded: the camera does not open a read meanwhile
    private String flashText;
    private long flashUntil;

    /** A short message in the pill (image reading). */
    private void flash(String text) {
        flashText = text;
        flashUntil = SystemClock.uptimeMillis() + 2500;
        renderPill();
    }

    private void renderButtons() {
        btnFlash.setImageResource(torch ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
        btnFlash.setActivated(torch);
        btnFlash.setContentDescription(getString(torch ? R.string.flash_off : R.string.flash_on));
        btnFlash.setVisibility(hasFlash || !camInfoKnown ? View.VISIBLE : View.GONE);
        btnMulti.setActivated(multi);
        btnMulti.setContentDescription(getString(multi ? R.string.multi_off : R.string.multi_on));
        int n = logic.collectedCount();
        multiBadge.setVisibility(multi ? View.VISIBLE : View.GONE);
        multiBadge.setText(String.valueOf(n));
    }

    private void setTorch(boolean on) {
        torch = on;
        renderButtons();
        cam.post(this::updateRepeating);
    }

    private void setMulti(boolean on) {
        if (!on) endMultiSession();
        multi = on;
        App.get().prefs().setMulti(on);
        renderButtons();
        renderPill();
    }

    private void endMultiSession() {
        logic.resetCollection();
        groupId = 0;
    }

    private void close() {
        long g = groupId;
        int n = logic.collectedCount();
        endMultiSession();
        renderButtons();
        Intent main = new Intent(this, MainActivity.class);
        if (multi && n > 0 && g != 0) {
            startActivities(new Intent[]{main, new Intent(this, CodesActivity.class)
                    .putExtra(CodesActivity.EXTRA_GROUP, g).putExtra(CodesActivity.EXTRA_JUST_SCANNED, true)});
        } else {
            startActivity(main);
        }
    }

    private final Runnable pillLoop = new Runnable() {
        @Override public void run() { renderPill(); ui.postDelayed(this, 300); }
    };

    private String suppressedShown;

    private void renderPill() {
        if (SystemClock.uptimeMillis() < flashUntil) {
            pill.setVisibility(View.VISIBLE);
            pill.setText(flashText);
            suppressedShown = null;
            return;
        }
        if (multi) {
            int n = logic.collectedCount();
            pill.setVisibility(View.VISIBLE);
            pill.setText(n == 0 ? getString(R.string.multi_hint) : getResources().getQuantityString(R.plurals.n_codes_tap, n, n));
            suppressedShown = null;
            return;
        }
        String s = logic.suppressed(SystemClock.uptimeMillis());
        suppressedShown = s;
        if (s == null && SystemClock.uptimeMillis() < hintUntil) {
            pill.setVisibility(View.VISIBLE);
            pill.setText(R.string.zoom_hint);
            return;
        }
        pill.setVisibility(s == null ? View.GONE : View.VISIBLE);
        if (s != null) pill.setText(getString(R.string.already_read, s.length() > 40 ? s.substring(0, 40) + "…" : s));
    }

    private void onPill() {
        if (SystemClock.uptimeMillis() < flashUntil) return;
        if (multi) { if (logic.collectedCount() > 0) close(); return; }
        String s = suppressedShown;
        if (s == null) return;
        History h = App.get().history();
        for (History.Entry e : h.topLevel()) if (e.kind == History.SINGLE && e.text.equals(s)) {
            startActivity(new Intent(this, DetailActivity.class).putExtra(DetailActivity.EXTRA_ID, e.id));
            return;
        }
    }

    // ---------------- results (UI thread) ----------------
    private void onSingle(String text) {
        if (handled || readingImage || isFinishing()) return;
        handled = true;
        Actions.vibrate(this, 40);
        History.Entry e = App.get().history().addSingle(text, System.currentTimeMillis());
        startActivity(new Intent(this, DetailActivity.class).putExtra(DetailActivity.EXTRA_ID, e.id));
        overridePendingTransition(R.anim.fade_in_fast, 0);
    }

    private void onCollected(String text) {
        if (!multi) return;
        History h = App.get().history();
        long now = System.currentTimeMillis();
        if (groupId == 0 || h.get(groupId) == null) groupId = h.newGroup(now).id;
        h.addChild(groupId, text, now);
        Actions.vibrate(this, 25);
        renderButtons();
        renderPill();
    }

    // ---------------- camera ----------------
    private void startCamera() {
        wantCamera = true;
        cam.post(this::openCamera);
    }

    private void stopCamera() {
        wantCamera = false;
        cam.post(this::closeCamera);
    }

    /** Prefer ~1920x1080 (16:9): enough pixels for small codes, still cheap to decode (as in QR transfer). */
    static Size choose(Size[] sizes) {
        Size best = null;
        double bestScore = Double.MAX_VALUE;
        for (Size s : sizes) {
            double aspect = (double) s.getWidth() / s.getHeight();
            double score = Math.abs(aspect - 16.0 / 9) * 4 + Math.abs(Math.log((double) s.getWidth() * s.getHeight() / (1920.0 * 1080)));
            if (s.getWidth() * s.getHeight() <= 1920 * 1080 && score < bestScore) { best = s; bestScore = score; }
        }
        return best != null ? best : sizes[0];
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        if (!wantCamera || device != null || opening) return;
        CameraManager cm = getSystemService(CameraManager.class);
        try {
            String id = null;
            for (String c : cm.getCameraIdList()) {
                Integer facing = cm.getCameraCharacteristics(c).get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) { id = c; break; }
            }
            if (id == null && cm.getCameraIdList().length > 0) id = cm.getCameraIdList()[0];
            if (id == null) { ui.post(() -> showMessage(getString(R.string.no_camera), null)); return; }
            CameraCharacteristics ch = cm.getCameraCharacteristics(id);
            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) return;
            size = choose(map.getOutputSizes(ImageFormat.YUV_420_888));
            Integer so = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = so == null ? 90 : so;
            Boolean fl = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            hasFlash = fl != null && fl;
            camInfoKnown = true;
            activeArray = ch.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Integer mr = ch.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
            maxAfRegions = mr == null ? 0 : mr;
            float zmin = 1, zmax = 1;
            useZoomRatio = false;
            if (Build.VERSION.SDK_INT >= 30) {
                Range<Float> zr = ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                if (zr != null && zr.getUpper() > 1) { useZoomRatio = true; zmin = Math.max(1f, zr.getLower()); zmax = zr.getUpper(); }
            }
            if (!useZoomRatio) {
                Float md = ch.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
                zmax = md == null ? 1 : Math.max(1, md);
            }
            zoomMin = zmin; zoomMax = Math.min(zmax, 10f);
            final float fmin = zoomMin, fmax = zoomMax;
            zoom = Math.max(zoomMin, Math.min(zoomMax, zoom));
            final int[] afModes = ch.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            afMode = CaptureRequest.CONTROL_AF_MODE_OFF;
            if (afModes != null) for (int m : afModes) {
                if (m == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) { afMode = m; break; }
                if (m == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) afMode = m;
            }
            ui.post(() -> { zoomBar.setRange(fmin, fmax); zoomBar.setZoom(zoom); configureTransform(); renderButtons(); });
            reader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.YUV_420_888, 2);
            reader.setOnImageAvailableListener(this::onImage, cam);
            final int gen = camGen;
            opening = true;
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice d) {
                    if (gen != camGen || !wantCamera) { d.close(); return; }
                    opening = false;
                    device = d;
                    startSession();
                }
                @Override public void onDisconnected(CameraDevice d) {
                    d.close();
                    if (gen == camGen) { opening = false; if (device == d) device = null; }
                }
                @Override public void onError(CameraDevice d, int e) {
                    d.close();
                    if (gen != camGen) return;
                    opening = false;
                    if (device == d) device = null;
                    ui.post(() -> showMessage(getString(R.string.camera_failed), null));
                }
            }, cam);
        } catch (CameraAccessException | SecurityException | IllegalArgumentException e) {
            ui.post(() -> showMessage(getString(R.string.camera_failed), null));
        }
    }

    @SuppressWarnings("deprecation")  // createCaptureSession(List...) keeps minSdk 26 simple
    private void startSession() {
        if (device == null || session != null || !surfaceReady || reader == null) return;
        try {
            SurfaceTexture st = preview.getSurfaceTexture();
            if (st == null) return;
            st.setDefaultBufferSize(size.getWidth(), size.getHeight());
            final Surface view = new Surface(st), yuv = reader.getSurface();
            rb = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            rb.addTarget(view);
            rb.addTarget(yuv);
            rb.set(CaptureRequest.CONTROL_AF_MODE, afMode);
            final CameraDevice dev = device;
            device.createCaptureSession(Arrays.asList(view, yuv), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession s) {
                    if (device != dev) { s.close(); return; }
                    session = s;
                    updateRepeating();
                }
                @Override public void onConfigureFailed(CameraCaptureSession s) {
                    ui.post(() -> showMessage(getString(R.string.camera_failed), null));
                }
            }, cam);
        } catch (CameraAccessException | IllegalStateException | IllegalArgumentException e) {
            ui.post(() -> showMessage(getString(R.string.camera_failed), null));
        }
    }

    /** Applies torch + zoom to the repeating request (camera thread). */
    private void updateRepeating() {
        if (session == null || rb == null) return;
        rb.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        rb.set(CaptureRequest.FLASH_MODE, torch && hasFlash ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
        applyZoom();
        try { session.setRepeatingRequest(rb.build(), null, cam); } catch (CameraAccessException | IllegalStateException | IllegalArgumentException ignored) { }
    }

    private void applyZoom() {
        float z = zoom;
        if (useZoomRatio && Build.VERSION.SDK_INT >= 30) rb.set(CaptureRequest.CONTROL_ZOOM_RATIO, z);
        else if (activeArray != null) rb.set(CaptureRequest.SCALER_CROP_REGION, cropFor(z));
    }

    private Rect cropFor(float z) {
        int w = activeArray.width(), h = activeArray.height();
        int cw = Math.round(w / z), chh = Math.round(h / z);
        int l = activeArray.left + (w - cw) / 2, t = activeArray.top + (h - chh) / 2;
        return new Rect(l, t, l + cw, t + chh);
    }

    private boolean zoomPosted;
    private void requestZoom(float z) {
        zoom = z;
        zoomBar.setZoom(z);
        if (zoomPosted) return;
        zoomPosted = true;
        cam.post(() -> { zoomPosted = false; updateRepeating(); });
    }

    /** Tap to focus: AF (and AE) on that spot, back to continuous AF after 3 s (camera thread). */
    private void focusAt(float nx, float ny) {
        if (session == null || rb == null || activeArray == null) return;
        // nx, ny: position in the frame (0..1, sensor orientation); map into the region the frame shows
        Rect basis;
        if (useZoomRatio) basis = new Rect(0, 0, activeArray.width(), activeArray.height());   // zoomed FOV = full array coordinates
        else basis = cropFor(zoom);
        double fa = (double) size.getWidth() / size.getHeight(), ba = (double) basis.width() / basis.height();
        double vw = basis.width(), vh = basis.height(), ox = basis.left, oy = basis.top;
        if (ba > fa) { vw = basis.height() * fa; ox += (basis.width() - vw) / 2; } else { vh = basis.width() / fa; oy += (basis.height() - vh) / 2; }
        int x = (int) (ox + nx * vw), y = (int) (oy + ny * vh);
        int half = (int) (Math.min(vw, vh) * 0.08);
        Rect r = new Rect(Math.max(basis.left, x - half), Math.max(basis.top, y - half),
                Math.min(basis.right - 1, x + half), Math.min(basis.bottom - 1, y + half));
        if (r.width() <= 0 || r.height() <= 0) return;
        MeteringRectangle[] mr = {new MeteringRectangle(r, MeteringRectangle.METERING_WEIGHT_MAX - 1)};
        try {
            cam.removeCallbacks(resumeContinuousAf);
            if (maxAfRegions > 0) rb.set(CaptureRequest.CONTROL_AF_REGIONS, mr);
            rb.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            rb.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            session.capture(rb.build(), null, cam);
            rb.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            session.capture(rb.build(), null, cam);
            rb.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            updateRepeating();
            cam.postDelayed(resumeContinuousAf, 3000);
        } catch (CameraAccessException | IllegalStateException | IllegalArgumentException ignored) { }
    }

    private final Runnable resumeContinuousAf = () -> {
        if (rb == null) return;
        rb.set(CaptureRequest.CONTROL_AF_REGIONS, null);
        rb.set(CaptureRequest.CONTROL_AF_MODE, afMode);
        rb.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
        updateRepeating();
    };

    private void closeCamera() {
        camGen++;
        opening = false;
        cam.removeCallbacks(resumeContinuousAf);
        if (session != null) { try { session.close(); } catch (RuntimeException ignored) { } session = null; }
        if (device != null) { device.close(); device = null; }
        if (reader != null) { reader.close(); reader = null; }
        rb = null;
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        configureTransform();
        cam.post(() -> { surfaceReady = true; startSession(); });
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { configureTransform(); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        cam.post(() -> {   // keep the device; only the session that drew into this surface goes
            surfaceReady = false;
            if (session != null) { try { session.close(); } catch (RuntimeException ignored) { } session = null; }
        });
        return true;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }

    // ---------------- geometry: frame (sensor) ↔ view ----------------
    private float viewScale;   // view px per portrait-frame px (centre crop)

    /** Centre-crop the portrait camera image into the view (UI thread). */
    private void configureTransform() {
        Size s = size;
        int vw = preview.getWidth(), vh = preview.getHeight();
        if (s == null || vw == 0 || vh == 0) return;
        float pw = rotated() ? s.getHeight() : s.getWidth(), ph = rotated() ? s.getWidth() : s.getHeight();
        float k = Math.max(vw / pw, vh / ph);
        viewScale = k;
        Matrix m = new Matrix();
        m.setScale(pw * k / vw, ph * k / vh, vw / 2f, vh / 2f);
        preview.setTransform(m);
    }

    private boolean rotated() { return sensorOrientation % 180 != 0; }

    /** frame px → view px (portrait display, back camera) */
    private void frameToView(float fx, float fy, float[] out, int i) {
        Size s = size;
        float W = s.getWidth(), H = s.getHeight(), px, py, pw, ph;
        switch (sensorOrientation) {
            case 90: px = H - fy; py = fx; pw = H; ph = W; break;
            case 270: px = fy; py = W - fx; pw = H; ph = W; break;
            case 180: px = W - fx; py = H - fy; pw = W; ph = H; break;
            default: px = fx; py = fy; pw = W; ph = H; break;
        }
        out[i] = (px - pw / 2) * viewScale + preview.getWidth() / 2f;
        out[i + 1] = (py - ph / 2) * viewScale + preview.getHeight() / 2f;
    }

    /** view px → normalised frame position (0..1, sensor orientation), or null outside the image */
    private float[] viewToFrame(float vx, float vy) {
        Size s = size;
        if (s == null || viewScale == 0) return null;
        float W = s.getWidth(), H = s.getHeight();
        float pw = rotated() ? H : W, ph = rotated() ? W : H;
        float px = (vx - preview.getWidth() / 2f) / viewScale + pw / 2, py = (vy - preview.getHeight() / 2f) / viewScale + ph / 2;
        float fx, fy;
        switch (sensorOrientation) {
            case 90: fx = py; fy = H - px; break;
            case 270: fx = W - py; fy = px; break;
            case 180: fx = W - px; fy = H - py; break;
            default: fx = px; fy = py; break;
        }
        if (fx < 0 || fy < 0 || fx > W || fy > H) return null;
        return new float[]{fx / W, fy / H};
    }

    // ---------------- decoding (camera thread) ----------------
    private boolean lastHadShapes;

    private void onImage(ImageReader r) {
        Image img;
        try { img = r.acquireLatestImage(); } catch (IllegalStateException e) { return; }
        if (img == null) return;
        List<QrEngine.Code> codes;
        int w, h;
        try {
            if (pipe == null) return;
            w = img.getWidth(); h = img.getHeight();
            Image.Plane p = img.getPlanes()[0];
            ByteBuffer buf = p.getBuffer();
            int rs = p.getRowStride();
            if (yBuf == null || yBuf.length != w * h) yBuf = new byte[w * h];
            if (rs == w) buf.get(yBuf, 0, w * h);
            else for (int y = 0; y < h; y++) { buf.position(y * rs); buf.get(yBuf, y * w, w); }
        } finally { img.close(); }
        long now = SystemClock.uptimeMillis();
        codes = pipe.decode(yBuf, w, h, now);
        if (codes.isEmpty() && !lastHadShapes) return;
        lastHadShapes = !codes.isEmpty();
        if (multi) {
            final List<String> fresh = new ArrayList<>();
            for (QrEngine.Code c : codes) if (logic.collect(c.text)) fresh.add(c.text);
            postShapes(codes, fresh);
            if (!fresh.isEmpty()) ui.post(() -> { for (String t : fresh) onCollected(t); });
        } else {
            final String t = logic.single(codes, w, h, now);
            postShapes(codes, null);
            if (t != null) ui.post(() -> onSingle(t));
        }
    }

    private void postShapes(final List<QrEngine.Code> codes, final List<String> fresh) {
        ui.post(() -> {
            if (size == null) return;
            List<OverlayView.Shape> s = new ArrayList<>();
            for (QrEngine.Code c : codes) {
                if (c.corners == null) continue;
                float[] v = new float[8];
                for (int i = 0; i < 8; i += 2) frameToView(c.corners[i], c.corners[i + 1], v, i);
                s.add(new OverlayView.Shape(v, fresh != null && fresh.contains(c.text)));
            }
            overlay.setShapes(s);
        });
    }

    // ---------------- gestures: vertical swipe = zoom, tap = focus ----------------
    private final class Gestures implements View.OnTouchListener {
        private float downX, downY, startZoom;
        private boolean zooming, moved;
        private final int slop = ViewConfiguration.get(ScannerActivity.this).getScaledTouchSlop();

        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX(); downY = e.getY(); startZoom = zoom; zooming = false; moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getX() - downX, dy = e.getY() - downY;
                    if (!zooming && Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) {
                        zooming = true;
                        downY = e.getY();   // start from here so the zoom does not jump by the slop
                        dy = 0;
                        zoomBar.setActive(true);
                    }
                    if (Math.hypot(dx, dy) > slop) moved = true;
                    if (zooming) requestZoom(ZoomMath.zoomFor(startZoom, e.getY() - downY, v.getHeight(), zoomMin, zoomMax));
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    zoomBar.setActive(false);
                    if (!zooming && !moved) {
                        overlay.showFocus(e.getX(), e.getY());
                        final float[] f = viewToFrame(e.getX(), e.getY());
                        if (f != null) cam.post(() -> focusAt(f[0], f[1]));
                        v.performClick();
                    }
                    lastZoom = zoom;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    zoomBar.setActive(false);
                    return true;
                default:
                    return true;
            }
        }
    }

    // for the JVM screenshot test
    void setPreviewForTest(int w, int h, int orientation) { size = new Size(w, h); sensorOrientation = orientation; configureTransform(); }
    void showShapesForTest(List<QrEngine.Code> codes, List<String> fresh) { postShapes(codes, fresh); }
    void collectForTest(String text) { if (logic.collect(text)) onCollected(text); }
    void setZoomForTest(float min, float max, float z) { zoomMin = min; zoomMax = max; zoomBar.setRange(min, max); zoom = z; zoomBar.setZoom(z); }
    View zoomBarForTest() { return zoomBar; }
    CharSequence pillTextForTest() { return pill.getVisibility() == View.VISIBLE ? pill.getText() : null; }
    void hideMessageForTest() { showMessage(null, null); }
}
