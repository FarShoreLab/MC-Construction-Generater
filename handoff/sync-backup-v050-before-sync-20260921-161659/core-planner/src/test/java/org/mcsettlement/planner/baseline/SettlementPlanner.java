package org.mcsettlement.planner.baseline;

import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.baseline.parcel.PlotPlanner;
import org.mcsettlement.planner.baseline.parcel.PlotPlanner.ParcelConfig;
import org.mcsettlement.planner.baseline.pathfinding.SlopeCostAStar;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.terrain.HeightfieldMap.ObstacleType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Master Settlement Planner.
 * Generates an organic hierarchical road network (Main Road + Mountain Branch + Closed Loop),
 * and allocates plots strictly without any road collisions.
 */
public class SettlementPlanner {

    public static class PlanRequest {
        public long seed = 42;
        public int targetPlots = 7;
        public int roadWidth = 3;
        public String settlementStyle = "medieval_rustic";
        public ParcelConfig parcelConfig = new ParcelConfig();
    }

    public static PlanningIR plan(HeightfieldMap map, PlanRequest request) {
        PlanningIR ir = new PlanningIR();
        ir.metadata.timestamp = Instant.now().toString();
        ir.metadata.randomSeed = request.seed;
        ir.metadata.minBounds = new int[]{map.getMinX(), 0, map.getMinZ()};
        ir.metadata.maxBounds = new int[]{map.getMinX() + map.getWidth() - 1, 256, map.getMinZ() + map.getDepth() - 1};

        // 1. Identify Key Settlement Nodes (Gateway, Central Plaza, High Ridge Overlook, River Landing)
        int[] entry = findBestGateway(map);
        int[] centralPlaza = findBestDestination(map, entry[0], entry[1]);
        int[] ridgeOverlook = findHighRidgeNode(map, centralPlaza[0], centralPlaza[1]);

        RoadNode nodeGate = new RoadNode("n_gate", entry[0], map.getSurfaceY(entry[0], entry[1]), entry[1], "entry");
        RoadNode nodePlaza = new RoadNode("n_plaza", centralPlaza[0], map.getSurfaceY(centralPlaza[0], centralPlaza[1]), centralPlaza[1], "plaza");
        RoadNode nodeRidge = new RoadNode("n_ridge", ridgeOverlook[0], map.getSurfaceY(ridgeOverlook[0], ridgeOverlook[1]), ridgeOverlook[1], "overlook");

        ir.transportNetwork.nodes.add(nodeGate);
        ir.transportNetwork.nodes.add(nodePlaza);
        ir.transportNetwork.nodes.add(nodeRidge);

        // 2. Plan Primary Road Corridor (Gateway -> Plaza)
        List<RoadStep> mainSteps = SlopeCostAStar.findRoadPath(map, entry[0], entry[1], centralPlaza[0], centralPlaza[1]);
        if (mainSteps.isEmpty()) {
            ir.auditLog.warnings.add("Failed to find walkable road connecting entry and plaza due to impassable terrain.");
            return ir;
        }

        RoadEdge primaryRoad = new RoadEdge();
        primaryRoad.id = "e_main";
        primaryRoad.fromNodeId = nodeGate.id;
        primaryRoad.toNodeId = nodePlaza.id;
        primaryRoad.roadType = "primary_road";
        primaryRoad.width = request.roadWidth;
        primaryRoad.steps = mainSteps;
        primaryRoad.maxSlope = computeMaxSlope(mainSteps);
        ir.transportNetwork.edges.add(primaryRoad);

        // 3. Plan Secondary Ridge Branch (from midpoint of main road -> High Ridge Node)
        if (mainSteps.size() >= 12) {
            int juncIdx1 = mainSteps.size() / 3;
            RoadStep juncStep1 = mainSteps.get(juncIdx1);
            RoadNode nodeJunc1 = new RoadNode("n_junc_ridge", juncStep1.x, juncStep1.y, juncStep1.z, "junction");
            ir.transportNetwork.nodes.add(nodeJunc1);

            List<RoadStep> branchSteps = SlopeCostAStar.findRoadPath(map, juncStep1.x, juncStep1.z, ridgeOverlook[0], ridgeOverlook[1]);
            if (!branchSteps.isEmpty()) {
                RoadEdge branchRoad = new RoadEdge();
                branchRoad.id = "e_branch_ridge";
                branchRoad.fromNodeId = nodeJunc1.id;
                branchRoad.toNodeId = nodeRidge.id;
                branchRoad.roadType = "secondary_road";
                branchRoad.width = Math.max(2, request.roadWidth - 1);
                branchRoad.steps = branchSteps;
                branchRoad.maxSlope = computeMaxSlope(branchSteps);
                ir.transportNetwork.edges.add(branchRoad);
            }

            // 4. Plan Closed Loop Road (from High Ridge Node -> another point on main road)
            int juncIdx2 = Math.min(mainSteps.size() - 2, (mainSteps.size() * 3) / 4);
            RoadStep juncStep2 = mainSteps.get(juncIdx2);
            RoadNode nodeJunc2 = new RoadNode("n_junc_loop", juncStep2.x, juncStep2.y, juncStep2.z, "junction");
            ir.transportNetwork.nodes.add(nodeJunc2);

            List<RoadStep> loopSteps = SlopeCostAStar.findRoadPath(map, ridgeOverlook[0], ridgeOverlook[1], juncStep2.x, juncStep2.z);
            if (!loopSteps.isEmpty()) {
                RoadEdge loopRoad = new RoadEdge();
                loopRoad.id = "e_loop";
                loopRoad.fromNodeId = nodeRidge.id;
                loopRoad.toNodeId = nodeJunc2.id;
                loopRoad.roadType = "pathway";
                loopRoad.width = Math.max(2, request.roadWidth - 1);
                loopRoad.steps = loopSteps;
                loopRoad.maxSlope = computeMaxSlope(loopSteps);
                ir.transportNetwork.edges.add(loopRoad);
            }
        }

        // 5. Plan building plots strictly distributed across all roads with zero road collisions
        request.parcelConfig.targetPlotCount = request.targetPlots;
        List<Plot> plots = PlotPlanner.planPlotsAlongRoadNetwork(map, ir.transportNetwork.edges, request.parcelConfig, request.seed);
        ir.plots.addAll(plots);

        // 6. Earthwork aggregation & scoring
        int totalCut = 0;
        int totalFill = 0;
        for (Plot p : plots) {
            int pMinX = p.polygon2D.get(0)[0];
            int pMinZ = p.polygon2D.get(0)[1];
            int pMaxX = p.polygon2D.get(2)[0];
            int pMaxZ = p.polygon2D.get(2)[1];
            int baseY = p.elevation.baseElevation;

            for (int x = pMinX; x <= pMaxX; x++) {
                for (int z = pMinZ; z <= pMaxZ; z++) {
                    int sy = map.getSurfaceY(x, z);
                    if (sy > baseY) totalCut += (sy - baseY);
                    else if (sy < baseY) totalFill += (baseY - sy);
                }
            }
        }

        ir.earthworks.totalCutVolume = totalCut;
        ir.earthworks.totalFillVolume = totalFill;
        ir.earthworks.cutFillBalance = totalCut - totalFill;

        double walkability = 1.0;
        for (RoadEdge edge : ir.transportNetwork.edges) {
            for (RoadStep s : edge.steps) {
                if ("stair".equals(s.structure) || "bridge".equals(s.structure)) {
                    walkability -= 0.003;
                }
            }
        }
        double balanceRatio = (totalCut + totalFill == 0) ? 1.0 :
                1.0 - (Math.abs(totalCut - totalFill) / (double)(totalCut + totalFill));

        ir.metadata.score.put("walkability", Math.max(0.0, walkability));
        ir.metadata.score.put("earthwork_balance", Math.max(0.0, balanceRatio));
        ir.metadata.score.put("plots_allocated", (double) plots.size());
        ir.metadata.score.put("road_network_edges", (double) ir.transportNetwork.edges.size());
        ir.metadata.score.put("total_score", (walkability * 0.4 + balanceRatio * 0.3 + (plots.size() / (double)request.targetPlots) * 0.3) * 100.0);

        return ir;
    }

