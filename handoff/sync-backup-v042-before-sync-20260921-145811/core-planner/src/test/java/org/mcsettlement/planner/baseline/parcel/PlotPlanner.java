package org.mcsettlement.planner.baseline.parcel;

import org.mcsettlement.planner.baseline.civil.EarthworkOptimizer;
import org.mcsettlement.planner.baseline.civil.EarthworkOptimizer.OptimizationResult;
import org.mcsettlement.planner.ir.PlanningIR.Plot;
import org.mcsettlement.planner.ir.PlanningIR.RoadEdge;
import org.mcsettlement.planner.ir.PlanningIR.RoadStep;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.terrain.HeightfieldMap.ObstacleType;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans and allocates building parcels along a road network with strict collision separation:
 * Buildings NEVER spawn on or overlap any road corridor.
 */
public class PlotPlanner {

    public static class ParcelConfig {
        public int defaultWidth = 8;
        public int defaultDepth = 8;
        public int roadSetback = 3; // Minimum distance between road edge and building wall
        public int minPlotSpacing = 4;
        public int targetPlotCount = 7;
        public float maxGroundSlope = 0.85f;
        public int maxCutBudget = 4;
        public int maxFillBudget = 4;
    }

    public static List<Plot> planPlotsAlongRoad(HeightfieldMap map, RoadEdge road, ParcelConfig config, long seed) {
        List<RoadEdge> singleRoadList = new ArrayList<>();
        singleRoadList.add(road);
        return planPlotsAlongRoadNetwork(map, singleRoadList, config, seed);
    }

    public static List<Plot> planPlotsAlongRoadNetwork(HeightfieldMap map, List<RoadEdge> roads, ParcelConfig config, long seed) {
        List<Plot> plots = new ArrayList<>();
        java.util.Random rnd = new java.util.Random(seed);

        // Build a strict spatial collision mask for all roads with clearance buffer
        boolean[][] roadClearanceMask = buildRoadClearanceMask(map, roads, config.roadSetback);

        for (RoadEdge road : roads) {
            List<RoadStep> steps = road.steps;
            if (steps.size() < 8) continue;

            int stepInterval = Math.max(6, steps.size() / (config.targetPlotCount + 1));

            for (int i = 3; i < steps.size() - 3; i += stepInterval) {
                if (plots.size() >= config.targetPlotCount) break;

                RoadStep curr = steps.get(i);
                RoadStep prev = steps.get(Math.max(0, i - 2));
                RoadStep next = steps.get(Math.min(steps.size() - 1, i + 2));

                int dx = next.x - prev.x;
                int dz = next.z - prev.z;

                int[][] sideNormals = {
                    {-sign(dz), sign(dx)},
                    {sign(dz), -sign(dx)}
                };

                if (rnd.nextBoolean()) {
                    int[] tmp = sideNormals[0];
                    sideNormals[0] = sideNormals[1];
                    sideNormals[1] = tmp;
                }

                for (int[] normal : sideNormals) {
                    org.mcsettlement.planner.baseline.BaselineBudget.candidate();
                    if (normal[0] == 0 && normal[1] == 0) continue;

                    // Compute plot placement strictly outside the road clearance buffer
                    int offsetDist = (road.width / 2) + config.roadSetback + 1;
                    int centerX = curr.x + normal[0] * (offsetDist + config.defaultWidth / 2);
                    int centerZ = curr.z + normal[1] * (offsetDist + config.defaultDepth / 2);

                    int minX = centerX - config.defaultWidth / 2;
                    int maxX = minX + config.defaultWidth - 1;
                    int minZ = centerZ - config.defaultDepth / 2;
                    int maxZ = minZ + config.defaultDepth - 1;

                    // Strict check: Plot must not overlap ANY road or existing plot
                    if (!isValidPlotLocation(map, minX, minZ, maxX, maxZ, plots, roadClearanceMask, config)) {
                        continue;
                    }

                    // Determine entrance facing towards the road
                    String facing = "NORTH";
                    int entranceX = curr.x + normal[0] * (road.width / 2 + 1);
                    int entranceZ = curr.z + normal[1] * (road.width / 2 + 1);
                    if (normal[0] == 1) facing = "WEST";
                    else if (normal[0] == -1) facing = "EAST";
                    else if (normal[1] == 1) facing = "NORTH";
                    else if (normal[1] == -1) facing = "SOUTH";

                    int entranceY = curr.y;

                    OptimizationResult opt = EarthworkOptimizer.optimizePlotFoundation(
                            map, minX, minZ, maxX, maxZ, entranceY,
                            config.maxCutBudget, config.maxFillBudget
                    );

                    Plot plot = new Plot();
                    plot.id = "plot_" + (plots.size() + 1);
                    plot.tags.add(plots.size() == 0 ? "landmark" : (plots.size() == 1 ? "workshop" : "residential"));

                    plot.polygon2D.add(new int[]{minX, minZ});
                    plot.polygon2D.add(new int[]{maxX, minZ});
                    plot.polygon2D.add(new int[]{maxX, maxZ});
                    plot.polygon2D.add(new int[]{minX, maxZ});

                    plot.elevation.baseElevation = opt.optimalBaseY;
                    plot.elevation.entranceElevation = entranceY;
                    plot.elevation.maxCutDepth = config.maxCutBudget;
                    plot.elevation.maxFillHeight = config.maxFillBudget;

                    plot.entrance.accessPoint = new int[]{entranceX, entranceY, entranceZ};
                    plot.entrance.facing = facing;
                    plot.entrance.connectedEdgeId = road.id;

                    plot.foundation = opt.strategy;
                    plot.builder.footprintSize = new int[]{config.defaultWidth, config.defaultDepth};
                    plot.builder.subSeed = rnd.nextLong();

                    plots.add(plot);
                    break;
                }
            }
        }

        return plots;
    }

