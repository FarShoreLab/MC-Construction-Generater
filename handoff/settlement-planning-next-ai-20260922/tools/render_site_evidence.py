#!/usr/bin/env python3
"""Render exact exported terrain and construction columns; no spline or display-only layout edits."""
from pathlib import Path
import argparse,gzip,json,math
import numpy as np
from PIL import Image,ImageDraw,ImageFont
from verify_site_expert import measure

def font(size):
    for path in ['/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc','C:/Windows/Fonts/msyh.ttc','C:/Windows/Fonts/arial.ttf']:
        if Path(path).is_file():return ImageFont.truetype(path,size)
    return ImageFont.load_default()

def layer(p,px=720):
    w,d=p['config']['width'],p['config']['depth'];o=p['original'];h=np.array(o['heights']).reshape(d,w);wet=np.array(o['waters']).reshape(d,w)>h
    rgb=np.empty((d,w,3),dtype=np.uint8);shade=np.clip((h-h.mean())*1.8,-20,20)
    for i,c in enumerate([99,133,81]):rgb[:,:,i]=np.clip(c+shade,0,255)
    rgb[wet]=[69,119,148]
    for x,y,z,material,*_ in o.get('voxels',[]):
        if material in (5,6):rgb[z,x]=[76,110,68]
    for c in p['plan']['groundColumns']:
        rgb[c['z'],c['x']]=[242,163,98] if c['kind']!='foundation' else [245,212,159]
        if c['structure'] in ['bridge','deck']:rgb[c['z'],c['x']]=[250,206,107]
    scale=px/max(w,d);im=Image.fromarray(rgb).resize((round(w*scale),round(d*scale)),Image.Resampling.NEAREST)
    pen=ImageDraw.Draw(im);m=measure(p)
    if m['bounds']:
        x,z,bx,bz=m['bounds'];pen.rectangle((round(x*scale),round(z*scale),round((bx+1)*scale),round((bz+1)*scale)),outline=(225,235,225),width=2)
    for i,b in enumerate(p['plan']['plots']):
        x,z=b['origin'];pen.text((round(x*scale),round(z*scale)-15),str(i+1),font=font(14),fill=(255,255,255),stroke_width=1,stroke_fill=(40,50,40))
    return im

def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--data',type=Path,required=True);ap.add_argument('--out',type=Path,required=True);a=ap.parse_args();a.out.mkdir(parents=True,exist_ok=True)
    results=json.loads((a.data/'results.json').read_text());files=[]
    for row in results['comparisons']:
        name=row['case'];old=json.loads(gzip.decompress((a.data/(name+'-legacy.json.gz')).read_bytes()));new=json.loads(gzip.decompress((a.data/(name+'-expert.json.gz')).read_bytes()))
        assert old['original']==new['original'];panels=[layer(old),layer(new)];h=max(p.height for p in panels);out=Image.new('RGB',(1500,h+155),(17,26,36));draw=ImageDraw.Draw(out)
        draw.text((28,12),f'{name} · 同一地形、同一建筑总数 · 全地图（未裁剪）',font=font(25),fill=(232,238,242))
        for i,p in enumerate([old,new]):
            m=measure(p);x=24+i*748;draw.text((x,55),('v0.4.2 沿路生长' if i==0 else 'v0.5.0 先选址后铺路')+f'  |  {p["metrics"]["plotCount"]} / 7',font=font(21),fill=(242,195,143));out.paste(panels[i],(x,93))
            draw.text((x,100+h),f'建筑框占图 {m["bbox"]*100:.1f}%    短/长轴 {m["minor"]:.3f}    中心凸包 {m["hull"]*100:.1f}%',font=font(17),fill=(215,226,230))
        file=name+'-comparison.png';out.save(a.out/file);files.append(file)
    (a.out/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>真实施工数据对照</title><style>body{background:#111a24;color:#eee;font:16px sans-serif}img{max-width:100%}</style><h1>全地图新旧布局对照</h1>'+''.join('<h2>'+f+'</h2><img src="'+f+'">' for f in files))
if __name__=='__main__':main()
