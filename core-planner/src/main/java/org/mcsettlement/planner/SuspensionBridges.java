package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.civil.PlanConstruction;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.GroundColumn;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import static org.mcsettlement.planner.RoadGeometry.*;

/** Bank towers and overhead cables are real, budgeted construction columns. */
public final class SuspensionBridges {
    private SuspensionBridges() {}
    public static void validate(Map<Long,GroundColumn> columns){
        Set<Long> seen=new HashSet<>(),usedAnchors=new HashSet<>();
        for(var first:columns.values()){
            if(!first.suspensionBridge||!seen.add(key(first.x,first.z)))continue;
            Set<Long> anchors=new HashSet<>();ArrayDeque<GroundColumn> queue=new ArrayDeque<>();queue.add(first);
            while(!queue.isEmpty()){
                var c=queue.remove();
                for(int[] d:CARDINAL){long k=key(c.x+d[0],c.z+d[1]);var next=columns.get(k);if(next==null)continue;
                    if("bridge_anchor".equals(next.kind))anchors.add(k);
                    else if((next.suspensionBridge||"bridge_rigging".equals(next.kind))&&seen.add(k))queue.add(next);
                }
            }
            if(anchors.size()<4)throw new IllegalArgumentException("MISSING_SUSPENSION_ANCHORS");
            usedAnchors.addAll(anchors);
        }
        for(var c:columns.values())if("bridge_anchor".equals(c.kind)&&!usedAnchors.contains(key(c.x,c.z))||"bridge_rigging".equals(c.kind)&&!seen.contains(key(c.x,c.z)))throw new IllegalArgumentException("ORPHAN_SUSPENSION_RIGGING");
    }
    static void add(HeightfieldMap map,SettlementPlanner.PlanRequest req,PlanningIR ir){
        if(!req.expert.suspensionBridges)return;
        Map<Long,GroundColumn> columns=new TreeMap<>();for(var c:ir.groundColumns)columns.put(key(c.x,c.z),c);
        Set<Long> seen=new HashSet<>();List<GroundColumn> towers=new ArrayList<>();
        for(var first:ir.groundColumns){
            if(!first.suspensionBridge||!seen.add(key(first.x,first.z)))continue;
            List<GroundColumn> span=new ArrayList<>();ArrayDeque<GroundColumn> queue=new ArrayDeque<>();queue.add(first);
            while(!queue.isEmpty()){
                var c=queue.remove();span.add(c);
                for(int[] d:CARDINAL){var next=columns.get(key(c.x+d[0],c.z+d[1]));if(next!=null&&next.suspensionBridge&&seen.add(key(next.x,next.z)))queue.add(next);}
            }
            int minX=span.stream().mapToInt(c->c.x).min().orElseThrow(),maxX=span.stream().mapToInt(c->c.x).max().orElseThrow();
            int minZ=span.stream().mapToInt(c->c.z).min().orElseThrow(),maxZ=span.stream().mapToInt(c->c.z).max().orElseThrow();
            boolean alongX=maxX-minX>=maxZ-minZ;
            int lo=alongX?minX:minZ,hi=alongX?maxX:maxZ,sideLo=alongX?minZ:minX,sideHi=alongX?maxZ:maxX;
            int deck=first.targetY;
            for(int side:new int[]{sideLo-1,sideHi+1}){
              int[] ends=new int[2];int endIndex=0;
              for(int end:new int[]{lo,hi}){
                GroundColumn tower=null;
                for(int distance=1;distance<=6;distance++){
                    int axis=end+(end==lo?-distance:distance),x=alongX?axis:side,z=alongX?side:axis;
                    if(!map.inBounds(x,z)||columns.containsKey(key(x,z))||!RoadTerrain.allowed(map,req,x,z)||RoadTerrain.wet(map,x,z)||map.getObstacle(x,z)!=HeightfieldMap.ObstacleType.NONE&&!map.isDerivedCliff(x,z))continue;
                    int y=map.getSurfaceY(x,z);if(Math.abs(y-deck)>2)continue;
                    tower=new GroundColumn(x,z,y,y,deck+7,"bridge_anchor");
                    for(int level=y+1;level<=deck+7;level++)tower.aboveBlocks.add("minecraft:oak_log");
                    columns.put(key(x,z),tower);towers.add(tower);ends[endIndex++]=axis;break;
                }
                if(tower==null)throw new IllegalArgumentException("SUSPENSION_BANK_ANCHOR_UNAVAILABLE deck="+deck+" span="+minX+","+minZ+":"+maxX+","+maxZ);
              }
              for(int axis=ends[0]+1;axis<ends[1];axis++){
                int x=alongX?axis:side,z=alongX?side:axis;
                if(!map.inBounds(x,z)||columns.containsKey(key(x,z))||!RoadTerrain.allowed(map,req,x,z)||RoadTerrain.wet(map,x,z)||map.getSurfaceY(x,z)>deck+2)throw new IllegalArgumentException("SUSPENSION_CABLE_SPACE_UNAVAILABLE");
                int ground=map.getSurfaceY(x,z);
                var c=new GroundColumn(x,z,ground,Math.max(ground,deck),deck+7,"bridge_rigging");if(ground<deck){c.structure="bridge";c.suspensionBridge=true;}
                double t=(axis-ends[0]-1)/(double)Math.max(1,ends[1]-ends[0]-2);
                int cable=3+(int)Math.round(4*Math.pow(2*t-1,2));
                for(int y=c.targetY+1;y<=deck+cable;y++)c.aboveBlocks.add(y==deck+1?"minecraft:oak_fence":y==deck+cable?"minecraft:chain[axis="+(alongX?"x":"z")+",waterlogged=false]":Math.floorMod(axis,3)==0?"minecraft:chain[axis=y,waterlogged=false]":"minecraft:air");
                columns.put(key(x,z),c);towers.add(c);
              }
            }
        }
        for(var c:towers){ir.groundColumns.add(c);ir.search.constructionEdits+=PlanConstruction.columnEditCount(c);}
        if(ir.groundColumns.size()>req.searchBudget.groundColumns||ir.search.constructionEdits>req.searchBudget.constructionEdits)throw new IllegalArgumentException("SUSPENSION_CONSTRUCTION_BUDGET");
    }
}
