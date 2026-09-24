package org.mcsettlement.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.mcsettlement.planner.ir.PlanningIR.Plot;
import org.mcsettlement.planner.preset.BuildingPreset;
import org.mcsettlement.planner.preset.BuildingPresetRegistry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * AI-Driven Architectural Generator.
 * Directly engages LLM (or rich thematic procedural fallback) to synthesize 3D block structures
 * customized for each plot's size, elevation, entrance facing, and settlement theme.
 */
public class AiBuildingArchitect {

    public static class BuildingVoxelModel {
        public String buildingName;
        public int sizeX;
        public int sizeY;
        public int sizeZ;
        public String[][][] blocks; // [x][y][z] -> Minecraft Block ID string (e.g. "minecraft:stone_bricks")
    }

    public static BuildingVoxelModel designBuildingForPlot(
            Plot plot,
            String settlementTheme,
            String userPrompt,
            LlmStrategyManager.LlmConfig config) {

        int width = plot.builder.footprintSize[0];
        int depth = plot.builder.footprintSize[1];
        int maxHeight = Math.min(12, plot.builder.heightLimit);
        String facing = plot.entrance.facing;

        // 1. Try remote AI generation if API key is provided
        if (config != null && config.apiKey != null && !config.apiKey.isBlank()) {
            try {
                System.out.printf("[AI Architect] Querying LLM for plot '%s' [%dx%d, theme: %s]...\n",
                        plot.id, width, depth, settlementTheme);
                return queryLlmForBuilding(width, depth, maxHeight, facing, settlementTheme, userPrompt, config);
            } catch (Exception e) {
                System.err.println("[AI Architect] LLM generation failed, falling back to procedural architectural library: " + e.getMessage());
            }
        }

        // 2. High-quality procedural architectural synthesis (Watchtower, Blacksmith, Tavern, Cottage, etc.)
        return synthesizeThematicBuilding(plot, width, depth, maxHeight, facing, settlementTheme, userPrompt);
    }

    private static BuildingVoxelModel queryLlmForBuilding(
            int sizeX, int sizeZ, int sizeY, String facing, String theme, String prompt,
            LlmStrategyManager.LlmConfig config) throws Exception {

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        String systemPrompt = "You are a master Minecraft architect. Design a 3D building fitting within dimensions " +
                sizeX + "x" + sizeZ + " horizontally and height " + sizeY + " vertically. Entrance facing: " + facing + ". " +
                "Theme: " + theme + ". User Prompt: " + prompt + ".\n" +
                "Output ONLY a valid JSON object matching:\n" +
                "{\n" +
                "  \"name\": \"string\",\n" +
                "  \"palette\": {\"C\": \"minecraft:cobblestone\", \"W\": \"minecraft:oak_planks\", \"L\": \"minecraft:oak_log\", \"G\": \"minecraft:glass_pane\", \"S\": \"minecraft:stone_stairs\", \"R\": \"minecraft:spruce_planks\", \".\": \"minecraft:air\"},\n" +
                "  \"layers\": [\n" +
                "    [\"row0_string\", \"row1_string\", ...], // Layer Y=0 from Z=0 to Z=depth-1\n" +
                "    ... // up to layer Y=height-1\n" +
                "  ]\n" +
                "}";

        JsonObject body = new JsonObject();
        body.addProperty("model", config.modelName != null && !config.modelName.isBlank() ? config.modelName : "gpt-4o-mini");
        JsonArray messages = new JsonArray();
        JsonObject sMsg = new JsonObject();
        sMsg.addProperty("role", "system");
        sMsg.addProperty("content", systemPrompt);
        messages.add(sMsg);
        JsonObject uMsg = new JsonObject();
        uMsg.addProperty("role", "user");
        uMsg.addProperty("content", "Generate the building JSON now.");
        messages.add(uMsg);
        body.add("messages", messages);
        body.addProperty("temperature", 0.7);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(config.apiEndpoint.endsWith("/chat/completions") ? config.apiEndpoint : config.apiEndpoint + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey)
                .timeout(Duration.ofSeconds(25))
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("API status " + resp.statusCode());
        }

        JsonObject respJson = JsonParser.parseString(resp.body()).getAsJsonObject();
        String text = respJson.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();

        if (text.contains("```json")) {
            text = text.substring(text.indexOf("```json") + 7);
            text = text.substring(0, text.indexOf("```"));
        } else if (text.contains("```")) {
            text = text.substring(text.indexOf("```") + 3);
            text = text.substring(0, text.indexOf("```"));
        }

