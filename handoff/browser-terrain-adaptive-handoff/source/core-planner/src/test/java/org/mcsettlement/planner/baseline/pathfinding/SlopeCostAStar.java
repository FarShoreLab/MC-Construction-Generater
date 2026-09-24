package org.mcsettlement.planner.baseline.pathfinding;

import org.mcsettlement.planner.ir.PlanningIR.RoadStep;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.terrain.HeightfieldMap.ObstacleType;

import java.util.*;

/**
 * Anisotropic Cost Surface A* Pathfinding for terrain-adaptive road networks.
 * Implements Galin et al. contour-preference, slope penalty, and stair/bridge tagging.
 */
public class SlopeCostAStar {
    private static final int[][] DIRS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1},
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private static class Node implements Comparable<Node> {
        int x, z;
        double gCost;
        double fCost;
        Node parent;

        Node(int x, int z, double gCost, double fCost, Node parent) {
            this.x = x;
            this.z = z;
            this.gCost = gCost;
            this.fCost = fCost;
            this.parent = parent;
        }

        @Override
        public int compareTo(Node o) {
            return Double.compare(this.fCost, o.fCost);
        }
    }

    public static List<RoadStep> findRoadPath(HeightfieldMap map, int startX, int startZ, int goalX, int goalZ) {
        int width = map.getWidth();
        int depth = map.getDepth();
        int minX = map.getMinX();
        int minZ = map.getMinZ();

        int startLx = startX - minX;
        int startLz = startZ - minZ;
        int goalLx = goalX - minX;
        int goalLz = goalZ - minZ;

        if (!map.inLocalBounds(startLx, startLz) || !map.inLocalBounds(goalLx, goalLz)) {
            return Collections.emptyList();
        }

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Map<Long, Double> gScores = new HashMap<>();

        long startKey = pack(startLx, startLz);
        double startH = heuristic(map, startLx, startLz, goalLx, goalLz);
        Node startNode = new Node(startLx, startLz, 0.0, startH, null);
        openSet.add(startNode);
        gScores.put(startKey, 0.0);

        Node bestGoalNode = null;

        while (!openSet.isEmpty()) {
            Node curr = openSet.poll();
            org.mcsettlement.planner.baseline.BaselineBudget.path();

            if (curr.x == goalLx && curr.z == goalLz) {
                bestGoalNode = curr;
                break;
            }

            long currKey = pack(curr.x, curr.z);
            if (curr.gCost > gScores.getOrDefault(currKey, Double.MAX_VALUE)) {
                continue;
            }

            int currY = map.getLocalSurfaceY(curr.x, curr.z);

            for (int[] dir : DIRS) {
                int nx = curr.x + dir[0];
                int nz = curr.z + dir[1];

                if (!map.inLocalBounds(nx, nz)) continue;

                ObstacleType obs = map.getLocalObstacle(nx, nz);
                if (obs == ObstacleType.EXISTING_BUILDING) continue;

                int nextY = map.getLocalSurfaceY(nx, nz);
                int deltaY = Math.abs(nextY - currY);

                // Severe cliff jump check (> 3 blocks vertical jump per horizontal block is non-walkable without switchbacks)
                if (deltaY > 3) continue;

                double horizDist = (dir[0] != 0 && dir[1] != 0) ? 1.4142 : 1.0;
                double slope = deltaY / horizDist;

                // Anisotropic cost function:
                // Walking along contour (deltaY == 0) is preferred.
                // Steep slope is heavily penalized.
                double slopeMultiplier = 1.0 + 3.0 * (slope * slope);
                if (deltaY > 1) {
                    slopeMultiplier += 4.0 * deltaY; // Extra penalty for stair step climbs
                }

                double stepCost = horizDist * slopeMultiplier;

                if (obs == ObstacleType.WATER || obs == ObstacleType.WATER_DEEP) {
                    stepCost += 15.0; // Bridging water penalty
                } else if (obs == ObstacleType.TREE_TRUNK) {
                    stepCost += 2.0;  // Tree clearance penalty
                }

                double tentativeG = curr.gCost + stepCost;
                long nextKey = pack(nx, nz);

                if (tentativeG < gScores.getOrDefault(nextKey, Double.MAX_VALUE)) {
                    gScores.put(nextKey, tentativeG);
                    double h = heuristic(map, nx, nz, goalLx, goalLz);
                    Node nextNode = new Node(nx, nz, tentativeG, tentativeG + h, curr);
                    openSet.add(nextNode);
                }
            }
        }

        if (bestGoalNode == null) {
            return Collections.emptyList();
        }

        // Reconstruct path and classify road structures
        List<RoadStep> rawPath = new ArrayList<>();
        Node curr = bestGoalNode;
        while (curr != null) {
            int wx = curr.x + minX;
            int wz = curr.z + minZ;
            int wy = map.getLocalSurfaceY(curr.x, curr.z);

            String structure = "surface";
            ObstacleType obs = map.getLocalObstacle(curr.x, curr.z);
            if (obs == ObstacleType.WATER || obs == ObstacleType.WATER_DEEP) {
                structure = "bridge";
                int waterY = map.getLocalWaterY(curr.x, curr.z);
                wy = Math.max(wy, waterY + 1); // Bridge platform above water
            }

            rawPath.add(new RoadStep(wx, wy, wz, structure));
            curr = curr.parent;
        }

        Collections.reverse(rawPath);

        // Second pass: tag stairs when elevation changes between adjacent steps
        for (int i = 1; i < rawPath.size(); i++) {
            RoadStep prev = rawPath.get(i - 1);
            RoadStep next = rawPath.get(i);
            if (!next.structure.equals("bridge")) {
                if (Math.abs(next.y - prev.y) >= 1) {
                    next.structure = "stair";
                }
            }
        }

        return rawPath;
    }

    private static double heuristic(HeightfieldMap map, int lx, int lz, int gx, int gz) {
        double dx = gx - lx;
        double dz = gz - lz;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        int dy = Math.abs(map.getLocalSurfaceY(gx, gz) - map.getLocalSurfaceY(lx, lz));
        return horiz + dy * 1.5;
    }

    private static long pack(int x, int z) {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }
}
