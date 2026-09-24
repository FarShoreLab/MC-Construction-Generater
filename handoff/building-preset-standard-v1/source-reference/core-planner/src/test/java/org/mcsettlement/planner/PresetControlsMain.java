package org.mcsettlement.planner;

import java.nio.file.*;
import java.util.*;
import org.mcsettlement.planner.SettlementPlanner.PlanRequest;
import org.mcsettlement.planner.civil.PlanConstruction;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.preset.*;
import org.mcsettlement.planner.terrain.HeightfieldMap;

/** Black-box selection, entrance persistence, archive and environmental preference checks. */
public final class PresetControlsMain {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void rejects(Runnable action){try{action.run();}catch(IllegalArgumentException ex){return;}throw new AssertionError("Invalid request accepted");}
    static HeightfieldMap flat(){var m=new HeightfieldMap(0,0,128,128);for(int x=0;x<128;x++)for(int z=0;z<128;z++)m.setSurfaceY(x,z,64);m.computeSlopes();return m;}
    static PlanRequest request(String id,long seed,String preference){var r=new PlanRequest();r.seed=seed;r.targetPlots=1;r.presetPalette="single";r.singlePresetId=id;r.expert=new ExpertSettings();r.expert.minBBoxCoverage=0;r.expert.minMinorAxisRatio=0;r.expert.autoDock=false;r.expert.maxPlanAttempts=1;r.expert.sitePreference=preference;return r;}
    static PlanningIR complete(HeightfieldMap m,PlanRequest r){var p=SettlementPlanner.plan(m,r);check("COMPLETE".equals(p.status),"Plan failed: "+r.singlePresetId+" "+p.status);return p;}
    public static void main(String[] args)throws Exception {
        var registry=BuildingPresetRegistry.getInstance();check(registry.getAllPresets().size()==34,"Active catalog");
        for(String id:List.of("estate_48","estate_64","estate_96")){check(registry.getPreset(id).archived,"Archive unavailable for replay");var archived=SettlementPlanner.plan(flat(),request(id,42,"balanced"));check("INVALID_REQUEST".equals(archived.status)&&archived.plots.isEmpty(),"Archived preset generated");}
        for(int seed=0;seed<100;seed++)check(!registry.resolveBestPreset("",null,null,seed).archived,"Archived fallback preset");
        System.out.println("PASS archived presets excluded, resources retained");
        var r=request("meadow_hut",42,"clearings");r.presetPalette="custom";r.targetPlots=3;
        r.expert.buildingSelection=ExpertSettings.parseBuildingSelection("[{\"presetId\":\"meadow_hut\",\"count\":2},{\"presetId\":\"village_bakery\",\"count\":1},{\"presetId\":\"stone_bell_tower\",\"enabled\":false,\"count\":4}]");
        var plan=complete(flat(),r);
        check(plan.plots.stream().filter(p->p.builder.presetId.equals("meadow_hut")).count()==2,"Exact hut count");
        check(plan.plots.stream().filter(p->p.builder.presetId.equals("village_bakery")).count()==1,"Exact bakery count");
        check(plan.plots.size()==3,"Disabled preset generated");
        var roundtrip=PlanningIR.fromJson(plan.toJson(false));
        check(PlanConstruction.prepare(plan,null).equals(PlanConstruction.prepare(roundtrip,null)),"Entrance lost on JSON roundtrip");
        r.targetPlots=4;rejects(()->SettlementPlanner.plan(flat(),r));
        for(String bad:List.of("[]","[{\"presetId\":\"meadow_hut\",\"count\":0}]","[{\"presetId\":\"estate_48\"}]","[{\"presetId\":\"meadow_hut\",\"count\":1.5}]")){
            rejects(()->{var q=request("meadow_hut",42,"clearings");q.presetPalette="custom";q.expert.buildingSelection=ExpertSettings.parseBuildingSelection(bad);SettlementPlanner.plan(flat(),q);});
        }
        System.out.println("PASS exact per-preset counts, disabled items, invalid counts, JSON construction replay");
        for(var b:registry.getAllPresets())for(int index=0;index<b.entranceCount();index++)for(String facing:List.of("NORTH","EAST","SOUTH","WEST"))for(boolean diagonal:List.of(false,true)){
            var s=new BuildingShape(b.selectEntrance(index).rotateToFacing(facing),diagonal);var grid=s.grid(null);
            check(s.entranceIndex==index,"Entrance index changed");
            check(grid[s.entranceX][1][s.entranceZ].equals("minecraft:air")&&grid[s.entranceX][2][s.entranceZ].equals("minecraft:air"),"Door blocked");
            int[] dir=PlannedBuilding.direction(s.facing);if(!b.entrances.isEmpty())check(!s.contains(s.entranceX+dir[0],s.entranceZ+dir[1]),"Candidate door faces interior: "+b.id);
        }
        var damaged=PlanningIR.fromJson(plan.toJson(false));damaged.plots.getFirst().builder.entranceIndex=100;
        rejects(()->PlanConstruction.prepare(damaged,null));
        System.out.println("PASS every entrance / cardinal / diagonal orientation and tampered index rejection");
        double[] forest=new double[2],shore=new double[2],slope=new double[2];
        for(int mode=0;mode<2;mode++)for(int seed=1;seed<=4;seed++){
            String pref=mode==0?"balanced":"clearings";
            var m=flat();for(int x=64;x<128;x++)for(int z=0;z<128;z++)m.setObstacle(x,z,HeightfieldMap.ObstacleType.TREE_TRUNK);
            var p=complete(m,request("square_cabin",seed,pref)).plots.getFirst();
            for(int[] c:p.footprint)if(m.getObstacle(p.origin2D[0]+c[0],p.origin2D[1]+c[1])==HeightfieldMap.ObstacleType.TREE_TRUNK)forest[mode]++;
            m=flat();for(int x=0;x<128;x++)for(int z=0;z<9;z++){m.setSurfaceY(x,z,60);m.setWaterY(x,z,63);m.setObstacle(x,z,HeightfieldMap.ObstacleType.WATER);}m.computeSlopes();
            p=complete(m,request("square_cabin",seed,pref)).plots.getFirst();
            for(int[] c:p.footprint)check(!RoadTerrain.wet(m,p.origin2D[0]+c[0],p.origin2D[1]+c[1]),"House on water");
            shore[mode]+=p.origin2D[1]-8;
            m=flat();for(int x=64;x<128;x++)for(int z=0;z<128;z++)m.setSurfaceY(x,z,64+(z%4<2?1:0));m.computeSlopes();
            p=complete(m,request("square_cabin",seed,pref)).plots.getFirst();
            for(int[] c:p.footprint)slope[mode]+=m.getSlope(p.origin2D[0]+c[0],p.origin2D[1]+c[1]);
        }
        System.out.printf("PREFERENCE balanced -> clearings: forest %.0f -> %.0f; shore %.0f -> %.0f; slope %.2f -> %.2f%n",forest[0],forest[1],shore[0],shore[1],slope[0],slope[1]);
        check(forest[1]<forest[0],"No reduction in forest usage");check(shore[1]<shore[0],"No improvement in shoreline usage");
        // The legacy soil cost already chooses the flat half of this fixture; the new preference must preserve it.
        check(slope[1]==0&&slope[1]<=slope[0],"Flat sites lost despite available clearing");
        var example=BuildingPreset.fromJson(Files.readString(Path.of("handoff/building-preset-standard-v1/example.native.json")));
        example.validateFootprint();check(example.components.size()==3&&example.entranceCount()==3,"Example missing semantic parts");registry.registerPreset(example);
        var demo=complete(flat(),request(example.id,42,"clearings"));check(!PlanConstruction.prepare(demo,null).isEmpty(),"Example cannot construct");
        System.out.println("PASS standard compound example constructs with main, annex, yard and entrances");
        System.out.println("ALL PRESET CONTROL CHECKS PASSED");
    }
}
