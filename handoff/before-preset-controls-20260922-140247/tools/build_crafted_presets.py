"""Rebuild the twelve authored voxel presets; no external assets or dependencies."""
import json
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / 'core-planner/src/main/resources/presets'
SPECS = [
    ('meadow_hut','草甸小屋',5,7,4,'rectangle','compact','residential'),
    ('timber_cottage','木构山墙屋',7,9,5,'rectangle','compact','residential'),
    ('village_bakery','村口面包坊',9,11,5,'rectangle','standard','commercial'),
    ('canal_rowhouse','沿街窄宅',7,15,9,'long_rectangle','standard','residential'),
    ('orchard_farmhouse','果园曲尺农舍',11,13,5,'l_shape','standard','residential'),
    ('artisan_court','工匠双翼作坊',15,13,6,'t_shape','standard','industrial'),
    ('courtyard_hostel','三翼庭院客栈',17,15,9,'u_shape','spacious','commercial'),
    ('cloister_market','回廊集市',19,17,5,'courtyard','spacious','commercial'),
    ('stone_bell_tower','石砌钟楼',9,9,14,'octagon','standard','landmark'),
    ('garden_manor','花园庄园',21,15,9,'cross','spacious','residential'),
    ('merchant_guild','商人会馆',23,19,10,'courtyard','spacious','civic'),
    ('harvest_barn','丰收谷仓',13,17,6,'rectangle','spacious','industrial'),
]

