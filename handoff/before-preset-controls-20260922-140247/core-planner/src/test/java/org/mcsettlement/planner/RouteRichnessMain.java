package org.mcsettlement.planner;

import java.nio.file.*;
import java.util.*;
import com.google.gson.Gson;
import org.mcsettlement.planner.SettlementPlanner.*;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.civil.PlanConstruction;
import org.mcsettlement.planner.regression.*;
import org.mcsettlement.planner.simulation.*;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import static org.mcsettlement.planner.RoadGeometry.*;
import static org.mcsettlement.planner.regression.RegressionMain.check;

/** Executable v0.4.1 regression tests: assertions use actual lattice centers and pavement. */
public final class RouteRichnessMain {
    private interface Test {void run()throws Exception;}
    private static int passed,failed;
    private static void test(String name,Test body){try{body.run();passed++;System.out.println("PASS "+name);}catch(Throwable e){failed++;System.out.println("FAIL "+name+": "+e);e.printStackTrace(System.out);}}
    private static void near(double a,double b){check(Math.abs(a-b)<1e-8,"metric mismatch: "+a+" vs "+b);}
    private static List<RoadStep> path(int... headings) {
        List<RoadStep> p=new ArrayList<>();int x=8,z=16;p.add(new RoadStep(x,64,z,"surface"));
        for(int h:headings){int[] d=directions(8)[h];x+=d[0];z+=d[1];p.add(new RoadStep(x,64,z,"surface"));}return p;
    }
    private static SearchStats stats(PlanRequest q){SearchStats s=new SearchStats();s.pathLimit=q.searchBudget.pathExpanded;s.gradeLimit=q.searchBudget.gradeRelaxations;s.stateLimit=q.searchBudget.pathStates;return s;}
    private record Fixture(HeightfieldMap map,PlanRequest q,Set<Long> roads,Map<Long,GroundColumn> anchors,
            List<RoadStep> access,int[] entry,TerrainRoadRouter.Result original) {}
    private static Fixture fixture(int directions,int heading,int width,int moves) {
        HeightfieldMap map=RegressionMain.flat(0,0,112,96,64);PlanRequest q=new PlanRequest();q.roadDirections=directions;q.roadWidth=width;
        List<RoadStep> p=new ArrayList<>();int x=12,z=20;int[] d=directions(directions)[heading];
        p.add(new RoadStep(x,64,z,"surface"));for(int i=0;i<moves;i++){x+=d[0];z+=d[1];p.add(new RoadStep(x,64,z,"surface"));}
        Map<Long,GroundColumn> anchors=new TreeMap<>();
        for(RoadStep endpoint:List.of(p.getFirst(),p.getLast()))for(int[] b:stencil(0,0,width)){
            int xx=endpoint.x+b[0],zz=endpoint.z+b[1];anchors.put(key(xx,zz),new GroundColumn(xx,zz,64,64,67,"road"));}
        Set<Long> roads=new TreeSet<>(footprint(List.of(p.getLast()),width));
        Set<Long> area=new TreeSet<>(roads);area.addAll(footprint(p,width));int[] entry={x,64,z};
        var walk=PavementGrades.solve(map,q,area,anchors,p,List.of(),List.of(),entry,stats(q));
        check(walk!=null,"Initial straight fixture not constructible");
        return new Fixture(map,q,roads,anchors,List.of(p.getFirst()),entry,new TerrainRoadRouter.Result(p,area,walk));
    }
    private static TerrainRoadRouter.Result refine(Fixture f,SearchStats s,TerrainRoadRouter.RefinementTrace t){
        return TerrainRoadRouter.refineLongRuns(f.map,f.q,Set.of(),f.roads,f.anchors,f.access,List.of(),List.of(),f.entry,s,Math.min(s.pathLimit,s.pathExpanded+2048),f.original,1,t);
    }
    private static void exactPavement(Fixture f,TerrainRoadRouter.Result result){
        Set<Long> area=new TreeSet<>(f.roads);area.addAll(footprint(result.centers(),f.q.roadWidth));check(area.equals(result.roadCells()),"Orphan old or missing new pavement");
        for(long k:area)check(result.walk().containsKey(k)&&buildable(f.map,x(k),z(k)),"Unbuilt/forbidden full-width cell");
        var first=result.centers().getFirst();var last=result.centers().getLast();
        check(first.x==f.original.centers().getFirst().x&&first.z==f.original.centers().getFirst().z&&last.x==f.entry[0]&&last.z==f.entry[2],"Endpoint changed");
        Set<Long> unique=new HashSet<>();for(var p:result.centers())check(unique.add(key(p.x,p.z)),"Self intersection");
        check(RouteQualityMetrics.measure(result.centers()).microZigzagWindows==0,"Introduced ABAB");
    }
    private static OrganicGuide.Shape shape(List<OrganicGuide.Shape> shapes,int index){return shapes.get(index);}
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args.length==0?"build/route-tests":args[0]);Files.createDirectories(out);
        test("empty_and_single_center_have_no_bends",()->{for(var p:List.of(List.<RoadStep>of(),path())){var q=RouteQualityMetrics.measure(p);check(q.stepCount==0&&q.effectiveBends==0&&q.bendSpacings.isEmpty(),"Degenerate metrics");}});
        test("straight_24_moves_is_not_hidden_by_sinuosity",()->{var p=fixture(8,0,3,24).original.centers();var q=RouteQualityMetrics.measure(p);check(q.maxStraightRun==24&&q.effectiveBends==0,"Lost straight run");near(q.sinuosity,1);near(q.longStraightFraction,1);});
        test("three_sustained_runs_have_two_effective_bends",()->{var q=RouteQualityMetrics.measure(path(0,0,0,1,1,1,0,0,0));check(q.effectiveBends==2&&q.maxStraightRun==3,"Effective bend classification");check(q.bendSpacings.size()==3,"Endpoint intervals missing");near(q.bendSpacings.get(1),3*Math.sqrt(2));});
        test("ABAB_noise_not_counted_as_meaningful_bends",()->{var q=RouteQualityMetrics.measure(path(0,1,0,1,0,1));check(q.microZigzagWindows==3&&q.effectiveBends==0&&q.shortRuns==6,"Noise misclassified");near(q.microZigzagRatio,1);});
        test("two_and_three_move_zippers_have_separate_metric",()->{
            for(int block:List.of(2,3)){int[] h=new int[block*4];for(int i=0;i<h.length;i++)h[i]=(i/block)%2;
                var q=RouteQualityMetrics.measure(path(h));check(q.microZigzagWindows==0&&q.rhythmZigzagWindows>0,"Sustained zipper hidden by ABAB=0");}
        });
        test("rhythm_normalization_rebuilds_real_pavement",()->{
            var f=fixture(8,0,3,16);List<RoadStep> p=new ArrayList<>();int x=12,z=20;p.add(new RoadStep(x,64,z,"surface"));
            for(int i=0;i<16;i++){int[] d=directions(8)[(i/2)%2==0?1:7];x+=d[0];z+=d[1];p.add(new RoadStep(x,64,z,"surface"));}
            Set<Long> area=new TreeSet<>(f.roads);area.addAll(footprint(p,3));var walk=PavementGrades.solve(f.map,f.q,area,f.anchors,p,List.of(),List.of(),f.entry,stats(f.q));
            check(walk!=null,"Zipper fixture invalid");var zipped=new Fixture(f.map,f.q,f.roads,f.anchors,f.access,f.entry,new TerrainRoadRouter.Result(p,area,walk));
            var trace=new TerrainRoadRouter.RefinementTrace();var r=refine(zipped,stats(f.q),trace);
            check(r.refined()&&trace.rhythmReorderings>0,"Only metric changed, not centerline");
            check(RouteQualityMetrics.measure(r.centers()).rhythmZigzagWindows<RouteQualityMetrics.measure(p).rhythmZigzagWindows,"Zipper not reduced");exactPavement(zipped,r);
        });
        test("one_move_corner_is_not_effective_bend",()->{var q=RouteQualityMetrics.measure(path(0,0,0,1,0,0,0));check(q.rawTurns==2&&q.effectiveBends==0,"Isolated move counted as sustained bend");});
        test("twelve_direction_world_length_is_not_move_count",()->{var q=RouteQualityMetrics.measure(fixture(12,1,3,20).original.centers());check(q.maxStraightRun==20,"12-heading run count");near(q.length,20*Math.sqrt(5));near(q.sinuosity,1);});
        test("long_straight_warning_is_strictly_above_16",()->{near(RouteQualityMetrics.measure(fixture(8,0,3,16).original.centers()).longStraightFraction,0);near(RouteQualityMetrics.measure(fixture(8,0,3,17).original.centers()).longStraightFraction,1);});
        test("bounded_role_candidates_and_deterministic_controls",()->{
            var map=RegressionMain.flat(0,0,128,128,64);
            for(String role:List.of("collector","local","secondary","ring")){
                var a=OrganicGuide.candidates(map,12,64,20,100,64,90,42,role,3,3,3,Set.of());
                var b=OrganicGuide.candidates(map,12,64,20,100,64,90,42,role,3,3,3,Set.of());
                check(new Gson().toJson(a).equals(new Gson().toJson(b)),"Nondeterministic candidates");
                check(a.size()==5&&a.getLast().conservative,"Missing conservative last stage");
                for(var s:a){check(s.controls.size()<=7&&s.samples.size()<=6150&&s.probeCells<=128*9,"Unbounded shape work");}
                for(int i=1;i<4;i++)check(shape(a,i-1).estimate<=shape(a,i).estimate,"Unsorted terrain candidates");
            }
        });
        test("straight_state_cost_keeps_growing_beyond_four",()->{
            var map=RegressionMain.flat(0,0,96,96,64);var candidates=OrganicGuide.candidates(map,12,64,20,75,64,20,42,"collector",3,3,3,Set.of());
            for(var s:candidates){var g=new OrganicGuide(map,s,"collector");check(g.straightCost(16,true)>g.straightCost(4,true)&&g.straightCost(24,true)>=g.straightCost(16,true),"Run history ineffective");check(g.straightCost(24,false)==0,"Penalty leaked across a turn");}
        });
        for(int width:List.of(1,3,5))test("real_offset_rebuilds_entire_width_"+width,()->{var f=fixture(8,0,width,36);var s=stats(f.q);var t=new TerrainRoadRouter.RefinementTrace();var r=refine(f,s,t);check(r.refined(),"No actual centerline change");check(RouteQualityMetrics.measure(r.centers()).maxStraightRun<=16,"Straight not broken");exactPavement(f,r);check(s.pathExpanded<=2048&&s.routeRefinementAttempts<=16,"Unbounded refinement");});
        for(int[] heading:List.of(new int[]{8,1},new int[]{12,0},new int[]{12,1}))test("integer_endpoint_balance_"+heading[0]+"_heading_"+heading[1],()->{var f=fixture(heading[0],heading[1],3,24);var r=refine(f,stats(f.q),new TerrainRoadRouter.RefinementTrace());check(r.refined(),"No offset for supported heading");exactPavement(f,r);});
        for(var obstacle:List.of(HeightfieldMap.ObstacleType.PROTECTED,HeightfieldMap.ObstacleType.WATER,HeightfieldMap.ObstacleType.STEEP_CLIFF,HeightfieldMap.ObstacleType.EXISTING_BUILDING))test("necessary_straight_retained_between_"+obstacle,()->{
            var f=fixture(8,0,3,24);for(int x=0;x<112;x++)for(int z=0;z<96;z++)if(!f.original.roadCells().contains(key(x,z)))f.map.setObstacle(x,z,obstacle);
            var trace=new TerrainRoadRouter.RefinementTrace();var r=refine(f,stats(f.q),trace);check(r==f.original&&!trace.rejections.isEmpty(),"Broke hard constraint for curvature");exactPavement(f,r);
        });
        test("curvature_does_not_relax_cut_fill_limits",()->{var f=fixture(8,0,3,24);
            for(int x=0;x<112;x++)for(int z=0;z<96;z++)if(!f.original.roadCells().contains(key(x,z)))f.map.setSurfaceY(x,z,90);
            var trace=new TerrainRoadRouter.RefinementTrace();var r=refine(f,stats(f.q),trace);
            check(r==f.original&&!trace.rejections.isEmpty(),"Curvature overrode cut/fill limits");exactPavement(f,r);
        });
        test("no_extra_path_work_after_global_budget",()->{var f=fixture(8,0,3,24);var s=stats(f.q);s.pathLimit=s.pathExpanded=123;var r=refine(f,s,new TerrainRoadRouter.RefinementTrace());check(r==f.original&&s.pathExpanded==123,"Exceeded path limit");});
        test("small_state_limit_leaves_feasible_route_unchanged",()->{var f=fixture(8,0,3,24);f.q.searchBudget.pathStates=1;var s=stats(f.q);var trace=new TerrainRoadRouter.RefinementTrace();check(refine(f,s,trace)==f.original&&trace.rejections.containsKey("PATH_STATES"),"Refinement ignored state budget");});
        test("seeded_refinement_is_exactly_replayable",()->{var f=fixture(12,1,3,24);var a=refine(f,stats(f.q),new TerrainRoadRouter.RefinementTrace());var b=refine(f,stats(f.q),new TerrainRoadRouter.RefinementTrace());check(new Gson().toJson(a).equals(new Gson().toJson(b)),"Refinement nondeterminism");});
        test("reproduction512_true_construction_rhythm_and_fallback_trace",()->{
            var r=SimulatedSettlementPipeline.run(512,512,58,24,"rolling_hills",42,45,7,12,false,new SearchBudget(),"mixed","square_cabin");
            var q=new PlanRequest();q.seed=45;q.roadDirections=12;q.presetPalette="mixed";
            var audit=TerrainAudit.audit(r.heightfield,q,r.plan);check(audit.violations()==0&&audit.reachable()==7,"Reproduction construction audit");
            var m=r.plan.transportNetwork.metrics;check(m.verifiedLoops>=1&&r.plan.plots.size()==7,"Connectivity/loop lost");check(m.maximumStraightRun<=16,"Reproduction long straight remains");
            check(r.plan.transportNetwork.corridors.stream().allMatch(e->e.quality.rhythmZigzagWindows==0),"Sustained zipper in reproduction");
            for(var e:r.plan.transportNetwork.corridors){check(e.quality.microZigzagWindows==0,"ABAB in route");if(Set.of("constrained_fallback","conservative_guided").contains(e.routingStyle))check(e.fallbackReason!=null&&!e.fallbackReason.isBlank(),"Silent fallback");}
            String connection="";int stage=-1,rich=0;
            for(var a:m.routingAttempts){String key=a.fromNodeId+":"+a.toNodeId+":"+a.roadType;int phase=List.of("rich","conservative","unguided").indexOf(a.stage);
                if(!key.equals(connection)){connection=key;stage=-1;rich=0;}check(phase>=stage,"Out-of-order fallback");if(phase==0)rich++;else check(rich==4,"Skipped rich proposal");stage=phase;
                check(a.refinementVariants<=16&&a.refinementPathExpanded<=2048,"Refinement exceeded cap");}
            // Advisory metadata does not authorize construction. Missing real paving still fails.
            var copy=PlanningIR.fromJson(r.plan.toJson(false));var edits=PlanConstruction.prepare(copy,null);copy.transportNetwork.corridors.getFirst().quality.maxStraightRun=999;
            check(edits.equals(PlanConstruction.prepare(copy,null)),"Quality metadata became construction truth");
            check(r.plan.search.guideFieldBuilds<=128&&r.plan.search.guideFieldCellVisits==2L*512*512*r.plan.search.guideFieldBuilds,"Field budget accounting");
            Files.writeString(out.resolve("reproduction-summary.json"),new Gson().toJson(Map.of("metrics",m,"audit",audit,"search",r.plan.search)));
        });
        Files.writeString(out.resolve("results.json"),new Gson().toJson(Map.of("passed",passed,"failed",failed)));
        System.out.println("RESULT "+passed+" passed; "+failed+" failed");if(failed>0)throw new AssertionError("Route richness failures: "+failed);
    }
}
