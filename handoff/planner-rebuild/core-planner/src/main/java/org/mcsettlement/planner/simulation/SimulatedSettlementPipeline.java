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

        // 2. Scan world into HeightfieldMap (exactly mirroring WorldTerrainScanner)
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

        // 3. Run original mod planning algorithm
        PlanRequest req = new PlanRequest();
        req.seed = seed;
        req.targetPlots = targetPlots;
        req.roadWidth = 3;
        PlanningIR ir = SettlementPlanner.plan(map, req);
        res.plan = ir;

        // 4. Execute original construction pipeline on the simulated voxel world
        executeConstruction(res.worldAfter, ir, res);

        return res;
    }

    private static void executeConstruction(SimulatedVoxelWorld world, PlanningIR ir, PipelineResult res) {
        // A. Clear vegetation in road corridors and plots
        for (Plot p : ir.plots) {
            int minX = p.polygon2D.get(0)[0];
            int minZ = p.polygon2D.get(0)[1];
            int maxX = p.polygon2D.get(2)[0];
            int maxZ = p.polygon2D.get(2)[1];
            int baseY = p.elevation.baseElevation;

            for (int x = minX - 1; x <= maxX + 1; x++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    for (int y = baseY + 1; y <= baseY + 14; y++) {
                        VoxelType b = world.getBlock(x, y, z);
                        if (b != VoxelType.AIR) {
                            world.setBlock(x, y, z, VoxelType.AIR);
                            res.clearedBlocks++;
                        }
                    }
                }
            }
        }

        // B. Execute Earthwork Cut & Fill
        for (Plot p : ir.plots) {
            int minX = p.polygon2D.get(0)[0];
            int minZ = p.polygon2D.get(0)[1];
            int maxX = p.polygon2D.get(2)[0];
            int maxZ = p.polygon2D.get(2)[1];
            int baseY = p.elevation.baseElevation;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    // Cut high ground
                    for (int y = baseY + 1; y <= baseY + 8; y++) {
                        if (world.getBlock(x, y, z).isSolid) {
                            world.setBlock(x, y, z, VoxelType.AIR);
                            res.cutBlocks++;
                        }
                    }
                    // Fill depressions
                    for (int y = baseY; y >= baseY - 6; y--) {
                        VoxelType b = world.getBlock(x, y, z);
                        if (!b.isSolid) {
                            world.setBlock(x, y, z, VoxelType.DIRT);
                            res.fillBlocks++;
                        } else {
                            break;
                        }
                    }
                }
            }
        }

        // C. Build Retaining Walls along slopes
        for (Plot p : ir.plots) {
            if (p.foundation != null && p.foundation.retainingWalls != null) {
                for (RetainingWallSpec wall : p.foundation.retainingWalls) {
                    int sx = Math.min(wall.startX, wall.endX);
                    int ex = Math.max(wall.startX, wall.endX);
                    int sz = Math.min(wall.startZ, wall.endZ);
                    int ez = Math.max(wall.startZ, wall.endZ);

                    for (int x = sx; x <= ex; x++) {
                        for (int z = sz; z <= ez; z++) {
                            for (int y = wall.baseElevation; y <= wall.topElevation; y++) {
                                world.setBlock(x, y, z, VoxelType.STONE_BRICKS);
                                res.retainingWallBlocks++;
                            }
                        }
                    }
                }
            }
        }

        // D. Pave Roads, Stairs, Bridges
        for (RoadEdge edge : ir.transportNetwork.edges) {
            int halfW = edge.width / 2;
            for (RoadStep step : edge.steps) {
                int sx = step.x;
                int sy = step.y;
                int sz = step.z;

                for (int dx = -halfW; dx <= halfW; dx++) {
                    for (int dz = -halfW; dz <= halfW; dz++) {
                        int rx = sx + dx;
                        int rz = sz + dz;

                        // Clear headroom
                        for (int h = 1; h <= 3; h++) {
                            world.setBlock(rx, sy + h, rz, VoxelType.AIR);
                        }

                        if ("bridge".equals(step.structure)) {
                            world.setBlock(rx, sy, rz, VoxelType.OAK_PLANKS);
                            if (dx == -halfW || dx == halfW || dz == -halfW || dz == halfW) {
                                world.setBlock(rx, sy + 1, rz, VoxelType.OAK_FENCE);
                            }
                            res.roadBlocks++;
                        } else if ("stair".equals(step.structure)) {
                            world.setBlock(rx, sy, rz, VoxelType.COBBLESTONE_STAIRS);
                            res.stairBlocks++;
                        } else {
                            VoxelType roadMat = ((rx + rz) % 3 == 0) ? VoxelType.COBBLESTONE : VoxelType.GRAVEL;
                            world.setBlock(rx, sy, rz, roadMat);
                            res.roadBlocks++;
                        }
                    }
                }
            }
        }

        // E. Build Adaptive Buildings with Anti-Floating Rooting
        for (Plot p : ir.plots) {
            int minX = p.polygon2D.get(0)[0];
            int minZ = p.polygon2D.get(0)[1];
            int maxX = p.polygon2D.get(2)[0];
            int maxZ = p.polygon2D.get(2)[1];
            int baseY = p.elevation.baseElevation;

            // 1. Anti-Floating Rooting: Extend foundation downwards until solid ground is hit!
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.setBlock(x, baseY, z, VoxelType.OAK_PLANKS); // Floor
                    res.buildingBlocks++;

                    for (int y = baseY - 1; y >= baseY - 12; y--) {
                        VoxelType b = world.getBlock(x, y, z);
                        if (!b.isSolid) {
                            world.setBlock(x, y, z, VoxelType.COBBLESTONE);
                            res.foundationRootBlocks++;
                        } else {
                            break;
                        }
                    }
                }
            }

            // 2. House walls & roof
            int wallH = 4;
            for (int y = baseY + 1; y <= baseY + wallH; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        boolean isCorner = (x == minX || x == maxX) && (z == minZ || z == maxZ);
                        boolean isWall = (x == minX || x == maxX || z == minZ || z == maxZ);

                        if (isCorner) {
                            world.setBlock(x, y, z, VoxelType.SPRUCE_LOG);
                            res.buildingBlocks++;
                        } else if (isWall) {
                            if (y == baseY + 2 && (x == (minX + maxX) / 2 || z == (minZ + maxZ) / 2)) {
                                world.setBlock(x, y, z, VoxelType.GLASS_PANE);
                            } else {
                                world.setBlock(x, y, z, VoxelType.COBBLESTONE);
                            }
                            res.buildingBlocks++;
                        } else {
                            world.setBlock(x, y, z, VoxelType.AIR);
                        }
                    }
                }
            }

            // Roof
            int roofY = baseY + wallH + 1;
            for (int x = minX - 1; x <= maxX + 1; x++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    world.setBlock(x, roofY, z, VoxelType.SPRUCE_PLANKS);
                    res.buildingBlocks++;
                }
            }

            // 3. Connect entrance steps to road
            int[] door = p.entrance.accessPoint;
            int floorY = baseY + 1;
            int deltaY = floorY - door[1];
            if (deltaY > 0) {
                for (int s = 0; s <= deltaY; s++) {
                    world.setBlock(door[0], door[1] + s, door[2], VoxelType.COBBLESTONE_STAIRS);
                    res.stairBlocks++;
                }
            }
        }
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