def build(spec):
    ident,name,w,d,wall,shape,tier,category=spec
    def occupied(x,z):
        if not (0<=x<w and 0<=z<d): return False
        if shape=='l_shape': return x<7 or z>=d-6
        if shape=='t_shape': return z<6 or abs(x-w//2)<=3
        if shape=='u_shape': return x<5 or x>=w-5 or z<5
        if shape=='courtyard': return x<5 or x>=w-5 or z<5 or z>=d-5
        if shape=='octagon': return min(x,w-1-x)+min(z,d-1-z)>=2
        if shape=='cross': return abs(x-w//2)<=4 or abs(z-d//2)<=3
        return True
    cells={(x,z) for z in range(d) for x in range(w) if occupied(x,z)}
    distance={p:0 for p in cells if any((p[0]+dx,p[1]+dz) not in cells for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)])}
    frontier=list(distance)
    for x,z in frontier:
        for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]:
            p=x+dx,z+dz
            if p in cells and p not in distance:distance[p]=distance[x,z]+1;frontier.append(p)
    door=(w//2,d-1) if (w//2,d-1) in cells else (2,d-1)
    roof_max=min(4,max(distance.values()));height=wall+roof_max+3
    grid=[[['.' for x in range(w)] for z in range(d)] for y in range(height)]
    for x,z in cells:
        grid[0][z][x]='S'
        boundary=distance[x,z]==0
        for y in range(1,wall+1):
            if y%5==0:grid[y][z][x]='W'
            elif boundary:
                post=(x%4==0 and z%4==0)
                grid[y][z][x]='L' if post else ('G' if y%5 in [2,3] else 'P')
            elif x%6==0 and z%6==0:grid[y][z][x]='L'
        ry=wall+1+min(distance[x,z],4)
        grid[ry][z][x]='R'
        if boundary:grid[wall][z][x]='L'
    # Clear a real two-block doorway; keep the floor and lintel in the mask.
    x,z=door
    grid[1][z][x]=grid[2][z][x]='.'
    grid[3][z][x]='L'
    # Masonry chimney adds a small silhouette above the roof, within its footprint.
    chimney=min(cells,key=lambda p:abs(p[0]-2)+abs(p[1]-2))
    x,z=chimney
    for y in range(wall,height):grid[y][z][x]='B'
    palette={'.':'minecraft:air','S':'minecraft:stone_bricks','W':'minecraft:oak_planks',
             'L':'minecraft:spruce_log','P':'minecraft:white_terracotta','G':'minecraft:glass_pane',
             'R':'minecraft:dark_oak_planks','B':'minecraft:bricks'}
    if ident in ['meadow_hut','timber_cottage','harvest_barn']:palette['P']='minecraft:oak_planks'
    if ident=='stone_bell_tower':palette['P']='minecraft:stone_bricks';palette['R']='minecraft:deepslate_tiles'
    if ident in ['garden_manor','merchant_guild']:palette['R']='minecraft:deepslate_tiles'
    obj=dict(id=ident,name=name,category=category,tags=[category,shape,tier,'crafted'],styles=['medieval_rustic'],
             author='MCSettlement',description=f'{w}×{d} 实际占地包络；分层坡屋顶、木构梁柱、窗带与石基。空缺占地保持原地形。',
             footprintShape=shape,sizeTier=tier,footprintMask=[''.join('#' if (x,z) in cells else '.' for x in range(w)) for z in range(d)],
             sizeX=w,sizeZ=d,sizeY=height,entrance=dict(facing='SOUTH',x=door[0],z=door[1]),palette=palette,
             themeReplacements={},layers=[[''.join(row) for row in layer] for layer in grid])
    (OUT/f'{ident}.json').write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def estate(size):
    ident=f'estate_{size}'
    names={48:'乡间庄园组团',64:'商贸庭院组团',96:'领主府邸组团'}
    height=22
    grid=[[['.' for _ in range(size)] for _ in range(size)] for _ in range(height)]
    palette={'.':'minecraft:air','g':'minecraft:grass_block','p':'minecraft:gravel','h':'minecraft:oak_leaves','f':'minecraft:stone_bricks'}
    for z in range(size):
        for x in range(size):
            grid[0][z][x]='p' if abs(x-size//2)<=2 or abs(z-size//2)<=2 else 'g'
            if (x in [0,size-1] or z in [0,size-1]) and not (z==size-1 and abs(x-size//2)<=2):
                grid[1][z][x]='f';grid[2][z][x]='h'
    placements=[('garden_manor',size//2-10,4),('harvest_barn',4,size-21),('timber_cottage',size-12,size-14)]
    if size>=64:placements += [('village_bakery',4,size//2-9),('artisan_court',size-20,size//2-11)]
    if size>=96:placements += [('merchant_guild',size//2-11,size//2+8),('orchard_farmhouse',size-18,8),('stone_bell_tower',4,8)]
    for source,ox,oz in placements:
        p=json.loads((OUT/f'{source}.json').read_text(encoding='utf-8'))
        symbols={}
        for key,block in p['palette'].items():
            if block not in palette.values():palette[chr(65+len(palette))]=block
            symbols[key]=next(k for k,v in palette.items() if v==block)
        for y,layer in enumerate(p['layers']):
            for z,row in enumerate(layer):
                for x,value in enumerate(row):
                    if p['footprintMask'][z][x]=='#':grid[y][oz+z][ox+x]=symbols[value]
        # Three-wide private path from each entrance to the central courtyard spine.
        ex=ox+p['entrance']['x'];ez=oz+p['entrance']['z']+1
        for x in range(min(ex,size//2),max(ex,size//2)+1):
            for z in range(ez,min(size,ez+3)):grid[0][z][x]='p'
    obj=dict(id=ident,name=f'{size}格·{names[size]}',category='government',tags=['government','compound','estate'],
        styles=['medieval_rustic'],author='MCSettlement',description=f'{size}×{size} 完整预留地块；主体、附属仓库、小屋、内部步道与绿地庭院。需连续平缓陆地。',
        footprintShape='compound',sizeTier='estate',footprintMask=['#'*size for _ in range(size)],
        sizeX=size,sizeZ=size,sizeY=height,entrance=dict(facing='SOUTH',x=size//2,z=size-1),
        palette=palette,themeReplacements={},layers=[[''.join(row) for row in layer] for layer in grid])
    (OUT/f'{ident}.json').write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

if __name__=='__main__':
    for spec in SPECS:build(spec)
    for size in [48,64,96]:estate(size)
    print('Built 12 single-building and 3 estate presets')
