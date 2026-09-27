"""Verify site-only planning and exact confirmed-site road generation using the real Java adapter."""
import json
from pathlib import Path
from urllib.parse import urlencode, urlparse
from preview_server import query_values, run_java

def plan(**params):
    params={k:json.dumps(v,separators=(',',':')) if isinstance(v,list) else str(v).lower() if isinstance(v,bool) else v for k,v in params.items()}
    return run_java(query_values(urlparse('/api/plan?'+urlencode(params))))

def pins(data):
    return [dict(id=p['id'],kind='building',presetId=p['presetId'],x=p['origin'][0],z=p['origin'][1],y=p['baseElevation'],facing=p['sourceFacing'],diagonal45=p['diagonal45'],entranceIndex=p['entranceIndex']) for p in data['plan']['plots']]

def signature(data):
    return [(p['presetId'],p['origin'],p['baseElevation'],p['sourceFacing'],p['diagonal45'],p['entranceIndex'],p['footprint']) for p in data['plan']['plots']]

def sites(**params):
    data=plan(planningStage='sites',**params)
    assert data['kind']=='SITES' and data['metrics']['status']=='SITES_READY', data['plan']['warnings']
    assert data['metrics']['search']['pathExpanded']==0 and data['metrics']['search']['siteLayoutsRouted']==0
    assert data['plan']['roadPaths']==[] and data['metrics']['roadColumnCount']==0
    assert data['metrics']['constructionEdits']==0 and data['original']==data['constructed']
    return data

def main():
    results=[]
    base=dict(presetPalette='estates',roadDirections=16,autoDock=False,maxPlanAttempts=3)
    initial=sites(**base);locked=pins(initial)
    road=plan(planningStage='roads',pins=locked,siteSeed=42,**base)
    assert road['metrics']['status']=='COMPLETE', road['plan']['sitePlanning']['attempts']
    assert signature(initial)==signature(road)
    assert road['metrics']['roadColumnCount']>0
    assert road['metrics']['search']['siteRepairsTried']==0
    results.append('PASS default: 7 sites, no routing/construction; roads preserve exact sites')
    edit=sites(pins=locked,**base)
    assert signature(initial)==signature(edit)
    broken=[dict(p) for p in locked];broken[0]['x']=127
    rejected=plan(planningStage='sites',pins=broken,**base)
    assert rejected['metrics']['status']=='REJECTED' and rejected['metrics']['constructionEdits']==0
    results.append('PASS all-site edit roundtrip and invalid footprint rejection')
    many=sites(width=256,depth=256,targetPlots=10,presetPalette='single',singlePresetId='square_cabin',diagonalBuildings=True,minBBoxCoverage=0,minMinorAxisRatio=0)
    assert any(p['diagonal45'] for p in many['plan']['plots']), 'No actual diagonal footprint covered'
    selections=[dict(presetId='square_cabin',count=10,enabled=True)]
    repeat=sites(width=256,depth=256,targetPlots=10,presetPalette='custom',buildingSelection=selections,pins=pins(many),diagonalBuildings=True,minBBoxCoverage=0,minMinorAxisRatio=0)
    assert signature(many)==signature(repeat)
    results.append('PASS 10 exact sites, custom list, entrance and diagonal footprint roundtrip')
    entrance=sites(targetPlots=1,presetPalette='single',singlePresetId='meadow_hut',relief=4,minBBoxCoverage=0,minMinorAxisRatio=0)
    entrance_pins=pins(entrance);entrance_pins[0]['entranceIndex']=1;entrance_pins[0]['y']=None
    entrance=sites(targetPlots=1,presetPalette='single',singlePresetId='meadow_hut',pins=entrance_pins,relief=4,minBBoxCoverage=0,minMinorAxisRatio=0)
    assert entrance['plan']['plots'][0]['entranceIndex']==1
    entrance_road=plan(planningStage='roads',targetPlots=1,presetPalette='single',singlePresetId='meadow_hut',pins=pins(entrance),relief=4,minBBoxCoverage=0,minMinorAxisRatio=0)
    assert entrance_road['metrics']['status']=='COMPLETE' and signature(entrance)==signature(entrance_road)
    results.append('PASS non-default entrance index preserved from sites through roads')
    custom=sites(targetPlots=3,presetPalette='custom',buildingSelection=[dict(presetId='square_cabin',enabled=True,count=2),dict(presetId='timber_cottage',enabled=True,count=1)],minBBoxCoverage=0,minMinorAxisRatio=0)
    assert sorted(p['presetId'] for p in custom['plan']['plots'])==['square_cabin','square_cabin','timber_cottage']
    try: plan(planningStage='roads',pins=locked[:1],**base)
    except RuntimeError as e: assert 'ROADS_REQUIRE_ALL_CONFIRMED_SITES_WITH_Y' in str(e)
    else: raise AssertionError('Incomplete confirmed sites accepted')
    results.append('PASS custom selection counts and incomplete road-input rejection')
    out=Path('build/verification/two-stage');out.mkdir(parents=True,exist_ok=True)
    (out/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
    for line in results: print(line)

if __name__=='__main__': main()
