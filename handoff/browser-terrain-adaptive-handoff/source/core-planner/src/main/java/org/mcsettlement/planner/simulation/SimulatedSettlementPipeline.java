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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;

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
        public int width;
        public int depth;
        public int baseElevation;
        public int relief;
        public int targetPlots;
        public String terrainType;
        public long terrainSeed;
        public long planSeed;
        public String originalTerrainHash;
        public String planHash;
    }

    public static PipelineResult run(int width, int depth, int baseElevation, int relief, long seed, int targetPlots) {
        return run(width, depth, baseElevation, relief, "rolling_hills", seed, seed, targetPlots);
    }

    /**
     * Run a fresh planning rehearsal. Terrain generation is keyed only by terrainSeed;
     * planSeed is passed to the bounded planner and can therefore be rolled repeatedly
     * against an unchanged natural world.
     */
    public static PipelineResult run(
            int width, int depth, int baseElevation, int relief, String terrainType,
            long terrainSeed, long planSeed, int targetPlots) {
        PipelineResult res = new PipelineResult();
        res.width = width;
        res.depth = depth;
        res.baseElevation = baseElevation;
        res.relief = relief;
        res.targetPlots = targetPlots;
        res.terrainType = TerrainBlockGenerator.TerrainType.from(terrainType).id;
        res.terrainSeed = terrainSeed;
        res.planSeed = planSeed;

        // 1. Generate natural terrain block world
        SimulatedVoxelWorld naturalWorld = TerrainBlockGenerator.generateWorld(
                width, depth, baseElevation, relief, res.terrainType, terrainSeed);
        res.worldBefore = naturalWorld;
        res.worldAfter = copyWorld(naturalWorld);
        res.originalTerrainHash = hashWorld(naturalWorld);

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
        req.seed = planSeed;
        req.targetPlots = targetPlots;
        req.roadWidth = 3;
        PlanningIR ir = SettlementPlanner.plan(map, req);
        res.plan = ir;
        res.planHash = hashPlan(ir);

        // 4. Execute the shared construction edit program on the simulated voxel world
        executeConstruction(res.worldAfter, ir, res);

        return res;
    }

    /** Hashes every in-bounds voxel, including trees and water, for replay checks. */
    public static String hashWorld(SimulatedVoxelWorld world) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateInt(digest, world.getMinX());
            updateInt(digest, world.getMinY());
            updateInt(digest, world.getMinZ());
            updateInt(digest, world.getSizeX());
            updateInt(digest, world.getSizeY());
            updateInt(digest, world.getSizeZ());
            for (int x = 0; x < world.getSizeX(); x++) {
                for (int y = 0; y < world.getSizeY(); y++) {
                    for (int z = 0; z < world.getSizeZ(); z++) {
                        updateInt(digest, world.getLocalBlock(x, y, z).id);
                    }
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** Stable layout signature that excludes timestamps and elapsed timing. */
    public static String hashPlan(PlanningIR ir) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateString(digest, ir.status);
            for (Plot plot : ir.plots) {
                updateString(digest, plot.id);
                updateString(digest, plot.requirementId);
                for (int[] point : plot.polygon2D) {
                    updateInt(digest, point[0]);
                    updateInt(digest, point[1]);
                }
                updateInt(digest, plot.elevation.baseElevation);
                updateInt(digest, plot.entrance.accessPoint[0]);
                updateInt(digest, plot.entrance.accessPoint[1]);
                updateInt(digest, plot.entrance.accessPoint[2]);
                updateString(digest, plot.builder.presetId);
            }
            ir.groundColumns.stream()
                    .sorted(Comparator.comparingInt((PlanningIR.GroundColumn c) -> c.x)
                            .thenComparingInt(c -> c.z))
                    .forEach(c -> {
                        updateInt(digest, c.x);
                        updateInt(digest, c.z);
                        updateInt(digest, c.originalY);
                        updateInt(digest, c.targetY);
                        updateInt(digest, c.clearToY);
                        updateString(digest, c.kind);
                    });
            for (PlanningIR.RoadEdge edge : ir.transportNetwork.corridors) {
                updateString(digest, edge.id);
                for (RoadStep step : edge.steps) {
                    updateInt(digest, step.x);
                    updateInt(digest, step.y);
                    updateInt(digest, step.z);
                    updateString(digest, step.structure);
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format("%02x", b & 0xff));
        return out.toString();
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
