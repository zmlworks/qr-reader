package com.zmlworks.qrreader;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.widget.TextView;


/**
 * The blurred content line of a protected row. The real text is never drawn: the line shows the fixed
 * {@link #PLACEHOLDER}, blurred by RenderEffect on Android 12+ or drawn from a pre-blurred bitmap below that.
 */
final class Obscured {
    private Obscured() {}

    static final float BLUR_DP = 4.5f;
    /** tests: the bitmap path on every API level (JVM screenshots draw in software, where RenderEffect does not show) */
    static boolean forceBitmapForTest;

    /** what is drawn (then blurred) instead of the content: the same fixed string for every protected row */
    static final String PLACEHOLDER = " * * * * * * * * * * *";

    /** Shows (on) or removes (off) the obscured line in `title`. */
    static void apply(TextView title, long id, boolean on) {
        float density = title.getResources().getDisplayMetrics().density;
        if (Build.VERSION.SDK_INT >= 31) title.setRenderEffect(null);
        if (Build.VERSION.SDK_INT >= 31 && !forceBitmapForTest) {
            if (on) {
                float r = BLUR_DP * density;
                title.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.DECAL));
            } else title.setRenderEffect(null);
            title.setTag(R.id.obscured, on ? "render-effect" : null);
            if (on) title.setText(PLACEHOLDER);
        } else {
            title.setTag(R.id.obscured, on ? "bitmap" : null);
            if (on) title.setText(blurredLine(title, PLACEHOLDER, Math.max(1, Math.round(BLUR_DP * density))));
        }
        title.setContentDescription(on ? title.getContext().getString(R.string.protected_title) : null);
    }

    /** The placeholder drawn into a bitmap and box-blurred (3 passes ≈ Gaussian), as a one-character ImageSpan. */
    static CharSequence blurredLine(TextView tv, String text, int radius) {
        TextPaint p = new TextPaint(tv.getPaint());
        p.setColor(tv.getCurrentTextColor());
        Paint.FontMetricsInt fm = p.getFontMetricsInt();
        int pad = radius * 2, w = (int) Math.ceil(p.measureText(text)) + 2 * pad, h = fm.descent - fm.ascent + 2 * pad;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(b).drawText(text, pad, pad - fm.ascent, p);
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        int rgb = tv.getCurrentTextColor() & 0xFFFFFF;
        int[] a = new int[w * h], t = new int[w * h];
        for (int i = 0; i < px.length; i++) a[i] = px[i] >>> 24;
        for (int k = 0; k < 3; k++) { boxH(a, t, w, h, radius); boxV(t, a, w, h, radius); }
        for (int i = 0; i < px.length; i++) px[i] = (a[i] << 24) | rgb;
        b.setPixels(px, 0, w, 0, 0, w, h);
        SpannableString s = new SpannableString("\uFFFC");
        s.setSpan(new BlurSpan(b, pad), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    /** Draws the pre-blurred line; takes the height of a normal text line (the soft edge spills over a little). */
    static final class BlurSpan extends android.text.style.ReplacementSpan {
        final Bitmap bitmap;
        private final int pad;

        BlurSpan(Bitmap b, int pad) { bitmap = b; this.pad = pad; }

        @Override public int getSize(Paint p, CharSequence t, int start, int end, Paint.FontMetricsInt fm) {
            if (fm != null) {
                Paint.FontMetricsInt m = p.getFontMetricsInt();
                fm.ascent = m.ascent; fm.descent = m.descent; fm.top = m.top; fm.bottom = m.bottom; fm.leading = m.leading;
            }
            return bitmap.getWidth();
        }

        @Override public void draw(Canvas c, CharSequence t, int start, int end, float x, int top, int y, int bottom, Paint p) {
            c.drawBitmap(bitmap, x, y + p.getFontMetricsInt().ascent - pad, null);
        }
    }

    private static void boxH(int[] in, int[] out, int w, int h, int r) {
        int div = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w, sum = 0;
            for (int x = -r; x <= r; x++) sum += in[row + Math.min(w - 1, Math.max(0, x))];
            for (int x = 0; x < w; x++) {
                out[row + x] = sum / div;
                sum += in[row + Math.min(w - 1, x + r + 1)] - in[row + Math.max(0, x - r)];
            }
        }
    }

    private static void boxV(int[] in, int[] out, int w, int h, int r) {
        int div = 2 * r + 1;
        for (int x = 0; x < w; x++) {
            int sum = 0;
            for (int y = -r; y <= r; y++) sum += in[Math.min(h - 1, Math.max(0, y)) * w + x];
            for (int y = 0; y < h; y++) {
                out[y * w + x] = sum / div;
                sum += in[Math.min(h - 1, y + r + 1) * w + x] - in[Math.max(0, y - r) * w + x];
            }
        }
    }
}
