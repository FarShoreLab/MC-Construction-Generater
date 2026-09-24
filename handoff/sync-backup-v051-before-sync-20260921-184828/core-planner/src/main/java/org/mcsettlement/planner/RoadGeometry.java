package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.terrain.HeightfieldMap;

/** Integer-grid convention, conservative swept footprints, and actual stair-face connectivity. */
public final class RoadGeometry {
    private RoadGeometry() {}
    public static final int[][] CARDINAL={{1,0},{0,1},{-1,0},{0,-1}};
    private static final int[][] EIGHT={{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1}};
    // Twelve symmetric lattice headings, approximately 0/26.6/63.4/90 degrees per quadrant.
    // NOT an asymmetric 8+4 set, and NOT a claim of exact 30-degree unit-grid moves.
    private static final int[][] TWELVE={{1,0},{2,1},{1,2},{0,1},{-1,2},{-2,1},{-1,0},{-2,-1},{-1,-2},{0,-1},{1,-2},{2,-1}};
    private static final Map<String,List<int[]>> STENCILS=new HashMap<>();
    static { for(int w=1;w<=5;w++){stencil(0,0,w);for(int[] d:EIGHT)stencil(d[0],d[1],w);for(int[] d:TWELVE)stencil(d[0],d[1],w);} }
    public static int[][] directions(int count){return count==12?TWELVE:EIGHT;}
    public static int low(int width) { return -(width / 2); }
    public static int high(int width) { return low(width) + width - 1; }
    public static long key(int x, int z) { return ((long)x << 32) | (z & 0xffffffffL); }
    public static int x(long key) { return (int)(key >> 32); }
    public static int z(long key) { return (int)key; }
    public static boolean buildable(HeightfieldMap map, int x, int z) {
        if (!map.inBounds(x,z)) return false;
        return switch (map.getObstacle(x,z)) {case NONE, VEGETATION, TREE_TRUNK -> true; default -> false;};
    }
    public static boolean gradeFits(HeightfieldMap map, int x, int z, int y, int cut, int fill) {
        if (!buildable(map,x,z)) return false;
        int d = map.getSurfaceY(x,z) - y;
        return d <= cut && -d <= fill;
    }
    /** Every cell touched by the closed center segment, dilated by the exact even/odd square brush.
     *  Corner-only contacts are INCLUDED. Long diagonal moves cannot tunnel across skipped cells.
     */
    public static synchronized List<int[]> stencil(int dx,int dz,int width) {
        if(width<1||width>5||Math.abs(dx)>2||Math.abs(dz)>2)throw new IllegalArgumentException("INVALID_ROAD_STENCIL");
        String id=dx+","+dz+","+width;
        List<int[]> cached=STENCILS.get(id);if(cached!=null)return cached;
        Set<Long> cells=new TreeSet<>();
        for(int x=Math.min(0,dx);x<=Math.max(0,dx);x++)for(int z=Math.min(0,dz);z<=Math.max(0,dz);z++) {
            if(!segmentHitsCell(dx,dz,x,z))continue;
            for(int ox=low(width);ox<=high(width);ox++)for(int oz=low(width);oz<=high(width);oz++)cells.add(key(x+ox,z+oz));
        }
        List<int[]> out=new ArrayList<>();for(long cell:cells)out.add(new int[]{x(cell),z(cell)});
        out=Collections.unmodifiableList(out);STENCILS.put(id,out);return out;
    }
    private static boolean segmentHitsCell(int dx,int dz,int x,int z) {
        double lo=0,hi=1;
        int[] ds={dx,dz},cs={x,z};
        for(int axis=0;axis<2;axis++){
            if(ds[axis]==0){if(cs[axis]!=0)return false;continue;}
            double a=(cs[axis]-0.5)/ds[axis],b=(cs[axis]+0.5)/ds[axis];
            lo=Math.max(lo,Math.min(a,b));hi=Math.min(hi,Math.max(a,b));
        }
        return lo<=hi+1e-12;
    }
    public static Set<Long> footprint(List<RoadStep> path,int width) {
        Set<Long> result=new TreeSet<>();
        for(int i=0;i<path.size();i++){
            RoadStep a=path.get(i),b=i+1<path.size()?path.get(i+1):a;
            for(int[] p:stencil(b.x-a.x,b.z-a.z,width))result.add(key(a.x+p[0],a.z+p[1]));
        }
        return result;
    }
    public static String facing(int dx,int dz){
        if(dx==1&&dz==0)return "EAST";if(dx==-1&&dz==0)return "WEST";
        if(dx==0&&dz==1)return "SOUTH";if(dx==0&&dz==-1)return "NORTH";
        throw new IllegalArgumentException("NON_CARDINAL_STAIR");
    }
    public static boolean stair(GroundColumn c){return "stair".equals(c.structure);}
    /** A conservative no-jump passage: steps are half-block rises, not a one-block auto-jump.
     * Equal-height blocks can share a face. A rise requires the higher stair's low face toward
     * the lower cell, whose exit must be a full-height face (or a correctly aligned stair).
     */
    public static boolean canWalk(GroundColumn a,GroundColumn b) {
        if(a==null||b==null||Math.abs(a.x-b.x)+Math.abs(a.z-b.z)!=1)return false;
        if(a.targetY==b.targetY)return true;
        if(Math.abs(a.targetY-b.targetY)!=1)return false;
        GroundColumn low=a.targetY<b.targetY?a:b,high=low==a?b:a;
        String direction=facing(high.x-low.x,high.z-low.z);
        return stair(high)&&direction.equals(high.facing)&&(!stair(low)||direction.equals(low.facing));
    }
    public static GroundColumn copy(GroundColumn c){
        GroundColumn r=new GroundColumn(c.x,c.z,c.originalY,c.targetY,c.clearToY,c.kind);r.structure=c.structure;r.facing=c.facing;r.waterY=c.waterY;r.support=c.support;return r;
    }
    public static RoadStep step(GroundColumn c){return new RoadStep(c.x,c.targetY,c.z,c.structure);}
    public static int[] distanceField(HeightfieldMap map,Collection<Long> goals) {
        int w=map.getWidth(),d=map.getDepth();int[] result=new int[w*d];Arrays.fill(result,10000000);
        for(long k:goals)if(map.inBounds(x(k),z(k)))result[(z(k)-map.getMinZ())*w+x(k)-map.getMinX()]=0;
        for(int z=0;z<d;z++)for(int x=0;x<w;x++){
            int i=z*w+x;
            if(x>0)result[i]=Math.min(result[i],result[i-1]+10);
            if(z>0){result[i]=Math.min(result[i],result[i-w]+10);if(x>0)result[i]=Math.min(result[i],result[i-w-1]+14);if(x+1<w)result[i]=Math.min(result[i],result[i-w+1]+14);}
        }
        for(int z=d-1;z>=0;z--)for(int x=w-1;x>=0;x--){
            int i=z*w+x;
            if(x+1<w)result[i]=Math.min(result[i],result[i+1]+10);
            if(z+1<d){result[i]=Math.min(result[i],result[i+w]+10);if(x>0)result[i]=Math.min(result[i],result[i+w-1]+14);if(x+1<w)result[i]=Math.min(result[i],result[i+w+1]+14);}
        }
        return result;
    }
    public static void exhausted(SearchStats stats,String name){if(!stats.exhaustedBudgets.contains(name))stats.exhaustedBudgets.add(name);stats.budgetExhausted=true;}
}
