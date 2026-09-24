package org.mcsettlement.mod.constructor;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import org.mcsettlement.planner.RoadTerrain;
import net.minecraft.block.Block;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mcsettlement.mod.building.AdaptiveBuildingPlacer;
import org.mcsettlement.mod.scanner.WorldTerrainScanner;
import org.mcsettlement.planner.RoadGeometry;
import org.mcsettlement.planner.civil.PlanConstruction;
import org.mcsettlement.planner.ir.PlanningIR;
import java.util.HashMap;
import java.util.Map;

/** Executes exactly the validated column/preset program; never invents stairs, piers or deep roots. */
public class WorldConstructor {
    public static class ConstructionResult {
        public int clearedBlocks, cutBlocks, fillBlocks, roadBlocks, stairBlocks, buildingsConstructed;
    }
    public static ConstructionResult execute(World world,PlanningIR ir) {
        return execute(world,ir,"medieval_rustic","");
    }
    public static ConstructionResult execute(World world,PlanningIR ir,String theme,String userPrompt) {
        if (!(world instanceof ServerWorld) || !world.getServer().isOnThread())
            throw new IllegalStateException("Construction requires the authoritative server thread");
        var edits=PlanConstruction.prepare(ir,theme);
        ConstructionResult result=new ConstructionResult();
        if (edits.isEmpty()) return result;
        int[] min=ir.metadata.minBounds,max=ir.metadata.maxBounds;
        var fresh=WorldTerrainScanner.scanRegion(world,min[0],min[2],max[0],max[2]);
        BlockPos.Mutable pos=new BlockPos.Mutable();
        // Full preflight before any mutation: changed terrain, fluids, structures and unsupported voids fail closed.
        Map<Long,PlanningIR.GroundColumn> columns=new HashMap<>();
        boolean expertManifest=PlanningIR.EXPERT_SCHEMA_VERSION.equals(ir.metadata.version)&&ir.sitePlanning!=null;
        for(var c:ir.groundColumns) {
            columns.put(RoadGeometry.key(c.x,c.z),c);
            if(!RoadTerrain.matchesFreshTerrain(fresh,c,expertManifest))
                throw new IllegalStateException("Terrain changed or protected at "+c.x+","+c.z+"; replan first");
            if(c.targetY<world.getBottomY() || c.clearToY>=world.getTopY())
                throw new IllegalStateException("Construction exceeds world height bounds");
            pos.set(c.x,Math.min(c.originalY,c.targetY),c.z);
            if(!WorldTerrainScanner.isNaturalGround(world.getBlockState(pos)))
                throw new IllegalStateException("Foundation would rest on a void or unsupported block");
        }
        Map<String,BlockState> states=new HashMap<>();
        for(var e:edits) {
            pos.set(e.x(),e.y(),e.z());
            var column=columns.get(RoadGeometry.key(e.x(),e.z()));
            // Water replacement is authorized ONLY for the manifest's real pile voxel, not a fill brush.
            boolean pileInWater=expertManifest&&column!=null&&RoadTerrain.raised(column)&&column.support&&
                    e.y()>column.originalY&&e.y()<column.targetY&&"minecraft:oak_log".equals(e.block())&&world.getBlockState(pos).isOf(Blocks.WATER);
            if(!pileInWater&&!WorldTerrainScanner.isReplaceableForPlanning(world.getBlockState(pos)))
                throw new IllegalStateException("Protected block in construction clearance at "+pos);
            BlockState state=states.computeIfAbsent(e.block(),AdaptiveBuildingPlacer::parseBlockState);
            if(state.isAir() && !"minecraft:air".equals(e.block()))
                throw new IllegalStateException("Unavailable preset block: "+e.block());
        }
        java.util.Set<PlanConstruction.Cell> plannedStairs=new java.util.HashSet<>();
        for(var c:ir.groundColumns)if("stair".equals(c.structure))plannedStairs.add(new PlanConstruction.Cell(c.x,c.targetY,c.z));
        for(var e:edits) {
            pos.set(e.x(),e.y(),e.z());
            if("minecraft:air".equals(e.block()) && !world.getBlockState(pos).isAir())result.clearedBlocks++;
            // Ground-manifest stairs are emitted last; keep the validated straight tread.
            int flags=plannedStairs.contains(new PlanConstruction.Cell(e.x(),e.y(),e.z())) ? Block.NOTIFY_LISTENERS|Block.FORCE_STATE : 3;
            world.setBlockState(pos,states.get(e.block()),flags);
        }
        // Cut/fill count uses unique actual construction columns, not repeated brush stamps.
        result.cutBlocks=ir.earthworks.totalCutVolume;
        result.fillBlocks=ir.earthworks.totalFillVolume;
        result.roadBlocks=(int)ir.groundColumns.stream().filter(c->"road".equals(c.kind)).count();
        result.stairBlocks=plannedStairs.size();
        result.buildingsConstructed=ir.plots.size();
        return result;
    }
}
