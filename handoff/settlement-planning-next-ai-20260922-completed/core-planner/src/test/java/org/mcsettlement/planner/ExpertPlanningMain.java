package org.mcsettlement.planner;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.mcsettlement.planner.SettlementPlanner.*;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.terrain.HeightfieldMap.ObstacleType;
import org.mcsettlement.planner.civil.PlanConstruction;
import org.mcsettlement.planner.simulation.*;
import org.mcsettlement.planner.simulation.SimulatedVoxelWorld.VoxelType;

/** Executable full-pipeline and adversarial fixtures; no mocked terrain, router or construction. */
public final class ExpertPlanningMain {
    private static final List<Map<String,Object>> checks=new ArrayList<>();
    private static Path out;
    private interface Test { void run() throws Exception; }
    private static void test(String name,Test t){try{t.run();checks.add(Map.of("name",name,"passed",true));System.out.println("PASS "+name);}catch(Throwable ex){checks.add(Map.of("name",name,"passed",false,"error",ex.toString()));System.out.println("FAIL "+name+" "+ex);ex.printStackTrace();}}
    private static void require(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private static void invalid(Test t)throws Exception{try{t.run();}catch(IllegalArgumentException ex){return;}throw new AssertionError("Expected invalid argument");}
    private static final String BRIDGE="[{\"id\":\"home\",\"x\":12,\"z\":20,\"facing\":\"EAST\",\"connectTo\":\"other\"},{\"id\":\"other\",\"x\":76,\"z\":72,\"facing\":\"WEST\"},{\"id\":\"third\",\"x\":76,\"z\":10,\"facing\":\"WEST\"}]";
    private static final String WATER="[{\"id\":\"waterhome\",\"x\":43,\"z\":48,\"facing\":\"WEST\",\"medium\":\"water\"},{\"id\":\"left\",\"x\":12,\"z\":16,\"facing\":\"EAST\"},{\"id\":\"right\",\"x\":76,\"z\":16,\"facing\":\"WEST\"},{\"id\":\"pier\",\"kind\":\"dock\",\"x\":50,\"z\":82}]";
    private static PlanRequest request(String json){PlanRequest r=new PlanRequest();r.targetPlots=3;r.entry=new int[]{2,64,46};r.presetPalette="single";r.singlePresetId="square_cabin";r.expert=new ExpertSettings();r.expert.autoDock=false;r.expert.pins=ExpertSettings.parsePins(json);return r;}
    private static HeightfieldMap map(boolean river,int bed){var m=new HeightfieldMap(0,0,96,96);for(int x=0;x<96;x++)for(int z=0;z<96;z++){boolean wet=river&&x>=40&&x<=55;m.setSurfaceY(x,z,wet?bed:64);if(wet){m.setWaterY(x,z,63);m.setObstacle(x,z,ObstacleType.WATER_DEEP);}}m.computeSlopes();return m;}
    private static void complete(HeightfieldMap m,PlanRequest r,PlanningIR ir){require("COMPLETE".equals(ir.status),ir.status+" "+ir.sitePlanning.reasons+" "+ir.sitePlanning.attempts);require(ir.plots.size()==r.targetPlots,"building count");ExpertTerrainAudit.validate(m,r,ir);require(!PlanConstruction.prepare(ir,"medieval").isEmpty(),"empty construction");require(ir.search.candidateChecks<=r.searchBudget.candidateChecks&&ir.search.pathExpanded<=r.searchBudget.pathExpanded&&ir.search.gradeRelaxations<=r.searchBudget.gradeRelaxations,"global budget exceeded");}
    private static void rejected(PlanningIR ir){require(!"COMPLETE".equals(ir.status)&&ir.plots.isEmpty()&&ir.groundColumns.isEmpty(),"rejected plan committed partial edits");}
    private static PlanningIR copy(PlanningIR ir){return PlanningIR.fromJson(ir.toJson(false));}
    private static Plot tinyPlot(int x,int z){Plot p=new Plot();p.origin2D=new int[]{x,z};p.footprint.add(new int[]{0,0});return p;}
    private static String geometry(PlanningIR ir){var g=new Gson();return g.toJson(ir.plots)+g.toJson(ir.groundColumns)+g.toJson(ir.transportNetwork)+g.toJson(ir.sitePlanning);}
    public static void main(String[] args)throws Exception{
        out=Path.of(args.length==0?"build/site-expert":args[0]);Files.createDirectories(out);
        var river=map(true,58);var bridgeReq=request(BRIDGE);var bridge=SettlementPlanner.plan(river,bridgeReq);
        var waterReq=request(WATER);var water=SettlementPlanner.plan(river,waterReq);
        test("river-bridge-full-width-forced-target",()->{complete(river,bridgeReq,bridge);require(bridge.sitePlanning.bridgeColumns>0&&bridge.sitePlanning.pileColumns>0,"missing bridge/piers");Files.writeString(out.resolve("river-bridge-PlanningIR.json"),bridge.toJson(true));});
        test("pinned-water-house-and-mandatory-dock",()->{complete(river,waterReq,water);require(water.sitePlanning.waterBuildingCount==1&&water.sitePlanning.docks==1,"missing water house/dock");Files.writeString(out.resolve("water-house-dock-PlanningIR.json"),water.toJson(true));});
        test("raised-manifest-applies-actual-voxels-without-filling-river",()->{
            complete(river,waterReq,water);var world=new SimulatedVoxelWorld(0,48,0,96,48,96);
            for(int x=0;x<96;x++)for(int z=0;z<96;z++){int bed=river.getSurfaceY(x,z);for(int y=48;y<=bed;y++)world.setBlock(x,y,z,VoxelType.STONE);if(RoadTerrain.wet(river,x,z))for(int y=bed+1;y<=63;y++)world.setBlock(x,y,z,VoxelType.WATER);}
            var edits=PlanConstruction.prepare(water,"medieval");long count=water.groundColumns.stream().mapToLong(PlanConstruction::columnEditCount).sum();require(count==edits.size(),"edit count not bounded by manifest");
            Map<String,Long> materials=new TreeMap<>();for(var e:edits){require(world.inBounds(e.x(),e.y(),e.z()),"edit outside voxel world");materials.merge(e.block(),1L,Long::sum);VoxelType type=e.block().equals("minecraft:air")?VoxelType.AIR:e.block().equals("minecraft:oak_log")?VoxelType.OAK_LOG:e.block().contains("stairs")?VoxelType.COBBLESTONE_STAIRS:e.block().contains("planks")?VoxelType.SPRUCE_PLANKS:VoxelType.COBBLESTONE;world.setBlock(e.x(),e.y(),e.z(),type);}
            int kept=0,piers=0;
            for(var c:water.groundColumns)if(RoadTerrain.raised(c)){for(int y=c.originalY+1;y<c.targetY;y++){require(world.getBlock(c.x,y,c.z)==(c.support?VoxelType.OAK_LOG:VoxelType.WATER),"water filled or pier absent");if(c.support)piers++;else kept++;}require(world.getBlock(c.x,c.targetY,c.z).isSolid,"deck absent");}
            require(kept>0&&piers>0,"did not exercise both supported and unsupported columns");
            Files.writeString(out.resolve("actual-construction.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("editCount",edits.size(),"retainedWaterVoxels",kept,"pierVoxels",piers,"materials",materials,"worldAfterHash",SimulatedSettlementPipeline.hashWorld(world))));
        });
        test("fresh-world-preflight-allows-only-matching-wet-manifest",()->{
            for(var c:bridge.groundColumns)require(RoadTerrain.matchesFreshTerrain(river,c,true),"valid fresh bridge rejected");
            var c=bridge.groundColumns.stream().filter(RoadTerrain::raised).findFirst().orElseThrow();
            require(!RoadTerrain.matchesFreshTerrain(river,c,false),"legacy manifest allowed wet construction");
            var changed=map(true,58);changed.setWaterY(c.x,c.z,62);require(!RoadTerrain.matchesFreshTerrain(changed,c,true),"changed water level ignored");
            changed=map(true,58);changed.setObstacle(c.x,c.z,ObstacleType.PROTECTED);require(!RoadTerrain.matchesFreshTerrain(changed,c,true),"protected water ignored");
        });
        test("exact-pin-origin-facing-y-and-required-corridor",()->{var r=request(BRIDGE);r.expert.pins.getFirst().y=64;var ir=SettlementPlanner.plan(river,r);complete(river,r,ir);var p=ir.plots.stream().filter(x->"home".equals(x.requirementId)).findFirst().orElseThrow();require(Arrays.equals(p.origin2D,new int[]{12,20})&&"EAST".equals(p.builder.sourceFacing)&&p.elevation.baseElevation==64,"pin moved");require(ir.sitePlanning.requiredConnections.stream().anyMatch(c->c.from.equals("home")&&c.to.equals("other")&&c.status.startsWith("VERIFIED")),"forced target absent");});
        test("deterministic-repeat-excludes-only-timestamp-and-timing",()->require(geometry(bridge).equals(geometry(SettlementPlanner.plan(river,request(BRIDGE)))),"nondeterministic geometry or decisions"));
        test("schema050-json-roundtrip-same-edit-program",()->require(PlanConstruction.prepare(bridge,"medieval").equals(PlanConstruction.prepare(copy(bridge),"medieval")),"roundtrip changed edits"));
        test("bridge-disabled-no-silent-infill-or-disconnected-complete",()->{var r=request(BRIDGE);r.expert.allowBridges=false;rejected(SettlementPlanner.plan(river,r));});
        test("max-continuous-water-span-is-enforced",()->{var r=request(BRIDGE);r.expert.maxBridgeSpan=4;rejected(SettlementPlanner.plan(river,r));});
        test("deep-water-piles-over16-rejected",()->rejected(SettlementPlanner.plan(map(true,40),request(BRIDGE))));
        test("explicit-cliff-not-confused-with-derived-shore-abutment",()->{var m=map(true,58);for(int z=0;z<96;z++)m.setObstacle(39,z,ObstacleType.STEEP_CLIFF);rejected(SettlementPlanner.plan(m,request(BRIDGE)));});
        test("protected-stripe-is-never-crossed",()->{var m=map(true,58);for(int z=0;z<96;z++)m.setObstacle(39,z,ObstacleType.PROTECTED);rejected(SettlementPlanner.plan(m,request(BRIDGE)));});
        test("pinned-overlap-is-rejected-not-moved",()->{var r=request(BRIDGE);r.expert.pins.get(1).x=12;r.expert.pins.get(1).z=20;rejected(SettlementPlanner.plan(river,r));});
        test("impossible-locked-height-is-rejected-not-adjusted",()->{var r=request(BRIDGE);r.expert.pins.getFirst().y=90;rejected(SettlementPlanner.plan(river,r));});
        test("mandatory-dock-on-land-is-rejected-not-dropped",()->{var r=request(WATER);r.expert.pins.getLast().x=30;rejected(SettlementPlanner.plan(river,r));});
        test("high-coverage-threshold-is-not-lowered",()->{var r=request(BRIDGE);r.expert.minBBoxCoverage=.85;rejected(SettlementPlanner.plan(river,r));});
        test("diagonal-large-bbox-still-rejected-by-axis-and-hull",()->{var r=request(WATER);r.expert.pins.get(2).z=76;var ir=SettlementPlanner.plan(river,r);rejected(ir);require(ir.sitePlanning.bboxCoverage>.25&&ir.sitePlanning.minorAxisRatio<.3&&ir.sitePlanning.hullCoverage<.0875,"did not exercise bbox gaming");});
        test("tiny-building-cluster-rejected-before-routing",()->{var r=request("[{\"id\":\"a\",\"x\":10,\"z\":10},{\"id\":\"b\",\"x\":25,\"z\":10},{\"id\":\"c\",\"x\":10,\"z\":25}]");var ir=SettlementPlanner.plan(map(false,58),r);rejected(ir);require(ir.search.siteLayoutsRouted==0,"route search spent on inadequate bbox");});
        test("coverage-uses-buildings-only-not-network-extents",()->{var report=new SitePlanning();SiteMetrics.measure(new HeightfieldMap(0,0,100,100),List.of(tinyPlot(10,10),tinyPlot(80,10),tinyPlot(10,80)),new ExpertSettings(),report);require(Math.abs(report.bboxCoverage-.5041)<1e-9&&Math.abs(report.hullCoverage-.245)<1e-9,"wrong building bbox or center hull");});
        test("non-square-map-axis-normalization",()->{var settings=new ExpertSettings();var a=new SitePlanning();var b=new SitePlanning();SiteMetrics.measure(new HeightfieldMap(0,0,100,100),List.of(tinyPlot(10,10),tinyPlot(80,10),tinyPlot(10,80)),settings,a);SiteMetrics.measure(new HeightfieldMap(0,0,200,100),List.of(tinyPlot(20,10),tinyPlot(160,10),tinyPlot(20,80)),settings,b);require(Math.abs(a.minorAxisRatio-b.minorAxisRatio)<1e-9,"rectangular map incorrectly marked elongated");});
        for(String field:List.of("candidate","path","grade","edits"))test("budget-"+field+"-atomic-rejection",()->{var r=request(BRIDGE);switch(field){case "candidate"->r.searchBudget.candidateChecks=1;case "path"->r.searchBudget.pathExpanded=1;case "grade"->r.searchBudget.gradeRelaxations=1;case "edits"->r.searchBudget.constructionEdits=1;}var ir=SettlementPlanner.plan(river,r);rejected(ir);require(ir.search.candidateChecks<=r.searchBudget.candidateChecks&&ir.search.pathExpanded<=r.searchBudget.pathExpanded&&ir.search.gradeRelaxations<=r.searchBudget.gradeRelaxations,"budget overshoot");});
        for(String json:List.of("{}","[{\"id\":\"a\",\"x\":1.5,\"z\":2}]","[{\"id\":\"a\",\"x\":1,\"x\":2,\"z\":2}]","[{\"id\":\"a\",\"x\":1,\"z\":2,\"extra\":1}]","[{id:'a',x:1,z:2}]","[] /*comment*/","[{\"id\":2,\"x\":1,\"z\":2}]","[{\"id\":\"a\",\"x\":2147483648,\"z\":2}]"))test("strict-pin-json-"+checks.size(),()->invalid(()->ExpertSettings.parsePins(json)));
        test("unknown-target-rejected",()->{var s=new ExpertSettings();s.pins=ExpertSettings.parsePins("[{\"id\":\"a\",\"x\":1,\"z\":2,\"connectTo\":\"absent\"}]");invalid(()->s.validate(96,96,0,0,7));});
        test("unknown-preset-rejected",()->{var s=new ExpertSettings();s.pins=ExpertSettings.parsePins("[{\"id\":\"a\",\"x\":1,\"z\":2,\"presetId\":\"absent\"}]");invalid(()->s.validate(96,96,0,0,7));});
        test("duplicate-pin-id-rejected",()->{var s=new ExpertSettings();s.pins=ExpertSettings.parsePins("[{\"id\":\"a\",\"x\":1,\"z\":2},{\"id\":\"a\",\"x\":30,\"z\":40}]");invalid(()->s.validate(96,96,0,0,7));});
        test("nonfinite-expert-parameters-rejected",()->{var s=new ExpertSettings();s.minBBoxCoverage=Double.NaN;invalid(()->s.validate(96,96,0,0,7));});
        test("pin-outside-map-rejected",()->{var r=request(BRIDGE);r.expert.pins.getFirst().x=100;invalid(()->r.expert.validate(96,96,0,0,3));});
        test("pinned-building-count-over-target-rejected",()->{var r=request(BRIDGE);invalid(()->r.expert.validate(96,96,0,0,2));});
        test("water-pin-cannot-be-silently-converted-to-land",()->{var r=request(WATER);r.expert.allowBridges=false;invalid(()->r.expert.validate(96,96,0,0,3));});
        for(String tamper:List.of("water","support","version","pin","coverage","required"))test("tampered-"+tamper+"-rejected",()->{var ir=copy(bridge);var c=ir.groundColumns.stream().filter(RoadTerrain::raised).findFirst().orElseThrow();switch(tamper){case "water"->c.waterY--;case "support"->c.support=!c.support;case "version"->ir.metadata.version="0.4.0";case "pin"->ir.plots.getFirst().origin2D[0]++;case "coverage"->ir.sitePlanning.bboxCoverage=1;case "required"->ir.sitePlanning.requiredConnections.getFirst().corridorId="nonexistent";}invalid(()->{ExpertTerrainAudit.validate(river,bridgeReq,ir);PlanConstruction.prepare(ir,"medieval");});});
        for(int width:new int[]{1,5})test("bridge-width-"+width,()->{var r=request(BRIDGE);r.roadWidth=width;var ir=SettlementPlanner.plan(river,r);complete(river,r,ir);require(ir.transportNetwork.corridors.stream().allMatch(c->c.width==width),"width dropped");});
        test("three-auto-buildings-use-three-regions-not-center-plus-two",()->{
            var e=new ExpertSettings();var result=SimulatedSettlementPipeline.run(128,128,58,12,"rolling_hills",42,44,3,8,false,new SearchBudget(),"classic","square_cabin",TerrainParameters.fromOverrides("rolling_hills",Map.of("horizontalScale","1.5","detailStrength",".5","waterLevelRatio","0")),e);
            var req=new PlanRequest();req.expert=e;req.targetPlots=3;complete(result.heightfield,req,result.plan);
        });
        for(int[] dims:List.of(new int[]{128,128,12,42,8},new int[]{192,96,24,43,12}))test("generated-terrain-"+dims[0]+"x"+dims[1]+"-"+dims[4]+"directions",()->{
            var e=new ExpertSettings();var result=SimulatedSettlementPipeline.run(dims[0],dims[1],58,dims[2],"rolling_hills",42,dims[3],7,dims[4],dims[4]==12,new SearchBudget(),"mixed","square_cabin",TerrainParameters.defaults("rolling_hills"),e);
            var req=new PlanRequest();req.expert=e;req.targetPlots=7;complete(result.heightfield,req,result.plan);require(result.constructionEdits>0&&!SimulatedSettlementPipeline.hashWorld(result.worldBefore).equals(SimulatedSettlementPipeline.hashWorld(result.worldAfter)),"construction did not alter actual world");
            Files.writeString(out.resolve("generated-"+dims[0]+"x"+dims[1]+".json"),result.plan.toJson(true));
        });
        Files.writeString(out.resolve("results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("checks",checks,"passed",checks.stream().filter(c->Boolean.TRUE.equals(c.get("passed"))).count(),"total",checks.size())));
        if(checks.stream().anyMatch(c->!Boolean.TRUE.equals(c.get("passed"))))throw new AssertionError("Expert regression failed");
        System.out.println("ALL "+checks.size()+" EXPERT CHECKS PASSED");
    }
}
