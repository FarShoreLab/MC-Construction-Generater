package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.SettlementPlanner.PlanRequest;
import org.mcsettlement.planner.civil.PlanConstruction;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.preset.BuildingPresetRegistry;
import org.mcsettlement.planner.preset.PresetPalette;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import static org.mcsettlement.planner.RoadGeometry.*;

/** Regression checks for the 2026-09-22 road/site/retry/land-use/layout-mode handoff. */
public final class NextAiPlanningMain {
    private interface Check { void run() throws Exception; }
    private static int passed,failed;
    private static void test(String name,Check check){try{check.run();passed++;System.out.println("PASS "+name);}catch(Throwable t){failed++;System.out.println("FAIL "+name+": "+t);t.printStackTrace(System.out);}}
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static HeightfieldMap flat(int w,int d,int y){var m=new HeightfieldMap(0,0,w,d);for(int x=0;x<w;x++)for(int z=0;z<d;z++)m.setSurfaceY(x,z,y);m.computeSlopes();return m;}
    private static PlanRequest request(int plots){var r=new PlanRequest();r.seed=41;r.targetPlots=plots;r.presetPalette="mixed";r.expert=new ExpertSettings();r.expert.autoDock=false;r.expert.minBBoxCoverage=0;r.expert.minMinorAxisRatio=0;r.expert.maxPlanAttempts=1;r.entry=new int[]{2,64,64};return r;}
    private static void complete(PlanningIR ir){require("COMPLETE".equals(ir.status),ir.status+" reasons="+(ir.sitePlanning==null?List.of():ir.sitePlanning.reasons)+" attempts="+(ir.sitePlanning==null?List.of():ir.sitePlanning.attempts));}

    public static void main(String[] args) {
        test("ordinary_land_stays_within_fill_and_short_ravine_is_exception",NextAiPlanningMain::terrainFollowingBounds);
        test("continuous_dry_airborne_run_has_hard_cap",NextAiPlanningMain::landBridgeRun);
        test("locked_site_seed_survives_road_roll",NextAiPlanningMain::fixedSites);
        test("rejected_plan_retries_stop_at_strict_cap",NextAiPlanningMain::retryCap);
        test("farmland_and_pasture_are_terrain_following_non_rectangles",NextAiPlanningMain::naturalLandUses);
        test("settlement_modes_change_density_target_and_height_mix",NextAiPlanningMain::layoutModes);
        System.out.println("RESULT "+passed+" passed; "+failed+" failed");if(failed>0)throw new AssertionError("Next-AI regressions="+failed);
    }

    private static void terrainFollowingBounds(){
        var m=flat(48,32,64);var r=request(1);r.roadMaxFill=3;r.expert.maxLandBridgeSpan=8;
        for(int x=0;x<48;x++)for(int z=0;z<32;z++)require(RoadTerrain.upper(m,r,x,z)==67,"allowBridges raised ordinary flat land at "+x+","+z);
        // An 8-cell dry depression is a genuine short obstacle and may use the exceptional deck envelope.
        for(int x=20;x<=27;x++)for(int z=0;z<32;z++)m.setSurfaceY(x,z,56);m.computeSlopes();
        require(RoadTerrain.landBridgeEligible(m,r,23,16),"short two-sided ravine was not bridge-eligible");
        require(RoadTerrain.upper(m,r,23,16)>m.getSurfaceY(23,16)+r.roadMaxFill,"ravine did not receive short-span bridge envelope");
        require(!RoadTerrain.landBridgeEligible(m,r,8,16),"ordinary land became bridge-eligible");
        // Full-plan regression: ordinary flat dry roads stay on/within the normal earthwork envelope.
        var flatPlanMap=flat(96,96,64);var full=request(3);full.entry=new int[]{2,64,48};full.roadMaxFill=3;full.expert.maxLandBridgeSpan=8;
        PlanningIR planned=SettlementPlanner.plan(flatPlanMap,full);complete(planned);
        int maxClearance=planned.groundColumns.stream().filter(c->"road".equals(c.kind)&&c.waterY==null).mapToInt(c->c.targetY-c.originalY).max().orElse(0);
        require(maxClearance<=full.roadMaxFill,"ordinary dry road exceeded fill envelope: "+maxClearance);
        require(planned.sitePlanning.maxRoadClearance<=full.roadMaxFill,"audited road clearance exceeded fill envelope: "+planned.sitePlanning.maxRoadClearance);
    }

