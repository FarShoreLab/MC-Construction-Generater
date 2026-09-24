package org.mcsettlement.planner.civil;

import org.mcsettlement.planner.ir.PlanningIR.ElevationSpec;
import org.mcsettlement.planner.ir.PlanningIR.FoundationStrategy;
import org.mcsettlement.planner.ir.PlanningIR.RetainingWallSpec;
import org.mcsettlement.planner.terrain.HeightfieldMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Civil engineering optimization for cut-and-fill balance and retaining wall placement.
 */
public class EarthworkOptimizer {

    public static class OptimizationResult {
        public int optimalBaseY;
        public int cutVolume;
        public int fillVolume;
        public FoundationStrategy strategy;
    }

    /**
     * Finds the optimal horizontal foundation elevation Y_base minimizing earthwork and slope exposure.
     */
    public static OptimizationResult optimizePlotFoundation(
            HeightfieldMap map,
            int minX, int minZ, int maxX, int maxZ,
            int roadEntranceY,
            int maxCutBudget, int maxFillBudget) {

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (map.inBounds(x, z)) {
                    int y = map.getSurfaceY(x, z);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (minY == Integer.MAX_VALUE) {
            minY = roadEntranceY;
            maxY = roadEntranceY;
        }

        int bestY = roadEntranceY;
        double minTotalCost = Double.MAX_VALUE;
        int bestCut = 0;
        int bestFill = 0;

        // Search in range [minY, maxY]
        int searchLow = Math.max(minY, roadEntranceY - maxFillBudget);
        int searchHigh = Math.min(maxY, roadEntranceY + maxCutBudget);

        for (int candidateY = searchLow; candidateY <= searchHigh; candidateY++) {
            int cutVol = 0;
            int fillVol = 0;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (map.inBounds(x, z)) {
                        int y = map.getSurfaceY(x, z);
                        if (y > candidateY) {
                            cutVol += (y - candidateY);
                        } else if (y < candidateY) {
                            fillVol += (candidateY - y);
                        }
                    }
                }
            }

            // Cost formula: Cut cost + Fill cost + Earthwork balance deviation + Entrance gap penalty
            double balancePenalty = Math.abs(cutVol - fillVol) * 0.4;
            double entrancePenalty = Math.abs(candidateY - roadEntranceY) * 3.0;
            double totalCost = cutVol * 1.0 + fillVol * 1.2 + balancePenalty + entrancePenalty;

            if (totalCost < minTotalCost) {
                minTotalCost = totalCost;
                bestY = candidateY;
                bestCut = cutVol;
                bestFill = fillVol;
            }
        }

        // Generate retaining walls around the perimeter
        FoundationStrategy strategy = new FoundationStrategy();
        strategy.type = "horizontal_slab_with_retaining_wall";
        strategy.retainingWalls = generateRetainingWalls(map, minX, minZ, maxX, maxZ, bestY);

        OptimizationResult res = new OptimizationResult();
        res.optimalBaseY = bestY;
        res.cutVolume = bestCut;
        res.fillVolume = bestFill;
        res.strategy = strategy;
        return res;
    }

    private static List<RetainingWallSpec> generateRetainingWalls(
            HeightfieldMap map, int minX, int minZ, int maxX, int maxZ, int baseY) {

        List<RetainingWallSpec> walls = new ArrayList<>();

        // Check 4 sides: North (z=minZ), South (z=maxZ), West (x=minX), East (x=maxX)
        checkWallSide(map, minX, minZ - 1, maxX, minZ - 1, "NORTH", baseY, walls);
        checkWallSide(map, minX, maxZ + 1, maxX, maxZ + 1, "SOUTH", baseY, walls);
        checkWallSide(map, minX - 1, minZ, minX - 1, maxZ, "WEST", baseY, walls);
        checkWallSide(map, maxX + 1, minZ, maxX + 1, maxZ, "EAST", baseY, walls);

        return walls;
    }

    private static void checkWallSide(
            HeightfieldMap map, int sx, int sz, int ex, int ez,
            String side, int baseY, List<RetainingWallSpec> outList) {

        int maxOutsideY = baseY;
        int minOutsideY = baseY;
        boolean hasDiscrepancy = false;

        int stepX = Integer.compare(ex, sx);
        int stepZ = Integer.compare(ez, sz);

        int cx = sx;
        int cz = sz;
        while (true) {
            if (map.inBounds(cx, cz)) {
                int oy = map.getSurfaceY(cx, cz);
                if (Math.abs(oy - baseY) >= 1) {
                    hasDiscrepancy = true;
                    maxOutsideY = Math.max(maxOutsideY, oy);
                    minOutsideY = Math.min(minOutsideY, oy);
                }
            }
            if (cx == ex && cz == ez) break;
            cx += (stepX == 0 ? 0 : 1);
            cz += (stepZ == 0 ? 0 : 1);
        }

        if (hasDiscrepancy) {
            RetainingWallSpec wall = new RetainingWallSpec();
            wall.side = side;
            wall.startX = sx;
            wall.startZ = sz;
            wall.endX = ex;
            wall.endZ = ez;
            wall.baseElevation = Math.min(minOutsideY, baseY);
            wall.topElevation = Math.max(maxOutsideY, baseY);
            wall.height = wall.topElevation - wall.baseElevation;
            wall.materialTag = "stone_brick";
            outList.add(wall);
        }
    }
}
