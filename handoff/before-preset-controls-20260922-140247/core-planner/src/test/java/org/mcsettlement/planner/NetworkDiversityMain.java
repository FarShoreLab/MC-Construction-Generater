package org.mcsettlement.planner;

import com.google.gson.*;
import java.nio.file.*;
import java.lang.reflect.Method;
import java.util.*;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.SettlementPlanner.*;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.terrain.HeightfieldMap.ObstacleType;
import org.mcsettlement.planner.civil.PlanConstruction;
import org.mcsettlement.planner.simulation.*;
import static org.mcsettlement.planner.RoadGeometry.*;

/** Real router / site selection / construction regression. No fabricated API or preview geometry. */
public final class NetworkDiversityMain {
    private static final List<Map<String,Object>> checks=new ArrayList<>();
    private static Path out;
    private interface Test {void run() throws Exception;}
    private static void test(String name,Test run){try{run.run();checks.add(Map.of("name",name,"passed",true));System.out.println("PASS "+name);}catch(Throwable e){checks.add(Map.of("name",name,"passed",false,"error",e.toString()));System.out.println("FAIL "+name+" "+e);e.printStackTrace();}}
    private static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    private static void invalid(Test run)throws Exception{try{run.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("Expected rejection");}
    private static HeightfieldMap flat(int w,int d){var m=new HeightfieldMap(0,0,w,d);for(int x=0;x<w;x++)for(int z=0;z<d;z++)m.setSurfaceY(x,z,64);m.computeSlopes();return m;}
    private static PlanRequest request(int n,int dirs){var r=new PlanRequest();r.targetPlots=n;r.roadDirections=dirs;r.presetPalette="mixed";r.expert=new ExpertSettings();r.expert.autoDock=false;return r;}
    private static List<RoadStep> line(int x,int z,int dx,int dz,int length){var p=new ArrayList<RoadStep>();for(int i=0;i<=length;i++)p.add(new RoadStep(x+dx*i,64,z+dz*i,"surface"));return p;}
    private static RoadEdge edge(String id,List<RoadStep> steps){var e=new RoadEdge();e.id=id;e.width=3;e.steps=steps;return e;}
    private static void complete(HeightfieldMap m,PlanRequest r,PlanningIR ir){require("COMPLETE".equals(ir.status),ir.status+" "+ir.sitePlanning.reasons+" "+ir.sitePlanning.attempts);require(ir.plots.size()==r.targetPlots,"plot count");ExpertTerrainAudit.validate(m,r,ir);require(!PlanConstruction.prepare(ir,"medieval").isEmpty(),"empty actual edits");require(ir.search.pathExpanded<=r.searchBudget.pathExpanded&&ir.search.candidateChecks<=r.searchBudget.candidateChecks&&ir.search.gradeRelaxations<=r.searchBudget.gradeRelaxations,"shared budget exceeded");}
    private static Plot plot(int x,int z){var p=new Plot();p.origin2D=new int[]{x,z};p.footprint.add(new int[]{0,0});return p;}
    private record Reuse(TerrainRoadRouter.Result result,RoadEdge original,NetworkMetrics metrics,SearchStats stats){}
    private static Reuse reuse(int radius){var m=flat(112,96);var r=request(0,16);r.expert.roadMergeDistance=radius;var old=new ArrayList<>(line(10,40,1,0,80));old.addAll(line(90,41,0,1,3));
        Set<Long> roads=footprint(old,3);Map<Long,GroundColumn> fixed=new TreeMap<>();for(long k:roads)fixed.put(k,RoadTerrain.column(m,x(k),z(k),64,"road",67));
        var stats=new SearchStats();stats.pathLimit=180000;stats.gradeLimit=2000000;stats.columnLimit=40000;stats.editLimit=500000;Set<Long> goals=Set.of(key(90,44));
        var result=TerrainRoadRouter.routeTo(m,r,Set.of(),roads,fixed,List.of(new RoadStep(10,64,44,"surface")),List.of(),List.of(old),new int[]{10,64,40},distanceField(m,goals),stats,180000,goals,null);
        require(result!=null,"reuse fixture route failed");var nm=new NetworkMetrics();RoadNetworkAnalysis.centerlines(List.of(edge("old",old),edge("new",result.centers())),nm,7);return new Reuse(result,edge("old",old),nm,stats);
    }
    public static void main(String[] args)throws Exception{
        out=Path.of(args.length==0?"build/network-diversity":args[0]);Files.createDirectories(out);
        test("mixed-union-is-16-unique-directions-not-20",()->{Set<String> union=new HashSet<>();for(int n:new int[]{8,12})for(int[] d:directions(n))union.add(Arrays.toString(d));Set<String> actual=new HashSet<>();for(int[] d:directions(16))actual.add(Arrays.toString(d));require(union.size()==16&&actual.equals(union),"incorrect direction union");});
        test("mixed-directions-rotational-and-sign-symmetry",()->{Set<Long> ds=new HashSet<>();for(int[] d:directions(16))ds.add(key(d[0],d[1]));for(int[] d:directions(16))require(ds.contains(key(-d[0],-d[1]))&&ds.contains(key(-d[1],d[0])),"asymmetric directions");});
        test("mixed-state-key-no-sentinel-or-heading-alias",()->{Method method=TerrainRoadRouter.class.getDeclaredMethod("id",int.class,int.class,int.class,int.class,int.class,int.class,int.class);method.setAccessible(true);Set<Long> ids=new HashSet<>();for(int cell=100;cell<103;cell++)for(int y=61;y<65;y++)for(int h=0;h<=16;h++)for(int rise=-1;rise<=1;rise++)for(int run:new int[]{0,1,24})for(int wet:new int[]{0,1,96})for(int land:new int[]{0,1,32})require(ids.add((Long)method.invoke(null,cell,y,h,rise,run,wet,land)),"state collision");});
        for(int width:new int[]{1,3,5})test("mixed-all-swept-brushes-connected-width-"+width,()->{for(int[] dir:directions(16)){var cells=footprint(List.of(new RoadStep(20,64,20,"surface"),new RoadStep(20+dir[0],64,20+dir[1],"surface")),width);require(cells.contains(key(20,20))&&cells.contains(key(20+dir[0],20+dir[1])),"endpoint omitted");Set<Long> seen=new HashSet<>();ArrayDeque<Long> q=new ArrayDeque<>();q.add(cells.iterator().next());seen.add(q.peek());while(!q.isEmpty()){long k=q.remove();for(int[] d:CARDINAL){long t=key(x(k)+d[0],z(k)+d[1]);if(cells.contains(t)&&seen.add(t))q.add(t);}}require(seen.size()==cells.size(),"diagonal hole");}});
        var m=flat(112,96);var s=new SearchStats();var field=new RoadProximity(m,List.of(line(10,40,1,0,80)),7,s);
        test("parallel-proximity-penalty-and-radius-limit",()->{require(field.cost(44*112+50,64,1,0,false,false)>0,"near parallel free");require(field.cost(50*112+50,64,1,0,false,false)==0,"outside radius penalized");require(s.roadProximityCellVisits<=112*96,"field unbounded");});
        test("crossings-different-height-and-endpoint-exempt",()->{require(field.cost(44*112+50,64,0,1,false,false)==0,"perpendicular crossing penalized");require(field.cost(44*112+50,67,1,0,false,false)==0,"different level merged");require(field.cost(44*112+50,64,1,0,true,false)==0,"endpoint not exempt");});
        test("zero-radius-disables-neighbor-penalty",()->{var f=new RoadProximity(m,List.of(line(10,40,1,0,80)),0,new SearchStats());require(f.cost(41*112+50,64,1,0,false,false)==0,"radius0 active");});
        test("centerline-reuse-not-new-parallel-paving",()->{var a=reuse(0);var b=reuse(7);require(b.metrics.sharedCenterlineLength>a.metrics.sharedCenterlineLength+20,"no increased shared centerline");require(b.result.roadCells().size()<a.result.roadCells().size(),"new road area did not shrink");require(b.metrics.nearParallelLength<a.metrics.nearParallelLength,"parallel warning not reduced");Files.writeString(out.resolve("reuse-comparison.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("disabled",a,"enabled",b)));});
        test("duplicate-segments-counted-once-and-crossings-not-parallel",()->{var nm=new NetworkMetrics();var a=line(10,40,1,0,60);RoadNetworkAnalysis.centerlines(List.of(edge("a",a),edge("same",a),edge("cross",line(40,10,0,1,60))),nm,7);require(nm.uniqueCenterlineLength==120&&nm.sharedCenterlineLength==60&&nm.nearParallelLength==0,"duplicate/crossing metric");});
        test("large-real-rectangle-enclosure",()->{Set<Long> road=new HashSet<>();for(var line:List.of(line(20,20,1,0,60),line(80,20,0,1,60),line(80,80,-1,0,60),line(20,80,0,-1,60)))road.addAll(footprint(line,3));require(RoadNetworkAnalysis.enclosures(flat(112,112),road,3,null).size()==1,"missing macro rectangle");});
        test("narrow-horizontal-ribbon-is-not-a-macro-loop",()->{Set<Long> road=new HashSet<>();for(var l:List.of(line(20,40,1,0,60),line(80,40,0,1,8),line(80,48,-1,0,60),line(20,48,0,-1,8)))road.addAll(footprint(l,3));require(RoadNetworkAnalysis.enclosures(m,road,3,null).isEmpty(),"thin parallel pair counted as macro");});
        test("diagonal-ribbon-cannot-game-large-aabb",()->{Set<Long> road=new HashSet<>();for(var l:List.of(line(20,20,1,1,60),line(80,80,0,1,9),line(80,89,-1,-1,60),line(20,29,0,-1,9)))road.addAll(footprint(l,3));require(RoadNetworkAnalysis.enclosures(flat(112,112),road,3,null).isEmpty(),"rotated ribbon counted");});
        test("open-tree-has-no-enclosure",()->{var road=footprint(line(15,15,1,1,60),3);require(RoadNetworkAnalysis.enclosures(m,road,3,null).isEmpty(),"tree counted as loop");});
        test("spacing-detects-1-2-3-3-groups-despite-coverage",()->{List<Plot> grouped=List.of(plot(128,128),plot(20,20),plot(26,20),plot(210,20),plot(218,20),plot(215,28),plot(20,210),plot(27,210),plot(24,217));var report=new SitePlanning();SiteMetrics.measure(flat(256,256),grouped,new ExpertSettings(),report);require(SiteMetrics.failures(9,report).isEmpty()&&report.closePairs>=7&&report.crowdingPenalty>0,"crowding missed");});
        test("spacing-normalized-for-non-square-map",()->{var a=new SitePlanning();var b=new SitePlanning();SiteMetrics.measure(flat(100,100),List.of(plot(10,10),plot(15,15),plot(80,80)),new ExpertSettings(),a);SiteMetrics.measure(flat(200,100),List.of(plot(20,10),plot(30,15),plot(160,80)),new ExpertSettings(),b);require(Math.abs(a.crowdingPenalty-b.crowdingPenalty)<1e-9,"rectangular bias");});
        for(double value:new double[]{-1,3.1,Double.NaN,Double.POSITIVE_INFINITY})test("invalid-repulsion-"+value,()->{var e=new ExpertSettings();e.buildingRepulsion=value;invalid(()->e.validate(128,128,0,0,7));});
        for(int value:new int[]{-1,17})test("invalid-merge-radius-"+value,()->{var e=new ExpertSettings();e.roadMergeDistance=value;invalid(()->e.validate(128,128,0,0,7));});
        var pins=request(3,16);pins.entry=new int[]{2,64,50};pins.expert.minBBoxCoverage=0;pins.expert.minMinorAxisRatio=0;pins.expert.buildingRepulsion=3;
        pins.expert.pins=ExpertSettings.parsePins("[{\"id\":\"a\",\"x\":15,\"z\":15,\"connectTo\":\"b\"},{\"id\":\"b\",\"x\":32,\"z\":15},{\"id\":\"c\",\"x\":15,\"z\":32}]");
        var pinned=SettlementPlanner.plan(m,pins);
        test("close-manual-pins-override-soft-repulsion",()->{complete(m,pins,pinned);require(pinned.sitePlanning.closePairs>0&&pinned.search.siteRepairsAccepted==0,"pins repaired or not crowded");});
        test("forced-connection-persists-with-mixed-directions",()->{complete(m,pins,pinned);require(pinned.sitePlanning.requiredConnections.stream().anyMatch(c->c.from.equals("a")&&c.to.equals("b")&&c.status.startsWith("VERIFIED")),"mandatory link lost");});
        for(String change:List.of("spacing","loop","parallel"))test("independent-audit-rejects-forged-"+change,()->{complete(m,pins,pinned);var copy=PlanningIR.fromJson(pinned.toJson(false));switch(change){case "spacing"->copy.sitePlanning.crowdingPenalty+=1;case "loop"->copy.sitePlanning.macroLoops++;case "parallel"->copy.transportNetwork.metrics.nearParallelLength+=20;}invalid(()->ExpertTerrainAudit.validate(m,pins,copy));});
        for(int budget:new int[]{1,40})test("small-shared-path-budget-atomic-stop-"+budget,()->{var r=request(9,16);r.searchBudget.pathExpanded=budget;var ir=SettlementPlanner.plan(flat(128,128),r);require(!ir.status.equals("COMPLETE")&&ir.plots.isEmpty()&&ir.groundColumns.isEmpty()&&ir.search.pathExpanded<=budget,"partial edits or budget minted");});
        for(int[] c:List.of(new int[]{256,256,54,9,8,24},new int[]{512,512,41,9,8,24},new int[]{512,512,50,9,8,24},new int[]{256,256,54,9,16,24},new int[]{512,512,54,9,16,24},new int[]{192,96,43,7,16,24},new int[]{128,128,42,7,12,12})){
            String label=c[0]+"x"+c[1]+"-p"+c[2]+"-n"+c[3]+"-d"+c[4];
            test("actual-pipeline-"+label,()->{var r=request(c[3],c[4]);r.seed=c[2];r.expert.autoDock=true;var result=SimulatedSettlementPipeline.run(c[0],c[1],58,c[5],"rolling_hills",42,c[2],c[3],c[4],false,r.searchBudget,"mixed","square_cabin",TerrainParameters.defaults("rolling_hills"),r.expert);complete(result.heightfield,r,result.plan);require(result.constructionEdits>0,"not applied");if(c[3]==9)require(result.plan.sitePlanning.closePairs==0,"automatic crowding regression");if(c[0]==256)require(result.plan.sitePlanning.macroLoops>0,"screen topology still tree");if(c[4]==16)require(result.plan.transportNetwork.metrics.diagonal45Steps>0&&result.plan.transportNetwork.metrics.obliqueSteps>0,"union not used in real geometry");Files.writeString(out.resolve(label+"-metrics.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("site",result.plan.sitePlanning,"network",result.plan.transportNetwork.metrics,"search",result.plan.search,"worldBeforeHash",SimulatedSettlementPipeline.hashWorld(result.worldBefore),"worldAfterHash",SimulatedSettlementPipeline.hashWorld(result.worldAfter))));});
        }
        Files.writeString(out.resolve("results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("checks",checks,"total",checks.size(),"passed",checks.stream().filter(c->Boolean.TRUE.equals(c.get("passed"))).count())));
        if(checks.stream().anyMatch(c->!Boolean.TRUE.equals(c.get("passed"))))throw new AssertionError("Network diversity regression failed");System.out.println("ALL "+checks.size()+" NETWORK CHECKS PASSED");
    }
}
