import numpy as np
pts=np.load('edgepts.npy'); xs,ys=pts[:,3],pts[:,4]
L,R,T,B=131.49,1274.53,98.55,1281.69
cx=(L+R)/2; cy=(T+B)/2; hx=(R-L)/2; hy=(B-T)/2
u=np.abs(xs-cx); v=np.abs(ys-cy)
def resid(rx,ry,n):
    ux=u-(hx-rx); vy=v-(hy-ry)
    c=(ux>0)&(vy>0)
    # radial-ish distance in normalized corner coords
    rr=((np.abs(ux)/rx)**n+(np.abs(vy)/ry)**n)**(1/n)
    d=np.where(c,(rr-1)*min(rx,ry),np.maximum(u-hx,v-hy))
    return d
best=None
for rx in range(150,420,4):
  for ry in range(150,420,4):
    for n in np.arange(1.8,4.01,0.1):
        d=resid(rx,ry,n); s=np.mean(np.minimum(np.abs(d),5))
        if best is None or s<best[0]: best=(s,rx,ry,n)
print(best)
s,rx,ry,n=best
for drx in range(-4,5):
  for dry in range(-4,5):
    for dn in np.arange(-0.1,0.11,0.02):
        d=resid(rx+drx,ry+dry,n+dn); s2=np.mean(np.minimum(np.abs(d),5))
        if s2<best[0]: best=(s2,rx+drx,ry+dry,n+dn)
print(best)
d=resid(*best[1:]); print(np.percentile(np.abs(d),[50,90,99,100]))
np.save('fit.npy',np.array([L,R,T,B,best[1],best[2],best[3]]))
