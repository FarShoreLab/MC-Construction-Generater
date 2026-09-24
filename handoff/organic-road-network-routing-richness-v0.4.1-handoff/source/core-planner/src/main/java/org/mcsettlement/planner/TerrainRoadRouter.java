package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.SettlementPlanner.PlanRequest;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import static org.mcsettlement.planner.RoadGeometry.*;

/** Bounded weighted A* in (x,z,height,heading,rise). Never commits partial brush geometry. */
final class TerrainRoadRouter {
    record Result(List<RoadStep> centers,Set<Long> roadCells,Map<Long,GroundColumn> walk) {}
    private record Node(long id,int cell,int y,int heading,int rise,int run,int g,int f,Node parent) {}
    private static long id(int cell,int y,int heading,int rise,int run){return ((((long)cell*4096+y+2032)*13+heading)*3+rise+1)*5+run;}
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
        stats.routeAttempts++;
        RoadStep start=access.getLast();int w=map.getWidth();
        Map<Long,GroundColumn> anchors=new TreeMap<>(fixed);
        for(RoadStep s:access){long k=key(s.x,s.z);GroundColumn old=anchors.get(k);
            if(old!=null&&old.targetY!=s.y)return null;
            if(old==null)anchors.put(k,new GroundColumn(s.x,s.z,map.getSurfaceY(s.x,s.z),s.y,
                    Math.max(map.getSurfaceY(s.x,s.z),s.y+3),access.size()==1?"road":s==access.getFirst()?"foundation":"access"));}
        int[][] dirs=directions(req.roadDirections);
        List<List<int[]>> brushes=new ArrayList<>();for(int[] d:dirs)brushes.add(stencil(d[0],d[1],req.roadWidth));
        if(cost(map,req,forbidden,anchors,start.x,start.z,start.y,0,0,start.y,stencil(0,0,req.roadWidth))<0)return null;
        int cell=(start.z-map.getMinZ())*w+start.x-map.getMinX(),heading=dirs.length;
        if(distances[cell]>=960)distances=coarseBarrierHeuristic(map,forbidden,goals,distances,stats,end);
        Map<Long,Node> best=new HashMap<>();
        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingInt(Node::f).thenComparingInt(Node::g).thenComparingLong(Node::id));
        long rootId=id(cell,start.y,heading,0,0);
        Node root=new Node(rootId,cell,start.y,heading,0,0,0,distances[cell]*3,null);best.put(rootId,root);open.add(root);stats.peakPathStates=Math.max(stats.peakPathStates,1);stats.peakOpenNodes=Math.max(stats.peakOpenNodes,1);
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
                    List<RoadStep> merged=coalesceMicroTurns(map,req,forbidden,anchors,path);
                    if(merged!=path){Set<Long> area=new TreeSet<>(roads);area.addAll(footprint(merged,req.roadWidth));
                        Map<Long,GroundColumn> solved=PavementGrades.solve(map,req,area,anchors,merged,accesses,previousRoutes,entry,stats);
                        if(solved!=null)return new Result(merged,area,solved);}
                    Set<Long> footprint=new TreeSet<>(roads);footprint.addAll(footprint(path,req.roadWidth));
                    Map<Long,GroundColumn> solved=PavementGrades.solve(map,req,footprint,anchors,path,accesses,previousRoutes,entry,stats);
                    if(solved!=null)return new Result(path,footprint,solved);}
                if(++rejected>=6||stats.gradeRelaxations>=stats.gradeLimit)return null;
            }
            for(int di=0;di<dirs.length;di++){
                boolean turning=n.heading!=heading&&di!=n.heading;
                if(guide!=null&&turning&&n.run<3&&distances[n.cell]>50)continue;
                int dx=dirs[di][0],dz=dirs[di][1],nx=x+dx,nz=z+dz;if(!map.inBounds(nx,nz))continue;
                int next=(nz-map.getMinZ())*w+nx-map.getMinX();int natural=map.getSurfaceY(nx,nz);
                for(int rise:new int[]{0,-1,1}){
                    int y=n.y+rise;if(y<natural-req.roadMaxCut||y>natural+req.roadMaxFill)continue;
                    int soil=cost(map,req,forbidden,anchors,x,z,n.y,dx,dz,y,brushes.get(di));if(soil<0)continue;
                    int turn=turning?3:0;
                    if(guide!=null&&turning){int delta=Math.abs(n.heading-di);delta=Math.min(delta,dirs.length-delta);turn=10+delta*8;}
                    int g=n.g+(dx==0||dz==0?10:Math.abs(dx)+Math.abs(dz)==2?14:22)+soil+Math.abs(rise)*7+turn+(n.rise*rise<0?12:0)+(guide==null?0:guide.cost(next));
                    int run=guide==null?0:di==n.heading?Math.min(4,n.run+1):1;
                    long nextId=id(next,y,di,rise,run);Node old=best.get(nextId);if(old!=null&&g>=old.g)continue;
                    if(old==null&&best.size()>=req.searchBudget.pathStates){exhausted(stats,"PATH_STATES");continue;}
                    if(open.size()>=2*req.searchBudget.pathStates){exhausted(stats,"OPEN_NODES");return null;}
                    Node value=new Node(nextId,next,y,di,rise,run,g,g+3*distances[next],n);best.put(nextId,value);open.add(value);
                    stats.peakPathStates=Math.max(stats.peakPathStates,best.size());stats.peakOpenNodes=Math.max(stats.peakOpenNodes,open.size());
                }
            }
        }
        if(stats.pathExpanded>=stats.pathLimit)exhausted(stats,"PATH_EXPANSIONS");
        else if(stats.pathExpanded>=end)exhausted(stats,"ROUTE_OR_SLOT_EXPANSIONS");
        return null;
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
    private static int[] coarseBarrierHeuristic(HeightfieldMap map,Set<Long> forbidden,Set<Long> goals,int[] original,SearchStats stats,int end){
        int scale=8,w=map.getWidth(),d=map.getDepth(),cw=(w+7)/8,cd=(d+7)/8,n=cw*cd;
        boolean[] open=new boolean[n];
        for(int z=0;z<cd;z++)for(int x=0;x<cw;x++){
            outer:for(int dz=0;dz<8;dz++)for(int dx=0;dx<8;dx++){
                int wx=map.getMinX()+x*8+dx,wz=map.getMinZ()+z*8+dz;
                if(buildable(map,wx,wz)&&!forbidden.contains(key(wx,wz))){open[z*cw+x]=true;break outer;}
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
            if(forbidden.contains(k)||!buildable(m,xx,zz))return -1;
            int h=m.getSurfaceY(xx,zz),da=Math.abs(d[0])+Math.abs(d[1]),db=Math.abs(d[0]-dx)+Math.abs(d[1]-dz);
            int lo=Math.max(h-r.roadMaxCut,Math.max(y-da,ny-db)),hi=Math.min(h+r.roadMaxFill,Math.min(y+da,ny+db));
            GroundColumn c=fixed.get(k);if(c!=null){lo=Math.max(lo,c.targetY);hi=Math.min(hi,c.targetY);}
            if(lo>hi)return -1;soil+=Math.max(0,Math.max(lo-h,h-hi));
        }
        return (soil*6)/Math.max(1,brush.size())+Math.abs(m.getSurfaceY(x+dx,z+dz)-ny)*18;
    }
}
