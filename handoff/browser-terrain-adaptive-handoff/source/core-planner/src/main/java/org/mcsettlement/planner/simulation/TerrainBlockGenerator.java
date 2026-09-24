package org.mcsettlement.planner.simulation;

import org.mcsettlement.planner.simulation.SimulatedVoxelWorld.VoxelType;

import java.util.Random;

/**
 * Lightweight realistic Minecraft terrain block generator.
 * Synthesizes multi-harmonic elevation, stone/dirt/grass geological strata, water ponds, and natural trees.
 */
public class TerrainBlockGenerator {

    public static SimulatedVoxelWorld generateHillsideWorld(int width, int depth, int baseElevation, int relief, long seed) {
        return generateWorld(width, depth, baseElevation, relief, "rolling_hills", seed);
    }

    /**
     * Generate a deterministic, seedable terrain variant for offline rehearsal.
     * The old hillside entry point intentionally remains the default for callers
     * that do not expose terrain choices.
     */
    public static SimulatedVoxelWorld generateWorld(
            int width, int depth, int baseElevation, int relief, String terrainType, long seed) {
        if (width <= 0 || depth <= 0) throw new IllegalArgumentException("Terrain dimensions must be positive");
        TerrainType type = TerrainType.from(terrainType);
        int effectiveRelief = Math.max(4, Math.min(36, relief));
        int minY = 45;
        int terrainCeiling = baseElevation + effectiveRelief;
        int minimumWorldTop = minY + 71; // Preserve the original preview volume for ordinary inputs.
        int buildingHeadroom = 27; // 24-block building envelope plus three clear blocks.
        int worldTop = Math.max(minimumWorldTop, terrainCeiling + buildingHeadroom);
        int height = worldTop - minY + 1;
        SimulatedVoxelWorld world = new SimulatedVoxelWorld(0, minY, 0, width, height, depth);
        Random rnd = new Random(seed);

        double freq1 = 0.032;
        double freq2 = 0.075;
        double phaseX = rnd.nextDouble() * 120.0;
        double phaseZ = rnd.nextDouble() * 120.0;

        int waterLevel = baseElevation + (int)(effectiveRelief * (type == TerrainType.VALLEY ? 0.28 : 0.22));

        int[][] surfaceHeights = new int[width][depth];

        // 1. Generate geological strata
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                double nx = (x + phaseX);
                double nz = (z + phaseZ);

                // Multi-octave natural slope with a deterministic terrain profile.
                double v1 = Math.sin(nx * freq1) * Math.cos(nz * freq1);
                double v2 = Math.sin((nx + nz) * freq2) * 0.45;
                double slopeGradient = ((double) x / width) * 0.6 + ((double) z / depth) * 0.4;
                double combined = switch (type) {
                    case ROLLING_HILLS -> (v1 + v2 + slopeGradient + 1.2) / 2.8;
                    case MOUNTAIN -> {
                        double ridge = 1.0 - Math.abs(Math.sin((nx - nz) * 0.028));
                        yield Math.max(0.0, Math.min(1.0, 0.18 + ridge * 0.62 + (v1 + v2) * 0.16 + slopeGradient * 0.12));
                    }
                    case VALLEY -> {
                        double centerX = (x - (width - 1) * 0.5) / Math.max(1.0, width * 0.5);
                        double centerZ = (z - (depth - 1) * 0.5) / Math.max(1.0, depth * 0.5);
                        double valley = Math.min(1.0, Math.sqrt(centerX * centerX + centerZ * centerZ));
                        yield Math.max(0.0, Math.min(1.0, 0.18 + valley * 0.58 + (v1 + v2) * 0.16));
                    }
                    case PLATEAU -> {
                        double raw = (v1 + v2 + slopeGradient + 1.2) / 2.8;
                        double terraces = Math.round(Math.max(0.0, Math.min(1.0, raw)) * 5.0) / 5.0;
                        yield Math.max(0.0, Math.min(1.0, terraces + v2 * 0.06));
                    }
                };

                int surfaceY = baseElevation + (int) Math.round(combined * effectiveRelief);
                // Terrain elevation is bounded independently from the world volume. The latter
                // grows with the requested relief so construction always has vertical room.
                surfaceY = Math.min(terrainCeiling, Math.max(minY + 5, surfaceY));
                surfaceHeights[x][z] = surfaceY;

                // Fill columns
                for (int y = minY; y <= surfaceY; y++) {
                    if (y == surfaceY) {
                        if (y < waterLevel) {
                            world.setBlock(x, y, z, VoxelType.DIRT); // River bed
                        } else {
                            world.setBlock(x, y, z, VoxelType.GRASS_BLOCK); // Surface grass
                        }
                    } else if (y >= surfaceY - 3) {
                        world.setBlock(x, y, z, VoxelType.DIRT); // Subsurface dirt layer
                    } else {
                        world.setBlock(x, y, z, VoxelType.STONE); // Deep stone bedrock
                    }
                }

                // Fill water in depression
                if (surfaceY < waterLevel) {
                    for (int y = surfaceY + 1; y <= waterLevel; y++) {
                        world.setBlock(x, y, z, VoxelType.WATER);
                    }
                }
            }
        }

        // 2. Generate natural trees on dry slopes
        for (int x = 4; x < width - 4; x += 6 + rnd.nextInt(4)) {
            for (int z = 4; z < depth - 4; z += 6 + rnd.nextInt(4)) {
                int sy = surfaceHeights[x][z];
                if (sy > waterLevel + 1 && rnd.nextFloat() < 0.75f) {
                    placeOakTree(world, x, sy + 1, z, rnd);
                }
            }
        }

        return world;
    }

    public enum TerrainType {
        ROLLING_HILLS("rolling_hills"),
        MOUNTAIN("mountain"),
        VALLEY("valley"),
        PLATEAU("plateau");

        public final String id;

        TerrainType(String id) {
            this.id = id;
        }

        public static TerrainType from(String value) {
            if (value == null || value.isBlank()) return ROLLING_HILLS;
            for (TerrainType type : values()) {
                if (type.id.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) return type;
            }
            throw new IllegalArgumentException("Unknown terrain type: " + value);
        }
    }

    private static void placeOakTree(SimulatedVoxelWorld world, int trunkX, int baseY, int trunkZ, Random rnd) {
        int trunkHeight = 4 + rnd.nextInt(2);

        // Trunk
        for (int y = baseY; y < baseY + trunkHeight; y++) {
            world.setBlock(trunkX, y, trunkZ, VoxelType.OAK_LOG);
        }

        // Leaves canopy
        int leafBase = baseY + trunkHeight - 2;
        int leafTop = baseY + trunkHeight + 1;

        for (int y = leafBase; y <= leafTop; y++) {
            int radius = (y >= leafTop - 1) ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && rnd.nextBoolean()) continue;
                    int lx = trunkX + dx;
                    int lz = trunkZ + dz;
                    if (world.getBlock(lx, y, lz) == VoxelType.AIR) {
                        world.setBlock(lx, y, lz, VoxelType.OAK_LEAVES);
                    }
                }
            }
        }
    }
}
