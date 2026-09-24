package org.mcsettlement.planner.simulation;

import org.mcsettlement.planner.SettlementPlanner;
import org.mcsettlement.planner.SettlementPlanner.PlanRequest;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.Plot;
import org.mcsettlement.planner.ir.PlanningIR.RetainingWallSpec;
import org.mcsettlement.planner.ir.PlanningIR.RoadEdge;
import org.mcsettlement.planner.ir.PlanningIR.RoadStep;
import org.mcsettlement.planner.simulation.SimulatedVoxelWorld.VoxelType;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.terrain.HeightfieldMap.ObstacleType;

/**
 * End-to-end simulation runner connecting the lightweight terrain generator
 * directly to the settlement planning and construction engine.
 */
public class SimulatedSettlementPipeline {

    public static class PipelineResult {
        public SimulatedVoxelWorld worldBefore;
        public SimulatedVoxelWorld worldAfter;
        public HeightfieldMap heightfield;
        public PlanningIR plan;
        public int clearedBlocks = 0;
        public int cutBlocks = 0;
        public int fillBlocks = 0;
        public int roadBlocks = 0;
        public int stairBlocks = 0;
        public int buildingBlocks = 0;
        public int foundationRootBlocks = 0;
        public int retainingWallBlocks = 0;
    }

    public static PipelineResult run(int width, int depth, int baseElevation, int relief, long seed, int targetPlots) {
        PipelineResult res = new PipelineResult();

        // 1. Generate natural terrain block world
        SimulatedVoxelWorld naturalWorld = TerrainBlockGenerator.generateHillsideWorld(width, depth, baseElevation, relief, seed);
        res.worldBefore = copyWorld(naturalWorld);
        res.worldAfter = naturalWorld;

        // 2. Scan the finite simulation material set into HeightfieldMap (not a Fabric scanner test)
        HeightfieldMap map = new HeightfieldMap(0, 0, width, depth);
        int topY = naturalWorld.getMinY() + naturalWorld.getSizeY() - 1;
        int bottomY = naturalWorld.getMinY();

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                int surfaceY = bottomY;
                int waterY = -1;
                ObstacleType obs = ObstacleType.NONE;

                for (int y = topY; y >= bottomY; y--) {
                    VoxelType b = naturalWorld.getBlock(x, y, z);
                    if (b == VoxelType.AIR) continue;

                    if (b == VoxelType.WATER) {
                        if (waterY == -1) {
                            waterY = y;
                            obs = ObstacleType.WATER;
                        }
                        continue;
                    }

                    if (b == VoxelType.OAK_LEAVES) {
                        if (obs == ObstacleType.NONE) obs = ObstacleType.VEGETATION;
                        continue;
                    }

                    if (b == VoxelType.OAK_LOG) {
                        if (obs == ObstacleType.NONE) obs = ObstacleType.TREE_TRUNK;
                        continue;
                    }

                    if (b.isSolid) {
                        surfaceY = y;
                        break;
                    }
                }

                map.setLocalSurfaceY(x, z, surfaceY);
                if (waterY != -1) {
                    map.setLocalWaterY(x, z, waterY);
                    map.setLocalObstacle(x, z, (waterY - surfaceY > 3) ? ObstacleType.WATER_DEEP : ObstacleType.WATER);
                } else if (obs != ObstacleType.NONE) {
                    map.setLocalObstacle(x, z, obs);
                }
            }
        }
        map.computeSlopes();
        res.heightfield = map;

        // 3. Run the bounded planner
        PlanRequest req = new PlanRequest();
        req.seed = seed;
        req.targetPlots = targetPlots;
        req.roadWidth = 3;
        PlanningIR ir = SettlementPlanner.plan(map, req);
        res.plan = ir;

        // 4. Execute the shared construction edit program on the simulated voxel world
        executeConstruction(res.worldAfter, ir, res);

        return res;
    }

    private static void executeConstruction(SimulatedVoxelWorld world, PlanningIR ir, PipelineResult res) {
        if (!"COMPLETE".equals(ir.status) && !"PARTIAL".equals(ir.status)) return;
        var edits = org.mcsettlement.planner.civil.PlanConstruction.prepare(ir, "medieval_rustic");
        for (var edit : edits) {
            if (!world.inBounds(edit.x(), edit.y(), edit.z()))
                throw new IllegalStateException("Simulation bounds do not contain construction");
        }
        org.mcsettlement.planner.civil.PlanConstruction.apply(edits, (x,y,z,id) -> {
            VoxelType value = simulationMaterial(id);
            if (value == VoxelType.AIR && world.getBlock(x,y,z) != VoxelType.AIR) res.clearedBlocks++;
            world.setBlock(x,y,z,value);
        });
        res.cutBlocks = ir.earthworks.totalCutVolume;
        res.fillBlocks = ir.earthworks.totalFillVolume;
        res.roadBlocks = (int) ir.groundColumns.stream().filter(c -> "road".equals(c.kind)).count();
        res.buildingBlocks = (int) edits.stream().filter(e -> !"minecraft:air".equals(e.block())).count();
    }

    // Visual approximation only; collision verification uses raw block IDs in regression tests.
    private static VoxelType simulationMaterial(String id) {
        if ("minecraft:air".equals(id)) return VoxelType.AIR;
        if (id.contains("glass")) return VoxelType.GLASS_PANE;
        if (id.contains("log")) return VoxelType.SPRUCE_LOG;
        if (id.contains("planks")) return VoxelType.SPRUCE_PLANKS;
        if (id.contains("stairs")) return VoxelType.COBBLESTONE_STAIRS;
        if (id.contains("fence")) return VoxelType.OAK_FENCE;
        return VoxelType.COBBLESTONE;
    }

    private static SimulatedVoxelWorld copyWorld(SimulatedVoxelWorld src) {
        SimulatedVoxelWorld dst = new SimulatedVoxelWorld(
                src.getMinX(), src.getMinY(), src.getMinZ(),
                src.getSizeX(), src.getSizeY(), src.getSizeZ()
        );
        for (int x = 0; x < src.getSizeX(); x++) {
            for (int y = 0; y < src.getSizeY(); y++) {
                for (int z = 0; z < src.getSizeZ(); z++) {
                    dst.setLocalBlock(x, y, z, src.getLocalBlock(x, y, z));
                }
            }
        }
        return dst;
    }
}
