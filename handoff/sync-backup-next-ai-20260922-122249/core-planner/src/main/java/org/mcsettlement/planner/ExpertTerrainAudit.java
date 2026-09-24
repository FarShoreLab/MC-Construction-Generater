package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.SettlementPlanner.PlanRequest;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import static org.mcsettlement.planner.RoadGeometry.*;

/** Terrain-side audit, independent of path cost and candidate scoring. Construction audits topology. */
public final class ExpertTerrainAudit {
    private ExpertTerrainAudit() {}
    public static void validate(HeightfieldMap map,PlanRequest req,PlanningIR ir) {
        Set<Long> seen=new HashSet<>();
        for(var c:ir.groundColumns) {
            if(!map.inBounds(c.x,c.z)||!seen.add(key(c.x,c.z))||c.originalY!=map.getSurfaceY(c.x,c.z))throw new IllegalArgumentException("AUDIT_COLUMN_BOUNDS_OR_ORIGINAL");
            if(RoadTerrain.wet(map,c.x,c.z)) {
                if(!req.expert.allowBridges||!RoadTerrain.raised(c)||c.waterY==null||c.waterY!=map.getWaterY(c.x,c.z)||c.targetY!=c.waterY+1||c.targetY-c.originalY>16||c.support!=(Math.floorMod(c.x+c.z,4)==0))throw new IllegalArgumentException("AUDIT_WATER_NOT_RAISED");
            } else {
                if(!RoadTerrain.allowed(map,req,c.x,c.z))throw new IllegalArgumentException("AUDIT_FORBIDDEN_TERRAIN");
                if(RoadTerrain.landBridge(c)){
                    if(!req.expert.allowBridges||!"road".equals(c.kind)||c.targetY<=c.originalY||c.targetY-c.originalY>RoadTerrain.MAX_PILE_HEIGHT||c.support!=(Math.floorMod(c.x+c.z,4)==0))throw new IllegalArgumentException("AUDIT_LAND_BRIDGE");
                    continue;
                }
                if(RoadTerrain.raised(c))throw new IllegalArgumentException("AUDIT_FORBIDDEN_TERRAIN");
                int cut="foundation".equals(c.kind)?req.parcelConfig.maxCutBudget:req.roadMaxCut,fill="foundation".equals(c.kind)?req.parcelConfig.maxFillBudget:req.roadMaxFill;
                if(c.originalY-c.targetY>cut||c.targetY-c.originalY>fill)throw new IllegalArgumentException("AUDIT_CUT_FILL");
            }
        }
        Map<Long,PlanningIR.GroundColumn> columns=new TreeMap<>();for(var c:ir.groundColumns)columns.put(key(c.x,c.z),c);
        if(!RoadTerrain.validLandSpans(columns,req.expert.maxBridgeSpan))throw new IllegalArgumentException("AUDIT_LAND_BRIDGE_SPAN");
        for(var route:ir.transportNetwork.corridors) {
            int wetRun=0,headingX=0,headingZ=0;
            for(int i=1;i<route.steps.size();i++) {var a=route.steps.get(i-1);var b=route.steps.get(i);int dx=b.x-a.x,dz=b.z-a.z;boolean wet=false;
                for(int[] v:stencil(dx,dz,route.width))if(RoadTerrain.wet(map,a.x+v[0],a.z+v[1])){wet=true;break;}
                if(wet){if(dx!=0&&dz!=0||a.y!=b.y||wetRun>0&&(dx!=headingX||dz!=headingZ)||++wetRun>req.expert.maxBridgeSpan)throw new IllegalArgumentException("AUDIT_BRIDGE_SPAN_OR_TURN");headingX=dx;headingZ=dz;}else wetRun=0;
            }
        }
        for(var pin:req.expert.pins)if("building".equals(pin.kind)) {
            var p=ir.plots.stream().filter(a->pin.id.equals(a.requirementId)).findFirst().orElseThrow(()->new IllegalArgumentException("AUDIT_MISSING_PIN"));
            if(p.origin2D[0]!=pin.x||p.origin2D[1]!=pin.z||!pin.presetId.equals(p.builder.presetId)||!pin.facing.equals(p.builder.sourceFacing)||pin.y!=null&&pin.y!=p.elevation.baseElevation)throw new IllegalArgumentException("AUDIT_PIN_MOVED");
        }
        for(var pin:req.expert.pins){
            var connection=ir.sitePlanning.requiredConnections.stream().filter(c->pin.id.equals(c.from)).findFirst().orElseThrow(()->new IllegalArgumentException("AUDIT_MISSING_REQUIRED_CONNECTION"));
            if(!connection.status.startsWith("VERIFIED")||!pin.connectTo.equals(connection.to))throw new IllegalArgumentException("AUDIT_CONNECTION_NOT_VERIFIED");
            var route=ir.transportNetwork.corridors.stream().filter(c->connection.corridorId.equals(c.id)).findFirst().orElseThrow(()->new IllegalArgumentException("AUDIT_MISSING_REQUIRED_CORRIDOR"));
            if("dock".equals(pin.kind)){
                var dock=ir.transportNetwork.corridors.stream().filter(c->("dock:"+pin.id).equals(c.id)).findFirst().orElseThrow(()->new IllegalArgumentException("AUDIT_MISSING_DOCK"));
                var start=dock.steps.getFirst();if(start.x!=pin.x||start.z!=pin.z||!RoadTerrain.wet(map,pin.x,pin.z)||pin.y!=null&&pin.y!=start.y)throw new IllegalArgumentException("AUDIT_DOCK_MOVED");
            }
            if(!"network".equals(pin.connectTo)){
                int[] from=endpoint(ir,pin.id),to=endpoint(ir,pin.connectTo);var a=route.steps.getFirst();var b=route.steps.getLast();
                if(a.x!=from[0]||a.y!=from[1]||a.z!=from[2]||b.x!=to[0]||b.y!=to[1]||b.z!=to[2])throw new IllegalArgumentException("AUDIT_FORCED_ENDPOINT_MISMATCH");
            }
        }
        var measured=new PlanningIR.SitePlanning();SiteMetrics.measure(map,ir.plots,req.expert,measured);
        if(Math.abs(measured.bboxCoverage-ir.sitePlanning.bboxCoverage)>1e-9||Math.abs(measured.hullCoverage-ir.sitePlanning.hullCoverage)>1e-9||Math.abs(measured.minorAxisRatio-ir.sitePlanning.minorAxisRatio)>1e-9||!Arrays.equals(measured.bounds,ir.sitePlanning.bounds))throw new IllegalArgumentException("AUDIT_FORGED_COVERAGE_METRICS");
        if(!SiteMetrics.failures(ir.plots.size(),measured).isEmpty())throw new IllegalArgumentException("AUDIT_COVERAGE");
        if("site_expert/0.5.1".equals(ir.sitePlanning.algorithm)){
            if(!equal(measured.crowdingPenalty,ir.sitePlanning.crowdingPenalty)||
               !equal(measured.nearestNeighborMin,ir.sitePlanning.nearestNeighborMin)||
               !equal(measured.nearestNeighborMean,ir.sitePlanning.nearestNeighborMean)||
               measured.closePairs!=ir.sitePlanning.closePairs||measured.crowdedBuildings!=ir.sitePlanning.crowdedBuildings)
                throw new IllegalArgumentException("AUDIT_FORGED_SPACING_METRICS");
            Set<Long> roadCells=new HashSet<>();
            for(var route:ir.transportNetwork.corridors)roadCells.addAll(footprint(route.steps,route.width));
            for(var node:ir.transportNetwork.nodes)if("entry".equals(node.type))
                for(int[] v:stencil(0,0,ir.transportNetwork.corridorWidth))roadCells.add(key(node.pos[0]+v[0],node.pos[2]+v[1]));
            var areas=RoadNetworkAnalysis.enclosures(map,roadCells,ir.transportNetwork.corridorWidth,null);
            if(!areas.equals(ir.sitePlanning.enclosureAreas)||areas.size()!=ir.sitePlanning.macroLoops||
               areas.size()!=ir.transportNetwork.metrics.pavedEnclosures||areas.size()!=ir.transportNetwork.metrics.verifiedLoops)
                throw new IllegalArgumentException("AUDIT_FORGED_MACRO_LOOPS");
            var network=new PlanningIR.NetworkMetrics();
            RoadNetworkAnalysis.centerlines(ir.transportNetwork.corridors,network,req.expert.roadMergeDistance);
            var actual=ir.transportNetwork.metrics;
            if(!equal(network.uniqueCenterlineLength,actual.uniqueCenterlineLength)||
               !equal(network.sharedCenterlineLength,actual.sharedCenterlineLength)||
               !equal(network.nearParallelLength,actual.nearParallelLength)||network.cardinalSteps!=actual.cardinalSteps||
               network.diagonal45Steps!=actual.diagonal45Steps||network.obliqueSteps!=actual.obliqueSteps)
                throw new IllegalArgumentException("AUDIT_FORGED_CENTERLINE_METRICS");
        }
    }
    private static boolean equal(double a,double b){return Double.isFinite(a)&&Double.isFinite(b)&&Math.abs(a-b)<=1e-8;}
    private static int[] endpoint(PlanningIR ir,String id){
        if("entry".equals(id))return ir.transportNetwork.nodes.stream().filter(n->"entry".equals(n.type)).findFirst().orElseThrow().pos;
        var plot=ir.plots.stream().filter(p->id.equals(p.requirementId)).findFirst();
        if(plot.isPresent()){var p=plot.get().entrance.path.getLast();return new int[]{p.x,p.y,p.z};}
        var p=ir.transportNetwork.corridors.stream().filter(c->("dock:"+id).equals(c.id)).findFirst().orElseThrow().steps.getFirst();return new int[]{p.x,p.y,p.z};
    }
}
