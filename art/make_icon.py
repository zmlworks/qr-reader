"""Builds launcher icons from icon-source.jpg.

Uses the fitted squircle mask (fit.npy / edges.py) so the rounded QR art fills
the canvas the way Simple QR Reader's docs/icon.png did, but the outside is
always opaque white (never transparent/black on launchers or README).
"""
import numpy as np, os
from PIL import Image, ImageFilter
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'app', 'src', 'main', 'res')
WHITE = (255, 255, 255, 255)

src = np.asarray(Image.open(os.path.join(HERE, 'icon-source.jpg')).convert('RGB')).astype(np.float32)
FIT = os.path.join(HERE, 'fit.npy')
L, R, T, B, rx, ry, n = np.load(FIT) if os.path.exists(FIT) else (131.49, 1274.53, 98.55, 1281.69, 227.0, 237.0, 1.98)
INSET = 1.0
H, W, _ = src.shape
cx = (L + R) / 2; cy = (T + B) / 2; hx = (R - L) / 2 - INSET; hy = (B - T) / 2 - INSET
SS = 4
yy, xx = np.mgrid[0:H * SS, 0:W * SS].astype(np.float32)
xx = (xx + 0.5) / SS; yy = (yy + 0.5) / SS
u = np.abs(xx - cx); v = np.abs(yy - cy)
ux = u - (hx - rx); vy = v - (hy - ry)
corner = (ux > 0) & (vy > 0)
rr = np.sqrt((np.maximum(ux, 0) / rx) ** 2 + (np.maximum(vy, 0) / ry) ** 2)
inside = np.where(corner, rr <= 1, (u <= hx) & (v <= hy))
alpha = inside.reshape(H, SS, W, SS).mean(axis=(1, 3))
del xx, yy, u, v, ux, vy, corner, rr, inside

rgb = src.copy()
solid = alpha >= 0.999
img = np.dstack([rgb, alpha * 255]).clip(0, 255).astype(np.uint8)
im = Image.fromarray(img, 'RGBA')

def blur1(ch):
    return np.asarray(Image.fromarray(ch.clip(0, 255).astype(np.uint8), 'L').filter(ImageFilter.GaussianBlur(3)), dtype=np.float32)

wsum = blur1(solid.astype(np.float32) * 255)
fill = np.dstack([blur1(rgb[..., c] * solid) for c in range(3)]) * 255 / np.maximum(wsum, 1e-3)[..., None]
fill = np.where((wsum > 1)[..., None], fill, rgb)
edge = (alpha > 0) & (alpha < 0.999)
out = np.asarray(im).copy()
out[edge, :3] = fill[edge].clip(0, 255).astype(np.uint8)
im = Image.fromarray(out, 'RGBA')
bbox = im.getbbox(); im = im.crop(bbox)


def fit_square(img, size, content, bg=WHITE):
    """Scale img so the longer side equals content, center on an opaque white canvas."""
    w, h = img.size
    s = content / max(w, h)
    t = img.resize((max(1, round(w * s)), max(1, round(h * s))), Image.LANCZOS)
    c = Image.new('RGBA', (size, size), bg)
    # composite with mask so anti-aliased edge blends onto white (no black punch-through)
    c.paste(t, ((size - t.size[0]) // 2, (size - t.size[1]) // 2), t)
    # force fully opaque white outside (no residual alpha)
    a = np.asarray(c).copy()
    a[..., 3] = 255
    return Image.fromarray(a, 'RGBA')


# Preview assets: white canvas (not transparent); names kept for history
fit_square(im, 512, 512).save(os.path.join(HERE, 'icon-transparent.png'), optimize=True)
fit_square(im, 512, 512).save(os.path.join(HERE, 'icon-512.png'), optimize=True)
print('tight crop', im.size, 'bbox', bbox)

# Monochrome: dark QR modules only (themed icon keeps alpha)
g = np.asarray(im.convert('RGB')).astype(np.float32).mean(axis=2)
a = np.asarray(im)[..., 3].astype(np.float32) / 255
dark = np.clip((150 - g) / 90, 0, 1) * a
mono = Image.fromarray(np.dstack([np.zeros_like(g)] * 3 + [dark * 255]).astype(np.uint8), 'RGBA')

dens = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
for d, f in dens.items():
    md = os.path.join(RES, 'mipmap-' + d)
    os.makedirs(md, exist_ok=True)
    # Legacy launcher: full art on opaque white
    fit_square(im, round(48 * f), round(44 * f)).save(os.path.join(md, 'ic_launcher.webp'), quality=92, method=6)
    # Adaptive foreground: art sized for the safe zone; bg layer is solid white
    fit_square(im, round(108 * f), round(56 * f)).save(os.path.join(md, 'ic_launcher_foreground.webp'), quality=92, method=6)
    # Monochrome keeps alpha (themed); no white fill
    def fit_alpha(img, size, content):
        w, h = img.size
        s = content / max(w, h)
        t = img.resize((max(1, round(w * s)), max(1, round(h * s))), Image.LANCZOS)
        c = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        c.paste(t, ((size - t.size[0]) // 2, (size - t.size[1]) // 2), t)
        return c
    fit_alpha(mono, round(108 * f), round(56 * f)).save(os.path.join(md, 'ic_launcher_monochrome.webp'), lossless=True, method=6)

docs = os.path.join(HERE, '..', 'docs')
os.makedirs(docs, exist_ok=True)
fit_square(im, 512, 512).save(os.path.join(docs, 'icon.png'), optimize=True)
print('ok', 'white-outside', im.size)
