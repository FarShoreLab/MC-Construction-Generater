#!/usr/bin/env python3
"""Export reproducible scenes after build_offline.py --test. No network required."""
import argparse,os,subprocess
from pathlib import Path
from build_offline import ROOT, MAIN, TEST, gson_jar

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=ROOT/'build/release-scenes');a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
    cp=os.pathsep.join(map(str,[MAIN,TEST,gson_jar()]))
    scenes=[('reference',512,512,24,'mountain',42,42,7,'classic'),('loop',128,128,4,'rolling_hills',42,42,7,'classic'),('mixed',128,128,12,'rolling_hills',42,42,7,'mixed'),('mixed-low-relief',128,128,4,'rolling_hills',42,42,7,'mixed')]
    for name,*params in scenes:
        with (a.out/(name+'.log')).open('w') as log:
            subprocess.run(['java','-Xmx1g','-cp',cp,'EvidenceExport',str(a.out/name),*map(str,params)],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,timeout=90,check=True)
        print('EXPORTED',name,flush=True)
if __name__=='__main__':main()
