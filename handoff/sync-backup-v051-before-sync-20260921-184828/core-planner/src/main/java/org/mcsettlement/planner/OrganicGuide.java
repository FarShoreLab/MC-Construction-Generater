package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.terrain.SpatialNoise;
import static org.mcsettlement.planner.RoadGeometry.*;

/** Bounded, deterministic shape proposals. Guides are cost fields, NEVER rendered pavement. */
final class OrganicGuide {
    /** Cheap proposals carry sparse controls, not one map-sized field per candidate. */
    static final class Shape {
        final String id, family;
        final List<int[]> controls;
        final List<int[]> samples;
        final boolean conservative;
        double estimate;
        int probeCells;
        Shape(String id,String family,List<int[]> controls,List<int[]> samples,boolean conservative) {
            this.id=id;this.family=family;this.controls=controls;this.samples=samples;this.conservative=conservative;
        }
    }
    final int[] distance, remaining;
    final List<int[]> controls;
    final String candidateId;
    final boolean conservative;
    final int preferredRun, minimumRun;

    /** Compatibility constructor: the original arc remains the conservative, guided stage. */
    OrganicGuide(HeightfieldMap map,int ax,int az,int bx,int bz,long seed,int variant) {
        this(map,ax,az,bx,bz,seed,variant,4);
    }
    OrganicGuide(HeightfieldMap map,int ax,int az,int bx,int bz,long seed,int variant,double minimumOffset) {
        this(map,arc(map,ax,az,bx,bz,seed,variant,minimumOffset),"local");
    }
    OrganicGuide(HeightfieldMap map,Shape shape,String role) {
        controls=shape.controls;candidateId=shape.id;conservative=shape.conservative;
        preferredRun="local".equals(role)?14:10;minimumRun=3;
        int w=map.getWidth(),d=map.getDepth();distance=new int[w*d];remaining=new int[w*d];
        Arrays.fill(distance,10_000_000);
        double length=0;double[] along=new double[shape.samples.size()];
        for(int i=1;i<along.length;i++){int[] a=shape.samples.get(i-1),b=shape.samples.get(i);length+=StrictMath.hypot(a[0]-b[0],a[1]-b[1]);along[i]=length;}
        for(int j=0;j<along.length;j++){int[] p=shape.samples.get(j);if(!map.inBounds(p[0],p[1]))continue;
            int cell=(p[1]-map.getMinZ())*w+p[0]-map.getMinX();distance[cell]=0;remaining[cell]=(int)StrictMath.round((length-along[j])*10);}
        // Same octile transform as RoadGeometry.distanceField, carrying nearest-guide progress.
        // Ties prefer later progress, independent of HashSet iteration order.
        for(int z=0;z<d;z++)for(int x=0;x<w;x++){
            int i=z*w+x;if(x>0)relax(i,i-1,10);if(z>0){relax(i,i-w,10);if(x>0)relax(i,i-w-1,14);if(x+1<w)relax(i,i-w+1,14);}}
        for(int z=d-1;z>=0;z--)for(int x=w-1;x>=0;x--){
            int i=z*w+x;if(x+1<w)relax(i,i+1,10);if(z+1<d){relax(i,i+w,10);if(x>0)relax(i,i+w-1,14);if(x+1<w)relax(i,i+w+1,14);}}
    }
    private void relax(int i,int j,int cost){int v=distance[j]+cost;
        if(v<distance[i]||v==distance[i]&&remaining[j]<remaining[i]){distance[i]=v;remaining[i]=remaining[j];}}
    int cost(int cell) {
        return conservative?Math.min(100,distance[cell]/3):Math.min(140,distance[cell]*7/10);
    }
    int heuristic(int cell,int endpointDistance) {
        return conservative?endpointDistance:Math.max(endpointDistance,remaining[cell]+distance[cell]/2);
    }
    int straightCost(int previousRun,boolean sameHeading) {
        return !sameHeading?0:Math.min(84,Math.max(0,previousRun-preferredRun+1)*7);
    }

