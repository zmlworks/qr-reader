from PIL import Image; import numpy as np
a=np.asarray(Image.open('icon-source.jpg').convert('RGB')).astype(float)
H,W,_=a.shape
def samp(x,y):
    x0=int(np.floor(x)); y0=int(np.floor(y)); fx=x-x0; fy=y-y0
    return (a[y0,x0]*(1-fx)*(1-fy)+a[y0,x0+1]*fx*(1-fy)+a[y0+1,x0]*(1-fx)*fy+a[y0+1,x0+1]*fx*fy)
cx,cy=703.0,691.0
pts=[]
for k in range(1440):
    t=2*np.pi*k/1440; dx,dy=np.cos(t),np.sin(t)
    rs=np.arange(500,780,0.25)
    cols=[]
    for r in rs:
        x,y=cx+dx*r,cy+dy*r
        if x<1 or y<1 or x>W-2 or y>H-2: break
        cols.append(samp(x,y))
    cols=np.array(cols)
    g=np.linalg.norm(cols[4:]-cols[:-4],axis=1)
    i=np.argmax(g); r=rs[i+2]
    pts.append((t,r,g[i],cx+dx*r,cy+dy*r))
pts=np.array(pts)
np.save('edgepts.npy',pts)
xs,ys=pts[:,3],pts[:,4]
# straight edges
m=np.abs(np.sin(pts[:,0]))<0.25
print('left',np.median(xs[(np.cos(pts[:,0])<0)&m]),'right',np.median(xs[(np.cos(pts[:,0])>0)&m]))
m=np.abs(np.cos(pts[:,0]))<0.25
print('top',np.median(ys[(np.sin(pts[:,0])<0)&m]),'bottom',np.median(ys[(np.sin(pts[:,0])>0)&m]))
print('min grad',pts[:,2].min(), np.percentile(pts[:,2],5))
