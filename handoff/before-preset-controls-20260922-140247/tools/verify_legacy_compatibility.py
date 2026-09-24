#!/usr/bin/env python3
"""Compare untouched v0.4.2 compiled code with explicit legacy mode on identical inputs."""
import argparse,gzip,json,os,subprocess
from pathlib import Path

def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--baseline-source',type=Path,required=True);ap.add_argument('--comparisons',type=Path,required=True);ap.add_argument('--out',type=Path,default=Path('build/legacy-compatibility'));a=ap.parse_args();a.out.mkdir(parents=True,exist_ok=True)
    jar=Path(os.environ['GSON_JAR']);base=a.baseline_source.resolve();cp=os.pathsep.join([str(base/'core-planner/build/classes/java/main'),str(jar)]);rows=json.loads((a.comparisons/'results.json').read_text())['comparisons'];results=[]
    for row in rows:
        params=row['params'];args=['java','-Xmx1g','-cp',cp,'org.mcsettlement.planner.simulation.SimulationApiRunner']+['--'+k+'='+str(v).lower() if isinstance(v,bool) else '--'+k+'='+str(v) for k,v in params.items()]
        raw=subprocess.run(args,cwd=base,capture_output=True,timeout=95,check=True);p=json.loads(raw.stdout);new=json.loads(gzip.decompress((a.comparisons/(row['case']+'-legacy.json.gz')).read_bytes()))
        assert p['config']['originalTerrainHash']==new['config']['originalTerrainHash'];assert p['metrics']['planHash']==new['metrics']['planHash'];assert p['original']==new['original'] and p['constructed']==new['constructed'];assert p['plan']['plots']==new['plan']['plots']
        (a.out/(row['case']+'.stderr.log')).write_bytes(raw.stderr)
        results.append({'case':row['case'],'planHash':p['metrics']['planHash'],'terrainHash':p['config']['originalTerrainHash'],'identicalOriginalAndConstructedLayers':True,'identicalPlots':True});print('PASS untouched baseline',row['case'],flush=True)
    (a.out/'results.json').write_text(json.dumps({'passed':True,'comparisons':results},indent=2))
if __name__=='__main__':main()
