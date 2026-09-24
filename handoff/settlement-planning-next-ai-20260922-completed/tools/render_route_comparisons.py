#!/usr/bin/env python3
"""Render before/after evidence from authoritative IR columns and unsmoothed lattice centers.
Optional evidence tool: requires Pillow and numpy. Never feeds geometry back to the planner.
"""
from __future__ import annotations
import argparse, gzip, itertools, json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from verify_route_richness import SCENES, audit_metrics


def font(size:int):
    for path in ['/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf','C:/Windows/Fonts/arial.ttf']:
        if Path(path).is_file(): return ImageFont.truetype(path,size)
    return ImageFont.load_default(size=size)


def terrain_image(data:dict)->Image.Image:
    w,h=data['width'],data['depth'];height,water,obstacle=np.asarray(data['fields']).reshape(3,h,w)
    shade=np.clip(170+(height-height.mean())*2.6,100,225)
    rgb=np.stack([shade*.93,shade,shade*.91],axis=-1).astype(np.uint8)
    rgb[np.isin(obstacle,[1,2])]=(rgb[np.isin(obstacle,[1,2])]*.94).astype(np.uint8)
    rgb[(water>height)|np.isin(obstacle,[3,4])]=[95,145,176]
    rgb[obstacle==5]=[112,113,112];rgb[obstacle==6]=[120,72,72];rgb[obstacle==7]=[111,80,139]
    return Image.fromarray(rgb)


def draw_plan(base:Image.Image,plan:dict)->Image.Image:
    im=base.copy();d=ImageDraw.Draw(im)
    colors={'foundation':(75,139,99),'access':(228,179,89),'road':(247,221,168)}
    for c in plan['groundColumns']:d.point((c['x'],c['z']),fill=colors[c['kind']])
    for e in plan['transportNetwork']['corridors']:
        p=e['steps'];d.line([(s['x'],s['z']) for s in p],fill=(115,90,49),width=1)
        moves=[(b['x']-a['x'],b['z']-a['z']) for a,b in zip(p,p[1:])];i=0
        for _,group in itertools.groupby(moves):
            n=len(list(group))
            if n>16:d.line([(p[i]['x'],p[i]['z']),(p[i+n]['x'],p[i+n]['z'])],fill=(186,60,53),width=1)
            i+=n
    return im


def main()->None:
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--before',type=Path,required=True);ap.add_argument('--after',type=Path,required=True);ap.add_argument('--out',type=Path,required=True);a=ap.parse_args();a.out.mkdir(parents=True,exist_ok=True)
    records=[]
    for name in SCENES:
        before=json.loads((a.before/name/'PlanningIR.json').read_text());after=json.loads((a.after/name/'PlanningIR.json').read_text())
        terrain=json.load(gzip.open(a.after/name/'terrain.json.gz'));base=terrain_image(terrain)
        points=[(c['x'],c['z']) for p in [before,after] for c in p['groundColumns']]
        box=(max(0,min(x for x,z in points)-12),max(0,min(z for x,z in points)-12),min(base.width,max(x for x,z in points)+13),min(base.height,max(z for x,z in points)+13))
        cw,ch=box[2]-box[0],box[3]-box[1];scale=max(1,min(7,700//cw,720//ch));pw,ph=cw*scale,ch*scale
        width=max(1480,2*pw+80);height=ph+220;canvas=Image.new('RGB',(width,height),'#f6f7f8');draw=ImageDraw.Draw(canvas)
        draw.text((32,22),name+' | actual constructed road geometry',fill='#182633',font=font(24))
        draw.text((32,57),'Same terrain, same crop, same scale. Red = straight heading run > 16 moves.',fill='#475563',font=font(17))
        row={'scene':name,'crop':list(box),'scale':scale}
        for index,(label,p) in enumerate([('BEFORE',before),('AFTER v0.4.1',after)]):
            q=audit_metrics(p);metrics=p['transportNetwork']['metrics'];row[label]={'quality':q,'network':metrics}
            left=32+index*(width//2);draw.text((left,93),label,fill='#1c2935',font=font(21))
            info=f"Collector max run: {q['collectorMaxStraightRun']}  |  Verified loops: {metrics['verifiedLoops']}  |  ABAB: {q['microZigzagWindows']}"
            draw.text((left,123),info,fill='#475563',font=font(15))
            im=draw_plan(base,p).crop(box).resize((pw,ph),Image.Resampling.NEAREST);canvas.paste(im,(left,155))
            im.save(a.out/(name+('-before.png' if index==0 else '-after.png')))
        draw.text((32,height-43),'Source: PlanningIR.groundColumns + corridor.steps. No guide curves or spline post-processing.',fill='#475563',font=font(16))
        canvas.save(a.out/(name+'-comparison.png'));records.append(row)
    (a.out/'comparison-data.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')
    html='<!doctype html><meta charset="utf-8"><title>Route richness evidence</title><style>body{margin:24px;background:#f6f7f8;font:16px sans-serif}img{width:100%;max-width:1600px;display:block;margin:24px 0}</style><h1>Route richness: authoritative geometry before / after</h1><p>Red lines are warnings, not hidden or removed from the measurements. See VALIDATION_V041_ZH.md for retained straight-run exceptions.</p>'
    for name in SCENES:html+=f'<h2>{name}</h2><img src="{name}-comparison.png" alt="{name} actual geometry comparison">'
    (a.out/'index.html').write_text(html,encoding='utf-8')
    print('Rendered',len(records),'real-geometry comparisons')

if __name__=='__main__':main()
