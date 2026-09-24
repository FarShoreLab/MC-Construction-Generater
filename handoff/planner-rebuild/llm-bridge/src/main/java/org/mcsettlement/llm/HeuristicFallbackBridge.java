package org.mcsettlement.llm;

import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.terrain.HeightfieldMap.ObstacleType;

/**
 * Offline Heuristic strategy generator when no LLM API key is configured or network is unavailable.
 */
public class HeuristicFallbackBridge {

    public static PlanningIntent generateIntent(HeightfieldMap map, String userPrompt) {
        PlanningIntent intent = new PlanningIntent();

        // 1. Analyze terrain statistics
        int width = map.getWidth();
        int depth = map.getDepth();
        int totalCells = width * depth;

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int waterCells = 0;
        float totalSlope = 0.0f;

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                int y = map.getLocalSurfaceY(x, z);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                totalSlope += map.getLocalSlope(x, z);

                ObstacleType obs = map.getLocalObstacle(x, z);
                if (obs == ObstacleType.WATER || obs == ObstacleType.WATER_DEEP) {
                    waterCells++;
                }
            }
        }

        int relief = maxY - minY;
        float avgSlope = totalSlope / totalCells;
        float waterRatio = (float) waterCells / totalCells;

        // 2. Derive theme and style
        String promptLower = (userPrompt == null) ? "" : userPrompt.toLowerCase();

        if (promptLower.contains("渔村") || promptLower.contains("fish") || waterRatio > 0.15f) {
            intent.settlementTheme = "nordic_coastal";
            intent.roadStyle = "wooden_plank_trail";
            intent.paletteTag = "spruce_and_cobblestone";
            intent.landmarkPlacement = "valley";
            intent.loreDescription = "A weathered coastal haven built along the waterfront with stilt structures and wooden piers.";
        } else if (promptLower.contains("矿") || promptLower.contains("mine") || promptLower.contains("岩") || relief >= 14 || avgSlope > 0.6f) {
            intent.settlementTheme = "mountain_outpost";
            intent.roadStyle = "cobblestone_highway";
            intent.paletteTag = "stone_brick_and_deepslate";
            intent.landmarkPlacement = "ridge";
            intent.loreDescription = "A rugged highland fortress carved into the cliff faces, fortified with heavy stone retaining walls.";
        } else if (promptLower.contains("农") || promptLower.contains("farm") || promptLower.contains("平原")) {
            intent.settlementTheme = "farming_hamlet";
            intent.roadStyle = "dirt_path";
            intent.paletteTag = "oak_and_hay";
            intent.landmarkPlacement = "center";
            intent.loreDescription = "A quiet pastoral hamlet surrounded by fertile fields and gentle contour pathways.";
        } else {
            intent.settlementTheme = "medieval_rustic";
            intent.roadStyle = "gravel_path";
            intent.paletteTag = "oak_and_cobblestone";
            intent.landmarkPlacement = "ridge";
            intent.loreDescription = "An organic settlement naturally weaving through the contours of the landscape.";
        }

        // Target plot count adjusted by area
        intent.targetPlotCount = Math.max(3, Math.min(10, totalCells / 1200));
        intent.roadWidth = 3;

        return intent;
    }
}
