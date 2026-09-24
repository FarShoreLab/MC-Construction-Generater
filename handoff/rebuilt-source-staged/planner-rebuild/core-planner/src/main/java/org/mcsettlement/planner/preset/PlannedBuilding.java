package org.mcsettlement.planner.preset;

import org.mcsettlement.planner.ir.PlanningIR.Plot;

/** A locked, untrimmed preset and an explicit two-block-high door passage. No LLM calls. */
public final class PlannedBuilding {
    private PlannedBuilding() {}
    public static BuildingPreset resolve(Plot p) {
        if (p.builder.presetId == null) throw new IllegalArgumentException("MISSING_LOCKED_PRESET");
        BuildingPreset source = BuildingPresetRegistry.getInstance().getPreset(p.builder.presetId);
        if (source == null) throw new IllegalArgumentException("PRESET_UNAVAILABLE: " + p.builder.presetId);
        BuildingPreset b = source.rotateToFacing(p.entrance.facing);
        if (b.sizeX != p.builder.footprintSize[0] || b.sizeZ != p.builder.footprintSize[1]
                || b.sizeY > p.builder.heightLimit
                || p.polygon2D.get(2)[0] - p.polygon2D.get(0)[0] + 1 != b.sizeX
                || p.polygon2D.get(2)[1] - p.polygon2D.get(0)[1] + 1 != b.sizeZ)
            throw new IllegalArgumentException("LOCKED_PRESET_DIMENSION_MISMATCH: " + p.id);
        if (p.entrance.accessPoint[0] != p.polygon2D.get(0)[0] + b.entrance.x
                || p.entrance.accessPoint[2] != p.polygon2D.get(0)[1] + b.entrance.z
                || p.entrance.accessPoint[1] != p.elevation.baseElevation)
            throw new IllegalArgumentException("LOCKED_PRESET_ENTRANCE_MISMATCH: " + p.id);
        return b;
    }
    public static String[][][] grid(Plot p, String theme) { return grid(resolve(p), theme); }
    public static String[][][] grid(BuildingPreset b, String theme) {
        if (b.sizeY < 3 || b.entrance == null || b.entrance.x < 0 || b.entrance.x >= b.sizeX
                || b.entrance.z < 0 || b.entrance.z >= b.sizeZ)
            throw new IllegalArgumentException("INVALID_PRESET_ENTRANCE: " + b.id);
        String[][][] grid = b.toBlockGrid(theme);
        int[] direction = direction(b.entrance.facing);
        // Several shipped templates have solid blocks at their declared doors.
        // Repair only the declared passage (one step inward, then outward to footprint edge).
        int x = b.entrance.x - direction[0], z = b.entrance.z - direction[1];
        if (x < 0 || x >= b.sizeX || z < 0 || z >= b.sizeZ) {
            x = b.entrance.x; z = b.entrance.z;
        }
        while (x >= 0 && x < b.sizeX && z >= 0 && z < b.sizeZ) {
            grid[x][0][z] = "minecraft:cobblestone";
            grid[x][1][z] = "minecraft:air";
            grid[x][2][z] = "minecraft:air";
            x += direction[0]; z += direction[1];
        }
        return grid;
    }
    public static int[] direction(String facing) {
        return switch (facing) {
            case "NORTH" -> new int[]{0,-1}; case "SOUTH" -> new int[]{0,1};
            case "WEST" -> new int[]{-1,0}; case "EAST" -> new int[]{1,0};
            default -> throw new IllegalArgumentException("INVALID_FACING: " + facing);
        };
    }
}
