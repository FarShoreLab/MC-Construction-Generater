package org.mcsettlement.planner;

import java.util.*;
import java.nio.file.*;
import com.google.gson.Gson;
import org.mcsettlement.planner.SettlementPlanner.*;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.preset.*;
import org.mcsettlement.planner.regression.*;
import org.mcsettlement.planner.singlegrade.SingleGradeReference;
import org.mcsettlement.planner.civil.PlanConstruction;
import org.mcsettlement.planner.simulation.*;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import static org.mcsettlement.planner.regression.RegressionMain.*;
import static org.mcsettlement.planner.RoadGeometry.*;

/** Executable, dependency-light tests for IR 0.3. Not a replacement for Minecraft runtime tests. */
public final class AdaptiveTerrainMain {
    private static int passed,failed;private static Path output;private static final List<Map<String,Object>> results=new ArrayList<>();
    private static final List<String> comparisons=new ArrayList<>(List.of("case,algorithm,requested,allocated,foundation_cells,cut,fill,total_earthwork,stairs,candidate_checks,path_expanded,elapsed_ms,equal_count_comparison"));
    private static void run(String name,Runnable action){long start=System.nanoTime();Map<String,Object> row=new LinkedHashMap<>();row.put("name",name);
        try{action.run();passed++;row.put("passed",true);System.out.println("PASS "+name);}catch(Throwable e){failed++;row.put("passed",false);row.put("error",e.toString());System.out.println("FAIL "+name+": "+e);e.printStackTrace(System.out);}row.put("elapsedMs",(System.nanoTime()-start)/1e6);results.add(row);}
    private static void save(String name,PlanningIR p){try{Files.writeString(output.resolve(name+".json"),p.toJson(false));}catch(Exception e){throw new RuntimeException(e);}}
    private static PlanningIR audited(HeightfieldMap m,PlanRequest r){PlanningIR p=SettlementPlanner.plan(m,r);check(TerrainAudit.audit(m,r,p).violations()==0,"Hard/physical audit failed");return p;}
    public static void main(String[] args)throws Exception{
        output=Path.of(args.length>0?args[0]:"build/adaptive-evidence");Files.createDirectories(output);
        run("swept_stencils_8_12_all_widths_all_quadrants",AdaptiveTerrainMain::stencils);
        for(int dirs:new int[]{8,12}){
            run("real_diagonal_network_"+dirs,()->road(dirs,false));
            run("height_state_stairs_"+dirs,()->road(dirs,true));
            run("contour_extension_"+dirs,()->contour(dirs));
            for(var obstacle:List.of(HeightfieldMap.ObstacleType.PROTECTED,HeightfieldMap.ObstacleType.WATER,HeightfieldMap.ObstacleType.EXISTING_BUILDING))
                run("diagonal_width_corner_blocks_"+dirs+"_"+obstacle,()->blockedCorner(dirs,obstacle));
        }
        run("independent_platforms_and_raw_stair_blocks",AdaptiveTerrainMain::platforms);
        run("diagonal_building_toggle_actual_footprint_and_construction",AdaptiveTerrainMain::diagonalBuildings);
        run("all_40_diagonal_preset_doors_and_whole_footprints",AdaptiveTerrainMain::diagonalDoors);
        run("manifest_missing_corner_or_stair_orientation_rejected",AdaptiveTerrainMain::tamper);
        run("explicit_state_column_edit_and_grade_caps",AdaptiveTerrainMain::caps);
        run("fair_locked_preset_equal_count_earthwork_and_regression_reporting",AdaptiveTerrainMain::fair);
        for(int[] size:List.of(new int[]{128,128},new int[]{256,256},new int[]{512,512},new int[]{192,96},new int[]{511,257}))run("large_map_"+size[0]+"x"+size[1],()->large(size[0],size[1]));
        run("large_map_replay_and_request_immutability",AdaptiveTerrainMain::determinism);
        run("separate_terrain_plan_seeds_and_four_terrain_types",AdaptiveTerrainMain::terrainSeeds);
        run("invalid_map_heading_budget_rejected_before_allocation",AdaptiveTerrainMain::invalid);
        run("response_size_guard_rejects_without_truncating",AdaptiveTerrainMain::responseGuard);
        run("diagonal_multilevel_randomized_40_invariant_cases",AdaptiveTerrainMain::randomized);
        Files.writeString(output.resolve("adaptive-results.json"),new Gson().toJson(results));Files.write(output.resolve("equal-count-earthwork.csv"),comparisons);
        System.out.println("RESULT "+passed+" passed; "+failed+" failed");if(failed>0)throw new AssertionError("Adaptive failures="+failed);
    }
    private static void stencils(){for(int dirs:new int[]{8,12})for(int width=1;width<=5;width++)for(int[] d:directions(dirs)){
        Set<Long> expected=TerrainAudit.swept(-7,-9,-7+d[0],-9+d[1],width),actual=new HashSet<>();for(int[] p:stencil(d[0],d[1],width))actual.add(key(-7+p[0],-9+p[1]));check(actual.equals(expected),"Missed swept/corner cells "+dirs+"/"+width+"/"+Arrays.toString(d));}}
    private static TerrainRoadRouter.Result route(HeightfieldMap m,int dirs,int width,int sx,int sz,int gx,int gz,SearchStats st){
        PlanRequest r=request(demand("dummy","residential",1));r.roadDirections=dirs;r.roadWidth=width;r.searchBudget.pathExpanded=60000;r.searchBudget.pathStates=30000;
        st.pathLimit=60000;st.stateLimit=30000;st.gradeLimit=2000000;st.columnLimit=40000;st.editLimit=500000;
        Set<Long> roads=new TreeSet<>();Map<Long,GroundColumn> fixed=new TreeMap<>();int gy=m.getSurfaceY(gx,gz),sy=m.getSurfaceY(sx,sz);
        for(int[] d:stencil(0,0,width)){int x=gx+d[0],z=gz+d[1];long k=key(x,z);roads.add(k);fixed.put(k,new GroundColumn(x,z,m.getSurfaceY(x,z),gy,Math.max(gy+3,m.getSurfaceY(x,z)),"road"));}
        RoadStep from=new RoadStep(sx,sy,sz,"surface");fixed.put(key(sx,sz),new GroundColumn(sx,sz,sy,sy,sy+3,"access"));List<RoadStep> access=List.of(from);
        return TerrainRoadRouter.route(m,r,Set.of(),roads,fixed,access,List.of(access),List.of(),new int[]{gx,gy,gz},distanceField(m,roads),st,60000);
    }
    private static void road(int dirs,boolean slope){var m=flat(0,0,64,64,64);if(slope)for(int x=0;x<64;x++)for(int z=0;z<64;z++)m.setSurfaceY(x,z,64+x/10);m.computeSlopes();SearchStats stats=new SearchStats();
        var result=route(m,dirs,3,52,28,4,4,stats);check(result!=null,"No full-width route "+dirs+" slope="+slope);
        long diagonal=0,rises=0;for(int i=1;i<result.centers().size();i++){var a=result.centers().get(i-1);var b=result.centers().get(i);if(a.x!=b.x&&a.z!=b.z)diagonal++;if(a.y!=b.y)rises++;}
        check(diagonal>0,"No actual diagonal segment");check(!slope||rises>0&&result.walk().values().stream().anyMatch(RoadGeometry::stair),"Height changes did not produce stairs");
        for(var c:result.walk().values()){check(buildable(m,c.x,c.z),"Invalid sweep");check(Math.abs(c.targetY-c.originalY)<=3,"Cut/fill cap");if(stair(c))check(PlanConstruction.pavementBlock(c).contains("shape=straight"),"Not a physical stair");}
        if(dirs==12)check(result.centers().stream().anyMatch(s->s.x!=52),"No route movement");
    }
    private static void contour(int dirs){var m=flat(0,0,64,64,64);for(int x=0;x<64;x++)for(int z=0;z<64;z++)m.setSurfaceY(x,z,64+(x+z)/8);m.computeSlopes();var r=route(m,dirs,3,48,12,12,48,new SearchStats());check(r!=null,"No contour route");long changes=r.centers().stream().filter(s->s.y!=71).count();check(changes<=r.centers().size()/4,"Contour unnecessarily gains/loses altitude");}
    private static void blockedCorner(int dirs,HeightfieldMap.ObstacleType obstacle){
        for(int width:new int[]{1,2,3,4,5}){var m=flat(0,0,24,24,64);for(int x=0;x<24;x++)for(int z=0;z<24;z++)if(Math.abs(x-z)>width-1)m.setObstacle(x,z,obstacle);
            var r=route(m,dirs,width,19,19,4,4,new SearchStats());check(r==null,"Swept diagonal tunneled through corner / wide-footprint obstacle width="+width);}
    }
    private static PlanRequest homes(int count){var q=demand("homes","residential",count);q.presetId="nordic_cottage";return request(q);}
    private static HeightfieldMap incline(int divisor){var m=flat(0,0,64,64,64);for(int x=0;x<64;x++)for(int z=0;z<64;z++)m.setSurfaceY(x,z,64+x/divisor);m.computeSlopes();return m;}
    private static void platforms(){var m=incline(12);var r=homes(4);r.entry=new int[]{1,64,32};PlanningIR p=audited(m,r);check(p.plots.size()==4,"Building count regressed");check(p.plots.stream().map(q->q.elevation.baseElevation).distinct().count()>1,"Single global platform grade");
        var edits=PlanConstruction.prepare(p,null);check(edits.stream().anyMatch(e->e.block().startsWith("minecraft:cobblestone_stairs[")),"No physical stairs");save("multilevel-platforms",p);}
    private static void diagonalBuildings(){var m=flat(0,0,80,80,64);var r=homes(2);r.diagonalBuildings=true;r.roadDirections=12;
        var shape=new BuildingShape(BuildingPresetRegistry.getInstance().getAllPresets().stream().filter(p->p.id.equals("nordic_cottage")).findFirst().orElseThrow(),true);
        r.requirements.getFirst().minWidth=shape.sizeX;r.requirements.getFirst().maxWidth=shape.sizeX;r.requirements.getFirst().minDepth=shape.sizeZ;r.requirements.getFirst().maxDepth=shape.sizeZ;
        var p=audited(m,r);check(p.plots.size()==2,"Cannot place real diagonal buildings");check(p.plots.stream().allMatch(q->q.builder.diagonal45),"Toggle only changed graphics");for(Plot q:p.plots)check(q.footprint.size()<q.builder.footprintSize[0]*q.builder.footprintSize[1],"Still filled AABB");save("diagonal-buildings-12",p);
        r.diagonalBuildings=false;var disabled=audited(m,r);check(disabled.plots.isEmpty()&&disabled.unmetRequirements.getFirst().reason.contains("PRESET"),"Disabled diagonal shape still used / silently clipped");}
    private static void diagonalDoors(){for(var preset:BuildingPresetRegistry.getInstance().getAllPresets())for(String f:List.of("NORTH","EAST","SOUTH","WEST")){
        var shape=new BuildingShape(preset.rotateToFacing(f),true);var grid=shape.grid(null);check(shape.cells.size()>=preset.sizeX*preset.sizeZ*.85,"Footprint cropped");check(shape.cells.size()<=preset.sizeX*preset.sizeZ*1.15,"Unexpected raster area");
        check(grid[shape.entranceX][0][shape.entranceZ].equals("minecraft:cobblestone")&&grid[shape.entranceX][1][shape.entranceZ].equals("minecraft:air")&&grid[shape.entranceX][2][shape.entranceZ].equals("minecraft:air"),"Blocked transformed door");
        for(int x=0;x<shape.sizeX;x++)for(int z=0;z<shape.sizeZ;z++)if(!shape.contains(x,z))for(int y=0;y<shape.sizeY;y++)check("minecraft:air".equals(grid[x][y][z]),"Building outside rotated mask");}}
    private static void reject(Runnable r){try{r.run();throw new AssertionError("Malformed input accepted");}catch(IllegalArgumentException expected){}}
    private static void tamper(){var r=homes(4);r.entry=new int[]{1,64,32};var p=audited(incline(12),r);
        var q=PlanningIR.fromJson(p.toJson(false));q.groundColumns.remove(q.groundColumns.stream().filter(c->"road".equals(c.kind)).findFirst().orElseThrow());reject(()->PlanConstruction.prepare(q,null));
        var q2=PlanningIR.fromJson(p.toJson(false));GroundColumn stair=q2.groundColumns.stream().filter(RoadGeometry::stair).findFirst().orElseThrow();stair.facing=switch(stair.facing){case "NORTH"->"SOUTH";case "EAST"->"WEST";case "SOUTH"->"NORTH";default->"EAST";};reject(()->PlanConstruction.prepare(q2,null));
        var q3=PlanningIR.fromJson(p.toJson(false));q3.groundColumns.add(q3.groundColumns.getFirst());reject(()->PlanConstruction.prepare(q3,null));}
    private static void caps(){for(String name:List.of("states","columns","edits","grades")){var r=homes(2);r.searchBudget.attempts=1;r.searchBudget.candidateChecks=2000;r.searchBudget.pathExpanded=5000;
        switch(name){case "states"->r.searchBudget.pathStates=1;case "columns"->r.searchBudget.groundColumns=4;case "edits"->r.searchBudget.constructionEdits=10;case "grades"->r.searchBudget.gradeRelaxations=1;}
        var p=audited(flat(0,0,64,64,64),r);check(p.search.budgetExhausted,"Missing cap explanation "+name);check(p.groundColumns.size()<=r.searchBudget.groundColumns,"Column cap");check(p.search.peakPathStates<=r.searchBudget.pathStates,"State cap");if(!p.plots.isEmpty())check(PlanConstruction.prepare(p,null).size()<=r.searchBudget.constructionEdits,"Edit cap");}}
    private static void fair(){for(int divisor:new int[]{8,12,16,20}){var m=incline(divisor);var r=homes(4);r.entry=new int[]{1,64,32};var old=SingleGradeReference.plan(m,r);var now=audited(m,r);check(TerrainAudit.audit(m,r,old).violations()==0,"Reference violates hard constraints");
        boolean equal=old.plots.size()==now.plots.size()&&old.groundColumns.stream().filter(c->"foundation".equals(c.kind)).count()==now.groundColumns.stream().filter(c->"foundation".equals(c.kind)).count();
        for(boolean newer:new boolean[]{false,true}){var p=newer?now:old;comparisons.add(String.format(Locale.ROOT,"slope_x_div_%d,%s,4,%d,%d,%d,%d,%d,%d,%d,%d,%.3f,%s",divisor,newer?"adaptive":"provided_single_grade",p.plots.size(),p.groundColumns.stream().filter(c->"foundation".equals(c.kind)).count(),p.earthworks.totalCutVolume,p.earthworks.totalFillVolume,p.earthworks.totalCutVolume+p.earthworks.totalFillVolume,p.groundColumns.stream().filter(RoadGeometry::stair).count(),p.search.candidateChecks,p.search.pathExpanded,p.search.elapsedNanos/1e6,equal));save("equal-count-slope-"+divisor+"-"+(newer?"adaptive":"provided"),p);}
        if(divisor==12){check(equal&&now.plots.size()==4,"Improvement compared unequal counts/areas");check(now.earthworks.totalCutVolume+now.earthworks.totalFillVolume<old.earthworks.totalCutVolume+old.earthworks.totalFillVolume,"No measured earthwork improvement");}
        // x/8 is deliberately retained even when completion regresses: do not cherry-pick it away.
    }}
    private static void large(int w,int d){var r=homes(3);r.searchBudget.candidateChecks=6000;r.searchBudget.pathExpanded=60000;r.searchBudget.attempts=2;r.roadDirections=12;r.entry=new int[]{2,64,2};var p=audited(flat(0,0,w,d,64),r);check(p.plots.size()==3,"Flat large-map shortage");save("large-"+w+"x"+d,p);}
    private static void determinism(){var m=flat(-17,-33,192,96,64);var r=homes(3);r.diagonalBuildings=true;r.roadDirections=12;r.searchBudget.candidateChecks=6000;r.searchBudget.pathExpanded=60000;String requestBefore=new Gson().toJson(r);var a=audited(m,r);var b=audited(m,r);
        a.metadata.timestamp=b.metadata.timestamp="";a.search.elapsedNanos=b.search.elapsedNanos=0;check(a.toJson(false).equals(b.toJson(false)),"Same-input IR is not bit-stable");check(requestBefore.equals(new Gson().toJson(r)),"Request mutated");check(PlanConstruction.prepare(a,null).equals(PlanConstruction.prepare(b,null)),"Construction replay differs");}
    private static void terrainSeeds(){for(String type:List.of("rolling_hills","mountain","valley","plateau")){var a=SimulatedSettlementPipeline.run(48,48,58,18,type,42,42,2);var b=SimulatedSettlementPipeline.run(48,48,58,18,type,42,43,2);check(a.originalTerrainHash.equals(b.originalTerrainHash),"Plan roll changed terrain "+type);check(TerrainAudit.audit(a.heightfield,defaultRequest(42,2),a.plan).violations()==0,"Terrain physical audit "+type);
        var different=TerrainBlockGenerator.generateWorld(48,48,58,18,type,43);check(!a.originalTerrainHash.equals(SimulatedSettlementPipeline.hashWorld(different)),"Terrain seed ignored");}}
    private static PlanRequest defaultRequest(long seed,int target){var r=new PlanRequest();r.seed=seed;r.targetPlots=target;return r;}
    private static void invalid(){var r=homes(1);check(SettlementPlanner.plan(flat(0,0,513,32,64),r).status.equals("INVALID_REQUEST"),"Oversize accepted");r.roadDirections=10;check(SettlementPlanner.plan(flat(0,0,32,32,64),r).status.equals("INVALID_REQUEST"),"Ambiguous heading accepted");r.roadDirections=8;r.searchBudget.pathStates=200001;check(SettlementPlanner.plan(flat(0,0,32,32,64),r).status.equals("INVALID_REQUEST"),"Invalid state cap accepted");reject(()->TerrainBlockGenerator.generateWorld(513,512,58,24,"plateau",42));}
    private static void responseGuard(){try{SimulationApiRunner.encode(Map.of("oversize","x".repeat(SimulationApiRunner.MAX_RESPONSE_BYTES)));throw new AssertionError("Response cap missing");}catch(IllegalArgumentException expected){}catch(java.io.IOException e){throw new RuntimeException(e);}}
    private static void randomized(){int count=0;for(int seed=0;seed<40;seed++){var m=flat(-24,-24,48,48,64);Random random=new Random(seed);for(int x=-24;x<24;x++)for(int z=-24;z<24;z++)m.setSurfaceY(x,z,64+(x+24)/12+(z+24)/20);for(int i=0;i<8;i++){int x=random.nextInt(45)-24,z=random.nextInt(45)-24;for(int dx=0;dx<3;dx++)for(int dz=0;dz<3;dz++)m.setObstacle(x+dx,z+dz,i%3==0?HeightfieldMap.ObstacleType.PROTECTED:i%3==1?HeightfieldMap.ObstacleType.WATER:HeightfieldMap.ObstacleType.EXISTING_BUILDING);}m.computeSlopes();
        var r=homes(2);r.seed=seed;r.diagonalBuildings=true;r.roadDirections=seed%2==0?8:12;r.roadWidth=1+seed%5;r.searchBudget.candidateChecks=4000;r.searchBudget.pathExpanded=40000;var p=audited(m,r);count+=p.plots.size();}check(count>0,"Vacuous no-build randomized test");System.out.println("RANDOMIZED allocated="+count+" / 80");}
}