    private static boolean[][] buildRoadClearanceMask(HeightfieldMap map, List<RoadEdge> roads, int setback) {
        int w = map.getWidth();
        int d = map.getDepth();
        int minX = map.getMinX();
        int minZ = map.getMinZ();
        boolean[][] mask = new boolean[w][d];

        for (RoadEdge road : roads) {
            int radius = (road.width / 2) + setback;
            for (RoadStep step : road.steps) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int lx = step.x + dx - minX;
                        int lz = step.z + dz - minZ;
                        if (lx >= 0 && lx < w && lz >= 0 && lz < d) {
                            mask[lx][lz] = true;
                        }
                    }
                }
            }
        }
        return mask;
    }

    private static boolean isValidPlotLocation(
            HeightfieldMap map, int minX, int minZ, int maxX, int maxZ,
            List<Plot> existingPlots, boolean[][] roadClearanceMask, ParcelConfig config) {

        if (!map.inBounds(minX, minZ) || !map.inBounds(maxX, maxZ)) return false;

        int minLx = minX - map.getMinX();
        int minLz = minZ - map.getMinZ();
        int maxLx = maxX - map.getMinX();
        int maxLz = maxZ - map.getMinZ();

        // 1. STRICT ROAD COLLISION CHECK: Plot must NEVER intersect the road clearance mask!
        for (int lx = minLx; lx <= maxLx; lx++) {
            for (int lz = minLz; lz <= maxLz; lz++) {
                if (roadClearanceMask[lx][lz]) {
                    return false; // REJECT: overlaps road corridor!
                }
            }
        }

        // 2. Terrain slope and water check
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ObstacleType obs = map.getObstacle(x, z);
                if (obs == ObstacleType.WATER || obs == ObstacleType.WATER_DEEP || obs == ObstacleType.STEEP_CLIFF) {
                    return false;
                }
                if (map.getSlope(x, z) > config.maxGroundSlope) {
                    return false;
                }
            }
        }

        // 3. Spacing to existing plots
        for (Plot p : existingPlots) {
            int pMinX = p.polygon2D.get(0)[0];
            int pMinZ = p.polygon2D.get(0)[1];
            int pMaxX = p.polygon2D.get(2)[0];
            int pMaxZ = p.polygon2D.get(2)[1];

            boolean overlapX = (minX - config.minPlotSpacing <= pMaxX) && (maxX + config.minPlotSpacing >= pMinX);
            boolean overlapZ = (minZ - config.minPlotSpacing <= pMaxZ) && (maxZ + config.minPlotSpacing >= pMinZ);
            if (overlapX && overlapZ) {
                return false;
            }
        }

        return true;
    }

    private static int sign(int val) {
        return Integer.compare(val, 0);
    }
}
