package org.mcsettlement.planner;

import org.mcsettlement.planner.civil.PlanConstruction;
import org.mcsettlement.planner.terrain.HeightfieldMap;

/** Sites must remain usable even when no map-edge road entrance is feasible. */
public final class TwoStagePlanningMain {
    public static void main(String[] args){
        HeightfieldMap map=new HeightfieldMap(0,0,64,64);
        for(int x=0;x<64;x++)for(int z=0;z<64;z++){
            map.setLocalSurfaceY(x,z,x<4||z<4||x>=60||z>=60?((x+z)%2==0?40:80):60);
            if(x<4||z<4||x>=60||z>=60){map.setLocalWaterY(x,z,64);map.setLocalObstacle(x,z,HeightfieldMap.ObstacleType.WATER_DEEP);}
        }
        map.computeSlopes();
        SettlementPlanner.PlanRequest r=new SettlementPlanner.PlanRequest();r.targetPlots=1;r.presetPalette="single";r.singlePresetId="square_cabin";
        r.expert=new ExpertSettings();r.expert.planningStage="sites";r.expert.allowBridges=false;r.expert.minBBoxCoverage=0;r.expert.minMinorAxisRatio=0;
        var sites=SettlementPlanner.plan(map,r);
        if(!"SITES_READY".equals(sites.status)||sites.plots.size()!=1||sites.search.pathExpanded!=0||!sites.transportNetwork.nodes.isEmpty())throw new AssertionError("Sites incorrectly required a road entrance");
        try{PlanConstruction.prepare(sites,"medieval_rustic");throw new AssertionError("Site-only plan became constructible");}
        catch(IllegalArgumentException expected){if(!expected.getMessage().startsWith("PLAN_NOT_CONSTRUCTIBLE"))throw expected;}
        r.expert.planningStage="full";var full=SettlementPlanner.plan(map,r);
        System.out.println("Full stage: "+full.status+" "+full.sitePlanning.reasons);
        if(!"REJECTED".equals(full.status)||!full.sitePlanning.reasons.contains("NO_VALID_FULL_WIDTH_ENTRY"))throw new AssertionError("Full planning failed to require an entrance");
        System.out.println("PASS sites without road entrance; no routing; site-only IR cannot construct; full compatibility");
    }
}
