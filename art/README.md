# art/ — launcher icon

- `icon-source.jpg` — user artwork (1408×1408): QR with green scan line on mint→lavender gradient, white outside the squircle (keep white; do not punch to alpha).
- `icon-transparent.png` / `icon-512.png` — white-canvas previews (name kept for history; not transparent).
- `make_icon.py` — writes mipmaps (legacy 48 dp + adaptive foreground ~100 dp on 108 dp; monochrome = dark QR modules) and `docs/icon.png`. White corners are preserved so launchers never show black.
- Adaptive background: `res/drawable/ic_launcher_background.xml` (solid white `#FFFFFF`).
