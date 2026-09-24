package org.mcsettlement.planner.simulation;

import java.util.Arrays;

/**
 * Lightweight in-memory 3D Minecraft voxel world simulator.
 * Requires zero Minecraft runtime dependencies and enables instant offline testing and visualization.
 */
public class SimulatedVoxelWorld {

    public enum VoxelType {
        AIR(0, "#00000000", false),
        STONE(1, "#757575", true),
        DIRT(2, "#795548", true),
        GRASS_BLOCK(3, "#558B2F", true),
        WATER(4, "#1976D2", false),
        OAK_LOG(5, "#4E342E", true),
        OAK_LEAVES(6, "#2E7D32", false),
        GRAVEL(7, "#9E9E9E", true),
        COBBLESTONE(8, "#616161", true),
        STONE_BRICKS(9, "#424242", true),
        COBBLESTONE_STAIRS(10, "#E65100", true), // Highlighted orange for stairs visibility
        SPRUCE_LOG(11, "#3E2723", true),
        SPRUCE_PLANKS(12, "#8D6E63", true),
        OAK_PLANKS(13, "#BCAAA4", true),
        GLASS_PANE(14, "#80DEEA", false),
        OAK_FENCE(15, "#A1887F", false);

        public final int id;
        public final String hexColor;
        public final boolean isSolid;

        VoxelType(int id, String hexColor, boolean isSolid) {
            this.id = id;
            this.hexColor = hexColor;
            this.isSolid = isSolid;
        }
    }

    private final int minX, minY, minZ;
    private final int sizeX, sizeY, sizeZ;
    private final VoxelType[][][] grid;

    public SimulatedVoxelWorld(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.grid = new VoxelType[sizeX][sizeY][sizeZ];

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                Arrays.fill(this.grid[x][y], VoxelType.AIR);
            }
        }
    }

    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }

    public boolean inBounds(int worldX, int worldY, int worldZ) {
        int lx = worldX - minX;
        int ly = worldY - minY;
        int lz = worldZ - minZ;
        return lx >= 0 && lx < sizeX && ly >= 0 && ly < sizeY && lz >= 0 && lz < sizeZ;
    }

    public VoxelType getBlock(int worldX, int worldY, int worldZ) {
        if (!inBounds(worldX, worldY, worldZ)) return VoxelType.AIR;
        return grid[worldX - minX][worldY - minY][worldZ - minZ];
    }

    public void setBlock(int worldX, int worldY, int worldZ, VoxelType type) {
        if (!inBounds(worldX, worldY, worldZ)) return;
        grid[worldX - minX][worldY - minY][worldZ - minZ] = type;
    }

    public VoxelType getLocalBlock(int lx, int ly, int lz) {
        if (lx < 0 || lx >= sizeX || ly < 0 || ly >= sizeY || lz < 0 || lz >= sizeZ) return VoxelType.AIR;
        return grid[lx][ly][lz];
    }

    public void setLocalBlock(int lx, int ly, int lz, VoxelType type) {
        if (lx < 0 || lx >= sizeX || ly < 0 || ly >= sizeY || lz < 0 || lz >= sizeZ) return;
        grid[lx][ly][lz] = type;
    }
}
