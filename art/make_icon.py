"""Builds the transparent icon artwork and the Android launcher icons from icon-source.jpg.
The rounded square was fitted to the edge points of the source (edges.py / fit.py):
bounds L,R,T,B and elliptical corners rx, ry (exponent n = 2), median fit error ~0.1 px."""
import numpy as np, os
from PIL import Image, ImageDraw, ImageFilter
HERE=os.path.dirname(os.path.abspath(__file__))
RES=os.path.join(HERE,'..','app','src','main','res')
src=np.asarray(Image.open(os.path.join(HERE,'icon-source.jpg')).convert('RGB')).astype(np.float32)
FIT=os.path.join(HERE,'fit.npy')   # written by fit.py; the values below are its result for icon-source.jpg
L,R,T,B,rx,ry,n=np.load(FIT) if os.path.exists(FIT) else (131.49, 1274.53, 98.55, 1281.69, 227.0, 237.0, 1.98)
INSET=1.0   # px: drop the 1-px fringe that is blended with the white / shadow surround
H,W,_=src.shape
cx=(L+R)/2; cy=(T+B)/2; hx=(R-L)/2-INSET; hy=(B-T)/2-INSET
SS=4
yy,xx=np.mgrid[0:H*SS,0:W*SS].astype(np.float32)
xx=(xx+0.5)/SS; yy=(yy+0.5)/SS
u=np.abs(xx-cx); v=np.abs(yy-cy)
ux=u-(hx-rx); vy=v-(hy-ry)
corner=(ux>0)&(vy>0)
rr=np.sqrt((np.maximum(ux,0)/rx)**2+(np.maximum(vy,0)/ry)**2)
inside=np.where(corner, rr<=1, (u<=hx)&(v<=hy))
alpha=inside.reshape(H,SS,W,SS).mean(axis=(1,3))
del xx,yy,u,v,ux,vy,corner,rr,inside
# un-premultiply nothing: colour of edge pixels is the interior colour (inset), so keep RGB as is,
# but fill the fringe pixels' colour from the nearest interior pixel to avoid a light halo
rgb=src.copy()
solid=alpha>=0.999
img=np.dstack([rgb,alpha*255]).clip(0,255).astype(np.uint8)
im=Image.fromarray(img,'RGBA')
# colour bleed: replace RGB of partially transparent pixels by a blurred interior colour
# premultiplied blur of the solid interior only (PIL blurs RGBA channels independently)
def blur1(ch): return np.asarray(Image.fromarray(ch.clip(0,255).astype(np.uint8),'L').filter(ImageFilter.GaussianBlur(3)),dtype=np.float32)
wsum=blur1(solid.astype(np.float32)*255)
fill=np.dstack([blur1(rgb[...,c]*solid) for c in range(3)])*255/np.maximum(wsum,1e-3)[...,None]
fill=np.where((wsum>1)[...,None], fill, rgb)
edge=(alpha>0)&(alpha<0.999)
out=np.asarray(im).copy()
out[edge,:3]=fill[edge].clip(0,255).astype(np.uint8)
im=Image.fromarray(out,'RGBA')
bbox=im.getbbox(); im=im.crop(bbox)
im.save(os.path.join(HERE,'icon-transparent.png'), optimize=True)
print('tight crop', im.size, 'bbox', bbox)

def fit_square(img, size, content):
    """img scaled so its larger side is `content` px, centred on a transparent size×size canvas."""
    w,h=img.size; s=content/max(w,h)
    t=img.resize((max(1,round(w*s)),max(1,round(h*s))), Image.LANCZOS)
    c=Image.new('RGBA',(size,size),(0,0,0,0)); c.paste(t,((size-t.size[0])//2,(size-t.size[1])//2),t)
    return c
fit_square(im,512,512).save(os.path.join(HERE,'icon-512.png'), optimize=True)

# monochrome (themed icon) layer: the dark QR modules of the artwork
g=np.asarray(im.convert('RGB')).astype(np.float32).mean(axis=2)
a=np.asarray(im)[...,3].astype(np.float32)/255
dark=np.clip((150-g)/90,0,1)*a
mono=Image.fromarray(np.dstack([np.zeros_like(g)]*3+[dark*255]).astype(np.uint8),'RGBA')

dens={'mdpi':1,'hdpi':1.5,'xhdpi':2,'xxhdpi':3,'xxxhdpi':4}
for d,f in dens.items():
    md=os.path.join(RES,'mipmap-'+d); os.makedirs(md,exist_ok=True)
    # legacy icon: 48dp with ~2dp padding
    leg=fit_square(im, round(48*f), round(44*f))
    leg.save(os.path.join(md,'ic_launcher.webp'), quality=92, method=6)
    # adaptive foreground: 108dp canvas, art 56dp tall -> its farthest point stays inside the 66dp safe circle
    fit_square(im, round(108*f), round(56*f)).save(os.path.join(md,'ic_launcher_foreground.webp'), quality=92, method=6)
    fit_square(mono, round(108*f), round(56*f)).save(os.path.join(md,'ic_launcher_monochrome.webp'), lossless=True, method=6)
