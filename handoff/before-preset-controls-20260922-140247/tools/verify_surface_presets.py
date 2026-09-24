"""Real HTTP checks for estate capacity, road materials and farm construction."""
import argparse
import json
import urllib.error
import urllib.request
from pathlib import Path


def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--base',default='http://127.0.0.1:18770')
    ap.add_argument('--out',type=Path,default=Path('build/surface-preset-api'))
    args=ap.parse_args();args.out.mkdir(parents=True,exist_ok=True)
    results=[]
    def request(params):
        req=urllib.request.Request(args.base+'/api/plan',data=json.dumps(params).encode(),headers={'Content-Type':'application/json'})
        try:
            with urllib.request.urlopen(req,timeout=100) as response:return response.status,json.load(response)
        except urllib.error.HTTPError as error:return error.code,json.load(error)
    base=dict(width=128,depth=128,relief=4,treeDensity=0,waterLevelRatio=0,minBBoxCoverage=0,minMinorAxisRatio=0,autoDock=False,maxPlanAttempts=1,roadDirections=8)
    terrain=None;hashes=set()
    for material,voxel in [('dirt_path',16),('gravel',7),('cobblestone',8),('stone_bricks',9)]:
        code,data=request(dict(base,presetPalette='estates',targetPlots=7,roadSurface=material))
        assert code==200 and data['metrics']['status']=='COMPLETE',data
        assert data['config']['roadSurface']==material and len(data['presets'])==37
        layer=data['constructed'];width=data['config']['width']
        roads=[c for c in data['plan']['groundColumns'] if c['kind']=='road' and c['structure']=='surface']
        assert roads and all(layer['materials'][c['z']*width+c['x']]==voxel for c in roads)
        assert all(not p['presetId'].startswith('estate_') for p in data['plan']['plots'])
        assert any(a['type']=='farm_hut' for a in data['plan']['landUses'])
        assert any(c.get('surfaceMaterial')=='coarse_dirt' for c in data['plan']['groundColumns'])
        if terrain is None:terrain=data['config']['originalTerrainHash']
        assert terrain==data['config']['originalTerrainHash']
        assert data['metrics']['planHash'] not in hashes;hashes.add(data['metrics']['planHash'])
        results.append({'case':material,'status':'PASS'});print('PASS',material,flush=True)
    for size in [48,64,96]:
        code,data=request(dict(base,width=160,depth=160,presetPalette='single',singlePresetId=f'estate_{size}',targetPlots=1))
        assert code==200 and data['metrics']['status']=='COMPLETE',data
        plot=data['plan']['plots'][0]
        assert len(plot['footprint'])==size*size
        assert data['metrics']['constructionEdits']<=data['metrics']['search']['editLimit']
        results.append({'case':f'estate_{size}','status':'PASS','edits':data['metrics']['constructionEdits']});print('PASS estate',size,flush=True)
    code,data=request(dict(base,width=256,depth=256,presetPalette='estates',targetPlots=7))
    assert code==200 and data['metrics']['status']=='COMPLETE'
    assert sum(p['presetId'].startswith('estate_') for p in data['plan']['plots'])==1
    results.append({'case':'balanced256','status':'PASS'})
    code,data=request(dict(base,width=64,depth=64,presetPalette='single',singlePresetId='estate_96',targetPlots=1))
    assert code==200 and data['metrics']['status']=='REJECTED' and data['metrics']['constructionEdits']==0
    results.append({'case':'oversize-rejected-atomically','status':'PASS'})
    for params in [dict(base,roadSurface='lava'),dict(base,width=32,depth=32,presetPalette='estates',targetPlots=64)]:
        code,_=request(params);assert code==400,code
    results.append({'case':'invalid-material-and-map-capacity','status':'PASS'})
    (args.out/'results.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
    print('ALL SURFACE/PRESET API CHECKS PASSED')


if __name__=='__main__':main()
