package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.SettlementPlanner.PlanRequest;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import static org.mcsettlement.planner.RoadGeometry.*;

/** Bounded weighted A* in (x,z,height,heading,rise). Never commits partial brush geometry. */
final class TerrainRoadRouter {
    record Result(List<RoadStep> centers,Set<Long> roadCells,Map<Long,GroundColumn> walk,boolean refined) {
        Result(List<RoadStep> centers,Set<Long> roadCells,Map<Long,GroundColumn> walk){this(centers,roadCells,walk,false);}
    }
    static final class Trace { String failure="NO_ROUTE_WITHIN_CONSTRAINTS"; int rejectedGoals; }
    private record Node(long id,int cell,int y,int heading,int rise,int run,int wetRun,int g,int f,Node parent) {}
    private static long id(int cell,int y,int heading,int rise,int run,int wetRun){return (((((long)cell*4096+y+2032)*13+heading)*3+rise+1)*25+run)*97+wetRun;}
    static Result route(HeightfieldMap map,PlanRequest req,Set<Long> forbidden,Set<Long> roads,
            Map<Long,GroundColumn> fixed,List<RoadStep> access,List<List<RoadStep>> accesses,
            List<List<RoadStep>> previousRoutes,int[] entry,int[] distances,SearchStats stats,int end) {
        return routeTo(map,req,forbidden,roads,fixed,access,accesses,previousRoutes,entry,distances,stats,end,roads,null);
    }
    /** Explicit semantic endpoint; existing roads are anchors, not implicit early-termination goals. */
    static Result routeTo(HeightfieldMap map,PlanRequest req,Set<Long> forbidden,Set<Long> roads,
            Map<Long,GroundColumn> fixed,List<RoadStep> access,List<List<RoadStep>> accesses,
            List<List<RoadStep>> previousRoutes,int[] entry,int[] distances,SearchStats stats,int end,
            Set<Long> goals,OrganicGuide guide) {
        return routeTo(map,req,forbidden,roads,fixed,access,accesses,previousRoutes,entry,distances,stats,end,goals,guide,new Trace());
    }
    static Result routeTo(HeightfieldMap map,PlanRequest req,Set<Long> forbidden,Set<Long> roads,
            Map<Long,GroundColumn> fixed,List<RoadStep> access,List<List<RoadStep>> accesses,
            List<List<RoadStep>> previousRoutes,int[] entry,int[] distances,SearchStats stats,int end,
            Set<Long> goals,OrganicGuide guide,Trace trace) {
        if(req.expert!=null){if(stats.topologyEdgesTried>=128){exhausted(stats,"TOPOLOGY_EDGE_ATTEMPTS");trace.failure="TOPOLOGY_EDGE_ATTEMPTS";return null;}stats.topologyEdgesTried++;}
        stats.routeAttempts++;
        RoadStep start=access.getLast();int w=map.getWidth();
        Map<Long,GroundColumn> anchors=new TreeMap<>(fixed);
        for(RoadStep s:access){long k=key(s.x,s.z);GroundColumn old=anchors.get(k);
            if(old!=null&&old.targetY!=s.y){trace.failure="ANCHOR_HEIGHT_CONFLICT";return null;}
            if(old==null)anchors.put(k,RoadTerrain.column(map,s.x,s.z,s.y,access.size()==1?"road":s==access.getFirst()?"foundation":"access",s.y+3));}
        int[][] dirs=directions(req.roadDirections);
        List<List<int[]>> brushes=new ArrayList<>();for(int[] d:dirs)brushes.add(stencil(d[0],d[1],req.roadWidth));
        if(cost(map,req,forbidden,anchors,start.x,start.z,start.y,0,0,start.y,stencil(0,0,req.roadWidth))<0){trace.failure="START_BRUSH_BLOCKED_OR_GRADE";return null;}
        int cell=(start.z-map.getMinZ())*w+start.x-map.getMinX(),heading=dirs.length;
        if(distances[cell]>=960)distances=coarseBarrierHeuristic(map,req,forbidden,goals,distances,stats,end);
        Map<Long,Node> best=new HashMap<>();
        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingInt(Node::f).thenComparingInt(Node::g).thenComparingLong(Node::id));
        int startHeading=heading,startRise=0,startWet=0;
        if(req.expert!=null&&access.size()>=2){RoadStep before=access.get(access.size()-2);for(int di=0;di<dirs.length;di++)if(dirs[di][0]==start.x-before.x&&dirs[di][1]==start.z-before.z){startHeading=di;break;}startRise=start.y-before.y;
            for(int i=access.size()-1;i>0;i--){RoadStep a=access.get(i-1),b=access.get(i);boolean wet=false;for(int[] v:stencil(b.x-a.x,b.z-a.z,req.roadWidth))if(RoadTerrain.wet(map,a.x+v[0],a.z+v[1])){wet=true;break;}if(!wet)break;startWet++;}}
        if(req.expert!=null&&startWet>req.expert.maxBridgeSpan){trace.failure="ACCESS_BRIDGE_SPAN";return null;}
        long rootId=id(cell,start.y,startHeading,startRise,0,startWet);
        Node root=new Node(rootId,cell,start.y,startHeading,startRise,0,startWet,0,3*(guide==null?distances[cell]:guide.heuristic(cell,distances[cell])),null);best.put(rootId,root);open.add(root);stats.peakPathStates=Math.max(stats.peakPathStates,1);stats.peakOpenNodes=Math.max(stats.peakOpenNodes,1);
        int rejected=0;
        while(!open.isEmpty()&&stats.pathExpanded<end&&stats.pathExpanded<stats.pathLimit){
            Node n=open.remove();if(best.get(n.id)!=n)continue;stats.pathExpanded++;
            int x=n.cell%w+map.getMinX(),z=n.cell/w+map.getMinZ();long k=key(x,z);
            GroundColumn goal=fixed.get(k);
            if(goals.contains(k)&&goal!=null&&goal.targetY==n.y){
                List<RoadStep> path=new ArrayList<>();for(Node p=n;p!=null;p=p.parent)path.add(new RoadStep(p.cell%w+map.getMinX(),p.y,p.cell/w+map.getMinZ(),"surface"));Collections.reverse(path);
                // A physical cell cannot be visited at two elevations or used as a loop-overpass.
                Set<Long> unique=new HashSet<>();boolean simple=true;for(RoadStep s:path)if(!unique.add(key(s.x,s.z))){simple=false;break;}
                if(simple){
                    // Reorder A-B-A-B into A-A-B-B in the REAL centerline, not a visual overlay.
                    // The candidate retains endpoint/heights and must pass the SAME full-width solver.
                    List<RoadStep> merged=req.expert!=null?path:coalesceMicroTurns(map,req,forbidden,anchors,path);
                    if(merged!=path){Set<Long> area=new TreeSet<>(roads);area.addAll(footprint(merged,req.roadWidth));
                        Map<Long,GroundColumn> solved=PavementGrades.solve(map,req,area,anchors,merged,accesses,previousRoutes,entry,stats);
                        if(solved!=null)return new Result(merged,area,solved);}
                    Set<Long> footprint=new TreeSet<>(roads);footprint.addAll(footprint(path,req.roadWidth));
                    Map<Long,GroundColumn> solved=PavementGrades.solve(map,req,footprint,anchors,path,accesses,previousRoutes,entry,stats);
                    if(solved!=null)return new Result(path,footprint,solved);}
                trace.rejectedGoals=++rejected;
                if(rejected>=6||stats.gradeRelaxations>=stats.gradeLimit){trace.failure=stats.gradeRelaxations>=stats.gradeLimit?"GRADE_RELAXATIONS":"GOAL_GRADE_OR_STAIRS_REJECTED";return null;}
            }
            for(int di=0;di<dirs.length;di++){
                boolean turning=n.heading!=heading&&di!=n.heading;
                if(guide!=null&&turning&&n.run<guide.minimumRun&&distances[n.cell]>50)continue;
                int dx=dirs[di][0],dz=dirs[di][1],nx=x+dx,nz=z+dz;if(!map.inBounds(nx,nz))continue;
                int next=(nz-map.getMinZ())*w+nx-map.getMinX();int natural=RoadTerrain.natural(map,nx,nz);
                boolean wetBrush=false;
                if(req.expert!=null&&req.expert.allowBridges)for(int[] v:brushes.get(di))if(RoadTerrain.wet(map,x+v[0],z+v[1])){wetBrush=true;break;}
                int wetRun=wetBrush?n.wetRun+1:0;
                if(wetBrush&&(dx!=0&&dz!=0||n.wetRun>0&&turning||wetRun>req.expert.maxBridgeSpan))continue;
                for(int rise:new int[]{0,-1,1}){
                    if(req.expert!=null&&(n.rise*rise<0||turning&&(rise!=0||n.rise!=0)||dx!=0&&dz!=0&&rise!=0))continue;
                    int y=n.y+rise;if(y<RoadTerrain.lower(map,req,nx,nz)||y>RoadTerrain.upper(map,req,nx,nz)||wetBrush&&rise!=0)continue;
                    int soil=cost(map,req,forbidden,anchors,x,z,n.y,dx,dz,y,brushes.get(di));if(soil<0)continue;
                    int turn=turning?3:0;
                    if(guide!=null&&turning){int delta=Math.abs(n.heading-di);delta=Math.min(delta,dirs.length-delta);turn=10+delta*8;}
                    int g=n.g+(dx==0||dz==0?10:Math.abs(dx)+Math.abs(dz)==2?14:22)+soil+Math.abs(rise)*7+turn+(n.rise*rise<0?12:0)+(guide==null?0:guide.cost(next)+guide.straightCost(n.run,di==n.heading));
                    int run=guide==null?0:di==n.heading?Math.min(24,n.run+1):1;
                    long nextId=id(next,y,di,rise,run,wetRun);Node old=best.get(nextId);if(old!=null&&g>=old.g)continue;
                    if(old==null&&best.size()>=req.searchBudget.pathStates){exhausted(stats,"PATH_STATES");continue;}
                    if(open.size()>=2*req.searchBudget.pathStates){exhausted(stats,"OPEN_NODES");trace.failure="OPEN_NODES";return null;}
                    Node value=new Node(nextId,next,y,di,rise,run,wetRun,g,g+3*(guide==null?distances[next]:guide.heuristic(next,distances[next])),n);best.put(nextId,value);open.add(value);
                    stats.peakPathStates=Math.max(stats.peakPathStates,best.size());stats.peakOpenNodes=Math.max(stats.peakOpenNodes,open.size());
                }
            }
        }
        if(stats.pathExpanded>=stats.pathLimit){exhausted(stats,"PATH_EXPANSIONS");trace.failure="PATH_EXPANSIONS";}
        else if(stats.pathExpanded>=end){exhausted(stats,"ROUTE_OR_SLOT_EXPANSIONS");trace.failure="ROUTE_OR_SLOT_EXPANSIONS";}
        else if(best.size()>=req.searchBudget.pathStates)trace.failure="PATH_STATES";
        else if(rejected>0)trace.failure="GOAL_GRADE_OR_STAIRS_REJECTED";
        return null;
    }
    static final class RefinementTrace {
        final Map<String,Integer> rejections=new TreeMap<>();
        String outcome="NOT_REQUIRED";
        int rhythmWindowsChecked,rhythmReorderings;
        void reject(String reason){rejections.merge(reason,1,Integer::sum);}
    }
    private record Run(int start,int end,int heading) {}
    private record HeightNode(int y,int cost,HeightNode parent) {}

    /** Improve a VALIDATED feasible route, not its drawing. A low-frequency offset replaces
     * a long heading run. Each alternative re-solves heights and the ENTIRE swept pavement.
     * At most 16 variants / 4 accepted offsets / 48 original moves per offset. All height-DP
     * settlements consume the original path budget; the caller caps extra work at 2048. */
    static Result refineLongRuns(HeightfieldMap map,PlanRequest req,Set<Long> forbidden,Set<Long> roads,
            Map<Long,GroundColumn> fixed,List<RoadStep> access,List<List<RoadStep>> accesses,
            List<List<RoadStep>> previousRoutes,int[] entry,SearchStats stats,int end,Result original,int side,RefinementTrace trace) {
        RouteQuality originalQuality=RouteQualityMetrics.measure(original.centers);
        if(originalQuality.maxStraightRun<=16&&originalQuality.rhythmZigzagWindows==0)return original;
        Map<Long,GroundColumn> anchors=new TreeMap<>(fixed);
        for(RoadStep p:access)anchors.putIfAbsent(key(p.x,p.z),new GroundColumn(p.x,p.z,map.getSurfaceY(p.x,p.z),p.y,
                Math.max(map.getSurfaceY(p.x,p.z),p.y+3),access.size()==1?"road":p==access.getFirst()?"foundation":"access"));
        trace.outcome="RETAINED_NO_VALID_BETTER_CENTERLINE";
        Result current=original;Set<String> examined=new HashSet<>();int tries=0,accepted=0;
        int[][] dirs=directions(req.roadDirections);
        if(originalQuality.rhythmZigzagWindows>0&&stats.pathExpanded<end&&stats.gradeRelaxations<stats.gradeLimit){
            List<RoadStep> rhythm=coalesceRhythm(map,req,forbidden,anchors,current.centers,stats,end,trace);
            if(rhythm!=current.centers){Set<Long> area=new TreeSet<>(roads);area.addAll(footprint(rhythm,req.roadWidth));
                var solved=PavementGrades.solve(map,req,area,anchors,rhythm,accesses,previousRoutes,entry,stats);
                if(solved!=null)current=new Result(rhythm,area,solved,true);else {trace.reject("RHYTHM_WHOLE_PAVEMENT_GRADE_OR_STAIRS");trace.rhythmReorderings=0;}}
        }
        while(tries<16&&accepted<4&&stats.pathExpanded<end&&stats.gradeRelaxations<stats.gradeLimit) {
            Run run=longestUnexamined(current.centers,dirs,examined);if(run==null)break;
            List<RoadStep> path=current.centers;RoadStep first=path.get(run.start),last=path.get(run.end);
            examined.add(first.x+","+first.z+":"+last.x+","+last.z);
            int runLength=run.end-run.start;
            int[] a=dirs[run.heading],b=dirs[(run.heading+1)%dirs.length],c=dirs[(run.heading+dirs.length-1)%dirs.length];
            int[] counts=symmetricCounts(a,b,c);if(counts==null)continue;
            Result best=current;double bestScore=refinementScore(current);
            // First try the full run, then a shorter central offset. The latter can avoid
            // fixed junction grades / obstacles near the ends without touching the endpoints.
            for(int template=0;template<2;template++)for(int sign:new int[]{side,-side}) {
                if(tries>=16||stats.pathExpanded>=end||stats.gradeRelaxations>=stats.gradeLimit)break;
                int amplitude=3,n=Math.min(template==0?48:24,runLength);
                int begin=run.start+(runLength-n)/2,finish=begin+n;
                RoadStep localFirst=path.get(begin);
                int spent=counts[2]*amplitude,left=n-spent;if(left<3)continue;
                int lead=left>=9?left/3:0,tail=lead,middle=left-lead-tail;
                int[] leftDir=sign>0?b:c,rightDir=sign>0?c:b;
                int leftCount=amplitude*counts[sign>0?0:1],rightCount=amplitude*counts[sign>0?1:0];
                List<int[]> xy=new ArrayList<>();xy.add(new int[]{localFirst.x,localFirst.z});
                append(xy,a,lead);append(xy,leftDir,leftCount);append(xy,a,middle);append(xy,rightDir,rightCount);append(xy,a,tail);
                RoadStep goal=path.get(finish);int[] actual=xy.getLast();
                if(actual[0]!=goal.x||actual[1]!=goal.z)throw new IllegalStateException("OFFSET_ENDPOINT_MISMATCH");
                tries++;stats.routeRefinementAttempts++;
                List<RoadStep> part=gradeOffset(map,req,forbidden,anchors,xy,localFirst.y,goal.y,stats,end,trace);
                if(part==null)continue;
                List<RoadStep> candidate=new ArrayList<>(path.subList(0,begin));candidate.addAll(part);candidate.addAll(path.subList(finish+1,path.size()));
                Set<Long> unique=new HashSet<>();boolean simple=true;for(RoadStep p:candidate)if(!unique.add(key(p.x,p.z))){simple=false;break;}
                RouteQuality quality=RouteQualityMetrics.measure(candidate),before=RouteQualityMetrics.measure(current.centers);
                if(!simple){trace.reject("CENTERLINE_SELF_INTERSECTION");continue;}
                if(quality.microZigzagWindows>before.microZigzagWindows||quality.rhythmZigzagWindows>before.rhythmZigzagWindows||quality.longStraightFraction>=before.longStraightFraction){trace.reject("NO_QUALITY_GAIN");continue;}
                Set<Long> area=new TreeSet<>(roads);area.addAll(footprint(candidate,req.roadWidth));
                Map<Long,GroundColumn> solved=PavementGrades.solve(map,req,area,anchors,candidate,accesses,previousRoutes,entry,stats);
                if(solved==null){trace.reject("WHOLE_PAVEMENT_GRADE_OR_STAIRS");continue;}
                Result option=new Result(candidate,area,solved,true);double score=refinementScore(option);
                if(score<bestScore){best=option;bestScore=score;}else trace.reject("QUALITY_EARTHWORK_SCORE");
            }
            if(best!=current){current=best;accepted++;}
        }
        if(current!=original)trace.outcome="ACCEPTED_VALIDATED_CENTERLINE";
        if(stats.pathExpanded>=end||stats.pathExpanded>=stats.pathLimit)trace.reject("REFINEMENT_PATH_BUDGET");
        if(stats.gradeRelaxations>=stats.gradeLimit)trace.reject("GRADE_RELAXATIONS");
        return current;
    }
    /** Normalize short sustained A^k B^k A^k B^k blocks to A^(2k) B^(2k).
     * Called ONLY after semantic candidate selection, never during building placement.
     * No spline: endpoint and step elevations are retained; exact width checks and the
     * full pavement solver decide acceptance. At most 2048 pattern windows / 16 edits;
     * every exact segment check consumes the SAME 2048 shared refinement path budget. */
    private static List<RoadStep> coalesceRhythm(HeightfieldMap map,PlanRequest req,Set<Long> forbidden,
            Map<Long,GroundColumn> anchors,List<RoadStep> original,SearchStats stats,int end,RefinementTrace trace) {
        List<RoadStep> path=new ArrayList<>(original);RouteQuality quality=RouteQualityMetrics.measure(path);
        int maxRun=Math.max(16,quality.maxStraightRun);int changed=0;
        outer:for(int pass=0;pass<2;pass++)for(int block=2;block<=3;block++)for(int i=0;i+4*block<path.size();i++) {
            if(trace.rhythmWindowsChecked>=2048||changed>=16||stats.pathExpanded>=end)break outer;
            trace.rhythmWindowsChecked++;stats.rhythmWindowsChecked++;
            RoadStep start=path.get(i),b=path.get(i+1),c=path.get(i+block),d=path.get(i+block+1);
            int ax=b.x-start.x,az=b.z-start.z,bx=d.x-c.x,bz=d.z-c.z;
            if(ax==bx&&az==bz||ax*bx+az*bz<0)continue;
            boolean pattern=true;
            for(int j=0;j<4*block;j++){RoadStep u=path.get(i+j),v=path.get(i+j+1);boolean first=(j/block)%2==0;
                if(v.x-u.x!=(first?ax:bx)||v.z-u.z!=(first?az:bz)){pattern=false;break;}}
            if(!pattern)continue;
            List<RoadStep> old=new ArrayList<>(path.subList(i,i+4*block+1));List<RoadStep> replacement=new ArrayList<>();replacement.add(start);
            boolean valid=true;
            for(int j=1;j<=4*block;j++){
                if(stats.pathExpanded>=end||stats.pathExpanded>=stats.pathLimit){trace.reject("RHYTHM_PATH_BUDGET");valid=false;break;}
                stats.pathExpanded++;stats.refinementExpanded++;
                RoadStep u=replacement.getLast();int dx=j<=2*block?ax:bx,dz=j<=2*block?az:bz;
                RoadStep v=new RoadStep(u.x+dx,old.get(j).y,u.z+dz,"surface");
                if(cost(map,req,forbidden,anchors,u.x,u.z,u.y,dx,dz,v.y,stencil(dx,dz,req.roadWidth))<0){valid=false;trace.reject("RHYTHM_WIDTH_OBSTACLE_OR_GRADE");break;}
                replacement.add(v);
            }
            if(!valid)continue;
            for(int j=1;j<replacement.size();j++)path.set(i+j,replacement.get(j));
            RouteQuality next=RouteQualityMetrics.measure(path);Set<Long> seen=new HashSet<>();boolean simple=true;
            for(RoadStep step:path)if(!seen.add(key(step.x,step.z))){simple=false;break;}
            if(!simple||next.maxStraightRun>maxRun||next.microZigzagWindows>quality.microZigzagWindows||next.rhythmZigzagWindows>=quality.rhythmZigzagWindows){
                for(int j=1;j<old.size();j++)path.set(i+j,old.get(j));trace.reject("RHYTHM_NO_SAFE_QUALITY_GAIN");continue;}
            quality=next;changed++;trace.rhythmReorderings++;
        }
        return changed==0?original:path;
    }
    private static double refinementScore(Result result) {
        RouteQuality q=RouteQualityMetrics.measure(result.centers);double soil=0;
        for(GroundColumn c:result.walk.values())soil+=Math.abs(c.targetY-c.originalY);
        return q.longStraightFraction*1000+q.maxStraightRun*8+q.length+q.shortRuns*4+q.rawTurns*2+soil*.3;
    }
    private static Run longestUnexamined(List<RoadStep> path,int[][] dirs,Set<String> seen) {
        Run best=null;
        for(int i=1;i<path.size();) {
            int start=i-1,end=i;RoadStep a=path.get(start),b=path.get(i);int dx=b.x-a.x,dz=b.z-a.z;
            while(end+1<path.size()&&path.get(end+1).x-path.get(end).x==dx&&path.get(end+1).z-path.get(end).z==dz)end++;
            RoadStep last=path.get(end);String id=a.x+","+a.z+":"+last.x+","+last.z;
            if(end-start>16&&!seen.contains(id)&&(best==null||end-start>best.end-best.start)){
                for(int h=0;h<dirs.length;h++)if(dirs[h][0]==dx&&dirs[h][1]==dz){best=new Run(start,end,h);break;}}
            i=end+1;
        }
        return best;
    }
    /** Integer balance bCount*B + cCount*C = aCount*A for both 8 and 12 headings. */
    private static int[] symmetricCounts(int[] a,int[] b,int[] c) {
        for(int total=2;total<=8;total++)for(int nb=1;nb<total;nb++)for(int na=1;na<=8;na++){
            int nc=total-nb;if(nb*b[0]+nc*c[0]==na*a[0]&&nb*b[1]+nc*c[1]==na*a[1])return new int[]{nb,nc,na};}
        return null;
    }
    private static void append(List<int[]> xy,int[] direction,int n) {
        for(int i=0;i<n;i++){int[] p=xy.getLast();xy.add(new int[]{p[0]+direction[0],p[1]+direction[1]});}
    }
    private static List<RoadStep> gradeOffset(HeightfieldMap map,PlanRequest req,Set<Long> forbidden,
            Map<Long,GroundColumn> anchors,List<int[]> xy,int startY,int endY,SearchStats stats,int end,RefinementTrace trace) {
        Map<Integer,HeightNode> layer=new TreeMap<>();layer.put(startY,new HeightNode(startY,0,null));int created=1;
        for(int i=1;i<xy.size();i++){
            int[] a=xy.get(i-1),b=xy.get(i);if(!map.inBounds(b[0],b[1])){trace.reject("MAP_BOUNDARY");return null;}
            int dx=b[0]-a[0],dz=b[1]-a[1];List<int[]> brush=stencil(dx,dz,req.roadWidth);
            Map<Integer,HeightNode> next=new TreeMap<>();
            for(HeightNode previous:layer.values()){
                if(stats.pathExpanded>=end||stats.pathExpanded>=stats.pathLimit){trace.reject("REFINEMENT_PATH_BUDGET");return null;}
                stats.pathExpanded++;stats.refinementExpanded++;
                for(int rise:new int[]{0,-1,1}){
                    int y=previous.y+rise;if(i==xy.size()-1&&y!=endY)continue;
                    int soil=cost(map,req,forbidden,anchors,a[0],a[1],previous.y,dx,dz,y,brush);if(soil<0)continue;
                    int value=previous.cost+soil+Math.abs(rise)*7;HeightNode old=next.get(y);
                    if(old!=null&&value>=old.cost)continue;
                    if(++created>req.searchBudget.pathStates){trace.reject("PATH_STATES");return null;}
                    stats.peakPathStates=Math.max(stats.peakPathStates,created);
                    next.put(y,new HeightNode(y,value,previous));
                }
            }
            if(next.isEmpty()){trace.reject("WIDTH_OBSTACLE_OR_GRADE@"+b[0]+","+b[1]);return null;}layer=next;
        }
        HeightNode node=layer.get(endY);if(node==null){trace.reject("END_ANCHOR_HEIGHT");return null;}
        List<RoadStep> result=new ArrayList<>();
        for(int i=xy.size()-1;i>=0;i--){int[] p=xy.get(i);result.add(new RoadStep(p[0],node.y,p[1],"surface"));node=node.parent;}
        Collections.reverse(result);return result;
    }

    /** At most two scans and 128 local reorderings. Occupancy/cut/fill checks are exact;
     * the caller validates global grades and falls back unchanged if the candidate fails. */
    private static List<RoadStep> coalesceMicroTurns(HeightfieldMap map,PlanRequest req,Set<Long> forbidden,
            Map<Long,GroundColumn> anchors,List<RoadStep> original){
        List<RoadStep> path=new ArrayList<>(original);int changes=0;
        for(int pass=0;pass<2;pass++)for(int i=0;i+4<path.size()&&changes<128;i++){
            RoadStep p=path.get(i),a=path.get(i+1),b=path.get(i+2),c=path.get(i+3),e=path.get(i+4);
            int ax=a.x-p.x,az=a.z-p.z,bx=b.x-a.x,bz=b.z-a.z;
            if(ax==bx&&az==bz||ax*bx+az*bz<0||c.x-b.x!=ax||c.z-b.z!=az||e.x-c.x!=bx||e.z-c.z!=bz)continue;
            RoadStep[] q={p,new RoadStep(p.x+ax,a.y,p.z+az,"surface"),
                new RoadStep(p.x+2*ax,b.y,p.z+2*az,"surface"),
                new RoadStep(p.x+2*ax+bx,c.y,p.z+2*az+bz,"surface"),e};
            boolean valid=true;
            for(int j=0;j<4;j++){RoadStep u=q[j],v=q[j+1];int dx=v.x-u.x,dz=v.z-u.z;
                if(cost(map,req,forbidden,anchors,u.x,u.z,u.y,dx,dz,v.y,stencil(dx,dz,req.roadWidth))<0){valid=false;break;}}
            if(valid){for(int j=1;j<4;j++)path.set(i+j,q[j]);changes++;}
        }
        if(changes==0)return original;
        Set<Long> seen=new HashSet<>();for(RoadStep p:path)if(!seen.add(key(p.x,p.z)))return original;
        return path;
    }
    /** Obstacle-aware 8x8 guide for long routes. A tile is open when ANY cell is buildable,
     * so a narrow passage is never pruned. This is only a cost guide; the exact brush still decides.
     * At most 4096 settled tiles; every settlement also consumes the ORIGINAL path expansion budget. */
    private static int[] coarseBarrierHeuristic(HeightfieldMap map,PlanRequest req,Set<Long> forbidden,Set<Long> goals,int[] original,SearchStats stats,int end){
        int scale=8,w=map.getWidth(),d=map.getDepth(),cw=(w+7)/8,cd=(d+7)/8,n=cw*cd;
        boolean[] open=new boolean[n];
        for(int z=0;z<cd;z++)for(int x=0;x<cw;x++){
            outer:for(int dz=0;dz<8;dz++)for(int dx=0;dx<8;dx++){
                int wx=map.getMinX()+x*8+dx,wz=map.getMinZ()+z*8+dz;
                if(RoadTerrain.allowed(map,req,wx,wz)&&!forbidden.contains(key(wx,wz))){open[z*cw+x]=true;break outer;}
            }
        }
        int[] dist=new int[n];Arrays.fill(dist,1000000);boolean[] settled=new boolean[n];
        PriorityQueue<int[]> q=new PriorityQueue<>(Comparator.<int[]>comparingInt(a->a[1]).thenComparingInt(a->a[0]));
        for(long k:goals){int i=(z(k)-map.getMinZ())/scale*cw+(x(k)-map.getMinX())/scale;if(i>=0&&i<n&&dist[i]!=0){dist[i]=0;open[i]=true;q.add(new int[]{i,0});}}
        int workEnd=Math.min(end,stats.pathExpanded+Math.min(4096,Math.max(0,(end-stats.pathExpanded)/3)));
        while(!q.isEmpty()&&stats.pathExpanded<workEnd&&stats.pathExpanded<stats.pathLimit){
            int[] v=q.remove();int i=v[0];if(v[1]!=dist[i]||settled[i])continue;settled[i]=true;stats.pathExpanded++;stats.heuristicExpanded++;
            int x=i%cw,z=i/cw;
            for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
                int nx=x+dx,nz=z+dz;if(dx==0&&dz==0||nx<0||nz<0||nx>=cw||nz>=cd)continue;
                int next=nz*cw+nx,cost=v[1]+(dx==0||dz==0?10:14);
                if(open[next]&&cost<dist[next]){dist[next]=cost;q.add(new int[]{next,cost});}
            }
        }
        int[] out=original.clone();for(int z=0;z<d;z++)for(int x=0;x<w;x++){
            int i=z/8*cw+x/8;if(settled[i])out[z*w+x]=Math.max(out[z*w+x],dist[i]*scale-160);
        }return out;
    }
    /** All supercover and brush cells are checked, including skipped/corner cells of long diagonals. */
    private static int cost(HeightfieldMap m,PlanRequest r,Set<Long> forbidden,Map<Long,GroundColumn> fixed,
            int x,int z,int y,int dx,int dz,int ny,List<int[]> brush){
        int soil=0;
        for(int[] d:brush){int xx=x+d[0],zz=z+d[1];long k=key(xx,zz);
            if(forbidden.contains(k)||!RoadTerrain.allowed(m,r,xx,zz))return -1;
            if(RoadTerrain.wet(m,xx,zz)&&(dx!=0&&dz!=0||y!=ny))return -1;
            int h=RoadTerrain.natural(m,xx,zz),da=Math.abs(d[0])+Math.abs(d[1]),db=Math.abs(d[0]-dx)+Math.abs(d[1]-dz);
            int lo=Math.max(RoadTerrain.lower(m,r,xx,zz),Math.max(y-da,ny-db)),hi=Math.min(RoadTerrain.upper(m,r,xx,zz),Math.min(y+da,ny+db));
            GroundColumn c=fixed.get(k);if(c!=null){lo=Math.max(lo,c.targetY);hi=Math.min(hi,c.targetY);}
            if(lo>hi)return -1;soil+=Math.max(0,Math.max(lo-h,h-hi));
        }
        return (soil*6)/Math.max(1,brush.size())+Math.abs(RoadTerrain.natural(m,x+dx,z+dz)-ny)*18+(RoadTerrain.wet(m,x+dx,z+dz)?24:0);
    }
}
