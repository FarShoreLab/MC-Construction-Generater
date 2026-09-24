package org.mcsettlement.mod.constructor;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.mcsettlement.mod.building.AdaptiveBuildingPlacer;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.Plot;
import org.mcsettlement.planner.ir.PlanningIR.RoadEdge;
import org.mcsettlement.planner.ir.PlanningIR.RoadStep;

/**
 * Master construction executor.
 * Translates PlanningIR into physical blocks in the Minecraft world according to civil engineering constraints.
 */
public class WorldConstructor {

    public static class ConstructionResult {
        public int clearedBlocks = 0;
        public int cutBlocks = 0;
        public int fillBlocks = 0;
        public int roadBlocks = 0;
        public int buildingsConstructed = 0;
    }

    public static ConstructionResult execute(World world, PlanningIR ir) {
        return execute(world, ir, "medieval_rustic", "");
    }

    public static ConstructionResult execute(World world, PlanningIR ir, String theme, String userPrompt) {
        ConstructionResult res = new ConstructionResult();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        // 1. Clear vegetation & obstacles inside building plots and road buffers
        for (Plot p : ir.plots) {
            int minX = p.polygon2D.get(0)[0];
            int minZ = p.polygon2D.get(0)[1];
            int maxX = p.polygon2D.get(2)[0];
            int maxZ = p.polygon2D.get(2)[1];
            int baseY = p.elevation.baseElevation;

            for (int x = minX - 1; x <= maxX + 1; x++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    for (int y = baseY + 1; y <= baseY + 15; y++) {
                        pos.set(x, y, z);
                        BlockState s = world.getBlockState(pos);
                        if (!s.isAir()) {
                            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                            res.clearedBlocks++;
                        }
                    }
                }
            }
        }

        // 2. Perform Cut & Fill for each plot
        for (Plot p : ir.plots) {
            int minX = p.polygon2D.get(0)[0];
            int minZ = p.polygon2D.get(0)[1];
            int maxX = p.polygon2D.get(2)[0];
            int maxZ = p.polygon2D.get(2)[1];
            int baseY = p.elevation.baseElevation;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    // Fill up to baseY if lower
                    for (int y = baseY; y >= baseY - 8; y--) {
                        pos.set(x, y, z);
                        BlockState s = world.getBlockState(pos);
                        if (s.isAir() || s.isOf(Blocks.WATER)) {
                            world.setBlockState(pos, Blocks.DIRT.getDefaultState(), 3);
                            res.fillBlocks++;
                        } else {
                            break;
                        }
                    }
                }
            }
        }

        // 3. Pave Road Corridors and Stairs
        for (RoadEdge edge : ir.transportNetwork.edges) {
            int halfW = edge.width / 2;

            for (int i = 0; i < edge.steps.size(); i++) {
                RoadStep step = edge.steps.get(i);
                int sx = step.x;
                int sy = step.y;
                int sz = step.z;

                for (int dx = -halfW; dx <= halfW; dx++) {
                    for (int dz = -halfW; dz <= halfW; dz++) {
                        pos.set(sx + dx, sy, sz + dz);

                        // Clear overhead clearance (3 blocks)
                        for (int h = 1; h <= 3; h++) {
                            BlockPos airPos = pos.up(h);
                            if (!world.getBlockState(airPos).isAir()) {
                                world.setBlockState(airPos, Blocks.AIR.getDefaultState(), 3);
                            }
                        }

                        if ("bridge".equals(step.structure)) {
                            world.setBlockState(pos, Blocks.OAK_PLANKS.getDefaultState(), 3);
                            // Outer fence railing
                            if (dx == -halfW || dx == halfW || dz == -halfW || dz == halfW) {
                                world.setBlockState(pos.up(), Blocks.OAK_FENCE.getDefaultState(), 3);
                            }
                        } else if ("stair".equals(step.structure)) {
                            // Place cobblestone stairs facing the direction of progression
                            Direction facing = Direction.NORTH;
                            if (i > 0) {
                                RoadStep prev = edge.steps.get(i - 1);
                                if (sx > prev.x) facing = Direction.EAST;
                                else if (sx < prev.x) facing = Direction.WEST;
                                else if (sz > prev.z) facing = Direction.SOUTH;
                                else if (sz < prev.z) facing = Direction.NORTH;
                            }
                            world.setBlockState(pos, Blocks.COBBLESTONE_STAIRS.getDefaultState().with(StairsBlock.FACING, facing), 3);
                        } else {
                            // Surface paving: mixture of cobblestone and gravel
                            BlockState roadState = ((sx + sz) % 3 == 0) ? Blocks.COBBLESTONE.getDefaultState() : Blocks.GRAVEL.getDefaultState();
                            world.setBlockState(pos, roadState, 3);
                        }
                        res.roadBlocks++;
                    }
                }
            }
        }

        // 4. Place adaptive AI-designed buildings
        for (Plot p : ir.plots) {
            AdaptiveBuildingPlacer.placePlotBuilding(world, p, theme, userPrompt);
            res.buildingsConstructed++;
        }

        return res;
    }
}
