package org.mcsettlement.planner.terrain;

import java.util.Arrays;

/**
 * 2.5D Compound Heightfield Map representation.
 * Encapsulates elevation, surface slope gradient, water bodies, and obstacle masks.
 */
public class HeightfieldMap {
    public enum ObstacleType {
        NONE,
        VEGETATION,       // Tree leaves, flowers, tall grass (clearable)
        TREE_TRUNK,       // Tree log (clearable)
        WATER,            // River / ocean surface
        WATER_DEEP,       // Deep water
        STEEP_CLIFF,      // Natural slope exceeds traversable threshold
        EXISTING_BUILDING // Preserved structural area
    }

    private final int minX;
    private final int minZ;
    private final int width;
    private final int depth;

    private final int[][] surfaceY;
    private final int[][] waterY;
    private final ObstacleType[][] obstacles;
    private final float[][] slope; // Slope gradient (rise / run)

    public HeightfieldMap(int minX, int minZ, int width, int depth) {
        this.minX = minX;
        this.minZ = minZ;
        this.width = width;
        this.depth = depth;

        this.surfaceY = new int[width][depth];
        this.waterY = new int[width][depth];
        this.obstacles = new ObstacleType[width][depth];
        this.slope = new float[width][depth];

        for (int x = 0; x < width; x++) {
            Arrays.fill(this.waterY[x], -1);
            Arrays.fill(this.obstacles[x], ObstacleType.NONE);
        }
    }

    public int getMinX() { return minX; }
    public int getMinZ() { return minZ; }
    public int getWidth() { return width; }
    public int getDepth() { return depth; }

    public boolean inBounds(int worldX, int worldZ) {
        int lx = worldX - minX;
        int lz = worldZ - minZ;
        return lx >= 0 && lx < width && lz >= 0 && lz < depth;
    }

    public boolean inLocalBounds(int lx, int lz) {
        return lx >= 0 && lx < width && lz >= 0 && lz < depth;
    }

    public int getSurfaceY(int worldX, int worldZ) {
        return surfaceY[worldX - minX][worldZ - minZ];
    }

    public void setSurfaceY(int worldX, int worldZ, int y) {
        surfaceY[worldX - minX][worldZ - minZ] = y;
    }

    public int getLocalSurfaceY(int lx, int lz) {
        return surfaceY[lx][lz];
    }

    public void setLocalSurfaceY(int lx, int lz, int y) {
        surfaceY[lx][lz] = y;
    }

    public int getWaterY(int worldX, int worldZ) {
        return waterY[worldX - minX][worldZ - minZ];
    }

    public void setWaterY(int worldX, int worldZ, int y) {
        waterY[worldX - minX][worldZ - minZ] = y;
    }

    public int getLocalWaterY(int lx, int lz) {
        return waterY[lx][lz];
    }

    public void setLocalWaterY(int lx, int lz, int y) {
        waterY[lx][lz] = y;
    }

    public ObstacleType getObstacle(int worldX, int worldZ) {
        return obstacles[worldX - minX][worldZ - minZ];
    }

    public void setObstacle(int worldX, int worldZ, ObstacleType type) {
        obstacles[worldX - minX][worldZ - minZ] = type;
    }

    public ObstacleType getLocalObstacle(int lx, int lz) {
        return obstacles[lx][lz];
    }

    public void setLocalObstacle(int lx, int lz, ObstacleType type) {
        obstacles[lx][lz] = type;
    }

    public float getSlope(int worldX, int worldZ) {
        return slope[worldX - minX][worldZ - minZ];
    }

    public float getLocalSlope(int lx, int lz) {
        return slope[lx][lz];
    }

    /**
     * Compute slope gradient using Sobel-like 8-neighborhood central difference operator.
     */
    public void computeSlopes() {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                int x0 = Math.max(0, x - 1);
                int x1 = Math.min(width - 1, x + 1);
                int z0 = Math.max(0, z - 1);
                int z1 = Math.min(depth - 1, z + 1);

                float dx = (surfaceY[x1][z] - surfaceY[x0][z]) / (float) (x1 - x0 == 0 ? 1 : (x1 - x0));
                float dz = (surfaceY[x][z1] - surfaceY[x][z0]) / (float) (z1 - z0 == 0 ? 1 : (z1 - z0));
                float grad = (float) Math.sqrt(dx * dx + dz * dz);
                slope[x][z] = grad;

                if (grad > 1.4f && obstacles[x][z] == ObstacleType.NONE) {
                    obstacles[x][z] = ObstacleType.STEEP_CLIFF;
                }
            }
        }
    }

    /**
     * Helper to create a synthetic heightfield with slopes, ridges and valleys for offline testing.
     */
    public static HeightfieldMap createSynthetic(int minX, int minZ, int width, int depth, int baseElevation, int maxRelief, long seed) {
        HeightfieldMap map = new HeightfieldMap(minX, minZ, width, depth);
        java.util.Random rnd = new java.util.Random(seed);
        double freq1 = 0.035;
        double freq2 = 0.08;
        double phaseX = rnd.nextDouble() * 100.0;
        double phaseZ = rnd.nextDouble() * 100.0;

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                double nx = (x + phaseX);
                double nz = (z + phaseZ);
                // Multi-octave harmonic landscape with a natural diagonal ridge and valley
                double v1 = Math.sin(nx * freq1) * Math.cos(nz * freq1);
                double v2 = Math.sin((nx + nz) * freq2) * 0.5;
                double valley = Math.pow((x - width * 0.5) / (width * 0.5), 2.0) * 0.4;
                double combined = (v1 + v2 + valley + 1.5) / 3.0; // [0, 1]

                int y = baseElevation + (int) Math.round(combined * maxRelief);
                map.setLocalSurfaceY(x, z, y);

                // Add small water pond in the lowest corner
                if (combined < 0.28 && x < width * 0.35 && z < depth * 0.35) {
                    map.setLocalWaterY(x, z, baseElevation + (int)(0.28 * maxRelief));
                    map.setLocalObstacle(x, z, ObstacleType.WATER);
                } else if (rnd.nextFloat() < 0.04f) {
                    map.setLocalObstacle(x, z, ObstacleType.TREE_TRUNK);
                }
            }
        }
        map.computeSlopes();
        return map;
    }
}
