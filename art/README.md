# art/ — launcher icon

- `icon-source.jpg` — the icon artwork supplied by the user (1408×1408, white surround + drop shadow; metadata removed).
- `icon-transparent.png` — the rounded square only, tight crop (1142×1182), transparent outside, anti-aliased edge.
- `icon-512.png` — the same on a 512×512 transparent canvas.
- `icon-preview.png` — light/dark preview incl. the adaptive icon under circle and rounded-square masks.

Pipeline (Python 3 + Pillow + NumPy):
1. `edges.py` — finds the edge of the rounded square along 1440 rays from the centre (largest colour step).
2. `fit.py` — fits bounds + elliptical corners to those points (median error ≈ 0.1 px) → `fit.npy`.
3. `make_icon.py` — 4× supersampled mask, inset 1 px (drops the white/shadow fringe), colour bleed into the edge
   pixels, tight crop; writes the PNGs above and the app's mipmaps (legacy 48 dp, adaptive foreground: art 56 dp
   tall on the 108 dp layer so its farthest corner stays inside the 66 dp safe circle; monochrome = the QR modules).
4. `preview.py [out.png]` — the preview sheet.
