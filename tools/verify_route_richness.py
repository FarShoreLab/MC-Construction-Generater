#!/usr/bin/env python3
"""Replay five real pipeline scenes, with an independent centerline metric audit.

Uses the same RouteRichnessExport.java against either --source-root snapshot.
Needs build_offline.py --test first; never downloads dependencies. --repeat checks
plan/terrain/constructed-world hashes and byte-structured route diagnostics.
"""
from __future__ import annotations
import argparse, itertools, json, math, os, subprocess, time
from pathlib import Path
from build_offline import ROOT, gson_jar

SCENES = {
    'reference512': [512,512,24,'mountain',42,42,7,8,False,'classic','square_cabin'],
    'reproduction512': [512,512,24,'rolling_hills',42,45,7,12,False,'mixed','square_cabin'],
    'mixed128': [128,128,12,'rolling_hills',42,42,7,8,False,'mixed','square_cabin'],
    'loop128': [128,128,4,'rolling_hills',42,42,7,8,False,'classic','square_cabin'],
    'custom192x96': [192,96,24,'rolling_hills',42,43,7,12,True,'classic','square_cabin'],
}

def shape_metrics(steps: list[dict]) -> dict:
    dirs=[(b['x']-a['x'],b['z']-a['z']) for a,b in zip(steps,steps[1:])]
    runs=[(direction,len(list(values))) for direction,values in itertools.groupby(dirs)]
    lengths=[math.hypot(*direction)*n for direction,n in runs]
    total=sum(lengths); position=last=0.; spacings=[]; bends=0
    for i,((_,n),length) in enumerate(zip(runs,lengths)):
        position+=length
        if i+1<len(runs) and n>=3 and runs[i+1][1]>=3:
            bends+=1;spacings.append(position-last);last=position
    if len(steps)>=2: spacings.append(total-last)
    direct=math.hypot(steps[-1]['x']-steps[0]['x'],steps[-1]['z']-steps[0]['z']) if steps else 0
    zig=sum(dirs[i]==dirs[i+2] and dirs[i+1]==dirs[i+3] and dirs[i]!=dirs[i+1] for i in range(max(0,len(dirs)-3)))
    rhythm=sum(all(dirs[i+j]==dirs[i+(0 if (j//k)%2==0 else k)] for j in range(4*k)) and dirs[i]!=dirs[i+k]
        for k in [2,3] for i in range(max(0,len(dirs)-4*k+1)))
    return dict(rhythmZigzagWindows=rhythm,stepCount=len(dirs),maxStraightRun=max((n for _,n in runs),default=0),
        rawTurns=max(0,len(runs)-1),effectiveBends=bends,shortRuns=sum(n<3 for _,n in runs),
        length=total,endpointDistance=direct,sinuosity=total/direct if direct else 0,
        maxStraightLength=max(lengths,default=0),longStraightFraction=sum(l for (_,n),l in zip(runs,lengths) if n>16)/total if total else 0,
        microZigzagWindows=zig,microZigzagRatio=zig/max(1,len(dirs)-3),
        straightRunSteps=[n for _,n in runs],bendSpacings=spacings,meanBendSpacing=total/len(spacings) if spacings else 0)

def audit_metrics(plan:dict) -> dict:
    routes=[]
    for route in plan['transportNetwork']['corridors']:
        q=shape_metrics(route['steps']);stored=route.get('quality')
        if stored:
            for k,v in q.items():
                actual=stored[k]
                if isinstance(v,(float,int)): assert math.isclose(v,actual,rel_tol=1e-9,abs_tol=1e-9),(route['id'],k,v,actual)
                elif isinstance(v,list):
                    assert len(v)==len(actual) and all(math.isclose(a,b,rel_tol=1e-9,abs_tol=1e-9) for a,b in zip(v,actual)),(route['id'],k)
        routes.append(dict(id=route['id'],role=route['roadType'],style=route['routingStyle'],candidate=route.get('guideCandidateId'),fallbackReason=route.get('fallbackReason'),**q))
    return dict(maximumStraightRun=max((r['maxStraightRun'] for r in routes),default=0),
        collectorMaxStraightRun=max((r['maxStraightRun'] for r in routes if r['role']=='collector'),default=0),
        longStraightWarnings=[r['id'] for r in routes if r['maxStraightRun']>16],
        microZigzagWindows=sum(r['microZigzagWindows'] for r in routes),rhythmZigzagWindows=sum(r['rhythmZigzagWindows'] for r in routes),routes=routes)

def main()->int:
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--source-root',type=Path,default=ROOT)
    ap.add_argument('--out',type=Path,default=ROOT/'build/route-richness')
    ap.add_argument('--repeat',action='store_true')
    ap.add_argument('--case',choices=SCENES,action='append')
    args=ap.parse_args();args.source_root=args.source_root.resolve();args.out=args.out.resolve();args.out.mkdir(parents=True,exist_ok=True)
    main_classes=args.source_root/'core-planner/build/classes/java/main';test_classes=args.source_root/'core-planner/build/classes/java/offlineTest'
    if not (test_classes/'org/mcsettlement/planner/regression/TerrainAudit.class').is_file(): raise RuntimeError('Run tools/build_offline.py --test in the chosen source snapshot first.')
    harness=args.out/'harness-classes';harness.mkdir(exist_ok=True)
    cp=os.pathsep.join(map(str,[main_classes,test_classes,gson_jar(),harness]))
    compiler=subprocess.run(['javac','--release','21','-encoding','UTF-8','-cp',cp,'-d',str(harness),str(ROOT/'tools/RouteRichnessExport.java')],capture_output=True,timeout=90)
    (args.out/'compile-harness.log').write_bytes(compiler.stdout+compiler.stderr)
    if compiler.returncode: raise RuntimeError('Evidence harness compile failed')
    results=[]
    for name in args.case or SCENES:
        params=SCENES[name];out=args.out/name
        for repeat in range(2 if args.repeat else 1):
            dest=out if repeat==0 else out/'repeat';dest.mkdir(parents=True,exist_ok=True);start=time.perf_counter()
            command=['java','-Xmx1g','-cp',cp,'RouteRichnessExport',str(dest),*[str(x).lower() if isinstance(x,bool) else str(x) for x in params]]
            with (dest/'execution.log').open('wb') as log:
                try:code=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=90).returncode
                except subprocess.TimeoutExpired:code=124
            row=dict(scene=name,repeat=repeat,exit_code=code,wall_seconds=round(time.perf_counter()-start,3));results.append(row)
            if code: print('FAIL',row,flush=True);continue
            s=json.loads((dest/'summary.json').read_text());plan=json.loads((dest/'PlanningIR.json').read_text());q=audit_metrics(plan)
            (dest/'route-metrics.json').write_text(json.dumps(q,ensure_ascii=False,indent=2),encoding='utf-8')
            search=s['search'];assert search['pathExpanded']<=search['pathLimit'] and search['candidateChecks']<=search['candidateLimit']
            assert search['gradeRelaxations']<=search['gradeLimit'] and search['peakPathStates']<=search['stateLimit']
            assert search['peakOpenNodes']<=search['stateLimit']*2 and len(plan['groundColumns'])<=search['columnLimit']
            assert s['constructionEdits']<=search['editLimit'] and search['topologyEdgesTried']<=128
            assert s['status']=='COMPLETE' and s['plots']==7,(name,'fixture completeness')
            assert q['microZigzagWindows']==0,(name,'ABAB micro noise')
            if name in {'reference512','reproduction512','loop128'}:assert s['network']['verifiedLoops']>=1,(name,'physical loop')
            if s['network'].get('routeQualityVersion',0):
                for route in plan['transportNetwork']['corridors']:
                    if route['quality']['maxStraightRun']>16:assert route['quality'].get('straightException'),(name,'unexplained straight warning')
                    if route['routingStyle'] in {'conservative_guided','constrained_fallback'}:assert route.get('fallbackReason'),(name,'silent fallback')
                if name=='reproduction512':assert q['maximumStraightRun']<=16 and q['rhythmZigzagWindows']==0
                if name=='reference512':assert sum(r['maxStraightRun']<=16 and r['effectiveBends']>=2 for r in q['routes'] if r['role']=='collector')>=2
            row['qualityGate']='WARN_RETAINED_LONG_STRAIGHT' if q['maximumStraightRun']>16 else 'PASS_NO_LONG_STRAIGHT'
            row.update(status=s['status'],plots=s['plots'],network=s['network']['status'],loops=s['network']['verifiedLoops'],
                expanded=search['pathExpanded'],collectorMaxRun=q['collectorMaxStraightRun'],maxRun=q['maximumStraightRun'],microWindows=q['microZigzagWindows'],rhythmWindows=q['rhythmZigzagWindows'],planHash=s['planHash'])
            if repeat:
                first=json.loads((out/'summary.json').read_text());first_plan=json.loads((out/'PlanningIR.json').read_text())
                for k in ['planHash','originalTerrainHash','constructedWorldHash']:assert s[k]==first[k],(name,k)
                for k in ['transportNetwork','plots','groundColumns']:assert plan[k]==first_plan[k],(name,k)
                row['deterministic']=True
            print('PASS',json.dumps(row),flush=True)
    report={'sceneParameters':SCENES,'results':results,'allExecutionsPassed':all(r['exit_code']==0 for r in results),
        'referenceInputNote':'classic is a preset palette, not a terrain type; reference512 uses mountain as in supplied baseline JSON.'}
    (args.out/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    return 0 if report['allExecutionsPassed'] else 1

if __name__=='__main__':raise SystemExit(main())
