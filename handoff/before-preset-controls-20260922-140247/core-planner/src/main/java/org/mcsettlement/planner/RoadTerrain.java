package org.mcsettlement.planner;

import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.ir.PlanningIR.GroundColumn;
import org.mcsettlement.planner.ir.PlanningIR.RoadStep;
import org.mcsettlement.planner.SettlementPlanner.PlanRequest;
import static org.mcsettlement.planner.RoadGeometry.*;

/** Actual full-width wet-deck constraints shared by placement, routing and the pavement solver. */
public final class RoadTerrain {
    private RoadTerrain() {}
    public static final int MAX_PILE_HEIGHT=16;
    public static boolean wet(HeightfieldMap m,int x,int z) {
        if(!m.inBounds(x,z))return false;
        var o=m.getObstacle(x,z);return (o==HeightfieldMap.ObstacleType.WATER||o==HeightfieldMap.ObstacleType.WATER_DEEP)&&m.getWaterY(x,z)>m.getSurfaceY(x,z);
    }
    static boolean allowed(HeightfieldMap m,PlanRequest r,int x,int z) {
        return buildable(m,x,z)||r.expert!=null&&r.expert.allowBridges&&m.isDerivedCliff(x,z)||r.expert!=null&&r.expert.allowBridges&&wet(m,x,z)&&m.getWaterY(x,z)+1-m.getSurfaceY(x,z)<=MAX_PILE_HEIGHT;
    }
    static boolean shoreAbutment(HeightfieldMap m,int x,int z){
        // Only a computed slope classification at a water edge, never an explicit protected/cliff mask.
        if(!m.isDerivedCliff(x,z))return false;
        for(int[] d:CARDINAL)if(wet(m,x+d[0],z+d[1])&&Math.abs(m.getSurfaceY(x,z)-m.getWaterY(x+d[0],z+d[1])-1)<=1)return true;
        return false;
    }
    static int natural(HeightfieldMap m,int x,int z) {return wet(m,x,z)?m.getWaterY(x,z)+1:m.getSurfaceY(x,z);}
    static int lower(HeightfieldMap m,PlanRequest r,int x,int z) {return wet(m,x,z)?natural(m,x,z):m.getSurfaceY(x,z)-r.roadMaxCut;}
    /**
     * Dry decks are exceptional. A bridge-capable plan no longer grants MAX_PILE_HEIGHT on every
     * ordinary land cell: only derived cliff cells or a local two-sided depression can exceed
     * the ordinary fill budget. This removes the old global viaduct escape hatch.
     */
    static boolean landBridgeEligible(HeightfieldMap m,PlanRequest r,int x,int z) {
        if(r.expert==null||!r.expert.allowBridges||wet(m,x,z)||!m.inBounds(x,z))return false;
        if(m.isDerivedCliff(x,z))return true;
        int h=m.getSurfaceY(x,z),need=Math.max(2,r.roadMaxFill+1);
        int radius=Math.max(2,r.expert.maxLandBridgeSpan);
        return rims(m,x,z,1,0,h+need,radius)||rims(m,x,z,0,1,h+need,radius);
    }
    private static boolean rims(HeightfieldMap m,int x,int z,int dx,int dz,int required,int radius) {
        int a=Integer.MIN_VALUE,b=Integer.MIN_VALUE;
        for(int distance=1;distance<=radius;distance++){
            int ax=x+dx*distance,az=z+dz*distance,bx=x-dx*distance,bz=z-dz*distance;
            if(m.inBounds(ax,az)&&!wet(m,ax,az))a=Math.max(a,m.getSurfaceY(ax,az));
            if(m.inBounds(bx,bz)&&!wet(m,bx,bz))b=Math.max(b,m.getSurfaceY(bx,bz));
        }
        return a>=required&&b>=required;
    }
    static int upper(HeightfieldMap m,PlanRequest r,int x,int z) {
        if(wet(m,x,z))return natural(m,x,z);
        return m.getSurfaceY(x,z)+(landBridgeEligible(m,r,x,z)?MAX_PILE_HEIGHT:r.roadMaxFill);
    }
    static boolean airborneLand(HeightfieldMap m,PlanRequest r,int x,int z,int y){
        return !wet(m,x,z)&&y>m.getSurfaceY(x,z)+r.roadMaxFill;
    }
    static void markLand(PlanRequest r,GroundColumn c) {
        if(r.expert!=null&&r.expert.allowBridges&&"road".equals(c.kind)&&c.waterY==null&&c.targetY-c.originalY>r.roadMaxFill){c.structure="bridge";c.support=Math.floorMod(c.x+c.z,4)==0;}
    }
    public static boolean landBridge(GroundColumn c){return "bridge".equals(c.structure)&&c.waterY==null;}
    /** Full-width unsupported reach bound. A viaduct can contain several supported spans. */
    public static boolean validLandSpans(java.util.Map<Long,GroundColumn> cols,int limit){
        java.util.Set<Long> seen=new java.util.HashSet<>();
        for(var c:cols.values())if(landBridge(c)&&seen.add(key(c.x,c.z))){
            java.util.ArrayDeque<GroundColumn> queue=new java.util.ArrayDeque<>();queue.add(c);
            java.util.Map<Long,Integer> reach=new java.util.HashMap<>();java.util.ArrayDeque<GroundColumn> supports=new java.util.ArrayDeque<>();
            int count=0;boolean abutment=false;
            while(!queue.isEmpty()){var a=queue.remove();count++;boolean supported=a.support;
                for(int[] d:CARDINAL){var b=cols.get(key(a.x+d[0],a.z+d[1]));if(b==null)continue;
                    if(landBridge(b)){if(a.targetY!=b.targetY)return false;if(seen.add(key(b.x,b.z)))queue.add(b);}
                    else if(!raised(b)&&canWalk(a,b)){abutment=true;supported=true;}
                }
                if(supported){reach.put(key(a.x,a.z),0);supports.add(a);}
            }
            if(!abutment)return false;
            while(!supports.isEmpty()){var a=supports.remove();int distance=reach.get(key(a.x,a.z))+1;if(distance*2>limit)continue;
                for(int[] d:CARDINAL){var b=cols.get(key(a.x+d[0],a.z+d[1]));if(b!=null&&landBridge(b)&&reach.putIfAbsent(key(b.x,b.z),distance)==null)supports.add(b);}
            }
            if(reach.size()!=count)return false;
        }
        return true;
    }
    /** Longest consecutive route distance whose swept pavement contains a dry bridge column. */
    public static int maxLandAirborneRun(java.util.Map<Long,GroundColumn> cols,java.util.List<RoadStep> route,int width){
        int run=0,max=0,lastDx=0,lastDz=0;
        for(int i=1;i<route.size();i++){RoadStep a=route.get(i-1),b=route.get(i);int dx=b.x-a.x,dz=b.z-a.z;boolean bridge=false;
            for(int[] v:stencil(dx,dz,width)){GroundColumn c=cols.get(key(a.x+v[0],a.z+v[1]));if(c!=null&&landBridge(c)){bridge=true;break;}}
            if(bridge){if(run>0&&(dx!=lastDx||dz!=lastDz))return Integer.MAX_VALUE;run+=Math.max(Math.abs(dx),Math.abs(dz));max=Math.max(max,run);lastDx=dx;lastDz=dz;}
            else{run=0;lastDx=lastDz=0;}
        }
        return max;
    }
    public static boolean validLandDeckRun(java.util.Map<Long,GroundColumn> cols,java.util.List<RoadStep> route,int width,int limit){return maxLandAirborneRun(cols,route,width)<=limit;}

