#!/usr/bin/env python3
"""Real local Python -> Java API checks for terrain controls; never mocks a response."""
from __future__ import annotations
import argparse,json,math,time,urllib.error,urllib.parse,urllib.request
from pathlib import Path


def main() -> None:
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--base',default='http://127.0.0.1:18767')
    ap.add_argument('--out',type=Path,default=Path('build/terrain-api'))
    args=ap.parse_args()
    if urllib.parse.urlparse(args.base).hostname not in {'127.0.0.1','localhost'}:
        raise ValueError('Local test fixture only')
    args.out.mkdir(parents=True,exist_ok=True);checks=[];responses=[]
    def fetch(endpoint,params=None,raw_query=None):
        if endpoint=='/api/plan' and raw_query is None:params={'layoutMode':'legacy',**(params or {})}
        query=raw_query if raw_query is not None else urllib.parse.urlencode(params or {})
        url=args.base+endpoint+('?' + query if query else '')
        start=time.perf_counter()
        try:r=urllib.request.urlopen(url,timeout=95)
        except urllib.error.HTTPError as error:r=error
        with r:
            raw=r.read(32*1024*1024+1)
            assert len(raw)<=32*1024*1024 and int(r.headers['Content-Length'])==len(raw)
            result=json.loads(raw)
            responses.append(dict(endpoint=endpoint,params=params,raw_query=raw_query,status=r.status,bytes=len(raw),seconds=round(time.perf_counter()-start,3)))
            return r.status,result
    def terrain(name,params):
        code,p=fetch('/api/terrain',params);assert code==200,(name,code,p)
        assert p['ok'] and p['kind']=='TERRAIN_ONLY'
        assert 'plan' not in p and 'constructed' not in p and 'search' not in p['metrics']
        assert 'planSeed' not in p['config'] and 'targetPlots' not in p['config']
        l=p['original'];n=p['config']['width']*p['config']['depth']
        assert len(l['heights'])==len(l['waters'])==len(l['materials'])==n
        assert p['metrics']['waterCells']==sum(w>=0 for w in l['waters'])
        assert p['metrics']['minSurface']==min(l['heights']) and p['metrics']['maxSurface']==max(l['heights'])
        assert max(l['heights'])<=p['config']['baseElevation']+p['config']['relief']
        assert len(l['voxels'])<=400000
        (args.out/(name+'.json')).write_text(json.dumps({'config':p['config'],'metrics':p['metrics']},ensure_ascii=False,indent=2),encoding='utf-8')
        print('PASS',name,flush=True);return p
    try:
        code,schema=fetch('/api/terrain-controls');assert code==200 and len(schema['controls'])==10
        for t in schema['types']:
            p=terrain('default-'+t['id'],{'width':64,'depth':96,'terrainType':t['id']})
            for s in schema['controls']:
                expected=s['default'][t['id']] if isinstance(s['default'],dict) else s['default']
                assert p['config']['terrainParameters'][s['key']]==expected
        checks.append('All four type defaults agree across schema, HTTP parser and Java')
        params=dict(width=192,depth=96,terrainType='valley',terrainSeed=42,baseElevation=58,relief=12,horizontalScale=1.6,detailStrength=.4,warpStrength=.8,slopeStrength=.5,waterLevelRatio=.12,treeDensity=.5,treeSpacing=6,ridgeSharpness=2,valleyWidth=1.3,terraceLevels=8)
        a=terrain('custom192x96',params);b=terrain('custom192x96-repeat',params)
        assert a==b;checks.append('Custom non-square terrain repeats exactly')
        large=terrain('custom512',dict(width=512,depth=512,terrainType='rolling_hills',terrainSeed=42,horizontalScale=1.7,detailStrength=.3,treeDensity=.4,waterLevelRatio=.35))
        checks.append('512 terrain-only payload obeys dimensions, byte and voxel bounds')
        lo=terrain('water-low',dict(width=192,depth=192,terrainType='valley',waterLevelRatio=.1))
        hi=terrain('water-high',dict(width=192,depth=192,terrainType='valley',waterLevelRatio=.6))
        assert lo['original']['heights']==hi['original']['heights']
        assert hi['metrics']['waterCells']>lo['metrics']['waterCells']
        checks.append('Raising water level changes real water, not ground heights')
        shared=dict(width=128,depth=128,terrainType='rolling_hills',terrainSeed=42,relief=12,horizontalScale=1.5,detailStrength=.5,treeDensity=.5,waterLevelRatio=0)
        pure=terrain('integration-original',shared)
        code,planned=fetch('/api/plan',{**shared,'planSeed':44,'targetPlots':3});assert code==200 and planned['metrics']['plotCount']==3
        assert planned['original']==pure['original'] and planned['config']['originalTerrainHash']==pure['config']['originalTerrainHash']
        code,roll=fetch('/api/plan',{**shared,'planSeed':45,'targetPlots':3});assert code==200 and roll['original']==pure['original']
        checks.append('Terrain-only, full planning and changed planSeed use identical original terrain')
        for spec in schema['controls']:
            key=spec['key'];bad=[spec['min']-1,spec['max']+1,'NaN','Infinity','-Infinity','']
            if spec['integer']:bad.append('4.5')
            for value in bad:
                code,p=fetch('/api/terrain',{key:value});assert code==400 and not p['ok'],(key,value,code)
        for endpoint,params,query in [('/api/terrain',{'targetPlots':7},None),('/api/terrain',{'notAParameter':1},None),('/api/terrain',None,'horizontalScale=1&horizontalScale=2'),('/api/plan',{'horizontalScale':'NaN'},None)]:
            code,p=fetch(endpoint,params,query);assert code==400 and not p['ok']
        checks.append('Bounds, nonfinite values, empty/duplicate/unknown keys and fractional integers are rejected')
        result=dict(passed=True,checks=checks,responses=responses)
    except Exception as exc:
        result=dict(passed=False,error=str(exc),checks=checks,responses=responses);raise
    finally:
        (args.out/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print('RESULT terrain API checks passed',flush=True)

if __name__=='__main__':main()
