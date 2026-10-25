"""Builds launcher icons from icon-source.jpg (full-bleed QR on white; keep white, never punch to alpha)."""
import numpy as np, os
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'app', 'src', 'main', 'res')
# Source already has white outside the squircle; do NOT round-corners to alpha
# (transparent corners + adaptive bg used to show black on some launchers).
src = Image.open(os.path.join(HERE, 'icon-source.jpg')).convert('RGBA')

def fit_square(img, size, content, bg=(255, 255, 255, 255)):
    """Scale img so the longer side equals content, center on a solid white canvas."""
    w, h = img.size
    s = content / max(w, h)
    t = img.resize((max(1, round(w * s)), max(1, round(h * s))), Image.LANCZOS)
    c = Image.new('RGBA', (size, size), bg)
    # paste without mask so any residual alpha does not punch through to black
    c.paste(t.convert('RGB'), ((size - t.size[0]) // 2, (size - t.size[1]) // 2))
    return c

# Preview assets: white canvas (not transparent)
fit_square(src, src.size[0], src.size[0]).save(os.path.join(HERE, 'icon-transparent.png'), optimize=True)
fit_square(src, 512, 512).save(os.path.join(HERE, 'icon-512.png'), optimize=True)

# Monochrome: dark QR modules only (white/light becomes transparent for themed icons)
g = np.asarray(src.convert('RGB')).astype(np.float32).mean(axis=2)
dark = np.clip((140 - g) / 80, 0, 1)
mono = Image.fromarray(np.dstack([np.zeros_like(g)] * 3 + [dark * 255]).astype(np.uint8), 'RGBA')

dens = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
for d, f in dens.items():
    md = os.path.join(RES, 'mipmap-' + d)
    os.makedirs(md, exist_ok=True)
    # Legacy launcher: full-bleed on white
    fit_square(src, round(48 * f), round(46 * f)).save(os.path.join(md, 'ic_launcher.webp'), quality=92, method=6)
    # Adaptive foreground: nearly fill the 108 dp canvas (launcher mask shapes it)
    fit_square(src, round(108 * f), round(100 * f)).save(os.path.join(md, 'ic_launcher_foreground.webp'), quality=92, method=6)
    # Monochrome keeps alpha (themed icon); no white fill needed
    mw = round(108 * f)
    mc = round(100 * f)
    w, h = mono.size
    s = mc / max(w, h)
    t = mono.resize((max(1, round(w * s)), max(1, round(h * s))), Image.LANCZOS)
    canvas = Image.new('RGBA', (mw, mw), (0, 0, 0, 0))
    canvas.paste(t, ((mw - t.size[0]) // 2, (mw - t.size[1]) // 2), t)
    canvas.save(os.path.join(md, 'ic_launcher_monochrome.webp'), lossless=True, method=6)

docs = os.path.join(HERE, '..', 'docs')
os.makedirs(docs, exist_ok=True)
fit_square(src, 512, 512).save(os.path.join(docs, 'icon.png'), optimize=True)
print('ok', src.size, 'white-kept')