    // Building accesses remain earthwork/stairs; only full-width roads can create dry decks.
    static boolean fits(HeightfieldMap m,PlanRequest r,int x,int z,int y) {return allowed(m,r,x,z)&&y>=lower(m,r,x,z)&&y<=(wet(m,x,z)?natural(m,x,z):m.getSurfaceY(x,z)+r.roadMaxFill);}
    static GroundColumn column(HeightfieldMap m,int x,int z,int y,String kind,int clear) {
        GroundColumn c=new GroundColumn(x,z,m.getSurfaceY(x,z),y,Math.max(m.getSurfaceY(x,z),clear),kind);markWet(m,c);return c;
    }
    static void markWet(HeightfieldMap m,GroundColumn c) {
        if(wet(m,c.x,c.z)) {c.waterY=m.getWaterY(c.x,c.z);c.structure="foundation".equals(c.kind)?"deck":"bridge";c.support=Math.floorMod(c.x+c.z,4)==0;}
    }
    /** Re-scan preflight shared with the game constructor. Does not authorize arbitrary fluid edits. */
    public static boolean matchesFreshTerrain(HeightfieldMap fresh,GroundColumn c,boolean expertManifest) {
        if(!fresh.inBounds(c.x,c.z)||fresh.getSurfaceY(c.x,c.z)!=c.originalY)return false;
        if(landBridge(c))return expertManifest&&(buildable(fresh,c.x,c.z)||fresh.isDerivedCliff(c.x,c.z))&&c.targetY>c.originalY&&c.targetY-c.originalY<=MAX_PILE_HEIGHT;
        if(raised(c))return expertManifest&&wet(fresh,c.x,c.z)&&c.waterY!=null&&c.waterY==fresh.getWaterY(c.x,c.z)&&c.targetY==c.waterY+1&&c.targetY-c.originalY<=MAX_PILE_HEIGHT;
        return buildable(fresh,c.x,c.z)||expertManifest&&fresh.isDerivedCliff(c.x,c.z);
    }
    public static boolean raised(GroundColumn c){return "bridge".equals(c.structure)||"deck".equals(c.structure);}
}
