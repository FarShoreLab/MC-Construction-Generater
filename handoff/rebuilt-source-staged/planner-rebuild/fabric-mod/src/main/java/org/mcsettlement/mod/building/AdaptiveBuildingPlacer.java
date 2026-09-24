package org.mcsettlement.mod.building;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.state.property.Property;
import org.mcsettlement.llm.AiBuildingArchitect;
import org.mcsettlement.llm.AiBuildingArchitect.BuildingVoxelModel;
import org.mcsettlement.llm.LlmStrategyManager;
import org.mcsettlement.mod.ModConfig;
import org.mcsettlement.planner.ir.PlanningIR.Plot;
import org.mcsettlement.planner.ir.PlanningIR.RetainingWallSpec;
import org.mcsettlement.planner.preset.BuildingPreset;
import org.mcsettlement.planner.preset.BuildingPresetRegistry;

import java.util.Optional;

/**
 * Adaptive Building Placer and Foundation Wrapper.
 * Integrates AI-generated 3D architecture with civil engineering foundation guarantees:
 * 1. Downward rooting ensures 0% floating buildings.
 * 2. Retaining walls prevent burying by slopes.
 * 3. Entrance steps connect smoothly to planned roads.
 */
public class AdaptiveBuildingPlacer {

    public static void placePlotBuilding(World world, Plot plot, String theme, String userPrompt) {
        if ("locked_preset".equals(plot.builder.generatorType))
            throw new IllegalStateException("Planned plots must use WorldConstructor's preflighted manifest; no implicit roots/stairs");
        int minX = plot.polygon2D.get(0)[0];
        int minZ = plot.polygon2D.get(0)[1];
        int maxX = plot.polygon2D.get(2)[0];
        int maxZ = plot.polygon2D.get(2)[1];
        int baseY = plot.elevation.baseElevation;

        BlockPos.Mutable pos = new BlockPos.Mutable();

        // 1. Downward Rooting: Fill beneath the building foundation until solid ground is hit (NO FLOATING BUILDINGS)
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                pos.set(x, baseY, z);
                world.setBlockState(pos, Blocks.COBBLESTONE.getDefaultState(), 3);

                for (int y = baseY - 1; y >= baseY - 16; y--) {
                    pos.set(x, y, z);
                    BlockState s = world.getBlockState(pos);
                    if (s.isAir() || s.isOf(Blocks.WATER) || s.isOf(Blocks.GRASS) || s.isOf(Blocks.TALL_GRASS)) {
                        world.setBlockState(pos, Blocks.COBBLESTONE.getDefaultState(), 3);
                    } else {
                        break; // Solid ground reached
                    }
                }
            }
        }

        // 2. Retaining Walls (Masonry along slopes)
        if (plot.foundation != null && plot.foundation.retainingWalls != null) {
            for (RetainingWallSpec wall : plot.foundation.retainingWalls) {
                int sx = Math.min(wall.startX, wall.endX);
                int ex = Math.max(wall.startX, wall.endX);
                int sz = Math.min(wall.startZ, wall.endZ);
                int ez = Math.max(wall.startZ, wall.endZ);

                for (int x = sx; x <= ex; x++) {
                    for (int z = sz; z <= ez; z++) {
                        for (int y = wall.baseElevation; y <= wall.topElevation; y++) {
                            pos.set(x, y, z);
                            world.setBlockState(pos, Blocks.STONE_BRICKS.getDefaultState(), 3);
                        }
                    }
                }
            }
        }

        // 3. AI-Driven Architectural Structure Generation
        LlmStrategyManager.LlmConfig llmCfg = new LlmStrategyManager.LlmConfig();
        ModConfig cfg = ModConfig.get();
        if (cfg != null) {
            llmCfg.apiEndpoint = cfg.llmApiEndpoint;
            llmCfg.apiKey = cfg.llmApiKey;
            llmCfg.modelName = cfg.llmModelName;
            llmCfg.preferLocalAgy = cfg.preferLocalAgy;
        }

        BuildingVoxelModel aiModel = AiBuildingArchitect.designBuildingForPlot(plot, theme, userPrompt, llmCfg);
        if (aiModel != null && aiModel.blocks != null) {
            for (int bx = 0; bx < aiModel.sizeX; bx++) {
                for (int by = 0; by < aiModel.sizeY; by++) {
                    for (int bz = 0; bz < aiModel.sizeZ; bz++) {
                        String blockId = aiModel.blocks[bx][by][bz];
                        if (blockId == null || blockId.equals("minecraft:air")) continue;

                        int wx = minX + bx;
                        int wy = baseY + by;
                        int wz = minZ + bz;

                        BlockState state = parseBlockState(blockId);
                        if (!state.isAir()) {
                            pos.set(wx, wy, wz);
                            world.setBlockState(pos, state, 3);
                        }
                    }
                }
            }
        }

        // 4. Connect entrance steps to road
        int[] door = plot.entrance.accessPoint;
        connectEntranceSteps(world, door[0], door[1], door[2], baseY + 1, plot.entrance.facing);
    }

    private static void connectEntranceSteps(World world, int ex, int ey, int ez, int floorY, String facing) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int deltaY = floorY - ey;

        if (deltaY > 0) {
            for (int step = 0; step <= deltaY; step++) {
                pos.set(ex, ey + step, ez);
                world.setBlockState(pos, Blocks.COBBLESTONE_STAIRS.getDefaultState(), 3);
            }
        } else if (deltaY < 0) {
            for (int step = 0; step >= deltaY; step--) {
                pos.set(ex, ey + step, ez);
                world.setBlockState(pos, Blocks.COBBLESTONE_STAIRS.getDefaultState(), 3);
            }
        } else {
            pos.set(ex, ey, ez);
            world.setBlockState(pos, Blocks.GRAVEL.getDefaultState(), 3);
        }
    }

    /**
     * Places a standalone single-building preset at the specified origin with downward anti-floating rooting.
     */
    public static boolean placeStandalonePreset(World world, BlockPos origin, String presetId, String facing, String theme) {
        BuildingPreset preset = BuildingPresetRegistry.getInstance().getPreset(presetId);
        if (preset == null) return false;

        if (facing != null && !facing.isBlank()) {
            preset = preset.rotateToFacing(facing);
        }

        String[][][] grid = preset.toBlockGrid(theme);
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();

        BlockPos.Mutable pos = new BlockPos.Mutable();

        // 1. Foundation Rooting downwards (prevent floating)
        for (int x = 0; x < preset.sizeX; x++) {
            for (int z = 0; z < preset.sizeZ; z++) {
                pos.set(ox + x, oy, oz + z);
                world.setBlockState(pos, Blocks.COBBLESTONE.getDefaultState(), 3);

                for (int y = oy - 1; y >= oy - 16; y--) {
                    pos.set(ox + x, y, oz + z);
                    BlockState s = world.getBlockState(pos);
                    if (s.isAir() || s.isOf(Blocks.WATER) || s.isOf(Blocks.GRASS) || s.isOf(Blocks.TALL_GRASS)) {
                        world.setBlockState(pos, Blocks.COBBLESTONE.getDefaultState(), 3);
                    } else {
                        break;
                    }
                }
            }
        }

        // 2. Place 3D structure layer by layer
        for (int x = 0; x < preset.sizeX; x++) {
            for (int y = 0; y < preset.sizeY; y++) {
                for (int z = 0; z < preset.sizeZ; z++) {
                    pos.set(ox + x, oy + y, oz + z);
                    String blockId = grid[x][y][z];
                    BlockState state = parseBlockState(blockId);
                    world.setBlockState(pos, state, 3);
                }
            }
        }

        return true;
    }

    /**
     * Parses a block ID string with optional state properties (e.g. "minecraft:spruce_log[axis=y]")
     */
    public static BlockState parseBlockState(String blockStr) {
        if (blockStr == null || blockStr.isBlank() || "minecraft:air".equals(blockStr)) {
            return Blocks.AIR.getDefaultState();
        }

        String baseId = blockStr;
        String propsStr = null;
        int bracketStart = blockStr.indexOf('[');
        int bracketEnd = blockStr.lastIndexOf(']');
        if (bracketStart != -1 && bracketEnd > bracketStart) {
            baseId = blockStr.substring(0, bracketStart);
            propsStr = blockStr.substring(bracketStart + 1, bracketEnd);
        }

        Identifier id = new Identifier(baseId);
        net.minecraft.block.Block block = Registries.BLOCK.get(id);
        if (block == Blocks.AIR && !"minecraft:air".equals(baseId)) {
            return Blocks.AIR.getDefaultState();
        }

        BlockState state = block.getDefaultState();
        if (propsStr != null && !propsStr.isBlank()) {
            String[] propPairs = propsStr.split(",");
            for (String pair : propPairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    String propName = kv[0].trim();
                    String propVal = kv[1].trim();
                    Property<?> property = state.getBlock().getStateManager().getProperty(propName);
                    if (property != null) {
                        state = setPropertyValue(state, property, propVal);
                    }
                }
            }
        }
        return state;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState setPropertyValue(BlockState state, Property property, String value) {
        Optional<?> opt = property.parse(value);
        if (opt.isPresent()) {
            return (BlockState) state.with(property, (Comparable) opt.get());
        }
        return state;
    }
}
