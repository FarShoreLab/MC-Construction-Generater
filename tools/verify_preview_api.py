#!/usr/bin/env python3
"""End-to-end checks against the local real Python->Java preview API. No external network."""
from __future__ import annotations
import argparse, concurrent.futures, csv, gzip, hashlib, json, time, urllib.error, urllib.parse, urllib.request
from pathlib import Path

LIMIT=32*1024*1024

def main() -> None:
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--base',default='http://127.0.0.1:8766');ap.add_argument('--out',type=Path,default=Path('build/api-evidence'));args=ap.parse_args()
    parsed=urllib.parse.urlparse(args.base)
    if parsed.hostname not in {'127.0.0.1','localhost'}: raise ValueError('Only a local fixture may be tested')
    args.out.mkdir(parents=True,exist_ok=True);rows=[];checks=[]
    def fetch(params:dict) -> tuple[int,bytes,float]:
        begin=time.perf_counter();url=args.base+'/api/plan?'+urllib.parse.urlencode({'layoutMode':'legacy',**params})
        try: response=urllib.request.urlopen(url,timeout=95)
        except urllib.error.HTTPError as error: response=error
        with response:
            raw=response.read(LIMIT+1);assert len(raw)<=LIMIT,'Response exceeded 32 MiB'
            assert int(response.headers['Content-Length'])==len(raw),'Truncated or inconsistent response'
            return response.status,raw,time.perf_counter()-begin
    def run(name:str,params:dict) -> dict:
        status,raw,seconds=fetch(params);assert status==200,(name,status,raw[:300]);p=json.loads(raw);assert p['ok']
        conf=p['config'];m=p['metrics'];s=m['search'];cells=conf['width']*conf['depth']
        for layer in ['original','constructed']:
            assert len(p[layer]['heights'])==len(p[layer]['materials'])==len(p[layer]['waters'])==cells
            assert len(p[layer]['voxels'])<=400000
        assert s['candidateChecks']<=s['candidateLimit'] and s['pathExpanded']<=s['pathLimit']
        assert s['peakPathStates']<=s['stateLimit'] and s['peakOpenNodes']<=2*s['stateLimit']
        assert s['gradeRelaxations']<=s['gradeLimit'] and len(p['plan']['groundColumns'])<=s['columnLimit']
        assert m['constructionEdits']<=s['editLimit']
        row=dict(case=name,width=conf['width'],depth=conf['depth'],directions=conf['roadDirections'],diagonal=conf['diagonalBuildings'],requested=conf['targetPlots'],allocated=m['plotCount'],status=m['status'],http_seconds=round(seconds,3),planning_ms=round(s['elapsedNanos']/1e6,3),response_bytes=len(raw),stairs=m['stairBlocks'],min_platform=m['minPlatform'],max_platform=m['maxPlatform'],diagonal_plots=m['diagonalPlots'],edits=m['constructionEdits'],candidates=s['candidateChecks'],expanded=s['pathExpanded'],grade_work=s['gradeRelaxations'],exhausted='|'.join(s['exhaustedBudgets']))
        rows.append(row);print('PASS',name,row,flush=True)
        summary={'config':conf,'metrics':m,'plan':p['plan'],'original_extras':len(p['original']['voxels']),'constructed_extras':len(p['constructed']['voxels']),'response_bytes':len(raw),'response_sha256':hashlib.sha256(raw).hexdigest()}
        (args.out/(name+'.json')).write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
        if name in {'size512','reference512','reproduction512','loop128','mixed128'}:
            with gzip.open(args.out/(name+'-response.json.gz'),'wb') as f:f.write(raw)
        return p
    a=run('default128',{})
    repeat=run('repeat128',{});assert a['config']==repeat['config'] and a['plan']==repeat['plan'] and a['original']==repeat['original'] and a['constructed']==repeat['constructed'];checks.append('Repeated request is bit-equivalent excluding runtime metrics')
    roll=run('roll128',{'planSeed':43});assert a['config']['originalTerrainHash']==roll['config']['originalTerrainHash'] and a['original']==roll['original'];checks.append('Only planning seed changes: original terrain byte-structure unchanged')
    run('size256',{'width':256,'depth':256})
    run('size512',{'width':512,'depth':512})
    run('custom192x96',{'width':192,'depth':96,'roadDirections':12,'diagonalBuildings':'true','planSeed':43})
    ref=run('reference512',{'width':512,'depth':512,'terrainType':'mountain','relief':24,'presetPalette':'classic'})
    assert ref['metrics']['plotCount']==7 and ref['metrics']['network']['verifiedLoops']>=1 and ref['metrics']['network']['branchNodes']>=2
    repro=run('reproduction512',{'width':512,'depth':512,'terrainType':'rolling_hills','relief':24,'terrainSeed':42,'planSeed':45,'targetPlots':7,'roadDirections':12,'diagonalBuildings':'false','presetPalette':'mixed','singlePresetId':'square_cabin'})
    assert repro['metrics']['plotCount']==7 and repro['metrics']['network']['verifiedLoops']>=1
    assert repro['metrics']['network']['maximumStraightRun']<=16 and repro['metrics']['network']['microZigzagRatio']==0
    checks.append('Exact v0.4.1 rolling-hills reproduction: complete, physical loop and all straight runs <=16')
    loop=run('loop128',{'relief':4,'presetPalette':'classic'});assert loop['metrics']['network']['verifiedLoops']>=1
    mixed=run('mixed128',{'relief':12,'presetPalette':'mixed'});assert mixed['metrics']['plotCount']==7 and len(mixed['presets'])==22
    assert len({p['footprintShape'] for p in mixed['plan']['plots']})>=5
    single=run('single_courtyard',{'relief':4,'targetPlots':1,'presetPalette':'single','singlePresetId':'courtyard_house'})
    assert single['metrics']['plotCount']==1 and single['plan']['plots'][0]['presetId']=='courtyard_house'
    checks.append('22 real presets; mixed mask diversity; explicit courtyard; reference/fixture physical loops')
    low=run('budget_rejection' ,{'width':64,'depth':64,'groundColumns':1,'constructionEdits':1,'targetPlots':1});assert low['metrics']['plotCount']==0 and low['metrics']['constructionEdits']==0;checks.append('Tiny construction budget commits zero edits')
    for case in [{'terrainType':'classic'},{'width':513},{'depth':0},{'roadDirections':10},{'diagonalBuildings':'yes'},{'planSeed':9007199254740992},{'pathStates':200001},{'constructionEdits':1000001},{'width':'128.1'},{'unknownParameter':1},{'presetPalette':'silent_fallback'},{'singlePresetId':'unknown_preset'},{'singlePresetId':'../town_hall'},{'presetPalette':'single','singlePresetId':'/etc/passwd'}]:
        code,raw,_=fetch(case);assert code==400 and not json.loads(raw)['ok'],case
    checks.append('Fourteen invalid bounds/types/options rejected by HTTP 400')
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        first=pool.submit(fetch,{'width':512,'depth':512});time.sleep(.4)
        code,raw,_=fetch({'width':32,'depth':32});assert code==429,(code,raw[:100]);assert first.result()[0]==200
    checks.append('One active planner, concurrent request returns HTTP 429')
    with (args.out/'api-metrics.csv').open('w',newline='',encoding='utf-8') as f:
        writer=csv.DictWriter(f,fieldnames=rows[0]);writer.writeheader();writer.writerows(rows)
    (args.out/'api-checks.json').write_text(json.dumps({'passed':True,'checks':checks,'cases':rows},ensure_ascii=False,indent=2),encoding='utf-8')
    print('RESULT API checks passed',flush=True)

if __name__=='__main__': main()
