package org.mcsettlement.planner.civil;

import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.preset.PlannedBuilding;
import org.mcsettlement.planner.RoadGeometry;
import java.util.*;

/** One edit program used by both Fabric and the offline voxel executor. No Minecraft dependency. */
public final class PlanConstruction {
    private PlanConstruction() {}
    public record Cell(int x, int y, int z) {}
    public record Edit(int x, int y, int z, String block) {}
    @FunctionalInterface public interface BlockWriter { void set(int x,int y,int z,String block); }

    /** Prepares the WHOLE program before any world mutation; rejects unsupported/legacy plans. */
    public static List<Edit> prepare(PlanningIR ir,String theme) {
        if (!Set.of("COMPLETE","PARTIAL").contains(ir.status))
            throw new IllegalArgumentException("PLAN_NOT_CONSTRUCTIBLE: "+ir.status);
        if (ir.plots.isEmpty()) return List.of();
        if (ir.groundColumns.isEmpty()) throw new IllegalArgumentException("MISSING_CONSTRUCTION_MANIFEST");
        Map<Long,GroundColumn> columns=new HashMap<>();
        Map<Cell,Edit> edits=new LinkedHashMap<>();
        for(GroundColumn c:ir.groundColumns) {
            if(c.clearToY<c.targetY+2 || c.clearToY-c.targetY>64 || Math.abs(c.targetY-c.originalY)>16)
                throw new IllegalArgumentException("INVALID_CONSTRUCTION_COLUMN");
            if(columns.put(RoadGeometry.key(c.x,c.z),c)!=null)throw new IllegalArgumentException("DUPLICATE_COLUMN");
            for(int y=c.originalY+1;y<c.targetY;y++)put(edits,c.x,y,c.z,"minecraft:cobblestone");
            put(edits,c.x,c.targetY,c.z,"minecraft:cobblestone");
            for(int y=c.targetY+1;y<=c.clearToY;y++)put(edits,c.x,y,c.z,"minecraft:air");
        }
        for(Plot p:ir.plots) {
            String[][][] grid=PlannedBuilding.grid(p,theme);
            int ox=p.polygon2D.getFirst()[0],oz=p.polygon2D.getFirst()[1],oy=p.elevation.baseElevation;
            for(int x=0;x<grid.length;x++)for(int y=0;y<grid[x].length;y++)for(int z=0;z<grid[x][y].length;z++) {
                GroundColumn c=columns.get(RoadGeometry.key(ox+x,oz+z));
                if(c==null || !"foundation".equals(c.kind) || c.targetY!=oy || oy+y>c.clearToY)
                    throw new IllegalArgumentException("BUILDING_OUTSIDE_FOUNDATION_MANIFEST: "+p.id);
                String block=grid[x][y][z];
                // Empty template floor cells retain the structural slab.
                if(y==0 && "minecraft:air".equals(block)) continue;
                put(edits,ox+x,oy+y,oz+z,block);
            }
            if(p.entrance.width != 1 || p.entrance.path.isEmpty() || p.entrance.connectedEdgeId==null)
                throw new IllegalArgumentException("MISSING_ENTRANCE_CONNECTION: "+p.id);
            for(RoadStep s:p.entrance.path) {
                GroundColumn c=columns.get(RoadGeometry.key(s.x,s.z));
                if(c==null || c.targetY!=s.y)throw new IllegalArgumentException("UNBUILT_ENTRANCE: "+p.id);
            }
        }
        return List.copyOf(edits.values());
    }
    private static void put(Map<Cell,Edit> out,int x,int y,int z,String block) {
        out.put(new Cell(x,y,z),new Edit(x,y,z,block==null?"minecraft:air":block));
        if(out.size()>1000000)throw new IllegalArgumentException("CONSTRUCTION_EXCEEDS_ONE_MILLION_EDITS");
    }
    public static void apply(List<Edit> edits,BlockWriter writer) {
        for(Edit e:edits)writer.set(e.x,e.y,e.z,e.block);
    }
}
