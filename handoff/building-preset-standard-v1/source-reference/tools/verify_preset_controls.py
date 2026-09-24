"""Real HTTP roundtrip for custom building lists, entrances and archived presets."""
import argparse
from collections import Counter
import json
from pathlib import Path
import urllib.request
import urllib.error

def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--base',default='http://127.0.0.1:18771');ap.add_argument('--out',type=Path,default=Path('build/preset-controls-api'));args=ap.parse_args();args.out.mkdir(parents=True,exist_ok=True)
    results=[]
    def request(params):
        req=urllib.request.Request(args.base+'/api/plan',data=json.dumps(params).encode(),headers={'Content-Type':'application/json'})
        try:
            with urllib.request.urlopen(req,timeout=100) as response:return response.status,json.load(response)
        except urllib.error.HTTPError as error:return error.code,json.load(error)
    selection=[dict(presetId='meadow_hut',enabled=True,count=2),dict(presetId='village_bakery',enabled=True,count=1),dict(presetId='stone_bell_tower',enabled=False,count=3)]
    base=dict(width=128,depth=128,relief=4,treeDensity=0,waterLevelRatio=0,minBBoxCoverage=0,minMinorAxisRatio=0,autoDock=False,maxPlanAttempts=1,roadDirections=8,roadSurface='dirt_path',presetPalette='custom',targetPlots=3,buildingSelection=selection)
    for pref in ['clearings','balanced']:
        code,data=request(dict(base,sitePreference=pref));assert code==200 and data['metrics']['status']=='COMPLETE',(code,data)
        assert Counter(p['presetId'] for p in data['plan']['plots'])==Counter(meadow_hut=2,village_bakery=1)
        assert len(data['presets'])==34 and not any(p['id'].startswith('estate_') for p in data['presets'])
        assert all(p['entranceCount']>=1 for p in data['presets'])
        assert all(0<=p['entranceIndex']<4 and len(p['entrance'])==3 and p['entranceFacing'] in ['NORTH','SOUTH','EAST','WEST'] for p in data['plan']['plots'])
        assert data['config']['expertSettings']['buildingSelection']==selection
        assert data['config']['expertSettings']['sitePreference']==pref
        (args.out/f'{pref}.json').write_text(json.dumps(data,ensure_ascii=False),encoding='utf-8')
        results.append(dict(case=pref,status='PASS',plots=3));print('PASS custom',pref,flush=True)
    for size in [48,64,96]:
        code,data=request(dict(base,presetPalette='single',singlePresetId=f'estate_{size}',targetPlots=1,buildingSelection=[]));assert code==400 and 'ARCHIVED' in data['error'],data
        results.append(dict(case=f'archive_{size}',status='PASS'))
    code,data=request(dict(base,width=32,depth=32,targetPlots=64,buildingSelection=[dict(presetId='meadow_hut',enabled=True,count=64)]))
    assert code==200 and data['metrics']['status']=='REJECTED' and data['metrics']['constructionEdits']==0,(code,data)
    results.append(dict(case='custom_list_exceeds_map_capacity_no_partial_construction',status='PASS'))
    for params in [dict(targetPlots=4),dict(buildingSelection=[]),dict(sitePreference='invalid'),dict(buildingSelection=[dict(presetId='meadow_hut',count=1.5)]),dict(buildingSelection=[dict(presetId='meadow_hut',enabled=False,count=3)]),dict(layoutMode='legacy')]:
        code,data=request(dict(base,**params));assert code==400,(params,code,data)
        results.append(dict(case='invalid_'+str(params),status='PASS'))
    (args.out/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
    print('ALL PRESET CONTROL HTTP CHECKS PASSED')

if __name__=='__main__':main()
