package org.mcsettlement.planner.civil;

import org.mcsettlement.planner.ir.PlanningIR.FoundationStrategy;
import org.mcsettlement.planner.terrain.HeightfieldMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Civil engineering optimization for cut-and-fill balance and retaining wall placement.
 */
public class EarthworkOptimizer {

    public static class OptimizationResult {
        public boolean feasible;
        public String reason;
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

        OptimizationResult res = new OptimizationResult();
        res.strategy = new FoundationStrategy();
        res.strategy.type = "bounded_slab";
        if (maxCutBudget < 0 || maxFillBudget < 0 || minX > maxX || minZ > maxZ ||
                !map.inBounds(minX, minZ) || !map.inBounds(maxX, maxZ)) {
            res.reason = "INVALID_BOUNDS_OR_BUDGET";
            return res;
        }
        int low = Integer.MIN_VALUE, high = Integer.MAX_VALUE;
        List<Integer> heights = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            int h = map.getSurfaceY(x, z);
            low = Math.max(low, h - maxCutBudget);
            high = Math.min(high, h + maxFillBudget);
            heights.add(h);
        }
        if (low > high) {
            res.reason = "NO_COMMON_ELEVATION_WITHIN_CELL_LIMITS";
            return res;
        }
        // A clamped median minimizes unweighted absolute cut+fill (blocks^3).
        heights.sort(Integer::compareTo);
        int medianLow = heights.get((heights.size() - 1) / 2);
        int medianHigh = heights.get(heights.size() / 2);
        int y = Math.max(low, Math.min(high, Math.max(medianLow, Math.min(medianHigh, roadEntranceY))));
        res.optimalBaseY = y;
        for (int h : heights) {
            res.cutVolume += Math.max(0, h - y);
            res.fillVolume += Math.max(0, y - h);
        }
        res.feasible = true;
        res.reason = "FEASIBLE";
        // No writes outside the footprint. Retaining-wall generation was unbudgeted.
        return res;
    }

}
