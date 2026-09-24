#!/usr/bin/env python3
"""Audit exported terrain channels, with categorical-aware 2D autocorrelation.
Needs local NumPy/SciPy; no installation/network is performed. Equal constant
backgrounds are reported, but are not mistaken for a repeated nonconstant tile.
"""
from __future__ import annotations
import argparse,csv,json
from pathlib import Path
import numpy as np
from scipy.signal import fftconvolve
from scipy.ndimage import maximum_filter

def integral(a): return np.pad(a.cumsum(0).cumsum(1),((1,0),(1,0)))
def rect(s,x0,z0,x1,z1):return s[z1,x1]-s[z0,x1]-s[z1,x0]+s[z0,x0]
def crops(a,dx,dz):
    h,w=a.shape;x0=max(0,-dx);x1=min(w,w-dx);z0=max(0,-dz);z1=min(h,h-dz)
    return a[z0:z1,x0:x1],a[z0+dz:z1+dz,x0+dx:x1+dx]
def stats(a,b,categorical=False):
    n=a.size;equal=float(np.mean(a==b));vals=np.union1d(np.unique(a),np.unique(b))
    chance=sum(float(np.mean(a==v))*float(np.mean(b==v)) for v in vals)
    kappa=(equal-chance)/(1-chance) if chance<1-1e-12 else None
    av=a.astype(float);bv=b.astype(float);var=np.std(av)*np.std(bv)
    correlation=float(np.mean((av-av.mean())*(bv-bv.mean()))/var) if var>1e-12 else None
    positive_union=np.count_nonzero((a!=0)|(b!=0));positive_jaccard=float(np.count_nonzero((a!=0)&(b!=0))/positive_union) if positive_union else None
    size=64;exact=constant=isolated=0
    if min(a.shape)>=size:
        box=lambda v: (lambda s:s[size:,size:]-s[:-size,size:]-s[size:,:-size]+s[:-size,:-size])(integral(v))
        match=box((a==b).astype(float))>=size*size-.01
        sums=box(av);variances=box(av*av)-sums*sums/(size*size)
        exact=int(np.count_nonzero(match&(variances>1e-8)));constant=int(np.count_nonzero(match&(variances<=1e-8)))
        if set(vals)<=set([0,1]):isolated=int(np.count_nonzero(match&(variances>1e-8)&(np.minimum(sums,size*size-sums)<=1)))
    return dict(pairs=n,equality=equal,chance_equality=chance,adjusted_equality=kappa,correlation=None if categorical else correlation,positive_jaccard=positive_jaccard if set(vals)<=set([0,1]) else None,nonconstant_exact_64_tiles=exact,isolated_single_pixel_64_matches=isolated,nontrivial_exact_64_tiles=exact-isolated,constant_exact_64_tiles=constant)
def correlation2d(a,categorical=False):
    h,w=a.shape;r=min(128,h-1,w-1);lags=np.arange(-r,r+1);dx,dz=np.meshgrid(lags,lags)
    x0=np.maximum(0,-dx);x1=np.minimum(w,w-dx);z0=np.maximum(0,-dz);z1=np.minimum(h,h-dz);n=(x1-x0)*(z1-z0)
    if categorical:
        equal=np.zeros(n.shape);chance=np.zeros(n.shape)
        for v in np.unique(a):
            f=(a==v).astype(float);s=integral(f)
            equal+=fftconvolve(f,f[::-1,::-1],mode='full')[h-1+dz,w-1+dx]/n
            chance+=rect(s,x0,z0,x1,z1)*rect(s,x0+dx,z0+dz,x1+dx,z1+dz)/(n*n)
        with np.errstate(divide='ignore',invalid='ignore'): c=(equal-chance)/(1-chance)
        c[(1-chance)<1e-12]=np.nan
    else:
        f=a.astype(float);s=integral(f);s2=integral(f*f)
        sa=rect(s,x0,z0,x1,z1);sb=rect(s,x0+dx,z0+dz,x1+dx,z1+dz)
        dot=fftconvolve(f,f[::-1,::-1],mode='full')[h-1+dz,w-1+dx]
        va=rect(s2,x0,z0,x1,z1)-sa*sa/n;vb=rect(s2,x0+dx,z0+dz,x1+dx,z1+dz)-sb*sb/n
        with np.errstate(divide='ignore',invalid='ignore'):c=(dot-sa*sb/n)/np.sqrt(np.maximum(0,va*vb))
        c[(va<1e-10)|(vb<1e-10)]=np.nan
    peaks=np.nan_to_num(c,nan=-2);select=(peaks>=maximum_filter(peaks,size=9)-1e-10)&((dx*dx+dz*dz)>=32*32)&((dz>0)|((dz==0)&(dx>0)))
    indices=np.argwhere(select);indices=sorted(indices,key=lambda j:peaks[tuple(j)],reverse=True)[:8]
    return c,lags,[dict(dx=int(dx[tuple(j)]),dz=int(dz[tuple(j)]),value=float(c[tuple(j)])) for j in indices]
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--current',type=Path,required=True);p.add_argument('--baseline',type=Path);p.add_argument('--out',type=Path,required=True);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
    rows=[];report={};arrays={};failures=[]
    for label,path in [('baseline',a.baseline),('current',a.current)]:
        if path is None:continue
        d=json.loads(path.read_text());report[label]={};w=d['width'];h=d['depth']
        for channel,flat in zip(d['channels'],d['fields']):
            field=np.array(flat).reshape(h,w);categorical=channel=='material';c,lags,peaks=correlation2d(field,categorical);arrays[label+'_'+channel]=c;arrays['lags']=lags
            vals,counts=np.unique(field,return_counts=True)
            report[label][channel]=dict(distribution={str(v):int(k) for v,k in zip(vals,counts)},measure='categorical kappa' if categorical else 'overlap-normalized Pearson',suspicious_local_maxima_beyond_32=peaks)
            for shift in [16,32,64,96,128]:
                for dx,dz in [(shift,0),(0,shift),(shift,shift),(-shift,shift)]:
                    result=stats(*crops(field,dx,dz),categorical);rows.append(dict(version=label,channel=channel,dx=dx,dz=dz,**result))
                    if label=='current' and result['nontrivial_exact_64_tiles']>0:failures.append(f'{channel} at ({dx},{dz}): nontrivial exact 64x64 matches')
    np.savez_compressed(a.out/'autocorrelation-2d.npz',**arrays)
    with (a.out/'translation-metrics.csv').open('w',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=rows[0]);writer.writeheader();writer.writerows(rows)
    report['test']={'name':'four_channels_512_no_nontrivial_repeated_64_tiles_at_20_offsets','passed':not failures,'failures':failures,'tile_size':64,'offsets':[16,32,64,96,128],'directions':['x','z','diagonal','anti-diagonal'],'interpretation':'Constant grass, dry ground, treeless areas or lake interiors do not demonstrate tiling. Natural low-frequency correlation is not required to vanish. Binary 64x64 windows containing only one minority pixel are isolated-feature coincidences, separately counted without classifying them as repeated tiles. Full 257x257 correlations are saved, not only a selected lag.'}
    (a.out/'terrain-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps(report['test'],ensure_ascii=False),flush=True)
    if failures:raise SystemExit(1)
if __name__=='__main__':main()
