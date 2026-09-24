package org.mcsettlement.planner;

import java.nio.file.*;
import java.util.*;
import com.google.gson.Gson;
import org.mcsettlement.planner.SettlementPlanner.*;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.preset.*;
import org.mcsettlement.planner.civil.PlanConstruction;
import org.mcsettlement.planner.regression.*;
import org.mcsettlement.planner.simulation.*;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import static org.mcsettlement.planner.RoadGeometry.*;
import static org.mcsettlement.planner.regression.RegressionMain.check;

/** Executable integration tests against the real planner and edit program, never screenshot assertions. */
public final class OrganicPresetMain {
    private static int passed,failed;private static Path out;
    private static SimulatedSettlementPipeline.PipelineResult fixture,mixed,reference;
    private interface Test {void run()throws Exception;}
    private static void test(String name,Test f){try{f.run();passed++;System.out.println("PASS "+name);}catch(Throwable t){failed++;System.out.println("FAIL "+name+": "+t);t.printStackTrace(System.out);}}
    private static void rejects(Runnable f,String label){boolean rejected=false;try{f.run();}catch(IllegalArgumentException e){rejected=true;}check(rejected,label);}
    private static void audit(SimulatedSettlementPipeline.PipelineResult r){PlanRequest q=new PlanRequest();q.seed=r.planSeed;q.targetPlots=r.targetPlots;q.presetPalette=r.presetPalette;
        var a=TerrainAudit.audit(r.heightfield,q,r.plan);check(a.violations()==0,"Hard violations="+a.violations());check(a.reachable()==r.plan.plots.size(),"Unreachable allocated building");}
    private static SimulatedSettlementPipeline.PipelineResult scene(int width,int relief,String type,long seed,String palette){
        return SimulatedSettlementPipeline.run(width,width,58,relief,type,42,seed,7,8,false,new SearchBudget(),palette,null);
    }
    public static void main(String[] args)throws Exception{
        out=Path.of(args.length==0?"build/organic-evidence":args[0]);Files.createDirectories(out);
        test("registry_37_presets_retains_10_classic",()->{
            var ps=BuildingPresetRegistry.getInstance().getAllPresets();check(ps.size()==37,"Missing presets");check(BuildingPresetRegistry.CLASSIC_IDS.size()==10,"Classic set changed");
            for(BuildingPreset p:ps)p.validateFootprint();
            check(ps.stream().map(p->p.footprintArea()).distinct().count()>=10,"Insufficient size diversity");
            check(ps.stream().map(p->p.footprintShape).distinct().count()>=9,"Insufficient shape diversity");
        });
        test("all_88_cardinal_variants_preserve_exact_occupancy",()->{
            for(BuildingPreset p:BuildingPresetRegistry.getInstance().getAllPresets())for(String facing:List.of("NORTH","EAST","SOUTH","WEST")){
                BuildingPreset v=p.rotateToFacing(facing);v.validateFootprint();BuildingShape shape=new BuildingShape(v,false);
                check(shape.cells.size()==p.footprintArea(),p.id+" area changed");check(shape.facing.equals(facing),"Door direction not rotated");
                check(shape.grid(null)[shape.entranceX][1][shape.entranceZ].equals("minecraft:air"),"Door not clear");
                for(int[] c:shape.cells)check(v.occupies(c[0],c[1]),"AABB filled a hole");
            }
        });
        test("all_88_diagonal_variants_emit_only_true_mask_voxels",()->{
            for(BuildingPreset p:BuildingPresetRegistry.getInstance().getAllPresets())for(String facing:List.of("NORTH","EAST","SOUTH","WEST")){
                BuildingShape shape=new BuildingShape(p.rotateToFacing(facing),true);String[][][] grid=shape.grid(null);
                for(int x=0;x<shape.sizeX;x++)for(int z=0;z<shape.sizeZ;z++)if(!shape.contains(x,z))for(int y=0;y<shape.sizeY;y++)check("minecraft:air".equals(grid[x][y][z]),"Voxel outside rotated mask");
                check("minecraft:air".equals(grid[shape.entranceX][1][shape.entranceZ])&&"minecraft:air".equals(grid[shape.entranceX][2][shape.entranceZ]),"Rotated door blocked");
            }
        });
        test("reject_malformed_mask_and_nonair_voxel_in_courtyard",()->{
            BuildingPreset p=BuildingPresetRegistry.getInstance().getPreset("courtyard_house").copy();p.footprintMask.set(0,"bad");rejects(p::validateFootprint,"Malformed mask accepted");
            p=BuildingPresetRegistry.getInstance().getPreset("courtyard_house").copy();String row=p.layers.get(0).get(7);p.layers.get(0).set(7,row.substring(0,7)+"F"+row.substring(8));rejects(p::validateFootprint,"Voxel in declared hole accepted");
        });
        test("reject_disconnected_mask",()->{
            BuildingPreset p=new BuildingPreset();p.id="disconnected";p.sizeX=p.sizeZ=5;p.sizeY=3;p.entrance=new BuildingPreset.EntranceSpec("NORTH",0,0);
            p.footprintMask=List.of("#....",".....",".....",".....","....#");p.palette=Map.of(".","minecraft:air","#","minecraft:stone");
            p.layers=List.of(p.footprintMask,Collections.nCopies(5,"....."),Collections.nCopies(5,"....."));rejects(p::validateFootprint,"Disconnected footprint accepted");
        });
        test("protected_courtyard_is_not_foundation_or_clearance",()->protectedShape("courtyard_house"));
        test("protected_L_notch_remains_unmodified",()->protectedShape("l_cottage"));
        test("mixed_palette_locks_seven_distinct_full_size_presets",()->{
            mixed=scene(128,12,"rolling_hills",42,"mixed");audit(mixed);check(mixed.plan.plots.size()==7,"Mixed request incomplete");
            var wanted=PresetPalette.requirements("mixed",null,7).stream().map(q->q.presetId).sorted().toList();
            check(mixed.plan.plots.stream().map(p->p.builder.presetId).sorted().toList().equals(wanted),"Silently substituted a smaller preset");
            check(mixed.plan.plots.stream().map(p->p.builder.footprintShape).distinct().count()>=6,"Missing concave mix");
            for(Plot p:mixed.plan.plots)check(p.footprint.size()==BuildingPresetRegistry.getInstance().getPreset(p.builder.presetId).footprintArea(),"Scaled fixed preset");
            Files.writeString(out.resolve("mixed128-ir.json"),mixed.plan.toJson(true));
        });
        test("classic_unspecified_requirements_do_not_pick_new_small_houses",()->{
            PlanRequest q=RegressionMain.request(RegressionMain.demand("homes","residential",3));PlanningIR p=SettlementPlanner.plan(RegressionMain.flat(0,0,96,96,64),q);
            check(p.plots.size()==3,"Missing houses");for(Plot b:p.plots)check(BuildingPresetRegistry.CLASSIC_IDS.contains(b.builder.presetId),"Classic request selected new preset");
        });
        test("fixed_feasible_loop_has_real_paving_and_all_seven_doors",()->{
            fixture=scene(128,4,"rolling_hills",42,"classic");audit(fixture);PlanningIR p=fixture.plan;
            check(p.plots.size()==7&&"COMPLETE".equals(p.status),"Loop fixture incomplete");
            check(p.transportNetwork.metrics.verifiedLoops>=1&&p.transportNetwork.metrics.cycleRank>=1,"No macro loop");
            check(p.transportNetwork.metrics.pavedEnclosures>=1,"Loop only exists in metadata");
            Files.writeString(out.resolve("loop128-ir.json"),p.toJson(true));
        });
        test("two_real_branch_hubs_and_dendrite_axon_roles",()->{
            var n=fixture.plan.transportNetwork;check(n.metrics.hubs>=2&&n.metrics.branchNodes>=2,"Missing physical branches");boolean found=false;
            for(SemanticNode hub:n.semanticNodes)if(hub.type.equals("hub/plaza")){
                long local=n.semanticLinks.stream().filter(e->e.roadType.equals("local")&&e.toNodeId.equals(hub.id)).count();
                long collectors=n.semanticLinks.stream().filter(e->e.roadType.equals("collector")&&(e.fromNodeId.equals(hub.id)||e.toNodeId.equals(hub.id))).count();
                if(local>=2&&collectors>=1)found=true;
            }check(found,"No center with multiple local branches plus a collector");
            check(n.metrics.roadTypes.keySet().containsAll(Set.of("collector","local","secondary")),"Missing three semantic road types");
        });
        test("long_straight_regression_threshold_075",()->check(fixture.plan.transportNetwork.metrics.longestStraightRatio<.75,"Single long straight trunk"));
        test("micro_ABAB_zigzag_ratio_below_005",()->check(fixture.plan.transportNetwork.metrics.microZigzagRatio<.05,"Micro zigzag network"));
        test("same_seed_byte_stable_world_and_plan_signature",()->{
            var again=scene(128,4,"rolling_hills",42,"classic");check(again.originalTerrainHash.equals(fixture.originalTerrainHash),"Terrain differs");check(again.planHash.equals(fixture.planHash),"Plan differs");
            check(SimulatedSettlementPipeline.hashWorld(again.worldAfter).equals(SimulatedSettlementPipeline.hashWorld(fixture.worldAfter)),"Constructed world differs");
        });
        test("different_plan_seed_changes_topology_not_terrain",()->{
            var roll=scene(128,4,"rolling_hills",43,"classic");audit(roll);check(roll.originalTerrainHash.equals(fixture.originalTerrainHash),"Plan seed leaked into terrain");
            check(!new Gson().toJson(roll.plan.transportNetwork.semanticLinks).equals(new Gson().toJson(fixture.plan.transportNetwork.semanticLinks)),"Only curves changed, semantic graph did not");
            Files.writeString(out.resolve("loop128-roll43-ir.json"),roll.plan.toJson(true));
        });
        test("different_terrain_seed_changes_full_voxel_world",()->{
            var world=TerrainBlockGenerator.generateWorld(128,128,58,4,"rolling_hills",43);check(!SimulatedSettlementPipeline.hashWorld(world).equals(fixture.originalTerrainHash),"Terrain seed ignored");
        });
        test("semantic_edge_tamper_rejected_before_construction",()->{
            var p=PlanningIR.fromJson(fixture.plan.toJson(false));p.transportNetwork.semanticLinks.getFirst().corridorId="visual_only";
            rejects(()->PlanConstruction.prepare(p,null),"Fake edge accepted");
        });
        test("semantic_loop_count_tamper_rejected",()->{
            var p=PlanningIR.fromJson(fixture.plan.toJson(false));p.transportNetwork.metrics.verifiedLoops++;
            rejects(()->PlanConstruction.prepare(p,null),"Inflated loop metric accepted");
        });
        test("missing_loop_pavement_column_rejected",()->{
            var p=PlanningIR.fromJson(fixture.plan.toJson(false));var e=p.transportNetwork.corridors.getLast();RoadStep s=e.steps.get(e.steps.size()/2);
            p.groundColumns.removeIf(c->c.x==s.x&&c.z==s.z);rejects(()->PlanConstruction.prepare(p,null),"Unbuilt loop accepted");
        });
        test("JSON_roundtrip_identical_full_construction_program",()->{
            var p=PlanningIR.fromJson(mixed.plan.toJson(false));check(PlanConstruction.prepare(p,null).equals(PlanConstruction.prepare(mixed.plan,null)),"Consumer disagrees with serialized masks");
        });
        test("tiny_search_budget_stops_without_unbounded_topology",()->{
            PlanRequest q=new PlanRequest();q.presetPalette="mixed";q.searchBudget.pathExpanded=500;q.searchBudget.candidateChecks=1000;
            var p=SettlementPlanner.plan(RegressionMain.flat(0,0,96,96,64),q);
            check(p.search.pathExpanded<=500&&p.search.candidateChecks<=1000&&p.search.topologyEdgesTried<=128,"Exceeded original budget");
        });
        test("reference512_all_seven_hard_constraints_and_straight_threshold",()->{
            reference=scene(512,24,"mountain",42,"classic");audit(reference);check(reference.plan.plots.size()==7,"Reference allocated fewer than seven");
            Files.writeString(out.resolve("reference512-ir.json"),reference.plan.toJson(true));
            check(reference.plan.transportNetwork.metrics.longestStraightRatio<.80,"Reference long straight regression");
        });
        test("reference512_real_loop_secondary_and_no_ABAB",()->{
            var m=reference.plan.transportNetwork.metrics;
            check(m.hubs>=1&&m.branchNodes>=2,"Reference failed hub/branch acceptance: "+m.reasons);
            check(m.verifiedLoops>=1&&m.cycleRank>=1&&m.pavedEnclosures>=1,"Reference has no real loop");
            check(m.microZigzagRatio==0.0,"Reference has A-B-A-B micro turns");
            check(m.roadTypes.keySet().containsAll(Set.of("secondary","local","collector")),"Reference road types incomplete");
        });
        test("legacy_CLI_synthetic_deterministic_no_harmonic_86_diagonal",()->{
            var a=HeightfieldMap.createSynthetic(0,0,512,512,58,24,42);
            var b=HeightfieldMap.createSynthetic(0,0,512,512,58,24,42);
            var other=HeightfieldMap.createSynthetic(0,0,512,512,58,24,43);
            double sa=0,sb=0,saa=0,sbb=0,sab=0;int count=0,different=0;
            for(int x=0;x<512;x++)for(int z=0;z<512;z++){
                check(a.getSurfaceY(x,z)==b.getSurfaceY(x,z)&&a.getObstacle(x,z)==b.getObstacle(x,z),"CLI determinism");
                if(a.getSurfaceY(x,z)!=other.getSurfaceY(x,z))different++;
                if(x<426&&z<426){double u=a.getSurfaceY(x,z),v=a.getSurfaceY(x+86,z+86);sa+=u;sb+=v;saa+=u*u;sbb+=v*v;sab+=u*v;count++;}
            }
            double corr=(sab-sa*sb/count)/Math.sqrt((saa-sa*sa/count)*(sbb-sb*sb/count));
            check(corr<.9&&different>512*512/2,"CLI synthetic periodic or seed ignored: "+corr);
            System.out.println("  CLI shift(86,86) Pearson="+corr+" changed-height-cells="+different);
        });
        test("all_four_terrain_types_full_voxel_determinism_and_seed",()->{
            Set<String> hashes=new HashSet<>();for(String type:List.of("rolling_hills","mountain","valley","plateau")){
                String a=SimulatedSettlementPipeline.hashWorld(TerrainBlockGenerator.generateWorld(64,64,58,24,type,42));
                String b=SimulatedSettlementPipeline.hashWorld(TerrainBlockGenerator.generateWorld(64,64,58,24,type,42));
                String c=SimulatedSettlementPipeline.hashWorld(TerrainBlockGenerator.generateWorld(64,64,58,24,type,43));
                check(a.equals(b)&&!a.equals(c),"Voxel replay/seed failed: "+type);hashes.add(a);
            }check(hashes.size()==4,"Terrain types identical");
        });
        var result=Map.of("passed",passed,"failed",failed,"referenceNetwork",reference==null?new NetworkMetrics():reference.plan.transportNetwork.metrics);
        Files.writeString(out.resolve("results.json"),new Gson().toJson(result));System.out.println("RESULT "+passed+" passed; "+failed+" failed");
        if(failed>0)throw new AssertionError("Organic/preset failures: "+failed);
    }
    private static void protectedShape(String id)throws Exception{
        BuildingPreset b=BuildingPresetRegistry.getInstance().getPreset(id).rotateToFacing("SOUTH");
        int ox=28,oz=26,w=72,d=72,doorX=ox+b.entrance.x,doorZ=oz+b.entrance.z,roadZ=oz+b.sizeZ-1+5;
        HeightfieldMap map=RegressionMain.flat(0,0,w,d,64);for(int x=0;x<w;x++)for(int z=0;z<d;z++)map.setObstacle(x,z,HeightfieldMap.ObstacleType.PROTECTED);
        for(int x=0;x<b.sizeX;x++)for(int z=0;z<b.sizeZ;z++)if(b.occupies(x,z))map.setObstacle(ox+x,oz+z,HeightfieldMap.ObstacleType.NONE);
        for(int z=doorZ;z<=roadZ;z++)map.setObstacle(doorX,z,HeightfieldMap.ObstacleType.NONE);
        for(int x=0;x<=doorX+2;x++)for(int z=roadZ-1;z<=roadZ+1;z++)map.setObstacle(x,z,HeightfieldMap.ObstacleType.NONE);
        BuildingRequirement demand=RegressionMain.demand("shape","residential",1);demand.presetId=id;demand.minWidth=demand.maxWidth=b.sizeX;demand.minDepth=demand.maxDepth=b.sizeZ;
        PlanRequest q=RegressionMain.request(demand);q.entry=new int[]{1,64,roadZ};q.organicRoads=false;
        PlanningIR p=SettlementPlanner.plan(map,q);check(p.plots.size()==1,"Could not place true mask with protected empty space: "+p.toJson(false));
        var a=TerrainAudit.audit(map,q,p);check(a.violations()==0,"Protected mask audit failed");
        for(var e:PlanConstruction.prepare(p,null))check(map.getObstacle(e.x(),e.z())!=HeightfieldMap.ObstacleType.PROTECTED,"Modified protected notch/courtyard");
        check(p.plots.getFirst().footprint.size()==b.footprintArea(),"Filled bounding rectangle");
        Files.writeString(out.resolve(id+"-protected-ir.json"),p.toJson(true));
    }
}
