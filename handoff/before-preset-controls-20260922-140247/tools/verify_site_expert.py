#!/usr/bin/env python3
"""Real HTTP/Java verification and independent building-only coverage measurements."""
from __future__ import annotations
import argparse, concurrent.futures, gzip, json, math, time, urllib.request, urllib.error, urllib.parse
from pathlib import Path

def fetch(base, params, method='POST'):
    if method=='POST': req=urllib.request.Request(base+'/api/plan',data=json.dumps(params,ensure_ascii=False).encode(),headers={'Content-Type':'application/json'},method='POST')
    else: req=urllib.request.Request(base+'/api/plan?'+urllib.parse.urlencode({k:json.dumps(v) if isinstance(v,(list,dict,bool)) else v for k,v in params.items()}))
    start=time.perf_counter()
    try:r=urllib.request.urlopen(req,timeout=100)
    except urllib.error.HTTPError as e:r=e
    with r:
        raw=r.read(32*1024*1024+1);assert len(raw)<=32*1024*1024
        return r.status,json.loads(raw),raw,time.perf_counter()-start

def measure(p):
    w,d=p['config']['width'],p['config']['depth'];centers=[];cells=[]
    for b in p['plan']['plots']:
        origin=b['origin'];c=[(origin[0]+v[0],origin[1]+v[1]) for v in b['footprint']];cells.extend(c)
        x,z=zip(*c);centers.append(((min(x)+max(x))/2/w,(min(z)+max(z))/2/d))
    if not cells:return {'bbox':0,'minor':0,'hull':0,'bounds':None}
    x,z=zip(*cells);bounds=[min(x),min(z),max(x),max(z)];bbox=(max(x)-min(x)+1)*(max(z)-min(z)+1)/w/d
    mx=sum(a for a,b in centers)/len(centers);mz=sum(b for a,b in centers)/len(centers)
    xx=sum((a-mx)**2 for a,b in centers);zz=sum((b-mz)**2 for a,b in centers);xz=sum((a-mx)*(b-mz) for a,b in centers);delta=math.sqrt((xx-zz)**2+4*xz*xz);major=xx+zz+delta;minor=math.sqrt(max(0,xx+zz-delta)/major) if major>1e-12 else 0
    def cross(o,a,b):return (a[0]-o[0])*(b[1]-o[1])-(a[1]-o[1])*(b[0]-o[0])
    pts=sorted(set(centers));lower=[];upper=[]
    for a in pts:
        while len(lower)>=2 and cross(lower[-2],lower[-1],a)<=0:lower.pop()
        lower.append(a)
    for a in reversed(pts):
        while len(upper)>=2 and cross(upper[-2],upper[-1],a)<=0:upper.pop()
        upper.append(a)
    h=lower[:-1]+upper[:-1];area=abs(sum(h[i][0]*h[(i+1)%len(h)][1]-h[i][1]*h[(i+1)%len(h)][0] for i in range(len(h))))/2 if h else 0
    return {'bbox':bbox,'minor':minor,'hull':area,'bounds':bounds}