    private static float computeMaxSlope(List<RoadStep> steps) {
        float maxSlope = 0.0f;
        for (int i = 1; i < steps.size(); i++) {
            float s = Math.abs(steps.get(i).y - steps.get(i - 1).y);
            maxSlope = Math.max(maxSlope, s);
        }
        return maxSlope;
    }

    private static int[] findBestGateway(HeightfieldMap map) {
        int minX = map.getMinX();
        int minZ = map.getMinZ();
        int maxX = minX + map.getWidth() - 1;
        int maxZ = minZ + map.getDepth() - 1;

        int bestX = minX + 5;
        int bestZ = minZ + 5;
        int lowestY = Integer.MAX_VALUE;

        for (int x = minX + 5; x <= maxX - 5; x += 4) {
            int y1 = map.getSurfaceY(x, minZ + 5);
            if (y1 < lowestY && map.getObstacle(x, minZ + 5) == ObstacleType.NONE) {
                lowestY = y1;
                bestX = x;
                bestZ = minZ + 5;
            }
            int y2 = map.getSurfaceY(x, maxZ - 5);
            if (y2 < lowestY && map.getObstacle(x, maxZ - 5) == ObstacleType.NONE) {
                lowestY = y2;
                bestX = x;
                bestZ = maxZ - 5;
            }
        }
        return new int[]{bestX, bestZ};
    }

