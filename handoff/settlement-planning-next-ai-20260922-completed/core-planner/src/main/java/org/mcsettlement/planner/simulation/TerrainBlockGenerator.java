package org.mcsettlement.planner.simulation;

import org.mcsettlement.planner.simulation.SimulatedVoxelWorld.VoxelType;

import java.util.Random;
import static org.mcsettlement.planner.terrain.SpatialNoise.*;

/**
 * Lightweight realistic Minecraft terrain block generator.
 * Synthesizes domain-warped, non-periodic elevation, stone/dirt/grass geological strata, water ponds, and natural trees.
 */
public class TerrainBlockGenerator {

    public static final String GENERATOR_VERSION = "terrain-block/0.4.2";

    public static SimulatedVoxelWorld generateHillsideWorld(int width, int depth, int baseElevation, int relief, long seed) {
        return generateWorld(width, depth, baseElevation, relief, "rolling_hills", seed);
    }

    /** Original hillside shortcut, now also available with explicit controls. */
    public static SimulatedVoxelWorld generateHillsideWorld(int width, int depth, int baseElevation, int relief,
                                                           long seed, TerrainParameters parameters) {
        return generateWorld(width, depth, baseElevation, relief, "rolling_hills", seed, parameters);
    }

    /**
     * Generate a deterministic, seedable terrain variant for offline rehearsal.
     * The old hillside entry point intentionally remains the default for callers
     * that do not expose terrain choices.
     */
    public static SimulatedVoxelWorld generateWorld(
            int width, int depth, int baseElevation, int relief, String terrainType, long seed) {
        return generateWorld(width, depth, baseElevation, relief, terrainType, seed, TerrainParameters.defaults(terrainType));
    }

