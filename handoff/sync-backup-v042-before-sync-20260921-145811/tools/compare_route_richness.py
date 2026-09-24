#!/usr/bin/env python3
"""Validate fair before/after inputs and produce an explicit PASS/WARN quality report."""
from __future__ import annotations
import argparse,copy,json
from pathlib import Path
from verify_route_richness import SCENES,audit_metrics


def main()->None:
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--before',type=Path,required=True);ap.add_argument('--after',type=Path,required=True);ap.add_argument('--out',type=Path,required=True);a=ap.parse_args();a.out.parent.mkdir(parents=True,exist_ok=True)
    rows=[]
    def locked_plots(plan):
        plots=copy.deepcopy(plan['plots'])
        for p in plots:p['entrance'].pop('connectedEdgeId',None)
        return plots
    def side(p,s,q):
        routes=q['routes'];length=sum(r['length'] for r in routes)
        return dict(status=s['status'],plots=s['plots'],network=s['network']['status'],verifiedLoops=s['network']['verifiedLoops'],
            collectorMaxStraightRun=q['collectorMaxStraightRun'],allMaxStraightRun=q['maximumStraightRun'],
            longStraightFraction=sum(r['longStraightFraction']*r['length'] for r in routes)/length if length else 0,
            microZigzagWindows=q['microZigzagWindows'],rhythmZigzagWindows=q['rhythmZigzagWindows'],
            pathExpanded=s['search']['pathExpanded'],gradeRelaxations=s['search']['gradeRelaxations'],
            peakPathStates=s['search']['peakPathStates'],roadColumns=s['roadColumnCount'],groundColumns=len(p['groundColumns']),
            constructionEdits=s['constructionEdits'],stairBlocks=s['stairBlocks'],planningMilliseconds=s['search']['elapsedNanos']/1e6,
            budgetExhausted=s['search']['budgetExhausted'],exhaustedBudgets=s['search']['exhaustedBudgets'],
            selectedUnguidedFallbacks=s['network'].get('unguidedFallbackCount'),selectedConservativeRoutes=s['network'].get('conservativeRouteCount'),
            planHash=s['planHash'],originalTerrainHash=s['originalTerrainHash'],constructedWorldHash=s['constructedWorldHash'])
    for name in SCENES:
        before=json.loads((a.before/name/'PlanningIR.json').read_text());after=json.loads((a.after/name/'PlanningIR.json').read_text())
        bs=json.loads((a.before/name/'summary.json').read_text());ns=json.loads((a.after/name/'summary.json').read_text())
        bq,nq=audit_metrics(before),audit_metrics(after)
        (a.before/name/'route-metrics.json').write_text(json.dumps(bq,ensure_ascii=False,indent=2),encoding='utf-8')
        assert bs['config']==ns['config'] and bs['originalTerrainHash']==ns['originalTerrainHash'],(name,'not same input')
        assert locked_plots(before)==locked_plots(after),(name,'site or entrance changed')
        repeat=json.loads((a.after/name/'repeat/summary.json').read_text());rp=json.loads((a.after/name/'repeat/PlanningIR.json').read_text())
        for k in ['planHash','constructedWorldHash','originalTerrainHash']:assert ns[k]==repeat[k],(name,k)
        for k in ['transportNetwork','groundColumns','plots']:assert after[k]==rp[k],(name,k)
        warnings=[dict(id=e['id'],role=e['roadType'],quality=e['quality'],fallbackReason=e.get('fallbackReason')) for e in after['transportNetwork']['corridors'] if e['quality']['maxStraightRun']>16 or e['quality']['rhythmZigzagWindows']>0]
        rows.append(dict(scene=name,input=SCENES[name],sameTerrain=True,lockedSitesAndEntrancesUnchanged=True,deterministic=True,
            hardAudit=ns['audit'],before=side(before,bs,bq),after=side(after,ns,nq),qualityGate='WARN_RETAINED_CONSTRAINTS' if warnings else 'PASS',
            remainingQualityWarnings=warnings,routeFailureReasons=ns['network']['reasons'],routingAttempts=ns['network']['routingAttempts']))
    a.out.write_text(json.dumps({'scenes':rows,'note':'Planning milliseconds are observations in this environment, not a controlled benchmark. New guide work is separately bounded and reported; global search/construction limits are unchanged.'},ensure_ascii=False,indent=2),encoding='utf-8')
    for r in rows:print(r['scene'],r['qualityGate'],'collector',r['before']['collectorMaxStraightRun'],'->',r['after']['collectorMaxStraightRun'],'long%',round(r['before']['longStraightFraction']*100,2),'->',round(r['after']['longStraightFraction']*100,2))

if __name__=='__main__':main()