def verify(p):
    m=p['metrics'];s=m['search'];assert s['candidateChecks']<=s['candidateLimit'];assert s['pathExpanded']<=s['pathLimit'];assert s['gradeRelaxations']<=s['gradeLimit'];assert s['peakPathStates']<=s['stateLimit'];assert s['peakOpenNodes']<=2*s['stateLimit'];assert len(p['plan']['groundColumns'])<=s['columnLimit'];assert m['constructionEdits']<=s['editLimit']
    if m['status']=='COMPLETE' and p['config']['layoutMode']=='expert':
        a=measure(p);q=m['sitePlanning'];assert p['schemaVersion']=='0.5.0';assert m['plotCount']==p['config']['targetPlots'];assert q['status']=='ACCEPTED'
        for k,v in [('bbox','bboxCoverage'),('minor','minorAxisRatio'),('hull','hullCoverage')]:assert abs(a[k]-q[v])<1e-9,(k,a[k],q[v])
        assert a['bbox']+1e-9>=q['minBBoxCoverage'];assert a['bounds']==q['bounds']
        if m['plotCount']>=3:assert a['minor']+1e-9>=q['minMinorAxisRatio'] and a['hull']+1e-9>=q['minHullCoverage']
        original=p['original'];w=p['config']['width'];bed=original['heights'];waters=original['waters']
        for c in p['plan']['groundColumns']:
            index=c['z']*w+c['x'];assert c['originalY']==bed[index]
            if c['structure'] in ['bridge','deck']:
                assert c['targetY']==c['waterY']+1 and c['waterY']==waters[index] and c['waterY']>bed[index]
                assert c['support']==((c['x']+c['z'])%4==0)
    elif m['status'] not in ['COMPLETE','PARTIAL']:
        assert m['constructionEdits']==0 and not p['plan']['groundColumns'] and not p['plan']['plots'];assert p['original']==p['constructed']

