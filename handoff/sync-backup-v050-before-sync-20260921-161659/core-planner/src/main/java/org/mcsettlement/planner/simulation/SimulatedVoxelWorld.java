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
    private final byte[] grid;
    private static final VoxelType[] TYPES=VoxelType.values();

    public SimulatedVoxelWorld(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        long volume=(long)sizeX*sizeY*sizeZ;
        if(sizeX<1||sizeX>512||sizeZ<1||sizeZ>512||sizeY<1||sizeY>256||volume>67108864L)
            throw new IllegalArgumentException("VOXEL_WORLD_ALLOCATION_LIMIT");
        this.grid = new byte[(int)volume];

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
        return TYPES[grid[((worldX-minX)*sizeY+worldY-minY)*sizeZ+worldZ-minZ]&255];
    }

    public void setBlock(int worldX, int worldY, int worldZ, VoxelType type) {
        if (!inBounds(worldX, worldY, worldZ)) return;
        grid[((worldX-minX)*sizeY+worldY-minY)*sizeZ+worldZ-minZ]=(byte)type.id;
    }

    public VoxelType getLocalBlock(int lx, int ly, int lz) {
        if (lx < 0 || lx >= sizeX || ly < 0 || ly >= sizeY || lz < 0 || lz >= sizeZ) return VoxelType.AIR;
        return TYPES[grid[(lx*sizeY+ly)*sizeZ+lz]&255];
    }

    public void setLocalBlock(int lx, int ly, int lz, VoxelType type) {
        if (lx < 0 || lx >= sizeX || ly < 0 || ly >= sizeY || lz < 0 || lz >= sizeZ) return;
        grid[(lx*sizeY+ly)*sizeZ+lz]=(byte)type.id;
    }
    public SimulatedVoxelWorld copy(){SimulatedVoxelWorld out=new SimulatedVoxelWorld(minX,minY,minZ,sizeX,sizeY,sizeZ);System.arraycopy(grid,0,out.grid,0,grid.length);return out;}
    /** Preserve the original int-ID hash byte order while batching digest updates. */
    public void updateIntIdDigest(java.security.MessageDigest digest){
        byte[] buffer=new byte[8192];int n=0;
        for(byte value:grid){buffer[n++]=0;buffer[n++]=0;buffer[n++]=0;buffer[n++]=value;if(n==buffer.length){digest.update(buffer);n=0;}}
        if(n>0)digest.update(buffer,0,n);
    }

}
