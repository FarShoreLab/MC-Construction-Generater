package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.SettlementPlanner.PlanRequest;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.preset.PlannedBuilding;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.terrain.SpatialNoise;
import static org.mcsettlement.planner.RoadGeometry.*;

/**
 * Replaces the provisional feasibility roads, not a decorative overlay:
 * locked sites -> clustered plazas -> finite MST targets -> height-state routes -> optional redundant edges.
 * The original complete, constructible layout is an atomic fallback; no building is dropped or resized.
 */
final class OrganicRoadNetwork {
    private record Hub(String id,int x,int y,int z,List<Plot> plots) {
        long key(){return RoadGeometry.key(x,z);}
        int[] pos(){return new int[]{x,y,z};}
    }
    private record Pair(Hub from,Hub to,double cost) {}
    private static final class Network {
        Set<Long> roads=new TreeSet<>();
        Map<Long,GroundColumn> walk=new TreeMap<>();
        List<RoadEdge> routes=new ArrayList<>();
        List<List<RoadStep>> accesses=new ArrayList<>();
        List<SemanticLink> links=new ArrayList<>();
    }
    private final HeightfieldMap map;
    private final PlanRequest request;
    private final PlanningIR ir;
    private final SearchStats stats;
    private final Set<Long> forbidden=new HashSet<>();
    private final Map<Long,GroundColumn> foundations=new TreeMap<>();
    private final List<String> failures=new ArrayList<>();
    private final List<RouteAttempt> attempts=new ArrayList<>();
    private record Choice(TerrainRoadRouter.Result result,OrganicGuide.Shape shape,RouteAttempt attempt,double score) {}
    private final int[] entry;
    private Network network=new Network();
    private OrganicRoadNetwork(HeightfieldMap m,PlanRequest r,PlanningIR plan) {
        map=m;request=r;ir=plan;stats=plan.search;
        entry=plan.transportNetwork.nodes.stream().filter(n->"entry".equals(n.type)).findFirst().orElseThrow().pos.clone();
        for(GroundColumn c:plan.groundColumns)if("foundation".equals(c.kind))foundations.put(key(c.x,c.z),copy(c));
        for(Plot p:plan.plots)for(int[] cell:p.footprint)for(int dx=-r.parcelConfig.roadSetback;dx<=r.parcelConfig.roadSetback;dx++)
            for(int dz=-r.parcelConfig.roadSetback;dz<=r.parcelConfig.roadSetback;dz++)
                forbidden.add(key(PlannedBuilding.originX(p)+cell[0]+dx,PlannedBuilding.originZ(p)+cell[1]+dz));
        for(int[] d:stencil(0,0,r.roadWidth)) {
            int x=entry[0]+d[0],z=entry[2]+d[1];long k=key(x,z);
            network.roads.add(k);network.walk.put(k,new GroundColumn(x,z,m.getSurfaceY(x,z),entry[1],Math.max(m.getSurfaceY(x,z),entry[1]+3),"road"));
        }
    }
    static void rebuild(HeightfieldMap map,PlanRequest request,PlanningIR ir) {
        OrganicRoadNetwork builder=new OrganicRoadNetwork(map,request,ir);
        if(ir.plots.size()<3){builder.fallback("SMALL_SETTLEMENT: fewer than three buildings; retained validated access network");return;}
        if(!builder.build())builder.fallback("SEMANTIC_REROUTE_FAILED_WITHIN_BUDGET: retained all original locked sites and validated roads");
    }
    private void reason(String text){if(failures.size()<32&&!failures.contains(text))failures.add(text);}
    private void fallback(String reason) {
        NetworkMetrics m=ir.transportNetwork.metrics;m.routingAttempts=attempts;m.status="FEASIBILITY_FALLBACK";m.reasons.addAll(failures);m.reasons.add(reason);
        for(RoadEdge e:ir.transportNetwork.corridors)if(e.steps.size()>=30){m.longestStraightRatio=Math.max(m.longestStraightRatio,straightRatio(e.steps));m.microZigzagRatio=Math.max(m.microZigzagRatio,zigzagRatio(e.steps));}
        m.components=ir.plots.isEmpty()?0:1;
        Set<Long> roads=new HashSet<>();for(GroundColumn c:ir.groundColumns)if("road".equals(c.kind))roads.add(key(c.x,c.z));m.pavedEnclosures=enclosures(map,roads,request.roadWidth);
        for(RoadEdge e:ir.transportNetwork.corridors)e.fallbackReason=reason;
        for(RouteAttempt a:attempts)if(a.selected){a.selected=false;a.status="ROLLED_BACK";a.reason="ATOMIC_NETWORK_FALLBACK";}
        RouteQualityMetrics.summarize(ir.transportNetwork);
        ir.auditLog.warnings.add(reason);
    }
    private boolean build() {
        List<Hub> hubs=makeHubs();
        if(hubs.size()<2){reason("NO_FEASIBLE_PLAZA_MASK");return false;}
        Hub root=new Hub("entry",entry[0],entry[1],entry[2],List.of());
        // Form the complete target tree BEFORE routing any of its edges. At most seven graph vertices.
        List<Pair> tree=new ArrayList<>();List<Hub> planned=new ArrayList<>(List.of(root)),rest=new ArrayList<>(hubs);
        while(!rest.isEmpty()) {
            Pair best=null;
            for(Hub h:rest)for(Hub connected:planned){Pair p=pair(h,connected);if(best==null||compare(p,best)<0)best=p;}
            tree.add(best);planned.add(best.from);rest.remove(best.from);
        }
        Set<String> connected=new HashSet<>(Set.of(root.id));List<Hub> known=new ArrayList<>(List.of(root));
        for(int i=0;i<tree.size();i++) {
            Pair proposed=tree.get(i);List<Hub> alternatives=new ArrayList<>(known);
            alternatives.sort(Comparator.comparingDouble(h->distance(proposed.from,h)));
            alternatives.remove(proposed.to);alternatives.addFirst(proposed.to);
            boolean ok=false;
            for(Hub target:alternatives.stream().limit(3).toList()) {
                if(connect(proposed.from,target,null,"collector",false,tree.size()-i+ir.plots.size())){ok=true;break;}
            }
            if(!ok)return false;
            known.add(proposed.from);connected.add(proposed.from.id);
        }
        // Every building gets a real private entrance plus a full-width local connector to a plaza.
        for(int i=0;i<ir.plots.size();i++) {
            Plot plot=ir.plots.get(i);RoadStep end=plot.entrance.path.getLast();
            Hub source=new Hub("building:"+plot.id,end.x,end.y,end.z,List.of(plot));
            Hub preferred=hubs.stream().filter(h->h.plots.contains(plot)).findFirst().orElseThrow();
            List<Hub> alternatives=new ArrayList<>(hubs);alternatives.sort(Comparator.comparingDouble(h->distance(source,h)));
            alternatives.remove(preferred);alternatives.addFirst(preferred);boolean ok=false;
            for(Hub target:alternatives.stream().limit(3).toList()) {
                if(connect(source,target,plot,"local",false,ir.plots.size()-i+1)){ok=true;break;}
            }
            if(!ok)return false;
        }
        // Bounded detour-reduction candidates. Redundancy comes AFTER full connectivity.
        List<Pair> extras=new ArrayList<>();
        for(int i=0;i<hubs.size();i++)for(int j=i+1;j<hubs.size();j++)extras.add(pair(hubs.get(i),hubs.get(j)));
        extras.sort(Comparator.<Pair>comparingDouble(p->p.cost+(linked(p.from,p.to)?16:0))
                .thenComparing(p->p.from.id).thenComparing(p->p.to.id));
        int added=0,targetLoops=1+(int)Math.floorMod(SpatialNoise.mix(request.seed),2);
        for(Pair p:extras) {
            if(added>=targetLoops||stats.pathExpanded>=stats.pathLimit||stats.gradeRelaxations>=stats.gradeLimit)break;
            if(connect(p.from,p.to,null,added==0?"secondary":"ring",true,Math.max(1,targetLoops-added)))added++;
        }
        if(added==0)reason("NO_CONSTRUCTIBLE_REDUNDANT_EDGE_WITHIN_BUDGET: all buildings remain connected");
        commit(hubs,root);return true;
    }
    private boolean linked(Hub a,Hub b){return network.links.stream().anyMatch(e->
            e.fromNodeId.equals(a.id)&&e.toNodeId.equals(b.id)||e.fromNodeId.equals(b.id)&&e.toNodeId.equals(a.id));}
    private static double distance(Hub a,Hub b){return StrictMath.hypot(a.x-b.x,a.z-b.z);}
    private static Pair pair(Hub a,Hub b){return new Pair(a,b,distance(a,b)+Math.abs(a.y-b.y)*3);}
    private static int compare(Pair a,Pair b){int c=Double.compare(a.cost,b.cost);return c!=0?c:(a.from.id+":"+a.to.id).compareTo(b.from.id+":"+b.to.id);}
    private List<Hub> makeHubs() {
        int count=Math.min(6,Math.max(ir.plots.size()>=6?3:2,(ir.plots.size()+1)/3));
        List<Plot> seeds=new ArrayList<>();seeds.add(ir.plots.get((int)Math.floorMod(SpatialNoise.mix(request.seed),ir.plots.size())));
        while(seeds.size()<count){Plot best=null;double far=-1;
            for(Plot p:ir.plots)if(!seeds.contains(p)){RoadStep a=p.entrance.path.getLast();double d=Double.MAX_VALUE;
                for(Plot seed:seeds){RoadStep b=seed.entrance.path.getLast();d=Math.min(d,StrictMath.hypot(a.x-b.x,a.z-b.z));}
                if(d>far){far=d;best=p;}}
            seeds.add(best);
        }
        List<List<Plot>> groups=new ArrayList<>();for(int i=0;i<count;i++)groups.add(new ArrayList<>());
        for(Plot p:ir.plots){RoadStep a=p.entrance.path.getLast();int closest=0;double d=Double.MAX_VALUE;
            for(int i=0;i<count;i++){RoadStep b=seeds.get(i).entrance.path.getLast();double dd=StrictMath.hypot(a.x-b.x,a.z-b.z);
                if(dd<d){d=dd;closest=i;}}groups.get(closest).add(p);}
        boolean preferCertified=ir.transportNetwork.corridors.stream().anyMatch(e->{double length=0;for(int j=1;j<e.steps.size();j++){RoadStep a=e.steps.get(j-1),b=e.steps.get(j);length+=StrictMath.hypot(a.x-b.x,a.z-b.z);}return length>=96;});
        List<Hub> hubs=new ArrayList<>();
        for(int i=0;i<count;i++){
            List<Plot> plots=groups.get(i);if(plots.isEmpty())continue;
            double cx=0,cz=0;for(Plot p:plots){RoadStep s=p.entrance.path.getLast();cx+=s.x;cz+=s.z;}cx/=plots.size();cz/=plots.size();
            Map<Long,GroundColumn> prior=new HashMap<>();for(GroundColumn c:ir.groundColumns)if("road".equals(c.kind))prior.put(key(c.x,c.z),c);
            List<RoadStep> certified=new ArrayList<>();Set<Long> unique=new HashSet<>();
            for(RoadEdge e:ir.transportNetwork.corridors)for(RoadStep p:e.steps)if(unique.add(key(p.x,p.z)))certified.add(p);
            final double centerX=cx,centerZ=cz;
            certified.sort(Comparator.<RoadStep>comparingDouble(p->StrictMath.hypot(p.x-centerX,p.z-centerZ)).thenComparingInt(p->p.x).thenComparingInt(p->p.z));
            Hub best=null;double bestCost=Double.MAX_VALUE;Set<Long> tried=new HashSet<>();
            for(int trial=0;trial<640&&stats.candidateChecks<stats.candidateLimit;trial++) {
                double angle=(trial*2.399963229728653+SpatialNoise.unit(request.seed,i,0,0x5e11)*6.283185307179586);
                double radius=2+StrictMath.sqrt(trial)*1.45;
                boolean known=preferCertified&&trial<Math.min(320,certified.size());
                int x=known?certified.get(trial).x:(int)StrictMath.round(cx+StrictMath.cos(angle)*radius);
                int z=known?certified.get(trial).z:(int)StrictMath.round(cz+StrictMath.sin(angle)*radius);
                if(!tried.add(key(x,z)))continue;stats.candidateChecks++;
                if(hubs.stream().anyMatch(h->StrictMath.hypot(h.x-x,h.z-z)<12))continue;
                boolean close=false;for(Plot p:ir.plots){RoadStep a=p.entrance.path.getLast();if(Math.max(Math.abs(a.x-x),Math.abs(a.z-z))<6){close=true;break;}}if(close)continue;
                int lo=-2032,hi=2032,total=0,n=0;boolean fits=true;
                for(int[] d:stencil(0,0,request.roadWidth)){int xx=x+d[0],zz=z+d[1];if(forbidden.contains(key(xx,zz))||!buildable(map,xx,zz)){fits=false;break;}
                    int h=map.getSurfaceY(xx,zz);lo=Math.max(lo,h-request.roadMaxCut);hi=Math.min(hi,h+request.roadMaxFill);total+=h;n++;}
                if(!fits||lo>hi)continue;int y=Math.max(lo,Math.min(hi,Math.round(total/(float)n)));
                double cost=!preferCertified||prior.containsKey(key(x,z))?0:28;for(Plot p:plots){RoadStep a=p.entrance.path.getLast();cost+=StrictMath.hypot(a.x-x,a.z-z)+Math.abs(a.y-y)*3;}
                for(int[] d:stencil(0,0,request.roadWidth))cost+=Math.abs(map.getSurfaceY(x+d[0],z+d[1])-y)*2;
                if(cost<bestCost){bestCost=cost;best=new Hub("plaza_"+(i+1),x,y,z,plots);}
            }
            if(best!=null)hubs.add(best);
        }
        if(hubs.stream().mapToInt(h->h.plots.size()).sum()!=ir.plots.size())return List.of();
        return hubs;
    }
    private boolean connect(Hub from,Hub to,Plot plot,String role,boolean redundant,int remaining) {
        if(stats.pathExpanded>=stats.pathLimit||stats.gradeRelaxations>=stats.gradeLimit||stats.topologyEdgesTried>=128){
            reason("BUDGET_BEFORE_ROUTE "+from.id+" -> "+to.id+" "+role);return false;}
        int oldHoles=redundant?enclosures(map,network.roads,request.roadWidth):0;
        List<RoadStep> access=plot==null?List.of(new RoadStep(from.x,from.y,from.z,"surface")):plot.entrance.path;
        List<List<RoadStep>> accesses=new ArrayList<>(network.accesses);if(plot!=null)accesses.add(access);
        List<List<RoadStep>> routes=network.routes.stream().map(e->e.steps).toList();
        Map<Long,GroundColumn> fixed=new TreeMap<>(network.walk);
        if(plot==null&&!fixed.containsKey(from.key())) {
            for(int[] d:stencil(0,0,request.roadWidth)){int x=from.x+d[0],z=from.z+d[1];
                fixed.put(key(x,z),new GroundColumn(x,z,map.getSurfaceY(x,z),from.y,Math.max(map.getSurfaceY(x,z),from.y+3),"road"));}
        }
        Set<Long> goals=Set.of(to.key());int[] distances=distanceField(map,goals);
        Set<Long> separateBlocked=new HashSet<>(forbidden);
        for(long k:network.roads){if(near(k,from,7)||near(k,to,7))continue;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)separateBlocked.add(key(x(k)+dx,z(k)+dz));}
        List<OrganicGuide.Shape> shapes=OrganicGuide.candidates(map,from.x,from.y,from.z,to.x,to.y,to.z,
                request.seed^SpatialNoise.mix(role.hashCode()),role,request.roadWidth,request.roadMaxCut,request.roadMaxFill,separateBlocked);
        stats.guideCandidatesRanked+=shapes.size();for(var shape:shapes)stats.guideProbeCells+=shape.probeCells;
        int available=stats.pathLimit-stats.pathExpanded;
        int share=Math.max(256,available/Math.max(1,remaining));
        int pool=Math.min(45000,Math.max(1200,share*(distance(from,to)>64?3:2)));
        int connectionEnd=Math.min(stats.pathLimit,stats.pathExpanded+pool);
        Choice best=null;List<String> rejectedReasons=new ArrayList<>();
        for(int attempt=0;attempt<6;attempt++) {
            if(best!=null&&attempt>=4)break; // Never discard a feasible rich route merely to save length.
            if(stats.topologyEdgesTried>=128||stats.pathExpanded>=connectionEnd||stats.gradeRelaxations>=stats.gradeLimit)break;
            OrganicGuide.Shape shape=attempt<shapes.size()?shapes.get(attempt):null;
            boolean conservative=shape!=null&&shape.conservative;
            OrganicGuide guide=shape==null?null:new OrganicGuide(map,shape,role);
            if(guide!=null){stats.guideFieldBuilds++;stats.guideFieldCellVisits+=2L*map.getWidth()*map.getDepth();}
            // Reuse of built paving is only allowed after all rich/separate candidates.
            // Full-width/grade/obstacle validation is identical in every stage, and loops
            // still need fresh pavement + an independently counted macro enclosure.
            Set<Long> blocked=attempt<4?separateBlocked:forbidden;
            int allowance=attempt==5?connectionEnd-stats.pathExpanded:
                    Math.max(128,Math.min(10000,(int)(pool*(attempt==0?.22:attempt==1?.18:attempt<4?.12:.18))));
            int end=Math.min(connectionEnd,stats.pathExpanded+allowance);
            RouteAttempt audit=new RouteAttempt();audit.number=++stats.topologyEdgesTried;
            audit.fromNodeId=from.id;audit.toNodeId=to.id;audit.roadType=role;
            audit.candidateId=shape==null?"unguided":shape.id;
            audit.stage=shape==null?"unguided":conservative?"conservative":"rich";
            audit.estimatedCost=shape==null?0:shape.estimate;attempts.add(audit);
            int expanded=stats.pathExpanded,grades=stats.gradeRelaxations;
            TerrainRoadRouter.Trace trace=new TerrainRoadRouter.Trace();
            TerrainRoadRouter.Result result=TerrainRoadRouter.routeTo(map,request,blocked,network.roads,fixed,
                    access,accesses,routes,entry,distances,stats,end,goals,guide,trace);
            audit.pathExpanded=stats.pathExpanded-expanded;audit.gradeRelaxations=stats.gradeRelaxations-grades;
            String rejection=result==null?trace.failure:null;
            if(result!=null&&redundant){
                long fresh=result.roadCells().stream().filter(k->!network.roads.contains(k)).count();
                if(fresh<request.roadWidth*10||enclosures(map,result.roadCells(),request.roadWidth)<=oldHoles)
                    rejection="NO_NEW_PAVED_LOOP";
            }
            if(result!=null&&rejection==null){Map<Long,GroundColumn> cols=combined(result.walk());
                if(cols.size()>stats.columnLimit){exhausted(stats,"GROUND_COLUMNS");rejection="GROUND_COLUMNS";}
                else if(editCount(cols)>stats.editLimit){exhausted(stats,"CONSTRUCTION_EDITS");rejection="CONSTRUCTION_EDITS";}}
            if(rejection!=null){audit.status="REJECTED";audit.reason=rejection;rejectedReasons.add(audit.candidateId+":"+rejection);continue;}
            double score=routeScore(result,role,redundant);audit.status="FEASIBLE";audit.routeScore=score;
            if(best==null||score<best.score)best=new Choice(result,shape,audit,score);
            RouteQuality quality=RouteQualityMetrics.measure(best.result.centers());
            // Compare at least two rich proposals on long roads when the shared budget permits.
            if((attempt>=1||distance(from,to)<24)&&quality.maxStraightRun<=16&&quality.microZigzagWindows==0)break;
        }
        if(best!=null){
            int refinementStart=stats.pathExpanded,refinementTries=stats.routeRefinementAttempts,refinementGrades=stats.gradeRelaxations;
            Set<Long> selectedBlocked="rich".equals(best.attempt.stage)?separateBlocked:forbidden;
            TerrainRoadRouter.RefinementTrace refinementTrace=new TerrainRoadRouter.RefinementTrace();
            TerrainRoadRouter.Result result=TerrainRoadRouter.refineLongRuns(map,request,selectedBlocked,network.roads,fixed,access,accesses,routes,entry,
                    stats,Math.min(stats.pathLimit,stats.pathExpanded+2048),best.result,(request.seed&1)==0?1:-1,refinementTrace);
            if(redundant&&result.refined()&&(result.roadCells().stream().filter(k->!network.roads.contains(k)).count()<request.roadWidth*10
                    ||enclosures(map,result.roadCells(),request.roadWidth)<=oldHoles)){result=best.result;refinementTrace.outcome="RETAINED_LOOP_TOPOLOGY";}
            Map<Long,GroundColumn> refinedColumns=combined(result.walk());
            if(refinedColumns.size()>stats.columnLimit||editCount(refinedColumns)>stats.editLimit){result=best.result;refinementTrace.outcome="RETAINED_COLUMN_OR_EDIT_BUDGET";}
            best.attempt.rhythmWindowsChecked=refinementTrace.rhythmWindowsChecked;best.attempt.rhythmReorderings=result.refined()?refinementTrace.rhythmReorderings:0;
            best.attempt.refinementOutcome=refinementTrace.outcome;best.attempt.refinementRejections=refinementTrace.rejections;
            best.attempt.refinementPathExpanded=stats.pathExpanded-refinementStart;
            best.attempt.refinementVariants=stats.routeRefinementAttempts-refinementTries;
            best.attempt.pathExpanded+=stats.pathExpanded-refinementStart;best.attempt.gradeRelaxations+=stats.gradeRelaxations-refinementGrades;
            best.attempt.centerlineRefined=result.refined();best.attempt.routeScore=routeScore(result,role,redundant);
            OrganicGuide.Shape shape=best.shape;best.attempt.selected=true;best.attempt.status="SELECTED";
            RoadEdge edge=new RoadEdge();edge.id="organic_"+(network.routes.size()+1);edge.fromNodeId=from.id;edge.toNodeId=to.id;
            edge.roadType=role;edge.width=request.roadWidth;edge.steps=result.centers();
            if(result.refined())edge.refinementMethod="validated_centerline_refinement";
            edge.routingStyle=shape==null?"constrained_fallback":shape.conservative?"conservative_guided":"multi_control_height_astar";
            edge.guideCandidateId=shape==null?"unguided":shape.id;if(shape!=null)edge.guideControls=shape.controls;
            if(shape==null||shape.conservative){
                edge.fallbackReason="RICH_CANDIDATES_FAILED_WITHIN_BUDGET: "+String.join("; ",rejectedReasons);
                best.attempt.reason=edge.fallbackReason;
            }
            SemanticLink link=new SemanticLink();link.id="link_"+(network.links.size()+1);link.fromNodeId=from.id;link.toNodeId=to.id;
            link.roadType=role;link.corridorId=edge.id;link.accessPlotId=plot==null?null:plot.id;link.verifiedLoop=redundant;
            network.routes.add(edge);network.links.add(link);network.accesses=accesses;
            network.roads=result.roadCells();network.walk=result.walk();return true;
        }
        reason("ROUTE_FAILED: "+role+" "+from.id+" -> "+to.id+" ["+
                (rejectedReasons.isEmpty()?"NO_REMAINING_TOPOLOGY_PATH_OR_GRADE_BUDGET":String.join("; ",rejectedReasons))+"]");return false;
    }
    /** Rank only routes already accepted by the exact brush + height + stair solver. */
    private double routeScore(TerrainRoadRouter.Result result,String role,boolean redundant) {
        RouteQuality q=RouteQualityMetrics.measure(result.centers());boolean local="local".equals(role);
        double earthwork=0,stairs=0,fresh=0;
        for(GroundColumn c:result.walk().values())if(!network.walk.containsKey(key(c.x,c.z))){
            earthwork+=Math.abs(c.targetY-c.originalY);if(stair(c))stairs++;if("road".equals(c.kind))fresh++;}
        double ratio=Math.max(0,q.sinuosity-(redundant?2.2:local?1.35:1.65));
        double score=q.length*(local?1.5:1)+earthwork*.30+stairs*.6+ratio*ratio*100;
        score+=q.longStraightFraction*(local?30:180)+Math.max(0,q.maxStraightRun-16)*(local?2:6);
        score+=q.microZigzagWindows*100+q.shortRuns*4+Math.max(0,q.rawTurns-q.length/6)*3;
        if(!local&&q.length>=30&&q.effectiveBends<2)score+=50;
        // Reward real integration, not implicit nearest-road termination or duplicate loop edges.
        if(!redundant)score+=fresh*.025;
        return score;
    }
    private static boolean near(long k,Hub h,int radius){return Math.max(Math.abs(x(k)-h.x),Math.abs(z(k)-h.z))<=radius;}
    private Map<Long,GroundColumn> combined(Map<Long,GroundColumn> walk){Map<Long,GroundColumn> out=new TreeMap<>(walk);out.putAll(foundations);return out;}
    private static long editCount(Map<Long,GroundColumn> columns){long n=0;for(GroundColumn c:columns.values())n+=c.clearToY-Math.min(c.targetY,c.originalY+1)+1;return n;}

    /** Counts non-pavement components enclosed by actual full-width roads. Ignores tiny brush holes. */
    static int enclosures(HeightfieldMap map,Set<Long> roads,int width) {
        int w=map.getWidth(),d=map.getDepth();byte[] cells=new byte[w*d];
        for(long k:roads)if(map.inBounds(x(k),z(k)))cells[(z(k)-map.getMinZ())*w+x(k)-map.getMinX()]=1;
        int[] q=new int[w*d];int count=0;
        for(int start=0;start<cells.length;start++)if(cells[start]==0) {
            int head=0,tail=0;q[tail++]=start;cells[start]=2;boolean boundary=false;
            while(head<tail){int k=q[head++],x=k%w,z=k/w;if(x==0||z==0||x==w-1||z==d-1)boundary=true;
                for(int[] step:CARDINAL){int nx=x+step[0],nz=z+step[1];if(nx<0||nx>=w||nz<0||nz>=d)continue;int next=nz*w+nx;
                    if(cells[next]==0){cells[next]=2;q[tail++]=next;}}}
            if(!boundary&&tail>=Math.max(16,width*width*4))count++;
        }
        return count;
    }
    private void commit(List<Hub> hubs,Hub root) {
        Map<Long,GroundColumn> cols=combined(network.walk);TransportNetwork net=new TransportNetwork();
        net.algorithm="clustered_plazas_mst_multiguide_v041";net.directionCount=request.roadDirections;net.corridorWidth=request.roadWidth;
        net.semanticNodes.add(new SemanticNode(root.id,"entry",root.pos()));
        for(Hub h:hubs){SemanticNode n=new SemanticNode(h.id,"hub/plaza",h.pos());
            for(SemanticLink link:network.links)if(h.id.equals(link.toNodeId)&&link.accessPlotId!=null)n.plotIds.add(link.accessPlotId);
            net.semanticNodes.add(n);}
        for(Plot p:ir.plots)net.semanticNodes.add(new SemanticNode("building:"+p.id,"building",p.entrance.accessPoint.clone()));
        net.semanticLinks=network.links;net.corridors=network.routes;
        for(RoadEdge e:net.corridors){List<RoadStep> canonical=new ArrayList<>();for(RoadStep s:e.steps)canonical.add(step(cols.get(key(s.x,s.z))));e.steps=canonical;}
        Map<Long,String> incident=new HashMap<>();Set<Long> walk=new TreeSet<>(network.walk.keySet());
        for(long k:walk){GroundColumn c=cols.get(k);net.nodes.add(new RoadNode(nodeId(k),c.x,c.targetY,c.z,k==root.key()?"entry":"walkable"));}
        for(long k:walk)for(int[] d:List.of(CARDINAL[0],CARDINAL[1])) {
            long next=key(x(k)+d[0],z(k)+d[1]);if(!walk.contains(next)||!canWalk(cols.get(k),cols.get(next)))continue;
            RoadEdge e=new RoadEdge();e.id="e_"+net.edges.size();e.fromNodeId=nodeId(k);e.toNodeId=nodeId(next);e.roadType="walkable_adjacency";e.width=1;
            e.steps=List.of(step(cols.get(k)),step(cols.get(next)));net.edges.add(e);incident.putIfAbsent(k,e.id);incident.putIfAbsent(next,e.id);
        }
        for(Plot p:ir.plots){List<RoadStep> path=new ArrayList<>();for(RoadStep s:p.entrance.path)path.add(step(cols.get(key(s.x,s.z))));p.entrance.path=path;
            p.entrance.connectedEdgeId=incident.get(key(p.entrance.accessPoint[0],p.entrance.accessPoint[2]));}
        NetworkMetrics m=net.metrics;m.routingAttempts=attempts;m.hubs=hubs.size();m.semanticNodes=net.semanticNodes.size();m.semanticEdges=net.semanticLinks.size();m.components=1;
        m.cycleRank=m.semanticEdges-m.semanticNodes+1;m.verifiedLoops=(int)net.semanticLinks.stream().filter(e->e.verifiedLoop).count();
        m.pavedEnclosures=enclosures(map,network.roads,request.roadWidth);m.status=m.verifiedLoops>0?"CONNECTED_WITH_LOOPS":"CONNECTED_NO_LOOP";m.reasons.addAll(failures);
        for(SemanticLink e:net.semanticLinks)m.roadTypes.merge(e.roadType,1,Integer::sum);
        for(Hub h:hubs)if(branchDegree(net,h)>=3)m.branchNodes++;
        for(RoadEdge e:net.corridors)if(e.steps.size()>=30){m.longestStraightRatio=Math.max(m.longestStraightRatio,straightRatio(e.steps));m.microZigzagRatio=Math.max(m.microZigzagRatio,zigzagRatio(e.steps));}
        RouteQualityMetrics.summarize(net);
        ir.transportNetwork=net;ir.groundColumns=new ArrayList<>(cols.values());ir.search.constructionEdits=(int)editCount(cols);
        ir.earthworks=new EarthworkReport();for(GroundColumn c:cols.values()){ir.earthworks.totalCutVolume+=Math.max(0,c.originalY-c.targetY);ir.earthworks.totalFillVolume+=Math.max(0,c.targetY-c.originalY);}
        ir.earthworks.cutFillBalance=ir.earthworks.totalCutVolume-ir.earthworks.totalFillVolume;
        if(m.verifiedLoops==0)ir.auditLog.warnings.add("No verified macro loop in this bounded solve; see transportNetwork.metrics.reasons.");
    }
    private static String nodeId(long k){return "n_"+x(k)+"_"+z(k);}
    /** Distinct physical corridor exits of a semantic junction region, not degree of each brush cell. */
    private int branchDegree(TransportNetwork net,Hub hub) {
        int best=0;
        for(int radius:new int[]{5,8,11,14}) {
            List<RoadStep> exits=new ArrayList<>();
            for(RoadEdge e:net.corridors) {
                boolean start=hub.id.equals(e.fromNodeId),end=hub.id.equals(e.toNodeId);if(!start&&!end)continue;
                for(int i=0;i<e.steps.size();i++){RoadStep p=e.steps.get(start?i:e.steps.size()-1-i);
                    if(Math.max(Math.abs(p.x-hub.x),Math.abs(p.z-hub.z))>=radius){
                        if(exits.stream().noneMatch(q->StrictMath.hypot(q.x-p.x,q.z-p.z)<=request.roadWidth+1))exits.add(p);break;}}
            }
            best=Math.max(best,exits.size());
        }
        return best;
    }
    static double straightRatio(List<RoadStep> path) {
        double total=0,best=0,run=0;int px=0,pz=0;
        for(int i=1;i<path.size();i++){RoadStep a=path.get(i-1),b=path.get(i);int dx=b.x-a.x,dz=b.z-a.z;double length=StrictMath.hypot(dx,dz);
            run=dx==px&&dz==pz?run+length:length;best=Math.max(best,run);total+=length;px=dx;pz=dz;}
        return total==0?0:best/total;
    }
    static double zigzagRatio(List<RoadStep> path) {
        int n=0;for(int i=4;i<path.size();i++){
            int[] d=new int[8];for(int j=0;j<4;j++){RoadStep a=path.get(i-4+j),b=path.get(i-3+j);d[j*2]=b.x-a.x;d[j*2+1]=b.z-a.z;}
            if(d[0]==d[4]&&d[1]==d[5]&&d[2]==d[6]&&d[3]==d[7]&&(d[0]!=d[2]||d[1]!=d[3]))n++;
        }
        return path.size()<5?0:n/(double)(path.size()-4);
    }
}
