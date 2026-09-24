package org.mcsettlement.planner;

import org.junit.jupiter.api.Test;
import org.mcsettlement.planner.civil.EarthworkOptimizer;
import org.mcsettlement.planner.civil.EarthworkOptimizer.OptimizationResult;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.RoadStep;
import org.mcsettlement.planner.pathfinding.SlopeCostAStar;
import org.mcsettlement.planner.terrain.HeightfieldMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PlannerTest {

    @Test
    public void testHeightfieldSlopeCalculation() {
        HeightfieldMap map = new HeightfieldMap(0, 0, 10, 10);
        // Create an inclined plane from Y=60 to Y=69
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                map.setLocalSurfaceY(x, z, 60 + x);
            }
        }
        map.computeSlopes();

        // Central cells should have a slope gradient close to 1.0 (1 block rise per 1 block run)
        float midSlope = map.getLocalSlope(5, 5);
        assertTrue(midSlope > 0.8f && midSlope < 1.2f, "Slope should reflect 45 degree incline: " + midSlope);
    }

    @Test
    public void testAStarPathfindingOnSlope() {
        HeightfieldMap map = HeightfieldMap.createSynthetic(0, 0, 64, 64, 60, 16, 42L);

        int startX = 5;
        int startZ = 5;
        int goalX = 50;
        int goalZ = 50;

        List<RoadStep> path = SlopeCostAStar.findRoadPath(map, startX, startZ, goalX, goalZ);
        assertFalse(path.isEmpty(), "Path should successfully find a route across 16-block relief");

        // Verify that steps are connected (adjacent distance <= 1.5 blocks)
        boolean hasStair = false;
        for (int i = 1; i < path.size(); i++) {
            RoadStep prev = path.get(i - 1);
            RoadStep curr = path.get(i);
            int dx = Math.abs(curr.x - prev.x);
            int dz = Math.abs(curr.z - prev.z);
            assertTrue(dx <= 1 && dz <= 1, "Adjacent steps must be contiguous");

            int dy = Math.abs(curr.y - prev.y);
            assertTrue(dy <= 2, "Path must not have impassable cliff jumps (dy <= 2): " + dy);
            if ("stair".equals(curr.structure)) {
                hasStair = true;
            }
        }
        assertTrue(hasStair, "16-block relief path should contain stair segments for slope traversal");
    }

    @Test
    public void testEarthworkOptimization() {
        HeightfieldMap map = new HeightfieldMap(0, 0, 10, 10);
        // A slope within a 5x5 parcel from Y=60 to Y=64
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                map.setLocalSurfaceY(x, z, 60 + (x / 2));
            }
        }

        OptimizationResult res = EarthworkOptimizer.optimizePlotFoundation(map, 2, 2, 6, 6, 62, 3, 3);
        assertNotNull(res);
        assertTrue(res.optimalBaseY >= 60 && res.optimalBaseY <= 63, "Optimal base should be balanced within parcel elevation");
        assertTrue(res.cutVolume >= 0 && res.fillVolume >= 0);
    }

    @Test
    public void testEndToEndSettlementPlanningAndIRSerialization() {
        HeightfieldMap map = HeightfieldMap.createSynthetic(0, 0, 80, 80, 64, 15, 999L);
        SettlementPlanner.PlanRequest req = new SettlementPlanner.PlanRequest();
        req.seed = 999L;
        req.targetPlots = 4;

        PlanningIR ir = SettlementPlanner.plan(map, req);
        assertNotNull(ir);
        assertFalse(ir.transportNetwork.edges.isEmpty(), "Must generate a primary road");
        assertFalse(ir.plots.isEmpty(), "Must allocate plots along the road");
        assertTrue(ir.metadata.score.get("total_score") > 0.0, "Score should be positive");

        // Test serialization round-trip
        String json = ir.toJson(true);
        assertNotNull(json);
        assertTrue(json.contains("e_main"));

        PlanningIR deserialized = PlanningIR.fromJson(json);
        assertEquals(ir.metadata.randomSeed, deserialized.metadata.randomSeed);
        assertEquals(ir.plots.size(), deserialized.plots.size());
        assertEquals(ir.transportNetwork.edges.size(), deserialized.transportNetwork.edges.size());
    }
}
