package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.SettlementPlanner.*;
import org.mcsettlement.planner.civil.PlanConstruction;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.preset.*;
import org.mcsettlement.planner.simulation.*;
import org.mcsettlement.planner.terrain.HeightfieldMap;

/** Verify actual edit manifests, footprint holes, road materials and simulation colors. */
public final class SurfacePresetMain {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        var map=new HeightfieldMap(0,0,128,128);
        for(int x=0;x<128;x++)for(int z=0;z<128;z++)map.setSurfaceY(x,z,64);
        map.computeSlopes();
        for(var preset:BuildingPresetRegistry.getInstance().getAllPresets())if(preset.tags.contains("crafted")||preset.tags.contains("estate")){
            var r=new PlanRequest();r.targetPlots=1;r.presetPalette="single";r.singlePresetId=preset.id;
            r.expert=new ExpertSettings();r.expert.minBBoxCoverage=0;r.expert.minMinorAxisRatio=0;r.expert.autoDock=false;r.expert.maxPlanAttempts=1;
            var ir=SettlementPlanner.plan(map,r);
            check("COMPLETE".equals(ir.status),preset.id+": "+ir.status+" "+ir.sitePlanning.attempts);
            var edits=PlanConstruction.prepare(ir,"medieval_rustic");
            check(edits.stream().anyMatch(e->e.block().contains("glass")),preset.id+" missing windows");
            check(ir.plots.getFirst().footprint.size()==preset.footprintArea(),preset.id+" footprint changed");
            for(var c:ir.groundColumns)if("farmland".equals(c.kind))check(c.targetY==c.originalY,"Farm flattened terrain");
            check(edits.stream().anyMatch(e->e.block().equals("minecraft:coarse_dirt")),"No farm border");
            check(edits.stream().anyMatch(e->e.block().startsWith("minecraft:farmland")),"No tilled interior");
            System.out.println("PASS preset-construction "+preset.id);
        }
        String original=null;Set<String> hashes=new HashSet<>();
        for(String surface:List.of("dirt_path","gravel","cobblestone","stone_bricks")){
            var expert=new ExpertSettings();expert.minBBoxCoverage=0;expert.minMinorAxisRatio=0;expert.autoDock=false;
            var result=SimulatedSettlementPipeline.run(96,96,58,4,"rolling_hills",42,42,3,8,false,new SearchBudget(),"crafted",null,TerrainParameters.defaults("rolling_hills"),expert,surface);
            check("COMPLETE".equals(result.plan.status),surface+" plan failed");
            var edits=PlanConstruction.prepare(result.plan,"medieval_rustic");
            check(edits.stream().anyMatch(e->e.block().equals("minecraft:"+surface)),surface+" absent from actual edits");
            var column=result.plan.groundColumns.stream().filter(c->"road".equals(c.kind)&&"surface".equals(c.structure)).findFirst().orElseThrow();
            var expected=switch(surface){case "dirt_path"->SimulatedVoxelWorld.VoxelType.DIRT_PATH;case "gravel"->SimulatedVoxelWorld.VoxelType.GRAVEL;case "stone_bricks"->SimulatedVoxelWorld.VoxelType.STONE_BRICKS;default->SimulatedVoxelWorld.VoxelType.COBBLESTONE;};
            check(result.worldAfter.getBlock(column.x,column.targetY,column.z)==expected,"Wrong simulated road material");
            if(original==null)original=result.originalTerrainHash;else check(original.equals(result.originalTerrainHash),"Road material changed terrain");
            check(hashes.add(result.planHash),"Material omitted from plan hash");
            var roundtrip=PlanningIR.fromJson(result.plan.toJson(false));
            check(edits.equals(PlanConstruction.prepare(roundtrip,"medieval_rustic")),"IR material roundtrip lost");
            var stair=new PlanningIR.GroundColumn(1,1,64,65,67,"road");stair.structure="stair";stair.facing="EAST";stair.surfaceMaterial=surface;
            check(PlanConstruction.pavementBlock(stair).contains(surface.equals("stone_bricks")?"stone_brick_stairs":"cobblestone_stairs"),"Invalid stair fallback");
            System.out.println("PASS surface-roundtrip-simulation "+surface);
        }
        for(int size:List.of(64,128,192,256,512)){
            var terrain=new HeightfieldMap(0,0,size,size);for(int x=0;x<size;x++)for(int z=0;z<size;z++)terrain.setSurfaceY(x,z,64);terrain.computeSlopes();
            var r=new PlanRequest();r.targetPlots=8;r.presetPalette="estates";
            var allocation=PresetPalette.capacityRequirements(terrain,r);
            long big=allocation.stream().filter(q->q.presetId.startsWith("estate_")).count();
            check(allocation.size()==8&&big<=2,"Count allocation wrong");
            if(size<192)check(big==0,"Small map overloaded with estate");else check(big>0,"Large map lacks estate");
            int area=allocation.stream().mapToInt(q->BuildingPresetRegistry.getInstance().getPreset(q.presetId).footprintArea()).sum();
            check(area<=size*size*.22,"Map occupancy over capacity");
            System.out.println("PASS capacity "+size+" estates="+big+" reserved="+area);
        }
        System.out.println("ALL SURFACE/PRESET/CAPACITY CHECKS PASSED");
    }
}