    private static void landBridgeRun(){
        Map<Long,GroundColumn> cols=new TreeMap<>();List<RoadStep> route=new ArrayList<>();
        for(int x=0;x<=14;x++){route.add(new RoadStep(x,70,0,"bridge"));GroundColumn c=new GroundColumn(x,0,64,70,72,"road");c.structure="bridge";c.support=Math.floorMod(x,4)==0;cols.put(key(x,0),c);}
        int longest=RoadTerrain.maxLandAirborneRun(cols,route,1);require(longest==14,"unexpected dry airborne run="+longest);
        require(!RoadTerrain.validLandDeckRun(cols,route,1,12),"over-limit continuous viaduct accepted");
        require(RoadTerrain.validLandDeckRun(cols,route.subList(0,9),1,12),"short dry deck rejected");
    }

    private static String sites(PlanningIR ir){
        List<String> out=new ArrayList<>();for(var p:ir.plots)out.add(p.requirementId+":"+p.origin2D[0]+","+p.origin2D[1]+","+p.elevation.baseElevation+":"+p.builder.presetId+":"+p.builder.sourceFacing);Collections.sort(out);return String.join("|",out);
    }
    private static void fixedSites(){
        var m=flat(128,128,64);var a=request(4);a.presetPalette="mixed";a.expert.siteSeed=90210L;a.seed=51;
        var b=request(4);b.presetPalette="mixed";b.expert.siteSeed=90210L;b.seed=52;
        PlanningIR pa=SettlementPlanner.plan(m,a),pb=SettlementPlanner.plan(m,b);complete(pa);complete(pb);
        require(sites(pa).equals(sites(pb)),"road roll moved locked automatic sites\nA="+sites(pa)+"\nB="+sites(pb));
        require(pa.sitePlanning.effectiveSiteSeed==90210L&&pb.sitePlanning.effectiveSiteSeed==90210L,"siteSeed provenance changed");
    }

    private static void retryCap(){
        var m=flat(64,64,64);var r=request(1);r.entry=new int[]{2,64,32};r.presetPalette="single";r.singlePresetId="square_cabin";r.expert.minBBoxCoverage=.85;r.expert.maxPlanAttempts=2;r.seed=700;
        PlanningIR ir=SettlementPlanner.plan(m,r);require("REJECTED".equals(ir.status),"impossible coverage unexpectedly accepted: "+ir.status);
        require(ir.sitePlanning.planRollAttempts.size()==2,"retry cap not exact: "+ir.sitePlanning.planRollAttempts.size());
        require(ir.sitePlanning.planRollAttempts.get(0).planSeed==700&&ir.sitePlanning.planRollAttempts.get(1).planSeed==701,"retry seeds not rolled deterministically");
        require(ir.sitePlanning.planRollAttempts.stream().allMatch(a->"REJECTED".equals(a.status)&&!a.reasons.isEmpty()),"failed rounds lost reasons");
        require(ir.sitePlanning.effectivePlanSeed==701,"final effective plan seed not exposed");
    }

