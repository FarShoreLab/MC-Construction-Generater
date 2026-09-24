package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.ir.PlanningIR.*;

/** Geometry-only audit of ACTUAL centers. Never infers curvature from guide control points. */
public final class RouteQualityMetrics {
    public static final int LONG_STRAIGHT_WARNING=16;
    private RouteQualityMetrics() {}
    public static RouteQuality measure(List<RoadStep> path) {
        RouteQuality q=new RouteQuality();q.stepCount=Math.max(0,path.size()-1);
        if(path.size()<2)return q;
        List<Double> lengths=new ArrayList<>();int px=0,pz=0,run=0;double runLength=0;
        for(int i=1;i<path.size();i++){
            RoadStep a=path.get(i-1),b=path.get(i);int dx=b.x-a.x,dz=b.z-a.z;double length=StrictMath.hypot(dx,dz);
            if(i>1&&(dx!=px||dz!=pz)){q.straightRunSteps.add(run);lengths.add(runLength);run=0;runLength=0;}
            run++;runLength+=length;q.length+=length;px=dx;pz=dz;
        }
        q.straightRunSteps.add(run);lengths.add(runLength);q.rawTurns=Math.max(0,lengths.size()-1);
        double position=0,lastBend=0;
        for(int i=0;i<lengths.size();i++){
            int n=q.straightRunSteps.get(i);double length=lengths.get(i);
            q.maxStraightRun=Math.max(q.maxStraightRun,n);q.maxStraightLength=Math.max(q.maxStraightLength,length);
            if(n>LONG_STRAIGHT_WARNING)q.longStraightFraction+=length;if(n<3)q.shortRuns++;
            position+=length;
            // At least three moves on BOTH sides: isolated 1-cell noise is not an effective bend.
            if(i+1<lengths.size()&&n>=3&&q.straightRunSteps.get(i+1)>=3){q.effectiveBends++;q.bendSpacings.add(position-lastBend);lastBend=position;}
        }
        q.bendSpacings.add(q.length-lastBend); // includes both endpoint intervals, documented explicitly
        q.meanBendSpacing=q.length/q.bendSpacings.size();
        q.endpointDistance=StrictMath.hypot(path.getLast().x-path.getFirst().x,path.getLast().z-path.getFirst().z);
        q.sinuosity=q.endpointDistance>0?q.length/q.endpointDistance:0;
        if(q.length>0)q.longStraightFraction/=q.length;
        for(int i=4;i<path.size();i++){
            RoadStep a=path.get(i-4),b=path.get(i-3),c=path.get(i-2),d=path.get(i-1),e=path.get(i);
            int ax=b.x-a.x,az=b.z-a.z,bx=c.x-b.x,bz=c.z-b.z;
            if(ax==d.x-c.x&&az==d.z-c.z&&bx==e.x-d.x&&bz==e.z-d.z&&(ax!=bx||az!=bz))q.microZigzagWindows++;
        }
        // Sustained short blocks can still form a zipper: A^k B^k A^k B^k, k=2,3.
        // This supplements (does not weaken or rename) the legacy one-move ABAB audit.
        for(int block=2;block<=3;block++)for(int i=0;i+4*block<path.size();i++) {
            RoadStep a=path.get(i),b=path.get(i+1),c=path.get(i+block),d=path.get(i+block+1);
            int ax=b.x-a.x,az=b.z-a.z,bx=d.x-c.x,bz=d.z-c.z;
            if(ax==bx&&az==bz)continue;
            boolean matches=true;
            for(int j=0;j<4*block;j++){RoadStep u=path.get(i+j),v=path.get(i+j+1);boolean first=(j/block)%2==0;
                if(v.x-u.x!=(first?ax:bx)||v.z-u.z!=(first?az:bz)){matches=false;break;}}
            if(matches)q.rhythmZigzagWindows++;
        }
        q.microZigzagRatio=path.size()<5?0:q.microZigzagWindows/(double)(path.size()-4);
        return q;
    }
    static void summarize(TransportNetwork net) {
        NetworkMetrics m=net.metrics;m.routeQualityVersion=1;
        m.longestStraightRatio=0;m.microZigzagRatio=0;m.maximumStraightRun=0;m.majorMaxStraightRun=0;
        m.longStraightWarnings=0;m.unguidedFallbackCount=0;m.conservativeRouteCount=0;
        for(RoadEdge e:net.corridors){e.quality=measure(e.steps);RouteQuality q=e.quality;
            m.maximumStraightRun=Math.max(m.maximumStraightRun,q.maxStraightRun);
            if(!"local".equals(e.roadType))m.majorMaxStraightRun=Math.max(m.majorMaxStraightRun,q.maxStraightRun);
            if(q.maxStraightRun>LONG_STRAIGHT_WARNING){m.longStraightWarnings++;
                q.straightException=e.fallbackReason!=null?e.fallbackReason:"SOFT_GUIDE_CONSTRAINTS: selected constructible route under recorded finite candidate budget";
                for(RouteAttempt a:m.routingAttempts)if(a.selected&&Objects.equals(a.fromNodeId,e.fromNodeId)&&Objects.equals(a.toNodeId,e.toNodeId)&&Objects.equals(a.roadType,e.roadType)){
                    q.straightException+="; refinement="+a.refinementOutcome+", variants="+a.refinementVariants+", rejected="+a.refinementRejections;break;}}
            if("constrained_fallback".equals(e.routingStyle))m.unguidedFallbackCount++;
            if("conservative_guided".equals(e.routingStyle))m.conservativeRouteCount++;
            // Preserve legacy denominator/window semantics for old consumers and thresholds.
            if(e.steps.size()>=30){m.longestStraightRatio=Math.max(m.longestStraightRatio,q.length==0?0:q.maxStraightLength/q.length);m.microZigzagRatio=Math.max(m.microZigzagRatio,q.microZigzagRatio);}
        }
        m.unguidedFallbackAttempts=(int)m.routingAttempts.stream().filter(a->"unguided".equals(a.stage)).count();
        m.failedRouteAttempts=(int)m.routingAttempts.stream().filter(a->"REJECTED".equals(a.status)).count();
    }
}
