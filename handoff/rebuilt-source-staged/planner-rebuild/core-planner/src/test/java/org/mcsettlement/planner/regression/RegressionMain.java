package org.mcsettlement.planner.regression;

import org.mcsettlement.planner.*;
import org.mcsettlement.planner.SettlementPlanner.*;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.civil.*;
import org.mcsettlement.planner.preset.*;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.terrain.HeightfieldMap.ObstacleType;
import org.mcsettlement.planner.baseline.BaselineBudget;
import com.google.gson.Gson;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Dependency-light assertions over final Java geometry and actual shared block writes. */
public final class RegressionMain {
    private static int passed,failed;
    private static Path output;
    private static final List<String> csv=new ArrayList<>();
    private static final List<String> notes=new ArrayList<>();
    public record Case(String name,HeightfieldMap map,PlanRequest request,Consumer<PlanningIR> expected) {}
    public record Metrics(int reachable,int violations,int cut,int fill,double roadLength,int roadArea) {}
    private static final int[][] DIR={{1,0},{0,1},{-1,0},{0,-1}};
    public static void main(String[] args)throws Exception {
        output=Path.of(args.length==0?"build/offline-evidence":args[0]);Files.createDirectories(output);
        csv.add("case,algorithm,status,requested,allocated,matched_purpose_units,hard_violations,reachable_buildings,cut,fill,road_length,road_area,elapsed_ms,candidate_checks,path_expanded,model_calls");
        for(Case c:cases())run(c.name,()->scenario(c));
        run("earthwork_no_common_grade",RegressionMain::earthwork);
        run("reproducibility_and_no_request_mutation",RegressionMain::reproducibility);
        run("every_preset_all_rotations_has_walkable_declared_door",RegressionMain::doorways);
        run("explicit_entry_wrong_height_rejected",RegressionMain::badEntry);
        run("invalid_request_and_unavailable_preset",RegressionMain::invalid);
        run("spatial_relation_cycle_is_explained",RegressionMain::cycle);
        run("json_roundtrip_and_consumer_manifest",RegressionMain::roundTrip);
        run("legacy_plan_is_not_constructible",RegressionMain::refuseLegacy);
        Files.write(output.resolve("benchmark.csv"),csv);Files.write(output.resolve("metric-notes.txt"),notes);
        System.out.printf("RESULT %d passed; %d failed%n",passed,failed);
        if(failed>0)throw new AssertionError("Regression failures: "+failed);
    }
    private static void run(String name,Runnable task) {
        try {task.run();passed++;System.out.println("PASS "+name);}
        catch(Throwable t){failed++;System.out.println("FAIL "+name+": "+t);t.printStackTrace(System.out);}
    }
    public static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static HeightfieldMap flat(int x,int z,int w,int d,int y) {
        HeightfieldMap m=new HeightfieldMap(x,z,w,d);
        for(int xx=x;xx<x+w;xx++)for(int zz=z;zz<z+d;zz++)m.setSurfaceY(xx,zz,y);
        return m;
    }
    public static BuildingRequirement demand(String id,String purpose,int count) {
        BuildingRequirement q=new BuildingRequirement();q.id=id;q.purpose=purpose;q.count=count;return q;
    }
    public static PlanRequest request(BuildingRequirement... demands) {
        PlanRequest r=new PlanRequest();r.seed=42;r.requirements=new ArrayList<>(Arrays.asList(demands));
        r.targetPlots=r.requirements.stream().mapToInt(q->q.count).sum();return r;
    }
    private static void complete(PlanningIR p){check("COMPLETE".equals(p.status),"Expected complete, got "+p.status+" "+new Gson().toJson(p.unmetRequirements));}
    private static void incomplete(PlanningIR p){check(!"COMPLETE".equals(p.status),"Expected explicit shortage");check(!p.unmetRequirements.isEmpty(),"Missing shortage explanation");}
    public static List<Case> cases() {
        List<Case> cases=new ArrayList<>();
        cases.add(new Case("flat_mixed",flat(0,0,64,64,64),request(demand("homes","residential",2),demand("forge","workshop",1),demand("hall","government",1)),RegressionMain::complete));
        var slope=flat(0,0,64,64,64);for(int x=0;x<64;x++)for(int z=0;z<64;z++)slope.setSurfaceY(x,z,64+x/12);slope.computeSlopes();
        var sr=request(demand("homes","residential",2),demand("hall","government",1));sr.entry=new int[]{1,64,32};
        cases.add(new Case("limited_slope",slope,sr,RegressionMain::complete));
        var river=flat(0,0,64,64,64);for(int x=45;x<=49;x++)for(int z=0;z<64;z++){river.setObstacle(x,z,ObstacleType.WATER);river.setWaterY(x,z,65);}
        var waterfront=demand("waterfront","commercial",1);waterfront.placement="riverbank";waterfront.maxWaterDistance=8;
        var rr=request(waterfront,demand("homes","residential",2));rr.entry=new int[]{1,64,32};
        cases.add(new Case("riverbank",river,rr,RegressionMain::complete));
        var obstacle=flat(0,0,64,64,64);for(int x=20;x<=38;x++)for(int z=20;z<=38;z++)obstacle.setObstacle(x,z,ObstacleType.EXISTING_BUILDING);
        cases.add(new Case("existing_building_obstacle",obstacle,request(demand("homes","residential",3),demand("forge","workshop",1)),RegressionMain::complete));
        var small=flat(0,0,18,18,64);
        cases.add(new Case("insufficient_space",small,request(demand("homes","residential",3)),RegressionMain::incomplete));
        var disconnected=flat(0,0,64,64,64);
        for(int x=0;x<=16;x++)for(int z=0;z<64;z++)if(!(x<16&&z>=30&&z<=34)&&!(x==16&&z==32))disconnected.setObstacle(x,z,ObstacleType.EXISTING_BUILDING);
        var dr=request(demand("home","residential",1));dr.entry=new int[]{1,64,32};
        // A one-cell PRIVATE doorstep path is legal; the main road still has its full width.
        cases.add(new Case("one_cell_private_access_is_legal",disconnected,dr,RegressionMain::complete));
        var closed=flat(0,0,64,64,64);
        for(int x=0;x<=16;x++)for(int z=0;z<64;z++)if(!(x<16&&z>=30&&z<=34))closed.setObstacle(x,z,ObstacleType.EXISTING_BUILDING);
        var closedRequest=request(demand("home","residential",1));closedRequest.entry=new int[]{1,64,32};
        cases.add(new Case("fully_disconnected",closed,closedRequest,p->{incomplete(p);check(p.plots.isEmpty(),"No passage may cross the protected barrier");}));
        var chapel=demand("chapel","culture",1);chapel.presetId="village_chapel";chapel.minWidth=chapel.maxWidth=13;chapel.minDepth=chapel.maxDepth=9;
        var multi=request(demand("tower","military",1),demand("hall","government",1),demand("home","residential",1),chapel);
        cases.add(new Case("multiple_sizes_and_rotation",flat(0,0,80,72,64),multi,p->{complete(p);check(p.plots.stream().map(q->q.builder.footprintSize[0]).distinct().count()>=3,"Missing size diversity");}));
        var cliff=flat(0,0,64,64,70);for(int x=0;x<10;x++)for(int z=0;z<64;z++)cliff.setSurfaceY(x,z,64);cliff.computeSlopes();
        var er=request(demand("homes","residential",2));er.entry=new int[]{1,64,32};
        cases.add(new Case("entry_elevation_gap",cliff,er,RegressionMain::incomplete));
        var even=request(demand("homes","residential",2),demand("forge","workshop",1));even.roadWidth=2;
        cases.add(new Case("even_width_negative_coordinates",flat(-32,-40,64,64,64),even,RegressionMain::complete));
        var tiny=request(demand("home","residential",1));tiny.searchBudget.candidateChecks=2;tiny.searchBudget.pathExpanded=1;
        cases.add(new Case("tiny_search_budget",flat(0,0,64,64,64),tiny,RegressionMain::incomplete));
        var near=demand("homes","residential",2);near.nearPurpose="workshop";near.maxDistance=26;
        cases.add(new Case("hard_near_purpose_relation",flat(0,0,64,64,64),request(near,demand("forge","workshop",1)),RegressionMain::complete));
        var blockedRiver=flat(0,0,64,64,64);
        for(int x=0;x<=12;x++)for(int z=0;z<64;z++)if(z<30||z>34)blockedRiver.setObstacle(x,z,ObstacleType.EXISTING_BUILDING);
        for(int x=13;x<=19;x++)for(int z=0;z<64;z++){blockedRiver.setObstacle(x,z,ObstacleType.WATER);blockedRiver.setWaterY(x,z,65);}
        var br=request(demand("home","residential",1));br.entry=new int[]{1,64,32};
        cases.add(new Case("bridge_required_rejected",blockedRiver,br,p->{incomplete(p);check(p.plots.isEmpty(),"Unsupported bridge must be rejected");}));
        return cases;
    }
    private static void scenario(Case c) {
        PlanningIR p=SettlementPlanner.plan(c.map,c.request);
        Metrics m=audit(c.map,c.request,p);
        write(c.name+"-new.json",p.toJson(true));
        csv.add(row(c.name,"bounded",p.status,c.request,p,m,p.search.elapsedNanos,p.search.candidateChecks,p.search.pathExpanded,Integer.toString(m.reachable)));
        // Run baseline even when a new-planner business expectation fails.
        baseline(c);
        check(m.violations==0,"Hard constraint violations: "+m.violations);
        check(m.reachable==p.plots.size(),"An allocated building is not reachable through the built road/door passage");
        check(p.search.candidateChecks<=c.request.searchBudget.candidateChecks,"Candidate budget exceeded");
        check(p.search.pathExpanded<=c.request.searchBudget.pathExpanded,"Path budget exceeded");
        check(p.search.modelCalls==0,"Core geometry must be offline");
        c.expected.accept(p);
    }
    private static void write(String name,String text) {try{Files.writeString(output.resolve(name),text);}catch(Exception e){throw new RuntimeException(e);}}
    private static String row(String name,String algorithm,String status,PlanRequest r,PlanningIR p,Metrics m,long nanos,int candidates,int expanded,String reached) {
        return String.format(Locale.ROOT,"%s,%s,%s,%d,%d,%d,%d,%s,%d,%d,%.3f,%d,%.3f,%d,%d,0",
                name,algorithm,status,r.targetPlots,p.plots.size(),matched(r,p),m.violations,reached,m.cut,m.fill,m.roadLength,m.roadArea,nanos/1e6,candidates,expanded);
    }
    private static int matched(PlanRequest r,PlanningIR p) {
        Map<String,Integer> counts=new HashMap<>();
        for(Plot plot:p.plots){String purpose=plot.tags.isEmpty()?"unknown":plot.tags.getFirst();if(purpose.equals("landmark"))purpose="government";counts.merge(purpose,1,Integer::sum);}
        int matched=0;for(BuildingRequirement q:r.requirements){int n=Math.min(q.count,counts.getOrDefault(q.purpose,0));matched+=n;counts.merge(q.purpose,-n,Integer::sum);}return matched;
    }
    private static void baseline(Case c) {
        var request=new org.mcsettlement.planner.baseline.SettlementPlanner.PlanRequest();
        request.seed=c.request.seed;request.targetPlots=c.request.targetPlots;request.roadWidth=c.request.roadWidth;
        request.settlementStyle=c.request.settlementStyle;
        request.parcelConfig.maxCutBudget=c.request.parcelConfig.maxCutBudget;request.parcelConfig.maxFillBudget=c.request.parcelConfig.maxFillBudget;
        request.parcelConfig.maxGroundSlope=c.request.parcelConfig.maxGroundSlope;request.parcelConfig.minPlotSpacing=c.request.parcelConfig.minPlotSpacing;
        request.parcelConfig.roadSetback=c.request.parcelConfig.roadSetback;
        BaselineBudget.reset(c.request.searchBudget.candidateChecks,c.request.searchBudget.pathExpanded);
        long start=System.nanoTime();PlanningIR result;String status="LEGACY_UNVALIDATED";
        try{result=org.mcsettlement.planner.baseline.SettlementPlanner.plan(c.map,request);}
        catch(BaselineBudget.Limit limit){result=new PlanningIR();status="BUDGET_EXCEEDED";}
        catch(Exception exception){result=new PlanningIR();status="LEGACY_EXCEPTION";notes.add(c.name+": "+exception);}
        long elapsed=System.nanoTime()-start;
        Metrics metrics=auditLegacy(c.map,c.request,result);
        write(c.name+"-baseline.json",result.toJson(true));
        csv.add(row(c.name,"legacy",status,c.request,result,metrics,elapsed,BaselineBudget.candidateChecks,BaselineBudget.pathExpanded,"NA"));
    }
    private static Metrics auditLegacy(HeightfieldMap map,PlanRequest r,PlanningIR p) {
        int failures=0;Set<String> bad=new HashSet<>();Set<Long> road=new HashSet<>();double length=0;Set<String> segments=new HashSet<>();
        for(RoadEdge e:p.transportNetwork.edges) {
            // The ORIGINAL consumer stamps -width/2 .. +width/2 inclusively (even widths overshoot).
            if(e.width%2==0)bad.add("even_width_overshoot:"+e.id);
            for (int j=1;j<e.steps.size();j++) {
                var a=e.steps.get(j-1);var b=e.steps.get(j);
                long ak=RoadGeometry.key(a.x,a.z),bk=RoadGeometry.key(b.x,b.z);
                if (segments.add(Math.min(ak,bk)+":"+Math.max(ak,bk))) length+=Math.hypot(a.x-b.x,a.z-b.z);
            }
            for(int i=0;i<e.steps.size();i++) {
                RoadStep s=e.steps.get(i);
                for(int dx=-e.width/2;dx<=e.width/2;dx++)for(int dz=-e.width/2;dz<=e.width/2;dz++) {
                    int x=s.x+dx,z=s.z+dz;road.add(RoadGeometry.key(x,z));
                    if(!map.inBounds(x,z) || map.getObstacle(x,z)==ObstacleType.EXISTING_BUILDING)bad.add("road_footprint:"+x+":"+z);
                }
                if(i>0){RoadStep prev=e.steps.get(i-1);if(Math.abs(prev.y-s.y)>1)bad.add("vertical_jump:"+e.id+":"+i);}
            }
        }
        int cut=0,fill=0;
        for(Plot q:p.plots) {
            int x0=q.polygon2D.get(0)[0],z0=q.polygon2D.get(0)[1],x1=q.polygon2D.get(2)[0],z1=q.polygon2D.get(2)[1];
            for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++) {
                if(!map.inBounds(x,z)){bad.add("plot_bounds:"+q.id);continue;}
                if(map.getObstacle(x,z)==ObstacleType.EXISTING_BUILDING)bad.add("plot_protection:"+q.id);
                if(road.contains(RoadGeometry.key(x,z)))bad.add("plot_road_collision:"+q.id);
                int delta=map.getSurfaceY(x,z)-q.elevation.baseElevation;
                cut+=Math.max(0,delta);fill+=Math.max(0,-delta);
                if(delta>r.parcelConfig.maxCutBudget || -delta>r.parcelConfig.maxFillBudget)bad.add("plot_earthwork:"+q.id);
            }
            var preset=BuildingPresetRegistry.getInstance().resolveBestPreset("",q.tags.getFirst(),r.settlementStyle,q.builder.subSeed).rotateToFacing(q.entrance.facing);
            if(preset.sizeX>x1-x0+1 || preset.sizeZ>z1-z0+1)bad.add("preset_clipping:"+q.id);
        }
        failures=bad.size();notes.add("Legacy "+p.metadata.randomSeed+": detected="+bad+"; actual door reachability/bridge physics unverified; cut/fill excludes unreported roads.");
        return new Metrics(-1,failures,cut,fill,length,road.size());
    }

    /** Independent audit: expanded footprints, final block writes, graph adjacency and flood fill. */
    public static Metrics audit(HeightfieldMap map,PlanRequest r,PlanningIR p) {
        Set<String> errors=new LinkedHashSet<>();
        Map<Long,GroundColumn> cols=new HashMap<>();int cut=0,fill=0;
        for(GroundColumn c:p.groundColumns) {
            long k=RoadGeometry.key(c.x,c.z);
            if(cols.put(k,c)!=null)errors.add("duplicate_column");
            if(!map.inBounds(c.x,c.z)){errors.add("out_of_bounds");continue;}
            var o=map.getObstacle(c.x,c.z);
            if(o!=ObstacleType.NONE&&o!=ObstacleType.VEGETATION&&o!=ObstacleType.TREE_TRUNK)errors.add("protected_or_water_column");
            if(c.originalY!=map.getSurfaceY(c.x,c.z))errors.add("wrong_original_height");
            int delta=c.originalY-c.targetY;
            int mc="foundation".equals(c.kind)?r.parcelConfig.maxCutBudget:r.roadMaxCut;
            int mf="foundation".equals(c.kind)?r.parcelConfig.maxFillBudget:r.roadMaxFill;
            if(delta>mc||-delta>mf)errors.add("cell_cut_fill_limit");
            cut+=Math.max(0,delta);fill+=Math.max(0,-delta);
        }
        if(cut!=p.earthworks.totalCutVolume||fill!=p.earthworks.totalFillVolume)errors.add("earthwork_totals");
        Set<Long> roads=new HashSet<>(),walk=new HashSet<>();int roadLength=0;
        Set<String> centerSegments=new HashSet<>();
        for(RoadEdge corridor:p.transportNetwork.corridors) {
            if(corridor.width!=r.roadWidth)errors.add("corridor_width_changed");
            for(int i=0;i<corridor.steps.size();i++) {
                RoadStep s=corridor.steps.get(i);
                if(!"surface".equals(s.structure))errors.add("unsupported_road_structure");
                if(i>0){RoadStep prev=corridor.steps.get(i-1);if(Math.abs(s.x-prev.x)+Math.abs(s.z-prev.z)!=1||s.y!=prev.y)errors.add("non_cardinal_or_unbuildable_grade");
                    long a=RoadGeometry.key(prev.x,prev.z),b=RoadGeometry.key(s.x,s.z);centerSegments.add(Math.min(a,b)+":"+Math.max(a,b));}
                int low=-r.roadWidth/2;
                for(int dx=low;dx<low+r.roadWidth;dx++)for(int dz=low;dz<low+r.roadWidth;dz++) {
                    long k=RoadGeometry.key(s.x+dx,s.z+dz);roads.add(k);GroundColumn c=cols.get(k);
                    if(c==null||!"road".equals(c.kind)||c.targetY!=s.y)errors.add("full_width_road_missing");
                }
            }
        }
        roadLength=centerSegments.size();walk.addAll(roads);
        Map<String,BuildingRequirement> requirements=new HashMap<>();for(var q:r.requirements)requirements.put(q.id,q);
        Set<Long> plotCells=new HashSet<>();
        for(Plot q:p.plots) {
            var demand=requirements.get(q.requirementId);
            int x0=q.polygon2D.get(0)[0],z0=q.polygon2D.get(0)[1],x1=q.polygon2D.get(2)[0],z1=q.polygon2D.get(2)[1];
            if(demand!=null) {
                int w=x1-x0+1,d=z1-z0+1;
                if(w<demand.minWidth||w>demand.maxWidth||d<demand.minDepth||d>demand.maxDepth)errors.add("size_requirement");
                if(!q.tags.contains(demand.purpose))errors.add("purpose_requirement");
                if(demand.presetId!=null&&!demand.presetId.equals(q.builder.presetId))errors.add("locked_preset_requirement");
                if("riverbank".equals(demand.placement)&&distanceToWater(map,x0,z0,x1,z1)>demand.maxWaterDistance)errors.add("riverbank_relation");
                if(demand.nearPurpose!=null) {
                    boolean found=false;
                    for(Plot other:p.plots)if(other!=q&&other.tags.contains(demand.nearPurpose)) {
                        int ox=other.polygon2D.get(0)[0]+other.polygon2D.get(2)[0],oz=other.polygon2D.get(0)[1]+other.polygon2D.get(2)[1];
                        if(Math.abs(x0+x1-ox)+Math.abs(z0+z1-oz)<=2*demand.maxDistance)found=true;
                    }
                    if(!found)errors.add("near_purpose_relation");
                }
            }
            for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++) {
                long k=RoadGeometry.key(x,z);if(!plotCells.add(k))errors.add("plot_overlap");if(roads.contains(k))errors.add("plot_road_overlap");
                if(!cols.containsKey(k)||!"foundation".equals(cols.get(k).kind))errors.add("foundation_missing");
            }
            int[] door=q.entrance.accessPoint;
            if(q.entrance.path.isEmpty() || q.entrance.path.get(0).x!=door[0] || q.entrance.path.get(0).z!=door[2])errors.add("door_path_start");
            for(int i=0;i<q.entrance.path.size();i++) {
                RoadStep s=q.entrance.path.get(i);walk.add(RoadGeometry.key(s.x,s.z));
                if(i>0){RoadStep prev=q.entrance.path.get(i-1);if(Math.abs(prev.x-s.x)+Math.abs(prev.z-s.z)!=1||prev.y!=s.y)errors.add("entrance_path_not_contiguous");}
            }
            var edge=p.transportNetwork.edges.stream().filter(e->e.id.equals(q.entrance.connectedEdgeId)).findFirst();
            if(edge.isEmpty()||edge.get().steps.stream().noneMatch(s->s.x==door[0]&&s.z==door[2]))errors.add("false_door_edge_reference");
        }
        // Existing plot spacing independent of the production predicate.
        for(int i=0;i<p.plots.size();i++)for(int j=i+1;j<p.plots.size();j++) {
            var a=p.plots.get(i).polygon2D;var b=p.plots.get(j).polygon2D;int gap=r.parcelConfig.minPlotSpacing;
            if(a.get(0)[0]-gap<=b.get(2)[0]&&a.get(2)[0]+gap>=b.get(0)[0]&&a.get(0)[1]-gap<=b.get(2)[1]&&a.get(2)[1]+gap>=b.get(0)[1])errors.add("plot_spacing");
        }
        Map<Long,Integer> elevations=new HashMap<>();for(RoadNode n:p.transportNetwork.nodes)elevations.put(RoadGeometry.key(n.pos[0],n.pos[2]),n.pos[1]);
        if(!elevations.keySet().equals(walk))errors.add("topology_nodes_not_actual_pavement");
        Set<String> expectedEdges=new HashSet<>(),actualEdges=new HashSet<>();
        for(long k:walk)for(int[] dir:DIR){long n=RoadGeometry.key(RoadGeometry.x(k)+dir[0],RoadGeometry.z(k)+dir[1]);if(walk.contains(n))expectedEdges.add(Math.min(k,n)+":"+Math.max(k,n));}
        for(RoadEdge e:p.transportNetwork.edges) {
            if(e.steps.size()!=2){errors.add("unsplit_topological_edge");continue;}
            RoadStep a=e.steps.get(0),b=e.steps.get(1);long ak=RoadGeometry.key(a.x,a.z),bk=RoadGeometry.key(b.x,b.z);
            if(!actualEdges.add(Math.min(ak,bk)+":"+Math.max(ak,bk)))errors.add("duplicate_topology_edge");
            if(!e.fromNodeId.equals("n_"+a.x+"_"+a.z)||!e.toNodeId.equals("n_"+b.x+"_"+b.z))errors.add("topology_endpoint_mismatch");
        }
        if(!expectedEdges.equals(actualEdges))errors.add("missing_or_false_topology_adjacency");
        Map<PlanConstruction.Cell,String> blocks=new HashMap<>();
        if(!p.plots.isEmpty()) {
            var edits=PlanConstruction.prepare(p,r.settlementStyle);
            PlanConstruction.apply(edits,(x,y,z,block)->blocks.put(new PlanConstruction.Cell(x,y,z),block));
            for(var edit:edits){if(!cols.containsKey(RoadGeometry.key(edit.x(),edit.z())))errors.add("write_outside_manifest");}
        }
        for(long k:walk) {
            Integer y=elevations.get(k);if(y==null)continue;
            if(!walkable(blocks,map,RoadGeometry.x(k),y,RoadGeometry.z(k)))errors.add("actual_written_headroom_or_floor");
        }
        Set<Long> visited=new HashSet<>();ArrayDeque<Long> queue=new ArrayDeque<>();
        var entry=p.transportNetwork.nodes.stream().filter(n->"entry".equals(n.type)).findFirst();
        if(entry.isPresent()){long k=RoadGeometry.key(entry.get().pos[0],entry.get().pos[2]);queue.add(k);visited.add(k);}
        while(!queue.isEmpty()){
            long k=queue.remove();int y=elevations.get(k);
            for(int[] d:DIR){long next=RoadGeometry.key(RoadGeometry.x(k)+d[0],RoadGeometry.z(k)+d[1]);
                if(walk.contains(next)&&!visited.contains(next)&&elevations.get(next)==y&&walkable(blocks,map,RoadGeometry.x(next),y,RoadGeometry.z(next))){visited.add(next);queue.add(next);}}
        }
        int reachable=0;for(Plot q:p.plots)if(visited.contains(RoadGeometry.key(q.entrance.accessPoint[0],q.entrance.accessPoint[2])))reachable++;
        if(reachable!=p.plots.size())errors.add("unreachable_actual_door");
        if(!p.status.equals("COMPLETE")&&!p.status.equals("INVALID_REQUEST")) {
            if(p.unmetRequirements.isEmpty())errors.add("missing_failure_explanation");
            for(var u:p.unmetRequirements)if(u.reason==null||u.reason.isBlank()||u.allocated>=u.requested)errors.add("invalid_failure_explanation");
        }
        if(!errors.isEmpty())System.out.println("AUDIT "+errors);
        return new Metrics(reachable,errors.size(),cut,fill,roadLength,roads.size());
    }
    private static int distanceToWater(HeightfieldMap m,int x0,int z0,int x1,int z1){int best=Integer.MAX_VALUE;for(int x=m.getMinX();x<m.getMinX()+m.getWidth();x++)for(int z=m.getMinZ();z<m.getMinZ()+m.getDepth();z++)if(m.getObstacle(x,z)==ObstacleType.WATER||m.getObstacle(x,z)==ObstacleType.WATER_DEEP)best=Math.min(best,Math.max(0,Math.max(x0-x,x-x1))+Math.max(0,Math.max(z0-z,z-z1)));return best;}
    private static boolean walkable(Map<PlanConstruction.Cell,String> edits,HeightfieldMap m,int x,int y,int z){String floor=block(edits,m,x,y,z);return floor.equals("minecraft:cobblestone")&&block(edits,m,x,y+1,z).equals("minecraft:air")&&block(edits,m,x,y+2,z).equals("minecraft:air");}
    private static String block(Map<PlanConstruction.Cell,String> edits,HeightfieldMap m,int x,int y,int z){String v=edits.get(new PlanConstruction.Cell(x,y,z));if(v!=null)return v;return y<=m.getSurfaceY(x,z)?"minecraft:stone":"minecraft:air";}
    private static void earthwork(){var m=flat(0,0,3,3,64);m.setSurfaceY(2,2,80);var a=EarthworkOptimizer.optimizePlotFoundation(m,0,0,2,2,64,2,2);check(!a.feasible,"Infeasible elevation must not return success");check(a.reason!=null,"Missing earthwork reason");m.setSurfaceY(2,2,67);var b=EarthworkOptimizer.optimizePlotFoundation(m,0,0,2,2,64,2,2);check(b.feasible,"Expected common grade");for(int x=0;x<3;x++)for(int z=0;z<3;z++)check(Math.abs(m.getSurfaceY(x,z)-b.optimalBaseY)<=2,"Per-cell cap");}
    private static void reproducibility(){Case c=cases().get(0);String before=new Gson().toJson(c.request);var a=SettlementPlanner.plan(c.map,c.request);var b=SettlementPlanner.plan(c.map,c.request);a.metadata.timestamp=b.metadata.timestamp="";a.search.elapsedNanos=b.search.elapsedNanos=0;check(a.toJson(false).equals(b.toJson(false)),"Same inputs differ");check(before.equals(new Gson().toJson(c.request)),"Caller request was mutated");}
    private static void doorways(){for(var b:BuildingPresetRegistry.getInstance().getAllPresets())for(String facing:List.of("NORTH","EAST","SOUTH","WEST")){var v=b.rotateToFacing(facing);var grid=PlannedBuilding.grid(v,null);check(grid[v.entrance.x][0][v.entrance.z].equals("minecraft:cobblestone"),"Door floor");check(grid[v.entrance.x][1][v.entrance.z].equals("minecraft:air")&&grid[v.entrance.x][2][v.entrance.z].equals("minecraft:air"),"Door headroom");}}
    private static void badEntry(){var r=request(demand("home","residential",1));r.entry=new int[]{1,70,20};var p=SettlementPlanner.plan(flat(0,0,40,40,64),r);check(p.status.equals("INFEASIBLE")&&p.plots.isEmpty(),"Explicit entry was silently moved");}
    private static void invalid(){var r=request(demand("home","residential",1));r.roadWidth=0;check(SettlementPlanner.plan(flat(0,0,32,32,64),r).status.equals("INVALID_REQUEST"),"Bad width accepted");r.roadWidth=3;r.requirements.get(0).presetId="not-a-preset";var p=SettlementPlanner.plan(flat(0,0,32,32,64),r);check(!p.unmetRequirements.isEmpty()&&p.unmetRequirements.get(0).reason.contains("PRESET"),"Missing preset fallback hid unmet demand");}
    private static void cycle(){var a=demand("house","residential",1);a.nearPurpose="workshop";var b=demand("forge","workshop",1);b.nearPurpose="residential";var p=SettlementPlanner.plan(flat(0,0,64,64,64),request(a,b));check(p.status.equals("INFEASIBLE")&&p.unmetRequirements.size()==2,"Dependency cycle not explained");}
    private static void roundTrip(){Case c=cases().get(0);var p=SettlementPlanner.plan(c.map,c.request);var q=PlanningIR.fromJson(p.toJson(false));check(audit(c.map,c.request,q).violations==0,"JSON changed construction semantics");check(PlanConstruction.prepare(p,"medieval_rustic").equals(PlanConstruction.prepare(q,"medieval_rustic")),"Edit programs differ after JSON roundtrip");}
    private static void refuseLegacy(){try{PlanConstruction.prepare(new PlanningIR(),"medieval_rustic");throw new AssertionError("Legacy IR accepted");}catch(IllegalArgumentException expected){check(expected.getMessage().contains("PLAN_NOT_CONSTRUCTIBLE"),"Wrong rejection");}}
}