    private static void naturalLandUses(){
        var m=new HeightfieldMap(0,0,72,72);for(int x=0;x<72;x++)for(int z=0;z<72;z++)m.setSurfaceY(x,z,62+x/12+z/24);m.computeSlopes();
        var r=request(1);r.entry=null;r.expert.siteSeed=33L;var ir=new PlanningIR();ir.sitePlanning=new SitePlanning();
        Plot p=new Plot();p.origin2D=new int[]{33,33};p.builder.footprintSize=new int[]{5,5};p.builder.footprintArea=25;ir.plots.add(p);
        for(int x=33;x<38;x++)for(int z=33;z<38;z++){int y=m.getSurfaceY(x,z);GroundColumn c=new GroundColumn(x,z,y,y,y+6,"foundation");ir.groundColumns.add(c);ir.search.constructionEdits+=PlanConstruction.columnEditCount(c);}
        TerrainLandUsePlanner.add(m,r,ir);require(ir.landUses.stream().anyMatch(a->"farmland".equals(a.type)),"farmland missing");require(ir.landUses.stream().anyMatch(a->"pasture".equals(a.type)),"pasture missing");
        Map<Long,GroundColumn> manifest=new HashMap<>();for(var c:ir.groundColumns)manifest.put(key(c.x,c.z),c);
        for(var area:ir.landUses){require(area.cells.size()>=12,"land-use region too small");require(!area.boundary2D.isEmpty(),"natural boundary missing");Set<Long> cells=new HashSet<>();int minX=9999,minZ=9999,maxX=-9999,maxZ=-9999;
            for(int[] c:area.cells){cells.add(key(c[0],c[1]));minX=Math.min(minX,c[0]);maxX=Math.max(maxX,c[0]);minZ=Math.min(minZ,c[1]);maxZ=Math.max(maxZ,c[1]);GroundColumn g=manifest.get(key(c[0],c[1]));require(g!=null&&area.type.equals(g.kind)&&g.targetY==m.getSurfaceY(c[0],c[1])&&g.originalY==g.targetY,"land use did not follow terrain");}
            require("farm_hut".equals(area.type)||cells.size()<(maxX-minX+1)*(maxZ-minZ+1),"land-use region degenerated to rectangle");for(int[] c:area.boundary2D)require(cells.contains(key(c[0],c[1])),"boundary references non-member cell");
        }
    }

    private static double meanAutoHeight(String mode,int count){
        return PresetPalette.requirements("mixed",null,count,mode).stream().mapToInt(q->BuildingPresetRegistry.getInstance().getPreset(q.presetId).sizeY).average().orElse(0);
    }
    private static void layoutModes(){
        var village=new ExpertSettings();village.settlementMode="village";var town=new ExpertSettings();town.settlementMode="town";var city=new ExpertSettings();city.settlementMode="city";
        require(SiteMetrics.spacingReference(8,city)<SiteMetrics.spacingReference(8,town)&&SiteMetrics.spacingReference(8,town)<SiteMetrics.spacingReference(8,village),"mode density references not ordered");
        double vh=meanAutoHeight("village",8),ch=meanAutoHeight("city",8);require(ch>vh,"city automatic preset mix is not taller: city="+ch+" village="+vh);
        var m=flat(128,128,64);var vr=request(8);vr.expert.settlementMode="village";vr.expert.minBBoxCoverage=.12;vr.expert.buildingRepulsion=1;vr.seed=77;
        var cr=request(8);cr.expert.settlementMode="city";cr.expert.minBBoxCoverage=.12;cr.expert.buildingRepulsion=1;cr.seed=77;
        PlanningIR vp=SettlementPlanner.plan(m,vr),cp=SettlementPlanner.plan(m,cr);complete(vp);complete(cp);
        require(cp.sitePlanning.meanBuildingHeight>vp.sitePlanning.meanBuildingHeight,"full city plan did not raise building-height distribution");
        require(cp.sitePlanning.meanBuildingVolume>vp.sitePlanning.meanBuildingVolume,"full city plan did not raise building-volume distribution");
        require(cp.sitePlanning.buildingHeightStdDev>vp.sitePlanning.buildingHeightStdDev,"full city plan did not create a stronger height hierarchy");
        require(cp.sitePlanning.spacingReference<vp.sitePlanning.spacingReference,"full plan lost mode density target");
        // The city continuity field should not produce a looser plan than the village spread field.
        require(cp.sitePlanning.nearestNeighborMean<=vp.sitePlanning.nearestNeighborMean+1e-9,"city layout is less dense than village: city="+cp.sitePlanning.nearestNeighborMean+" village="+vp.sitePlanning.nearestNeighborMean);
    }
}