    /** Four low-frequency proposals (mirrored S / offset arc); no pointwise random noise.
     * Terrain ranking is soft: a blocked guide can still be routed around by exact A*.
     * Only the selected proposal allocates map-sized distance/progress fields. */
    static List<Shape> candidates(HeightfieldMap map,int ax,int ay,int az,int bx,int by,int bz,
            long seed,String role,int width,int maxCut,int maxFill,Set<Long> blocked) {
        double dx=bx-ax,dz=bz-az,length=StrictMath.hypot(dx,dz);
        boolean local="local".equals(role),redundant="secondary".equals(role)||"ring".equals(role);
        double sign=(SpatialNoise.hash(seed,ax+bx,az+bz,0x6617)&1)==0?1:-1;
        double amplitude=redundant?Math.max(width*4+8,Math.min(36,length*.24)):
                local?Math.min(12,Math.max(2,length*.13)):Math.min(30,Math.max(5,length*.20));
        int intervals=Math.max(3,Math.min(6,(int)StrictMath.ceil(length/28)));
        List<Shape> out=new ArrayList<>();
        for(int variant=0;variant<4;variant++) {
            boolean sweep=variant<2&&!redundant;double side=(variant%2==0?sign:-sign);
            List<int[]> controls=new ArrayList<>();controls.add(new int[]{ax,az});
            for(int i=1;i<intervals;i++){
                double t=i/(double)intervals;
                double offset=amplitude*side*(sweep?StrictMath.sin(2*StrictMath.PI*t):StrictMath.sin(StrictMath.PI*t));
                // Redundant proposals also vary outward amplitude, rather than sharing one arc.
                if(redundant&&variant>=2)offset*=1.55;
                double x=ax+dx*t-dz/Math.max(1,length)*offset,z=az+dz*t+dx/Math.max(1,length)*offset;
                controls.add(clampControl(map,x,z,width));
            }
            controls.add(new int[]{bx,bz});
            Shape p=new Shape((sweep?"sweep":"offset_arc")+"_"+variant,"catmull_rom",controls,sample(controls,false),false);
            p.estimate=estimate(map,p,ay,by,width,maxCut,maxFill,blocked,role);out.add(p);
        }
        out.sort(Comparator.comparingDouble((Shape s)->s.estimate).thenComparing(s->s.id));
        // The fifth attempt is explicitly conservative. Null-guide is owned by the network, last.
        Shape safe=arc(map,ax,az,bx,bz,seed,0,redundant?width*4+8:4);
        safe.estimate=estimate(map,safe,ay,by,width,maxCut,maxFill,blocked,role);out.add(safe);
        return out;
    }
    private static int[] clampControl(HeightfieldMap map,double x,double z,int width) {
        int pad=width/2+2;
        return new int[]{(int)StrictMath.round(Math.max(map.getMinX()+pad,Math.min(map.getMinX()+map.getWidth()-pad-1,x))),
            (int)StrictMath.round(Math.max(map.getMinZ()+pad,Math.min(map.getMinZ()+map.getDepth()-pad-1,z)))};
    }
    private static Shape arc(HeightfieldMap map,int ax,int az,int bx,int bz,long seed,int variant,double minimumOffset) {
        double dx=bx-ax,dz=bz-az,length=StrictMath.hypot(dx,dz);
        double sign=(SpatialNoise.hash(seed,ax+bx,az+bz,0x6617)&1)==0?1:-1;if(variant%2==1)sign=-sign;
        double offset=Math.min(42,Math.max(minimumOffset,length*.22))*sign;
        List<int[]> controls=List.of(new int[]{ax,az},clampControl(map,(ax+bx)*.5-dz/Math.max(1,length)*offset,(az+bz)*.5+dx/Math.max(1,length)*offset,3),new int[]{bx,bz});
        return new Shape("conservative_arc","quadratic",controls,sample(controls,true),true);
    }
    private static List<int[]> sample(List<int[]> controls,boolean quadratic) {
        List<int[]> points=new ArrayList<>();int segments=quadratic?1:controls.size()-1;
        for(int s=0;s<segments;s++){
            int[] a=controls.get(quadratic?0:s),b=controls.get(quadratic?2:s+1);
            int n=Math.max(8,Math.min(1024,(int)StrictMath.ceil(StrictMath.hypot(b[0]-a[0],b[1]-a[1])*2)));
            for(int i=0;i<=n;i++){double t=i/(double)n;int[] p=new int[2];
                for(int axis=0;axis<2;axis++){
                    double v;
                    if(quadratic){double u=1-t;v=u*u*a[axis]+2*u*t*controls.get(1)[axis]+t*t*b[axis];}
                    else {double p0=controls.get(Math.max(0,s-1))[axis],p1=a[axis],p2=b[axis],p3=controls.get(Math.min(controls.size()-1,s+2))[axis];
                        v=.5*((2*p1)+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t*t+(-p0+3*p1-3*p2+p3)*t*t*t);}
                    p[axis]=(int)StrictMath.round(v);
                }
                if(points.isEmpty()||!Arrays.equals(points.getLast(),p))points.add(p);
            }
        }
        return points;
    }
    /** At most 129 sample probes per shape; full-width feasibility is NOT decided here. */
    private static double estimate(HeightfieldMap map,Shape p,int ay,int by,int width,int maxCut,int maxFill,Set<Long> blocked,String role) {
        double obstacles=0,soil=0,grades=0,length=0;int probes=0,previousHeight=ay;
        for(int i=1;i<p.samples.size();i++){int[] a=p.samples.get(i-1),b=p.samples.get(i);length+=StrictMath.hypot(a[0]-b[0],a[1]-b[1]);}
        int stride=Math.max(1,(p.samples.size()+127)/128);
        for(int i=0;i<p.samples.size();i+=stride){int[] c=p.samples.get(i);probes++;
            int lo=-2032,hi=2032,center=previousHeight;boolean fits=true;
            for(int[] d:stencil(0,0,width)){
                p.probeCells++;
                int x=c[0]+d[0],z=c[1]+d[1];if(!buildable(map,x,z)||blocked.contains(key(x,z))){obstacles++;fits=false;continue;}
                int h=map.getSurfaceY(x,z);lo=Math.max(lo,h-maxCut);hi=Math.min(hi,h+maxFill);
                double y=ay+(by-ay)*i/(double)Math.max(1,p.samples.size()-1);soil+=Math.abs(h-y);
            }
            if(map.inBounds(c[0],c[1]))center=map.getSurfaceY(c[0],c[1]);
            grades+=Math.abs(center-previousHeight);previousHeight=center;
            if(fits&&lo>hi)grades+=(lo-hi)*8;
        }
        double weight="local".equals(role)?1.8:1.0;
        return obstacles*24/Math.max(1,probes)+soil*2/Math.max(1,probes)+grades+length*weight;
    }
}