        JsonObject parsed = JsonParser.parseString(text.trim()).getAsJsonObject();
        BuildingVoxelModel model = new BuildingVoxelModel();
        model.buildingName = parsed.get("name").getAsString();
        model.sizeX = sizeX;
        model.sizeZ = sizeZ;

        Map<Character, String> palette = new HashMap<>();
        JsonObject palObj = parsed.getAsJsonObject("palette");
        for (String key : palObj.keySet()) {
            palette.put(key.charAt(0), palObj.get(key).getAsString());
        }

        JsonArray layers = parsed.getAsJsonArray("layers");
        model.sizeY = layers.size();
        model.blocks = new String[sizeX][model.sizeY][sizeZ];

        for (int y = 0; y < model.sizeY; y++) {
            JsonArray layerRows = layers.get(y).getAsJsonArray();
            for (int z = 0; z < Math.min(sizeZ, layerRows.size()); z++) {
                String row = layerRows.get(z).getAsString();
                for (int x = 0; x < Math.min(sizeX, row.length()); x++) {
                    char ch = row.charAt(x);
                    model.blocks[x][y][z] = palette.getOrDefault(ch, "minecraft:air");
                }
            }
        }
        return model;
    }

    public static BuildingVoxelModel synthesizeThematicBuilding(
            Plot plot, int width, int depth, int height, String facing, String theme) {
        return synthesizeThematicBuilding(plot, width, depth, height, facing, theme, "");
    }

    public static BuildingVoxelModel synthesizeThematicBuilding(
            Plot plot, int width, int depth, int height, String facing, String theme, String userPrompt) {

        // 1. Try resolving an authentic single-building preset from the registry
        try {
            String primaryTag = (plot != null && plot.tags != null && !plot.tags.isEmpty()) ? plot.tags.get(0) : "residential";
            long seed = (plot != null && plot.builder != null) ? plot.builder.subSeed : 42L;
            BuildingPreset preset = BuildingPresetRegistry.getInstance().resolveBestPreset(userPrompt, primaryTag, theme, seed);
            if (preset != null) {
                // Rotate to match plot entrance facing
                BuildingPreset rotated = preset.rotateToFacing(facing);
                String[][][] grid = rotated.toBlockGrid(theme);

                BuildingVoxelModel model = new BuildingVoxelModel();
                model.buildingName = rotated.name;
                model.sizeX = width;
                model.sizeZ = depth;
                model.sizeY = Math.max(height, rotated.sizeY);
                model.blocks = new String[width][model.sizeY][depth];

                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < model.sizeY; y++) {
                        for (int z = 0; z < depth; z++) {
                            model.blocks[x][y][z] = "minecraft:air";
                        }
                    }
                }

                int offsetX = Math.max(0, (width - rotated.sizeX) / 2);
                int offsetZ = Math.max(0, (depth - rotated.sizeZ) / 2);

                for (int rx = 0; rx < rotated.sizeX && (offsetX + rx) < width; rx++) {
                    for (int ry = 0; ry < rotated.sizeY && ry < model.sizeY; ry++) {
                        for (int rz = 0; rz < rotated.sizeZ && (offsetZ + rz) < depth; rz++) {
                            String b = grid[rx][ry][rz];
                            if (b != null && !b.equals("minecraft:air")) {
                                model.blocks[offsetX + rx][ry][offsetZ + rz] = b;
                            }
                        }
                    }
                }
                return model;
            }
        } catch (Exception e) {
            System.err.println("[AI Architect] Preset synthesis failed, falling back to procedural: " + e.getMessage());
        }

        BuildingVoxelModel model = new BuildingVoxelModel();
        model.sizeX = width;
        model.sizeZ = depth;
        model.sizeY = height;
        model.blocks = new String[width][height][depth];

        // Fill with air by default
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    model.blocks[x][y][z] = "minecraft:air";
                }
            }
        }

        Random rnd = new Random(plot.builder.subSeed);
        int typeChoice = Math.abs(plot.id.hashCode()) % 3;

        String wallMat = "minecraft:cobblestone";
        String logMat = "minecraft:spruce_log";
        String plankMat = "minecraft:spruce_planks";
        String roofMat = "minecraft:dark_oak_stairs";

        if ("nordic_coastal".equals(theme)) {
            wallMat = "minecraft:spruce_planks";
            logMat = "minecraft:dark_oak_log";
            plankMat = "minecraft:oak_planks";
        } else if ("mountain_outpost".equals(theme)) {
            wallMat = "minecraft:stone_bricks";
            logMat = "minecraft:deepslate_bricks";
            plankMat = "minecraft:stone_brick_slab";
        }

        if (typeChoice == 0) {
            // 1. Highland Watchtower / Bastion
            model.buildingName = "Highland Watchtower";
            buildWatchtower(model, width, depth, height, wallMat, logMat);
        } else if (typeChoice == 1) {
            // 2. Blacksmith / Forge Workshop
            model.buildingName = "Artisan Blacksmith";
            buildBlacksmith(model, width, depth, height, wallMat, logMat, plankMat);
        } else {
            // 3. Cozy Alpine Tavern / Cottage
            model.buildingName = "Alpine Cottage";
            buildAlpineCottage(model, width, depth, height, wallMat, logMat, plankMat, facing);
        }

        return model;
    }

    private static void buildWatchtower(BuildingVoxelModel m, int w, int d, int h, String stone, String log) {
        int towerH = Math.min(h, 9);
        for (int y = 0; y < towerH; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean corner = (x == 1 || x == w - 2) && (z == 1 || z == d - 2);
                    boolean wall = (x >= 1 && x <= w - 2) && (z >= 1 && z <= d - 2) &&
                            (x == 1 || x == w - 2 || z == 1 || z == d - 2);

                    if (corner) {
                        m.blocks[x][y][z] = log;
                    } else if (wall) {
                        if (y == towerH - 1 && (x + z) % 2 == 0) {
                            m.blocks[x][y][z] = stone; // Battlements
                        } else if (y < towerH - 1) {
                            m.blocks[x][y][z] = stone;
                        }
                    } else if (y == 0 || y == 4 || y == towerH - 2) {
                        m.blocks[x][y][z] = "minecraft:oak_planks"; // Watchtower floors
                    }
                }
            }
        }
        // Doorway
        m.blocks[w / 2][1][1] = "minecraft:air";
        m.blocks[w / 2][2][1] = "minecraft:air";
    }

    private static void buildBlacksmith(BuildingVoxelModel m, int w, int d, int h, String stone, String log, String plank) {
        int wallH = 4;
        for (int y = 0; y < wallH; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean border = (x == 0 || x == w - 1 || z == 0 || z == d - 1);
                    if (border) {
                        if ((x == 0 || x == w - 1) && (z == 0 || z == d - 1)) {
                            m.blocks[x][y][z] = log;
                        } else if (y == 0 || y == wallH - 1) {
                            m.blocks[x][y][z] = stone;
                        } else if (x == w - 1 && z > 1 && z < d - 2) {
                            m.blocks[x][y][z] = "minecraft:iron_bars"; // Open smithy grating
                        } else {
                            m.blocks[x][y][z] = stone;
                        }
                    } else if (y == 0) {
                        m.blocks[x][y][z] = stone; // Stone floor
                    }
                }
            }
        }
        // Forge Chimney
        for (int y = 0; y < wallH + 3; y++) {
            m.blocks[1][y][1] = "minecraft:bricks";
            m.blocks[2][y][1] = "minecraft:bricks";
        }
        m.blocks[1][wallH + 3][1] = "minecraft:campfire";

        // Roof
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                m.blocks[x][wallH][z] = plank;
            }
        }
    }

    private static void buildAlpineCottage(BuildingVoxelModel m, int w, int d, int h, String stone, String log, String plank, String facing) {
        int wallH = 4;
        for (int y = 0; y < wallH; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
                    boolean wall = (x == 0 || x == w - 1 || z == 0 || z == d - 1);

                    if (corner) {
                        m.blocks[x][y][z] = log;
                    } else if (wall) {
                        if (y == 2 && (x == w / 2 || z == d / 2)) {
                            m.blocks[x][y][z] = "minecraft:glass_pane"; // Windows
                        } else {
                            m.blocks[x][y][z] = (y == 0) ? stone : plank;
                        }
                    } else if (y == 0) {
                        m.blocks[x][y][z] = plank;
                    }
                }
            }
        }

        // Pitched Roof
        int roofBase = wallH;
        for (int x = -1; x <= w; x++) {
            for (int z = -1; z <= d; z++) {
                if (x >= 0 && x < w && z >= 0 && z < d) {
                    m.blocks[x][roofBase][z] = plank;
                    int distToEdge = Math.min(x, w - 1 - x);
                    if (distToEdge >= 1 && roofBase + 1 < h) {
                        m.blocks[x][roofBase + 1][z] = plank;
                    }
                }
            }
        }

        // Doorway
        int doorX = w / 2;
        int doorZ = 0;
        if ("SOUTH".equals(facing)) doorZ = d - 1;
        else if ("WEST".equals(facing)) { doorX = 0; doorZ = d / 2; }
        else if ("EAST".equals(facing)) { doorX = w - 1; doorZ = d / 2; }

        m.blocks[doorX][1][doorZ] = "minecraft:air";
        m.blocks[doorX][2][doorZ] = "minecraft:air";
    }
}
