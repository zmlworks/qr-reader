# art/ — launcher icon

- `icon-source.jpg` — user artwork (1408×1408): QR with green scan line on mint→lavender gradient (same lineage as Simple QR Reader `d44ba95`).
- Squircle mask from `fit.npy` (edges.py / fit.py): art fills the canvas like the original `docs/icon.png`, with the outside forced to opaque white (never transparent/black).
- `icon-transparent.png` / `icon-512.png` — white-canvas previews (name kept for history; not transparent).
- `make_icon.py` — writes mipmaps (legacy 48 dp + adaptive foreground ~56 dp on 108 dp; monochrome = dark QR modules) and `docs/icon.png`.
- Adaptive background: `res/drawable/ic_launcher_background.xml` (solid white `#FFFFFF`).
