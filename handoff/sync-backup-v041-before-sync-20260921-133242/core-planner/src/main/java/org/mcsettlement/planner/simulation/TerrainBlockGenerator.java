package org.mcsettlement.planner.simulation;

import org.mcsettlement.planner.simulation.SimulatedVoxelWorld.VoxelType;

import java.util.Random;
import static org.mcsettlement.planner.terrain.SpatialNoise.*;

/**
 * Lightweight realistic Minecraft terrain block generator.
 * Synthesizes domain-warped, non-periodic elevation, stone/dirt/grass geological strata, water ponds, and natural trees.
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
        if (width < 1 || depth < 1 || width > 512 || depth > 512 || baseElevation < 50 || baseElevation > 75 || relief < 4 || relief > 36) throw new IllegalArgumentException("INVALID_TERRAIN_DIMENSIONS_OR_ELEVATION");
        TerrainType type = TerrainType.from(terrainType);
        int effectiveRelief = Math.max(4, Math.min(36, relief));
        int minY = 45;
        int terrainCeiling = baseElevation + effectiveRelief;
        int minimumWorldTop = minY + 71; // Preserve the original preview volume for ordinary inputs.
        int buildingHeadroom = 27; // 24-block building envelope plus three clear blocks.
        int worldTop = Math.max(minimumWorldTop, terrainCeiling + buildingHeadroom);
        int height = worldTop - minY + 1;
        SimulatedVoxelWorld world = new SimulatedVoxelWorld(0, minY, 0, width, height, depth);
        // Independent salts isolate elevation, warp, substrate, basins, tree sites and crowns.
        int waterLevel = baseElevation + (int)(effectiveRelief * (type == TerrainType.VALLEY ? 0.28 : 0.22));

        int[][] surfaceHeights = new int[width][depth];

        // 1. Generate geological strata
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                double wx=x+31*noise(seed,x/137.0,z/137.0,0x7811);
                double wz=z+31*noise(seed,x/137.0,z/137.0,0x9147);
                double broad=noise(seed,wx/113.0,wz/113.0,0x1021);
                double middle=noise(seed,wx/47.0,wz/47.0,0x1027);
                double fine=noise(seed,wx/19.0,wz/19.0,0x1039);
                double gradient=((double)x/width+(double)z/depth-1)*0.07;
                double combined = switch (type) {
                    case ROLLING_HILLS -> 0.46 + broad*0.31 + middle*0.16 + fine*0.045 + gradient;
                    case MOUNTAIN -> {
                        double ridge=1-Math.min(1,Math.abs(noise(seed,wx/83.0,wz/83.0,0x2017))*1.65);
                        yield 0.08+ridge*0.71+broad*0.15+middle*0.09+fine*0.035;
                    }
                    case VALLEY -> {
                        double cx=(x-(width-1)*0.5)/Math.max(1.0,width*0.5);
                        double cz=(z-(depth-1)*0.5)/Math.max(1.0,depth*0.5);
                        double valley=Math.min(1.0,StrictMath.sqrt(cx*cx+cz*cz));
                        yield 0.14+valley*0.52+broad*0.17+middle*0.095+fine*0.035;
                    }
                    case PLATEAU -> {
                        double raw=0.45+broad*0.31+middle*0.16+gradient;
                        yield StrictMath.round(Math.max(0,Math.min(1,raw))*5)/5.0+fine*0.04;
                    }
                };
                combined=Math.max(0,Math.min(1,combined));

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
                            // Surface patches have an independent, warped channel, not the tree RNG.
                            double substrate=noise(seed,(wx+18)/24.0,(wz-11)/24.0,0x3199);
                            VoxelType surface=substrate>0.67?VoxelType.STONE:
                                    substrate< -0.63?VoxelType.DIRT:VoxelType.GRASS_BLOCK;
                            world.setBlock(x, y, z, surface);
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

        // 2. Hash-priority thinning on every cell. No shared X columns, lattice jitter, or scan RNG.
        // A strict local minimum (radius 4) gives irregular spacing; moisture thins, never tiles.
        for(int x=4;x<width-4;x++)for(int z=4;z<depth-4;z++) {
            int sy=surfaceHeights[x][z];
            double priority=unit(seed,x,z,0x4109);
            if(sy<=waterLevel+1||priority>=0.065)continue;
            double moisture=noise(seed,x/67.0,z/67.0,0x4211);
            if(unit(seed,x,z,0x4231)>0.68+moisture*0.25)continue;
            boolean best=true;
            for(int dx=-4;dx<=4&&best;dx++)for(int dz=-4;dz<=4;dz++) {
                if(dx==0&&dz==0||dx*dx+dz*dz>20)continue;
                if(unit(seed,x+dx,z+dz,0x4109)<priority){best=false;break;}
            }
            if(best)placeOakTree(world,x,sy+1,z,new Random(hash(seed,x,z,0x4303)));
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
