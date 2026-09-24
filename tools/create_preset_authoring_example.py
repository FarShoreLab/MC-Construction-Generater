"""Rebuild the documented small compound example from two existing building models."""
import json
from pathlib import Path
from compile_building_preset import compile_preset

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'handoff/building-preset-standard-v1'

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    w,h,d=19,16,19
    palette={'.':'minecraft:air','g':'minecraft:grass_block','p':'minecraft:gravel'}
    parts=[];occupied=set()
    for ident,role,preset,ox,oz in [('house','main','timber_cottage',1,1),('store','annex','meadow_hut',12,1)]:
        b=json.loads((ROOT/f'core-planner/src/main/resources/presets/{preset}.json').read_text(encoding='utf-8'))
        remap={}
        for symbol,block in b['palette'].items():
            if block not in palette.values():
                key=next(c for c in 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789' if c not in palette)
                palette[key]=block
            remap[symbol]=next(k for k,v in palette.items() if v==block)
        rows=b['footprintMask']
        for z,row in enumerate(rows):
            for x,c in enumerate(row):
                if c=='#':occupied.add((ox+x,oz+z))
        layers=[[''.join(remap[c] for c in row) for row in layer] for layer in b['layers']]
        # All source models face south. Preserve their real two-high door into the yard.
        ex,ez=b['entrance']['x'],b['entrance']['z']
        for y in [1,2]:
            for z in [ez-1,ez]:
                row=list(layers[y][z]);row[ex]='.';layers[y][z]=''.join(row)
        parts.append(dict(id=ident,role=role,origin=[ox,oz],mask=rows,layers=layers))
    yard=[''.join('.' if (x,z) in occupied else '#' for x in range(w)) for z in range(d)]
    floor=[''.join('.' if (x,z) in occupied else 'p' if z>=12 or x==10 else 'g' for x in range(w)) for z in range(d)]
    parts.append(dict(id='yard',role='courtyard',origin=[0,0],mask=yard,layers=[floor]))
    source=dict(schemaVersion='preset-layout/1',id='example_cottage_store_court',name='木屋与仓库庭院示例',
        description='19×19：木屋主体、独立仓库、公共庭院；三处组团入口。',category='residential',tags=['compound','example'],
        sizeTier='medium',size=[w,h,d],reservedMask=['#'*w for _ in range(d)],groundSymbol='g',palette=palette,components=parts,
        entrances=[dict(x=10,z=18,facing='SOUTH'),dict(x=0,z=15,facing='WEST'),dict(x=18,z=15,facing='EAST')])
    native=compile_preset(source)
    for filename,data in [('example.layout.json',source),('example.native.json',native)]:
        (OUT/filename).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('PASS example compiled:',w,d)

if __name__=='__main__':main()
