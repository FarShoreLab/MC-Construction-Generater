package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.SettlementPlanner.PlanRequest;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import static org.mcsettlement.planner.RoadGeometry.*;

/** Bounded weighted A* in (x,z,height,heading,rise). Never commits partial brush geometry. */
final class TerrainRoadRouter {
    record Result(List<RoadStep> centers,Set<Long> roadCells,Map<Long,GroundColumn> walk) {}
    private record Node(long id,int cell,int y,int heading,int rise,int g,int f,Node parent) {}
    private static long id(int cell,int y,int heading,int rise){return (((long)cell*4096+y+2032)*13+heading)*3+rise+1;}
    static Result route(HeightfieldMap map,PlanRequest req,Set<Long> forbidden,Set<Long> roads,
            Map<Long,GroundColumn> fixed,List<RoadStep> access,List<List<RoadStep>> accesses,
            List<List<RoadStep>> previousRoutes,int[] entry,int[] distances,SearchStats stats,int end) {
        stats.routeAttempts++;
        RoadStep start=access.getLast();int w=map.getWidth();
        Map<Long,GroundColumn> anchors=new TreeMap<>(fixed);
        for(RoadStep s:access){long k=key(s.x,s.z);GroundColumn old=anchors.get(k);
            if(old!=null&&old.targetY!=s.y)return null;
            if(old==null)anchors.put(k,new GroundColumn(s.x,s.z,map.getSurfaceY(s.x,s.z),s.y,
                    Math.max(map.getSurfaceY(s.x,s.z),s.y+3),s==access.getFirst()?"foundation":"access"));}
        int[][] dirs=directions(req.roadDirections);
        List<List<int[]>> brushes=new ArrayList<>();for(int[] d:dirs)brushes.add(stencil(d[0],d[1],req.roadWidth));
        if(cost(map,req,forbidden,anchors,start.x,start.z,start.y,0,0,start.y,stencil(0,0,req.roadWidth))<0)return null;
        int cell=(start.z-map.getMinZ())*w+start.x-map.getMinX(),heading=dirs.length;
        Map<Long,Node> best=new HashMap<>();
        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingInt(Node::f).thenComparingInt(Node::g).thenComparingLong(Node::id));
        long rootId=id(cell,start.y,heading,0);
        Node root=new Node(rootId,cell,start.y,heading,0,0,distances[cell]*3,null);best.put(rootId,root);open.add(root);stats.peakPathStates=Math.max(stats.peakPathStates,1);stats.peakOpenNodes=Math.max(stats.peakOpenNodes,1);
        int rejected=0;
        while(!open.isEmpty()&&stats.pathExpanded<end&&stats.pathExpanded<stats.pathLimit){
            Node n=open.remove();if(best.get(n.id)!=n)continue;stats.pathExpanded++;
            int x=n.cell%w+map.getMinX(),z=n.cell/w+map.getMinZ();long k=key(x,z);
            GroundColumn goal=fixed.get(k);
            if(roads.contains(k)&&goal!=null&&goal.targetY==n.y){
                List<RoadStep> path=new ArrayList<>();for(Node p=n;p!=null;p=p.parent)path.add(new RoadStep(p.cell%w+map.getMinX(),p.y,p.cell/w+map.getMinZ(),"surface"));Collections.reverse(path);
                // A physical cell cannot be visited at two elevations or used as a loop-overpass.
                Set<Long> unique=new HashSet<>();boolean simple=true;for(RoadStep s:path)if(!unique.add(key(s.x,s.z))){simple=false;break;}
                if(simple){Set<Long> footprint=new TreeSet<>(roads);footprint.addAll(footprint(path,req.roadWidth));
                    Map<Long,GroundColumn> solved=PavementGrades.solve(map,req,footprint,anchors,path,accesses,previousRoutes,entry,stats);
                    if(solved!=null)return new Result(path,footprint,solved);}
                if(++rejected>=6||stats.gradeRelaxations>=stats.gradeLimit)return null;
            }
            for(int di=0;di<dirs.length;di++){
                int dx=dirs[di][0],dz=dirs[di][1],nx=x+dx,nz=z+dz;if(!map.inBounds(nx,nz))continue;
                int next=(nz-map.getMinZ())*w+nx-map.getMinX();int natural=map.getSurfaceY(nx,nz);
                for(int rise:new int[]{0,-1,1}){
                    int y=n.y+rise;if(y<natural-req.roadMaxCut||y>natural+req.roadMaxFill)continue;
                    int soil=cost(map,req,forbidden,anchors,x,z,n.y,dx,dz,y,brushes.get(di));if(soil<0)continue;
                    int turn=n.heading==heading||di==n.heading?0:3;
                    int g=n.g+(dx==0||dz==0?10:Math.abs(dx)+Math.abs(dz)==2?14:22)+soil+Math.abs(rise)*7+turn+(n.rise*rise<0?12:0);
                    long nextId=id(next,y,di,rise);Node old=best.get(nextId);if(old!=null&&g>=old.g)continue;
                    if(old==null&&best.size()>=req.searchBudget.pathStates){exhausted(stats,"PATH_STATES");continue;}
                    if(open.size()>=2*req.searchBudget.pathStates){exhausted(stats,"OPEN_NODES");return null;}
                    Node value=new Node(nextId,next,y,di,rise,g,g+3*distances[next],n);best.put(nextId,value);open.add(value);
                    stats.peakPathStates=Math.max(stats.peakPathStates,best.size());stats.peakOpenNodes=Math.max(stats.peakOpenNodes,open.size());
                }
            }
        }
        if(stats.pathExpanded>=stats.pathLimit)exhausted(stats,"PATH_EXPANSIONS");
        else if(stats.pathExpanded>=end)exhausted(stats,"ROUTE_OR_SLOT_EXPANSIONS");
        return null;
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