def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--base',default='http://127.0.0.1:18767');ap.add_argument('--out',type=Path,default=Path('build/site-api'));a=ap.parse_args();assert urllib.parse.urlparse(a.base).hostname in ('127.0.0.1','localhost');a.out.mkdir(parents=True,exist_ok=True)
    results={'checks':[],'comparisons':[]};old=None
    scenes=[('rolling512_p45',dict(width=512,depth=512,planSeed=45,presetPalette='mixed')),('rolling512_p48',dict(width=512,depth=512,planSeed=48,presetPalette='mixed')),('reproduction512_12',dict(width=512,depth=512,planSeed=45,presetPalette='mixed',roadDirections=12)),('mountain512',dict(width=512,depth=512,terrainType='mountain',presetPalette='classic')),('mixed128',dict(relief=12,presetPalette='mixed')),('custom192x96',dict(width=192,depth=96,planSeed=43,roadDirections=12,diagonalBuildings=True,presetPalette='mixed'))]
    for name,params in scenes:
        row={'case':name,'params':params}
        for mode in ['legacy','expert']:
            code,p,raw,seconds=fetch(a.base,{**params,'layoutMode':mode});assert code==200,(name,mode,code,p);verify(p)
            with gzip.open(a.out/(name+'-'+mode+'.json.gz'),'wb') as f:f.write(raw)
            row[mode]={'status':p['metrics']['status'],'buildings':p['metrics']['plotCount'],**measure(p),'paths':p['metrics']['search']['pathExpanded'],'columns':len(p['plan']['groundColumns']),'edits':p['metrics']['constructionEdits'],'seconds':seconds,'planHash':p['config']['planHash'] if 'planHash' in p['config'] else p['metrics']['planHash'],'terrainHash':p['config']['originalTerrainHash'],'bytes':len(raw)}
            if mode=='legacy':old=p
            else:
                assert p['original']==old['original'] and p['config']['originalTerrainHash']==old['config']['originalTerrainHash'];row['identicalTerrain']=True;row[mode]['sitePlanning']=p['metrics']['sitePlanning']
                code,repeated,raw2,_=fetch(a.base,{**params,'layoutMode':mode});assert code==200;assert repeated['plan']==p['plan'] and repeated['original']==p['original'] and repeated['constructed']==p['constructed'];row['deterministicRepeat']=True
        results['comparisons'].append(row);(a.out/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2));print('PASS',name,row['legacy']['status'],round(row['legacy']['bbox'],4),'->',row['expert']['status'],round(row['expert']['bbox'],4),flush=True)
    results['checks'].append('Six same-terrain legacy/expert pairs, independent bbox/PCA/hull, exact expert repeat, actual construction and all budgets')
    code,post,_,_=fetch(a.base,{'relief':4});assert code==200;code,get,_,_=fetch(a.base,{'relief':4},'GET');assert code==200 and get['plan']==post['plan'];results['checks'].append('GET/POST produce identical real plans; expert default')
    pins=[{'id':'home','x':20,'z':20,'facing':'EAST','connectTo':'other'},{'id':'other','x':95,'z':85,'facing':'WEST'}]
    recipe={'width':128,'depth':128,'relief':4,'waterLevelRatio':0,'pins':pins,'presetPalette':'mixed'}
    for seed in [42,43]:
        code,p,raw,_=fetch(a.base,{**recipe,'planSeed':seed});assert code==200;verify(p);assert p['metrics']['status']=='COMPLETE',(p['metrics']['status'],p['metrics']['sitePlanning'])
        for pin in pins:assert next(b for b in p['plan']['plots'] if b['requirementId']==pin['id'])['origin']==[pin['x'],pin['z']]
        with gzip.open(a.out/f'manual-pins-{seed}.json.gz','wb') as f:f.write(raw)
    (a.out/'manual-pins-request.json').write_text(json.dumps(recipe,ensure_ascii=False,indent=2));results['checks'].append('Two real manual pins retain exact origins across planSeed; named forced connection verified')
    for name,params in [('impossibleCoverage',{'width':64,'depth':64,'targetPlots':1,'minBBoxCoverage':.85}),('lowBudget',{'pathExpanded':1}),('overlap',{'pins':[{'id':'a','x':20,'z':20},{'id':'b','x':20,'z':20}]})]:
        code,p,raw,_=fetch(a.base,params);assert code==200;verify(p);assert p['metrics']['status']!='COMPLETE';(a.out/(name+'.json')).write_bytes(raw)
    results['checks'].append('Impossible coverage, overlapping exact pins and tiny path budget reject with unchanged actual voxel world')
    invalid=[{'layoutMode':'bad'},{'minBBoxCoverage':-.1},{'minBBoxCoverage':.86},{'minMinorAxisRatio':.91},{'maxBridgeSpan':3},{'maxBridgeSpan':4.5},{'allowBridges':'yes'},{'pins':{}},{'pins':[{'id':'a','x':1.5,'z':2}]},{'pins':[{'id':'a','x':1,'z':2,'bad':1}]},{'pins':[{'id':'a','x':1,'z':2,'connectTo':'bad'}]},{'pins':[{'id':'a','x':1,'z':2,'presetId':'bad'}]},{'pins':[{'id':'a','x':512,'z':2}]},{'layoutMode':'legacy','pins':[{'id':'a','x':1,'z':2}]},{'allowBridges':False,'pins':[{'id':'pier','kind':'dock','x':1,'z':2}]},{'pins':[{'id':'a','x':1,'z':2},{'id':'a','x':3,'z':4}]}]
    for params in invalid:
        code,p,_,_=fetch(a.base,params);assert code==400 and not p['ok'],(params,code,p)
    for raw in [b'{"width":128,"width":256}',b'{"pins":[{"id":"a","x":1,"x":2,"z":3}]}']:
        req=urllib.request.Request(a.base+'/api/plan',data=raw,headers={'Content-Type':'application/json'})
        try:r=urllib.request.urlopen(req,timeout=95)
        except urllib.error.HTTPError as e:r=e
        with r:assert r.status==400
    results['checks'].append('Sixteen invalid expert constraints plus duplicate top-level/pin keys rejected with HTTP400')
    req=urllib.request.Request(a.base+'/api/plan',data=b' '*16385,headers={'Content-Type':'application/json'})
    try:r=urllib.request.urlopen(req,timeout=95)
    except urllib.error.HTTPError as e:r=e
    with r:assert r.status==413
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        f=pool.submit(fetch,a.base,dict(width=512,depth=512));time.sleep(.35);code,p,_,_=fetch(a.base,dict(width=32,depth=32));assert code==429;assert f.result()[0]==200
    results['checks'].append('16KiB POST payload cap and one-active-request HTTP429 preserved')
    results['passed']=True;(a.out/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2));print('ALL EXPERT API CHECKS PASSED',flush=True)
if __name__=='__main__':main()
