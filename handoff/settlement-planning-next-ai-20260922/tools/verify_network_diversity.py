#!/usr/bin/env python3
"""Real local HTTP/API acceptance for v0.5.1; saves requests and compressed real responses."""
from __future__ import annotations
import argparse, gzip, json, time, urllib.request, urllib.error, urllib.parse
from pathlib import Path

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--base',default='http://127.0.0.1:18767');p.add_argument('--out',type=Path,default=Path('build/network-api'));a=p.parse_args()
    a.out.mkdir(parents=True,exist_ok=True);report={'passed':False,'scenes':[],'checks':[]}
    def request(params, method='POST',status=200):
        payload=json.dumps(params).encode(); url=a.base+'/api/plan'
        if method=='GET':url+='?'+urllib.parse.urlencode({k:str(v).lower() if isinstance(v,bool) else v for k,v in params.items()})
        req=urllib.request.Request(url,data=payload if method=='POST' else None,headers={'Content-Type':'application/json'},method=method)
        try:r=urllib.request.urlopen(req,timeout=100)
        except urllib.error.HTTPError as e:r=e
        with r:raw=r.read();actual=r.status
        assert actual==status,(params,actual,raw[:1500])
        return json.loads(raw),raw
    def stripped(data):
        if isinstance(data,dict):return {k:stripped(v) for k,v in data.items() if k not in {'elapsedNanos'}}
        if isinstance(data,list):return [stripped(v) for v in data]
        return data
    try:
        scenes=[('screen256_p54_9',256,256,54,9,8,24,'rolling_hills'),('failure512_p41_9',512,512,41,9,8,24,'rolling_hills'),('failure512_p50_9',512,512,50,9,8,24,'rolling_hills'),('mixed256_p54_9',256,256,54,9,16,24,'rolling_hills'),('mixed512_p54_9',512,512,54,9,16,24,'rolling_hills'),('large512_p54_20',512,512,54,20,16,24,'rolling_hills'),('plateau128_p42_7',128,128,42,7,16,24,'plateau'),('plateau512_p54_20',512,512,54,20,16,24,'plateau'),('rect192x96_mixed',192,96,43,7,16,24,'rolling_hills'),('legacy12_expert',128,128,42,7,12,12,'rolling_hills')]
        for name,w,d,seed,count,directions,relief,terrain in scenes:
            params={'width':w,'depth':d,'terrainSeed':42,'terrainType':terrain,'relief':relief,'planSeed':seed,'targetPlots':count,'roadDirections':directions,'presetPalette':'mixed','layoutMode':'expert','buildingRepulsion':1,'roadMergeDistance':7}
            start=time.perf_counter();data,raw=request(params);elapsed=time.perf_counter()-start
            m=data['metrics'];s=m['search'];n=m['network'];site=m['sitePlanning']
            assert m['status']=='COMPLETE' and m['plotCount']==count,(name,m['status'],site)
            assert m['constructionEdits']>0 and s['pathExpanded']<=s['pathLimit'] and s['candidateChecks']<=s['candidateLimit'] and s['gradeRelaxations']<=s['gradeLimit']
            assert site['bboxCoverage']>=.25 and site['minorAxisRatio']>=.3
            if count==9:assert site['closePairs']==0
            if w==256:assert site['macroLoops']>0
            if directions==16:assert n['diagonal45Steps']>0 and n['obliqueSteps']>0
            repeat,_=request(params,'GET');assert stripped(data)==stripped(repeat),name+' GET/POST/repeat mismatch'
            gzip.open(a.out/(name+'.json.gz'),'wb').write(raw)
            row={'name':name,'params':params,'seconds':round(elapsed,3),'status':m['status'],'plots':m['plotCount'],'planHash':m['planHash'],'bboxCoverage':site['bboxCoverage'],'closePairs':site['closePairs'],'macroLoops':site['macroLoops'],'pathExpanded':s['pathExpanded'],'edits':m['constructionEdits'],'cardinalSteps':n['cardinalSteps'],'diagonal45Steps':n['diagonal45Steps'],'obliqueSteps':n['obliqueSteps'],'repeatEqual':True}
            report['scenes'].append(row);print('PASS',name,flush=True)
        for params in [{'buildingRepulsion':-1},{'buildingRepulsion':3.01},{'buildingRepulsion':'NaN'},{'buildingRepulsion':'Infinity'},{'roadMergeDistance':-1},{'roadMergeDistance':17},{'roadMergeDistance':1.5},{'roadDirections':20},{'roadDirections':9},{'buildingRepulsion':''}]:
            request(params,status=400);report['checks'].append({'rejected':params})
        for params in [{'buildingRepulsion':0,'roadMergeDistance':0},{'buildingRepulsion':3,'roadMergeDistance':16}]:
            data,_=request(params);assert data['config']['expertSettings']['buildingRepulsion']==params['buildingRepulsion'];assert data['config']['expertSettings']['roadMergeDistance']==params['roadMergeDistance'];report['checks'].append({'boundsAccepted':params})
        data,_=request({'width':512,'depth':512,'roadDirections':16,'targetPlots':9,'pathExpanded':1})
        assert data['metrics']['status']=='REJECTED' and data['metrics']['plotCount']==0 and data['metrics']['constructionEdits']==0 and not data['plan']['groundColumns']
        report['checks'].append('Tiny shared path budget rejects atomically, without minting budget or fake buildings')
        report['passed']=True
    finally:(a.out/'results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print('ALL NETWORK API CHECKS PASSED')
if __name__=='__main__':main()
