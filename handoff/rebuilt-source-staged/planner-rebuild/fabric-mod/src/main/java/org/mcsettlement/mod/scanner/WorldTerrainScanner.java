package org.mcsettlement.mod.scanner;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.terrain.HeightfieldMap.ObstacleType;

/**
 * Scans Minecraft world blocks in a 3D bounding box to construct a 2.5D HeightfieldMap.
 */
public class WorldTerrainScanner {
    // Conservative material classifier, not provenance detection. Player-placed natural stone is indistinguishable.
    private static final java.util.Set<String> NATURAL = java.util.Set.of(
            "stone", "deepslate", "dirt", "coarse_dirt", "rooted_dirt", "grass_block", "podzol", "mycelium",
            "sand", "red_sand", "gravel", "clay", "terracotta", "granite", "diorite", "andesite", "tuff",
            "calcite", "sandstone", "red_sandstone", "snow_block", "netherrack", "basalt",
            "blackstone", "end_stone", "mud", "moss_block");

    public static boolean isNaturalGround(BlockState state) {
        String id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).getPath();
        return !state.hasBlockEntity() && (NATURAL.contains(id) || id.endsWith("_ore"));
    }
    public static boolean isReplaceableForPlanning(BlockState state) {
        if (state.hasBlockEntity()) return false;
        if (state.isAir() || isNaturalGround(state)) return true;
        return state.getBlock() instanceof LeavesBlock || state.isIn(net.minecraft.registry.tag.BlockTags.LOGS) ||
                state.isOf(Blocks.GRASS) || state.isOf(Blocks.TALL_GRASS) ||
                state.isOf(Blocks.FERN) || state.isOf(Blocks.LARGE_FERN) || state.isOf(Blocks.SNOW);
    }


    public static HeightfieldMap scanRegion(World world, int minX, int minZ, int maxX, int maxZ) {
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        HeightfieldMap map = new HeightfieldMap(minX, minZ, width, depth);

        int topY = world.getTopY() - 1;
        int bottomY = world.getBottomY();

        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = 0; x < width; x++) {
            int worldX = minX + x;
            for (int z = 0; z < depth; z++) {
                int worldZ = minZ + z;

                int surfaceY = bottomY;
                boolean foundGround = false;
                int waterY = -1;
                ObstacleType obstacle = ObstacleType.NONE;

                // Scan from top downwards
                for (int y = topY; y >= bottomY; y--) {
                    pos.set(worldX, y, worldZ);
                    BlockState state = world.getBlockState(pos);

                    if (state.isAir()) {
                        continue;
                    }

                    // Water check
                    if (state.isOf(Blocks.WATER)) {
                        if (waterY == -1) {
                            waterY = y;
                            obstacle = ObstacleType.WATER;
                        }
                        continue;
                    }

                    // Vegetation check (leaves, tall grass, flowers)
                    if (state.getBlock() instanceof LeavesBlock || state.isOf(Blocks.GRASS) ||
                        state.isOf(Blocks.TALL_GRASS) || state.isOf(Blocks.FERN) || state.isOf(Blocks.LARGE_FERN)) {
                        if (obstacle == ObstacleType.NONE) {
                            obstacle = ObstacleType.VEGETATION;
                        }
                        continue;
                    }

                    // Tree logs
                    if (state.getBlock() instanceof PillarBlock && state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) {
                        if (obstacle == ObstacleType.NONE) {
                            obstacle = ObstacleType.TREE_TRUNK;
                        }
                        continue;
                    }

                    // Unknown construction materials/block entities are preserved, not treated as terrain.
                    surfaceY = y;
                    foundGround = true;
                    if (!isNaturalGround(state)) obstacle = ObstacleType.EXISTING_BUILDING;
                    break;
                }

                if (!foundGround) obstacle = ObstacleType.EXISTING_BUILDING; // no load-bearing ground
                map.setLocalSurfaceY(x, z, surfaceY);
                if (obstacle == ObstacleType.EXISTING_BUILDING) {
                    map.setLocalObstacle(x, z, obstacle);
                } else if (waterY != -1) {
                    map.setLocalWaterY(x, z, waterY);
                    if (waterY - surfaceY > 3) {
                        map.setLocalObstacle(x, z, ObstacleType.WATER_DEEP);
                    } else {
                        map.setLocalObstacle(x, z, ObstacleType.WATER);
                    }
                } else if (obstacle != ObstacleType.NONE) {
                    map.setLocalObstacle(x, z, obstacle);
                }
            }
        }

        // Calculate slope gradients across the entire scanned region
        map.computeSlopes();
        return map;
    }
}