    private static int[] findBestDestination(HeightfieldMap map, int startX, int startZ) {
        int minX = map.getMinX();
        int minZ = map.getMinZ();
        int maxX = minX + map.getWidth() - 1;
        int maxZ = minZ + map.getDepth() - 1;

        int bestX = (minX + maxX) / 2;
        int bestZ = (minZ + maxZ) / 2;
        double maxScore = -1.0;

        for (int x = minX + 15; x <= maxX - 15; x += 4) {
            for (int z = minZ + 15; z <= maxZ - 15; z += 4) {
                if (map.getObstacle(x, z) != ObstacleType.NONE) continue;
                if (map.getSlope(x, z) > 0.6f) continue;

                double dist = Math.hypot(x - startX, z - startZ);
                int y = map.getSurfaceY(x, z);

                double score = dist * 0.5 + y * 0.5;
                if (score > maxScore) {
                    maxScore = score;
                    bestX = x;
                    bestZ = z;
                }
            }
        }
        return new int[]{bestX, bestZ};
    }

    private static int[] findHighRidgeNode(HeightfieldMap map, int avoidX, int avoidZ) {
        int minX = map.getMinX();
        int minZ = map.getMinZ();
        int maxX = minX + map.getWidth() - 1;
        int maxZ = minZ + map.getDepth() - 1;

        int bestX = maxX - 15;
        int bestZ = maxZ - 15;
        int highestY = Integer.MIN_VALUE;

        for (int x = minX + 12; x <= maxX - 12; x += 4) {
            for (int z = minZ + 12; z <= maxZ - 12; z += 4) {
                if (map.getObstacle(x, z) == ObstacleType.WATER || map.getObstacle(x, z) == ObstacleType.STEEP_CLIFF) continue;
                if (Math.hypot(x - avoidX, z - avoidZ) < 16) continue;

                int y = map.getSurfaceY(x, z);
                if (y > highestY) {
                    highestY = y;
                    bestX = x;
                    bestZ = z;
                }
            }
        }
        return new int[]{bestX, bestZ};
    }
}
