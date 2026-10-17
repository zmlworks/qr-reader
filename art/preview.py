import os
from PIL import Image, ImageDraw, ImageFont
HERE=os.path.dirname(os.path.abspath(__file__))
RES=os.path.join(HERE,'..','app','src','main','res')
import sys
out=sys.argv[1] if len(sys.argv)>1 else os.path.join(HERE,'icon-preview.png')
icon=Image.open(os.path.join(HERE,'icon-512.png'))
fg=Image.open(os.path.join(RES,'mipmap-xxxhdpi','ic_launcher_foreground.webp'))   # 432 px = 108dp
leg=Image.open(os.path.join(RES,'mipmap-xxxhdpi','ic_launcher.webp'))
try: font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',22)
except Exception: font=ImageFont.load_default()
W,Hh=1400,1000
img=Image.new('RGB',(W,Hh),(255,255,255))
d=ImageDraw.Draw(img)
bgs=[((255,255,255),(30,30,30),'light'),((24,24,28),(230,230,230),'dark')]
for i,(bg,fgc,name) in enumerate(bgs):
    y0=i*500
    d.rectangle([0,y0,W,y0+500],fill=bg)
    # 512px transparent artwork at 400 px
    t=icon.resize((400,400),Image.LANCZOS); img.paste(t,(40,y0+50),t)
    d.text((40,y0+460),'512px transparent PNG ('+name+')',fill=fgc,font=font)
    # adaptive icon masked by circle and squircle (72dp viewport of the 108dp layer), 216 px
    vp=fg.crop((72,72,360,360)).resize((216,216),Image.LANCZOS)
    for j,shape in enumerate(['circle','rounded']):
        m=Image.new('L',(216,216),0); md=ImageDraw.Draw(m)
        if shape=='circle': md.ellipse([0,0,215,215],fill=255)
        else: md.rounded_rectangle([0,0,215,215],radius=50,fill=255)
        layer=Image.new('RGBA',(216,216),(0,0,0,0)); layer.paste(vp,(0,0),vp)
        # visualise the mask outline faintly (transparent adaptive background)
        x=500+j*280; img.paste(layer,(x,y0+90),Image.composite(layer,Image.new('RGBA',(216,216),(0,0,0,0)),m))
        d.text((x,y0+330),'adaptive, '+shape+' mask',fill=fgc,font=font)
    # legacy 192 px + small sizes
    img.paste(leg,(1080,y0+90),leg)
    for k,s in enumerate([96,48]):
        t=leg.resize((s,s),Image.LANCZOS); img.paste(t,(1080+k*110,y0+300),t)
    d.text((1080,y0+420),'legacy mipmap',fill=fgc,font=font)
img.save(out,optimize=True); print(out)
