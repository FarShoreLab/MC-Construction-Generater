package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import static org.mcsettlement.planner.RoadGeometry.*;

/** Diagnostics are measured from the committed grid and centerlines, never guide drawings. */
final class RoadNetworkAnalysis {
    private RoadNetworkAnalysis() {}
    static Map<Long,Integer> walkDistances(Map<Long,GroundColumn> walk,long from,SearchStats stats){
        Map<Long,Integer> out=new HashMap<>();if(!walk.containsKey(from))return out;
        ArrayDeque<Long> queue=new ArrayDeque<>();queue.add(from);out.put(from,0);
        while(!queue.isEmpty()){long k=queue.removeFirst();if(stats!=null)stats.networkWalkVisits++;
            GroundColumn a=walk.get(k);int nextDistance=out.get(k)+1;
            for(int[] v:CARDINAL){long t=key(x(k)+v[0],z(k)+v[1]);if(out.containsKey(t))continue;
                if(canWalk(a,walk.get(t))){out.put(t,nextDistance);queue.addLast(t);}}
        }return out;
    }
    /** Bounded flood-fill: a macro enclosure must be internal, sufficiently large and thick.
     * Thickness is sqrt(12*minor covariance eigenvalue), rotation invariant; thin ribbons
     * cannot pass by making a large diagonal axis-aligned box. This is a projected ground
     * enclosure metric; bridges still require the independent connectivity/height audit.
     */
    static List<Integer> enclosures(HeightfieldMap map,Set<Long> roads,int width,SearchStats stats){
        int w=map.getWidth(),d=map.getDepth(),n=w*d;boolean[] seen=new boolean[n];int[] queue=new int[n];
        for(long k:roads)if(map.inBounds(x(k),z(k)))seen[(z(k)-map.getMinZ())*w+x(k)-map.getMinX()]=true;
        List<Integer> areas=new ArrayList<>();int minArea=Math.max(64,width*width*16);
        for(int start=0;start<n;start++)if(!seen[start]){
            int head=0,tail=1;queue[0]=start;seen[start]=true;boolean border=false;
            double sx=0,sz=0,sxx=0,szz=0,sxz=0;
            while(head<tail){int at=queue[head++],x=at%w,z=at/w;if(stats!=null)stats.enclosureCellVisits++;
                border|=x==0||z==0||x==w-1||z==d-1;sx+=x;sz+=z;sxx+=(double)x*x;szz+=(double)z*z;sxz+=(double)x*z;
                for(int[] v:CARDINAL){int xx=x+v[0],zz=z+v[1];if(xx<0||xx>=w||zz<0||zz>=d)continue;int next=zz*w+xx;
                    if(!seen[next]){seen[next]=true;queue[tail++]=next;}}
            }
            if(border||tail<minArea)continue;
            double xx=sxx/tail-Math.pow(sx/tail,2),zz=szz/tail-Math.pow(sz/tail,2),xz=sxz/tail-sx*sz/((double)tail*tail);
            double minor=Math.max(0,(xx+zz-Math.hypot(xx-zz,2*xz))/2);
            if(Math.sqrt(12*minor)>=Math.max(8,width*3))areas.add(tail);
        }
        areas.sort(Comparator.reverseOrder());return areas;
    }
    private record SegmentKey(int ax,int ay,int az,int bx,int by,int bz) {}
    private static final class Segment {
        final RoadStep a,b;final double length,mx,mz,my,dx,dz;int uses=1;
        Segment(RoadStep a,RoadStep b){this.a=a;this.b=b;dx=b.x-a.x;dz=b.z-a.z;length=Math.hypot(dx,dz);mx=(a.x+b.x)*.5;mz=(a.z+b.z)*.5;my=(a.y+b.y)*.5;}
    }
    static void centerlines(List<RoadEdge> routes,NetworkMetrics out,int radius){
        LinkedHashMap<SegmentKey,Segment> unique=new LinkedHashMap<>();
        out.uniqueCenterlineLength=out.sharedCenterlineLength=out.nearParallelLength=0;
        out.cardinalSteps=out.diagonal45Steps=out.obliqueSteps=0;out.proximityMetricComparisons=0;
        for(RoadEdge road:routes)for(int i=1;i<road.steps.size();i++){
            RoadStep a=road.steps.get(i-1),b=road.steps.get(i);if(a.x==b.x&&a.z==b.z)continue;
            if(a.x>b.x||a.x==b.x&&(a.z>b.z||a.z==b.z&&a.y>b.y)){RoadStep t=a;a=b;b=t;}
            var key=new SegmentKey(a.x,a.y,a.z,b.x,b.y,b.z);Segment old=unique.get(key);
            if(old==null)unique.put(key,new Segment(a,b));else old.uses++;
        }
        List<Segment> segments=new ArrayList<>(unique.values());
        for(Segment s:segments){out.uniqueCenterlineLength+=s.length;out.sharedCenterlineLength+=(s.uses-1)*s.length;
            if(s.dx==0||s.dz==0)out.cardinalSteps++;else if(Math.abs(s.dx)==Math.abs(s.dz))out.diagonal45Steps++;else out.obliqueSteps++;}
        if(radius<=0)return;
        int bucket=radius+3;Map<Long,List<Integer>> bins=new HashMap<>();boolean[] near=new boolean[segments.size()];
        for(int i=0;i<segments.size();i++){
            Segment a=segments.get(i);int bx=(int)Math.floor(a.mx/bucket),bz=(int)Math.floor(a.mz/bucket);
            for(int ox=-1;ox<=1;ox++)for(int oz=-1;oz<=1;oz++)for(int j:bins.getOrDefault(key(bx+ox,bz+oz),List.of())){
                out.proximityMetricComparisons++;Segment b=segments.get(j);
                if(parallel(a,b,radius))near[i]=near[j]=true;
            }
            bins.computeIfAbsent(key(bx,bz),k->new ArrayList<>()).add(i);
        }
        for(int i=0;i<near.length;i++)if(near[i])out.nearParallelLength+=segments.get(i).length;
    }
    private static boolean parallel(Segment a,Segment b,int radius){
        if(Math.abs(a.my-b.my)>1||Math.hypot(a.mx-b.mx,a.mz-b.mz)>radius+3)return false;
        double dot=a.dx*b.dx+a.dz*b.dz;
        if(dot*dot<.72*a.length*a.length*b.length*b.length)return false;
        // Positive overlap on both longitudinal projections; touching consecutive segments
        // and perpendicular crossing junctions must not be reported as parallel lanes.
        double ua=(a.dx*b.a.x+a.dz*b.a.z-a.dx*a.a.x-a.dz*a.a.z)/a.length;
        double ub=(a.dx*b.b.x+a.dz*b.b.z-a.dx*a.a.x-a.dz*a.a.z)/a.length;
        if(Math.min(a.length,Math.max(ua,ub))-Math.max(0,Math.min(ua,ub))<=.05)return false;
        double va=(b.dx*a.a.x+b.dz*a.a.z-b.dx*b.a.x-b.dz*b.a.z)/b.length;
        double vb=(b.dx*a.b.x+b.dz*a.b.z-b.dx*b.a.x-b.dz*b.a.z)/b.length;
        if(Math.min(b.length,Math.max(va,vb))-Math.max(0,Math.min(va,vb))<=.05)return false;
        double separation=Math.abs(a.dx*(b.mz-a.mz)-a.dz*(b.mx-a.mx))/a.length;
        return separation>.25&&separation<=radius;
    }
}
