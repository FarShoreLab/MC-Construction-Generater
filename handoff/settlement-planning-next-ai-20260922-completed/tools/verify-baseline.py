#!/usr/bin/env python3
"""Verify test-only baseline against an untouched original source ZIP.
Only namespace relocation and two operation-budget hooks are permitted.
"""
from pathlib import Path
import hashlib, json, sys, zipfile
root=Path(__file__).resolve().parents[1]
archive=Path(sys.argv[1])
results=[]
with zipfile.ZipFile(archive) as z:
    for rel in ('SettlementPlanner.java','parcel/PlotPlanner.java','civil/EarthworkOptimizer.java','pathfinding/SlopeCostAStar.java'):
        original=z.read('core-planner/src/main/java/org/mcsettlement/planner/'+rel).decode('utf-8')
        actual=(root/'core-planner/src/test/java/org/mcsettlement/planner/baseline'/rel).read_text()
        actual=actual.replace('package org.mcsettlement.planner.baseline;', 'package org.mcsettlement.planner;')
        for sub in ('parcel','civil','pathfinding'):
            actual=actual.replace('org.mcsettlement.planner.baseline.'+sub,'org.mcsettlement.planner.'+sub)
        actual=actual.replace('\n                    org.mcsettlement.planner.baseline.BaselineBudget.candidate();','')
        actual=actual.replace('\n            org.mcsettlement.planner.baseline.BaselineBudget.path();','')
        # Ignore archive CRLF convention, not Java source changes.
        original=original.replace('\r\n','\n')
        assert original==actual, f'Unexpected baseline algorithm modification: {rel}'
        results.append({'file':rel,'result':'PASS','original_normalized_sha256':hashlib.sha256(original.encode()).hexdigest()})
print(json.dumps(results,ensure_ascii=False,indent=2))