    /** The original generation pipeline with explicit, immutable controls. */
    public static SimulatedVoxelWorld generateWorld(
            int width, int depth, int baseElevation, int relief, String terrainType, long seed,
            TerrainParameters parameters) {
        validateDimensions(width, depth, baseElevation, relief);
        if (parameters == null) throw new IllegalArgumentException("TerrainParameters must not be null");
        TerrainType type = TerrainType.from(terrainType);
        int effectiveRelief = relief;
        int minY = 45;
        int terrainCeiling = baseElevation + effectiveRelief;
        int minimumWorldTop = minY + 71;
        int buildingHeadroom = 27;
        int worldTop = Math.max(minimumWorldTop, terrainCeiling + buildingHeadroom);
        int height = worldTop - minY + 1;
        SimulatedVoxelWorld world = new SimulatedVoxelWorld(0, minY, 0, width, height, depth);
        int waterLevel = waterLevel(baseElevation, relief, parameters);

        int[][] surfaceHeights = new int[width][depth];

        // 1. Generate geological strata from the same sampler exposed to callers.
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                ElevationSample sample = sample(x, z, width, depth, type, seed, parameters);
                double wx = sample.warpedX, wz = sample.warpedZ;
                int surfaceY = boundedSurfaceY(sample.normalizedHeight, baseElevation, relief);
                surfaceHeights[x][z] = surfaceY;

                // Fill columns
                for (int y = minY; y <= surfaceY; y++) {
                    if (y == surfaceY) {
                        if (y < waterLevel) {
                            world.setBlock(x, y, z, VoxelType.DIRT);
                        } else {
                            double substrate=noise(seed,(wx+18)/24.0,(wz-11)/24.0,0x3199);
                            VoxelType surface=substrate>0.67?VoxelType.STONE:
                                    substrate< -0.63?VoxelType.DIRT:VoxelType.GRASS_BLOCK;
                            world.setBlock(x, y, z, surface);
                        }
                    } else if (y >= surfaceY - 3) {
                        world.setBlock(x, y, z, VoxelType.DIRT);
                    } else {
                        world.setBlock(x, y, z, VoxelType.STONE);
                    }
                }
                if (surfaceY < waterLevel) {
                    for (int y = surfaceY + 1; y <= waterLevel; y++)
                        world.setBlock(x, y, z, VoxelType.WATER);
                }
            }
        }

        // 2. Original hash-priority tree placement. Density changes acceptance, not the seed.
        // Spacing changes the original local-minimum radius; there is no lattice or new RNG.
        int spacing = parameters.treeSpacing(), margin = Math.max(4, spacing);
        for(int x=margin;x<width-margin;x++)for(int z=margin;z<depth-margin;z++) {
            int sy=surfaceHeights[x][z];
            double priority=unit(seed,x,z,0x4109);
            if(sy<=waterLevel+1||priority>=0.065||parameters.treeDensity()==0)continue;
            double moisture=noise(seed,x/67.0,z/67.0,0x4211);
            double acceptance = Math.min(1, (0.68+moisture*0.25)*parameters.treeDensity());
            if(unit(seed,x,z,0x4231)>acceptance)continue;
            boolean best=true;
            for(int dx=-spacing;dx<=spacing&&best;dx++)for(int dz=-spacing;dz<=spacing;dz++) {
                if(dx==0&&dz==0||dx*dx+dz*dz>spacing*spacing+spacing)continue;
                if(unit(seed,x+dx,z+dz,0x4109)<priority){best=false;break;}
            }
            if(best)placeOakTree(world,x,sy+1,z,new Random(hash(seed,x,z,0x4303)));
        }
        return world;
    }

    /**
     * Ground surface Y before water/trees. This is the SAME formula called by generateWorld.
     * x/z are local in-map coordinates; no voxel world, planner or JSON dependency is allocated.
     */
    public static int sampleSurfaceY(int x, int z, int width, int depth, int baseElevation, int relief,
                                    String terrainType, long seed, TerrainParameters parameters) {
        validateDimensions(width, depth, baseElevation, relief);
        if (x < 0 || x >= width || z < 0 || z >= depth) throw new IllegalArgumentException("SAMPLE_OUTSIDE_MAP");
        if (parameters == null) throw new IllegalArgumentException("TerrainParameters must not be null");
        return boundedSurfaceY(sample(x, z, width, depth, TerrainType.from(terrainType), seed, parameters).normalizedHeight,
                baseElevation, relief);
    }

    /** Global fill level, not a target water coverage percentage. */
    public static int waterLevel(int baseElevation, int relief, TerrainParameters parameters) {
        if (baseElevation < 50 || baseElevation > 75 || relief < 4 || relief > 36 || parameters == null)
            throw new IllegalArgumentException("INVALID_TERRAIN_ELEVATION_OR_PARAMETERS");
        return baseElevation + (int)(relief * parameters.waterLevelRatio());
    }

    private static int boundedSurfaceY(double normalized, int base, int relief) {
        return Math.min(base + relief, Math.max(50, base + (int)Math.round(normalized * relief)));
    }
    private record ElevationSample(double warpedX, double warpedZ, double normalizedHeight) {}

    private static ElevationSample sample(int x, int z, int width, int depth, TerrainType type,
                                          long seed, TerrainParameters p) {
        double scale = p.horizontalScale();
        double warp = 31 * p.warpStrength() * scale;
        double wx=x+warp*noise(seed,x/(137.0*scale),z/(137.0*scale),0x7811);
        double wz=z+warp*noise(seed,x/(137.0*scale),z/(137.0*scale),0x9147);
        double broad=noise(seed,wx/(113.0*scale),wz/(113.0*scale),0x1021);
        double middle=noise(seed,wx/(47.0*scale),wz/(47.0*scale),0x1027);
        double fine=noise(seed,wx/(19.0*scale),wz/(19.0*scale),0x1039)*p.detailStrength();
        double gradient=((double)x/width+(double)z/depth-1)*0.07*p.slopeStrength();
        double combined = switch (type) {
            case ROLLING_HILLS -> 0.46 + broad*0.31 + middle*0.16 + fine*0.045 + gradient;
            case MOUNTAIN -> {
                double ridge=1-Math.min(1,Math.abs(noise(seed,wx/(83.0*scale),wz/(83.0*scale),0x2017))*p.ridgeSharpness());
                yield 0.08+ridge*0.71+broad*0.15+middle*0.09+fine*0.035;
            }
            case VALLEY -> {
                double cx=(x-(width-1)*0.5)/Math.max(1.0,width*0.5*p.valleyWidth());
                double cz=(z-(depth-1)*0.5)/Math.max(1.0,depth*0.5*p.valleyWidth());
                double valley=Math.min(1.0,StrictMath.sqrt(cx*cx+cz*cz));
                yield 0.14+valley*0.52+broad*0.17+middle*0.095+fine*0.035;
            }
            case PLATEAU -> {
                double raw=0.45+broad*0.31+middle*0.16+gradient;
                yield StrictMath.round(Math.max(0,Math.min(1,raw))*p.terraceLevels())/(double)p.terraceLevels()+fine*0.04;
            }
        };
        return new ElevationSample(wx, wz, Math.max(0,Math.min(1,combined)));
    }

    private static void validateDimensions(int width, int depth, int baseElevation, int relief) {
        if (width < 1 || depth < 1 || width > 512 || depth > 512 || baseElevation < 50 || baseElevation > 75 || relief < 4 || relief > 36)
            throw new IllegalArgumentException("INVALID_TERRAIN_DIMENSIONS_OR_ELEVATION");
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
