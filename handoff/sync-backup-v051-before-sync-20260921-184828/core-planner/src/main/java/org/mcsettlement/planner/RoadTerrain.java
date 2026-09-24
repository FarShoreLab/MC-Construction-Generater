package org.mcsettlement.planner;

import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.ir.PlanningIR.GroundColumn;
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
        return buildable(m,x,z)||r.expert!=null&&r.expert.allowBridges&&shoreAbutment(m,x,z)||r.expert!=null&&r.expert.allowBridges&&wet(m,x,z)&&m.getWaterY(x,z)+1-m.getSurfaceY(x,z)<=MAX_PILE_HEIGHT;
    }
    static boolean shoreAbutment(HeightfieldMap m,int x,int z){
        // Only a computed slope classification at a water edge, never an explicit protected/cliff mask.
        if(!m.isDerivedCliff(x,z))return false;
        for(int[] d:CARDINAL)if(wet(m,x+d[0],z+d[1])&&Math.abs(m.getSurfaceY(x,z)-m.getWaterY(x+d[0],z+d[1])-1)<=1)return true;
        return false;
    }
    static int natural(HeightfieldMap m,int x,int z) {return wet(m,x,z)?m.getWaterY(x,z)+1:m.getSurfaceY(x,z);}
    static int lower(HeightfieldMap m,PlanRequest r,int x,int z) {return wet(m,x,z)?natural(m,x,z):m.getSurfaceY(x,z)-r.roadMaxCut;}
    static int upper(HeightfieldMap m,PlanRequest r,int x,int z) {return wet(m,x,z)?natural(m,x,z):m.getSurfaceY(x,z)+r.roadMaxFill;}
    static boolean fits(HeightfieldMap m,PlanRequest r,int x,int z,int y) {return allowed(m,r,x,z)&&y>=lower(m,r,x,z)&&y<=upper(m,r,x,z);}
    static GroundColumn column(HeightfieldMap m,int x,int z,int y,String kind,int clear) {
        GroundColumn c=new GroundColumn(x,z,m.getSurfaceY(x,z),y,Math.max(m.getSurfaceY(x,z),clear),kind);markWet(m,c);return c;
    }
    static void markWet(HeightfieldMap m,GroundColumn c) {
        if(wet(m,c.x,c.z)) {c.waterY=m.getWaterY(c.x,c.z);c.structure="foundation".equals(c.kind)?"deck":"bridge";c.support=Math.floorMod(c.x+c.z,4)==0;}
    }
    /** Re-scan preflight shared with the game constructor. Does not authorize arbitrary fluid edits. */
    public static boolean matchesFreshTerrain(HeightfieldMap fresh,GroundColumn c,boolean expertManifest) {
        if(!fresh.inBounds(c.x,c.z)||fresh.getSurfaceY(c.x,c.z)!=c.originalY)return false;
        if(raised(c))return expertManifest&&wet(fresh,c.x,c.z)&&c.waterY!=null&&c.waterY==fresh.getWaterY(c.x,c.z)&&c.targetY==c.waterY+1&&c.targetY-c.originalY<=MAX_PILE_HEIGHT;
        return buildable(fresh,c.x,c.z)||expertManifest&&shoreAbutment(fresh,c.x,c.z);
    }
    public static boolean raised(GroundColumn c){return "bridge".equals(c.structure)||"deck".equals(c.structure);}
}
