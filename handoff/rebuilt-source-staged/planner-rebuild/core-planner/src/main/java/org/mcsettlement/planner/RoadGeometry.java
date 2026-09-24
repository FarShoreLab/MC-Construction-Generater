package org.mcsettlement.planner;

import org.mcsettlement.planner.terrain.HeightfieldMap;

/** Shared integer-grid convention, including asymmetric even-width footprints. */
public final class RoadGeometry {
    private RoadGeometry() {}
    public static int low(int width) { return -(width / 2); }
    public static int high(int width) { return low(width) + width - 1; }
    public static long key(int x, int z) { return ((long)x << 32) | (z & 0xffffffffL); }
    public static int x(long key) { return (int)(key >> 32); }
    public static int z(long key) { return (int)key; }
    public static boolean buildable(HeightfieldMap map, int x, int z) {
        if (!map.inBounds(x,z)) return false;
        return switch (map.getObstacle(x,z)) {
            case NONE, VEGETATION, TREE_TRUNK -> true;
            default -> false;
        };
    }
    public static boolean gradeFits(HeightfieldMap map, int x, int z, int y, int cut, int fill) {
        if (!buildable(map,x,z)) return false;
        int d = map.getSurfaceY(x,z) - y;
        return d <= cut && -d <= fill;
    }
}
