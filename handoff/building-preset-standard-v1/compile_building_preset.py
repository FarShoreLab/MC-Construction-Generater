"""Validate preset-layout/1 and compile component layers to a native planner preset."""
import argparse
from collections import deque
import json
from pathlib import Path
import re


def require(condition,message):
    if not condition:raise ValueError(message)


def mask_rows(rows,w,d,label):
    require(isinstance(rows,list) and len(rows)==d,f'{label}: wrong depth')
    require(all(isinstance(row,str) and len(row)==w and set(row)<=set('.#') for row in rows),f'{label}: wrong row or character')
    return {(x,z) for z,row in enumerate(rows) for x,c in enumerate(row) if c=='#'}


def compile_preset(source):
    require(source.get('schemaVersion')=='preset-layout/1','schemaVersion must be preset-layout/1')
    allowed={'schemaVersion','id','name','description','category','tags','sizeTier','size','reservedMask','groundSymbol','palette','components','entrances'}
    require(not set(source)-allowed,'Unknown top-level fields: '+str(set(source)-allowed))
    require(re.fullmatch(r'[a-z][a-z0-9_]{0,63}',source['id']) is not None,'Invalid id')
    w,h,d=source['size'];require(all(type(v) is int for v in [w,h,d]) and 3<=w<=32 and 3<=d<=32 and 3<=h<=48,'size is [X,Y,Z]; active presets max X/Z=32')
    reserved=mask_rows(source['reservedMask'],w,d,'reservedMask');require(bool(reserved),'Empty footprint')
    seen={next(iter(reserved))};queue=deque(seen)
    while queue:
        x,z=queue.popleft()
        for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]:
            cell=x+dx,z+dz
            if cell in reserved and cell not in seen:seen.add(cell);queue.append(cell)
    require(seen==reserved,'Footprint must be four-connected')
    palette=source['palette'];require(palette.get('.')=='minecraft:air','Palette . must be minecraft:air')
    require(all(isinstance(k,str) and len(k)==1 and re.fullmatch(r'minecraft:[a-z0-9_]+(?:\[[a-z0-9_=,]+\])?',v) for k,v in palette.items()),'Invalid palette symbol or block ID')
    ground=source['groundSymbol'];require(ground in palette and palette[ground]!='minecraft:air','Ground must be non-air')
    layers=[[['.' for _ in range(w)] for _ in range(d)] for _ in range(h)]
    for x,z in reserved:layers[0][z][x]=ground
    parts=[];claimed=set();ids=set()
    for part in source['components']:
        require(not set(part)-{'id','role','origin','mask','layers'},'Unknown component field')
        require(part['id'] not in ids,'Duplicate component id');ids.add(part['id'])
        require(part['role'] in {'main','annex','courtyard','path','garden'},'Invalid component role')
        ox,oz=part['origin'];require(type(ox) is int and type(oz) is int,'Origin must be integer X/Z')
        rows=part['mask'];require(bool(rows) and isinstance(rows[0],str),'Empty component mask')
        pw,pd=len(rows[0]),len(rows);cells=mask_rows(rows,pw,pd,part['id'])
        require(bool(cells),'Empty component')
        absolute={(ox+x,oz+z) for x,z in cells}
        require(absolute<=reserved,'Component outside reserved footprint')
        require(not absolute&claimed,'Overlapping component masks');claimed.update(absolute)
        require(1<=len(part['layers'])<=h,'Component layer count out of range')
        for y,layer in enumerate(part['layers']):
            require(len(layer)==pd and all(len(row)==pw for row in layer),'Wrong component layer dimensions')
            for z,row in enumerate(layer):
                for x,symbol in enumerate(row):
                    require(symbol in palette,'Undefined symbol: '+symbol)
                    require((x,z) in cells or palette[symbol]=='minecraft:air','Voxel outside component mask')
                    if (x,z) in cells:layers[y][oz+z][ox+x]=symbol
        parts.append(dict(id=part['id'],role=part['role'],mask=[''.join('#' if (x,z) in absolute else '.' for x in range(w)) for z in range(d)]))
    require(any(p['role']=='main' for p in parts),'At least one main component required')
    require(claimed==reserved,'Components must classify every reserved cell, including courtyard/garden')
    entrances=source['entrances'];require(1<=len(entrances)<=8,'Provide 1–8 candidate entrances')
    seen_doors=set()
    directions={'NORTH':(0,-1),'EAST':(1,0),'SOUTH':(0,1),'WEST':(-1,0)}
    for door in entrances:
        require(set(door)=={'x','z','facing'},'Entrance keys must be x,z,facing')
        x,z,f=door['x'],door['z'],door['facing'];require(type(x) is int and type(z) is int and f in directions,'Invalid entrance')
        require((x,z,f) not in seen_doors,'Duplicate entrance');seen_doors.add((x,z,f))
        dx,dz=directions[f];require((x,z) in reserved and (x+dx,z+dz) not in reserved,'Entrance must face out of reserved mask')
        require(palette[layers[0][z][x]]!='minecraft:air','Entrance has no floor')
        require(all(palette[layers[y][z][x]]=='minecraft:air' for y in [1,2]),'Entrance needs two air blocks above floor')
    return dict(id=source['id'],name=source['name'],description=source.get('description',''),category=source['category'],
                tags=source.get('tags',[]),styles=['medieval_rustic'],author='Preset-layout/1',sizeTier=source.get('sizeTier','standard'),
                footprintShape='compound_small',sizeX=w,sizeY=h,sizeZ=d,footprintMask=source['reservedMask'],palette=palette,
                entrance=entrances[0],entrances=entrances,components=parts,themeReplacements={},
                layers=[[''.join(row) for row in layer] for layer in layers])


def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('input',type=Path);ap.add_argument('--output',type=Path)
    args=ap.parse_args();preset=compile_preset(json.loads(args.input.read_text(encoding='utf-8')))
    if args.output:
        args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(preset,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f"PASS {preset['id']}: {preset['sizeX']}x{preset['sizeZ']}, {len(preset['components'])} components, {len(preset['entrances'])} entrances")


if __name__=='__main__':main()
