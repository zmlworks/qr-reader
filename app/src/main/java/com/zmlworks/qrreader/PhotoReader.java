package com.zmlworks.qrreader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Loads a picked picture (content:// or file://), scaled to at most {@link #MAX_SIDE} px, and decodes its QR codes. */
final class PhotoReader {
    static final int MAX_SIDE = 3000;
    private PhotoReader() {}

    /** @return the codes found (maybe empty), or null if the picture could not be read. Call off the UI thread. */
    static List<String> read(Context c, Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / sample > MAX_SIDE) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            d.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) { b = BitmapFactory.decodeStream(in, null, d); }
            if (b == null) return null;
            int w = b.getWidth(), h = b.getHeight();
            int[] px = new int[w * h];
            b.getPixels(px, 0, w, 0, 0, w, h);
            b.recycle();
            byte[] y = PhotoCodes.luminance(px, w, h);
            List<QrEngine> engines = new ArrayList<>();
            engines.add(CppEngine.create(true));   // null where the native library is missing
            engines.add(new JavaEngine());
            return PhotoCodes.decode(y, w, h, engines);
        } catch (IOException | SecurityException | OutOfMemoryError | IllegalArgumentException e) {
            return null;
        }
    }

}
