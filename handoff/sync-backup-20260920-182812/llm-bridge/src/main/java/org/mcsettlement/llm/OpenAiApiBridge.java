package org.mcsettlement.llm;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.mcsettlement.planner.terrain.HeightfieldMap;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Standard LLM API bridge supporting custom API keys (OpenAI / Gemini compatible format).
 */
public class OpenAiApiBridge {

    public static PlanningIntent requestIntent(
            String endpoint, String apiKey, String modelName,
            HeightfieldMap map, String userPrompt) throws Exception {

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        String systemPrompt = "You are a professional architectural planner and game director in Minecraft. " +
                "Given terrain characteristics and player prompt, you determine the settlement design parameters. " +
                "You MUST reply ONLY with a valid JSON object matching these fields:\n" +
                "{\n" +
                "  \"settlementTheme\": \"medieval_rustic\" | \"mountain_outpost\" | \"nordic_coastal\" | \"desert_oasis\",\n" +
                "  \"roadStyle\": \"gravel_path\" | \"cobblestone_highway\" | \"wooden_plank_trail\",\n" +
                "  \"paletteTag\": \"oak_and_cobblestone\" | \"spruce_and_stone\" | \"deepslate_and_brick\",\n" +
                "  \"targetPlotCount\": integer (between 3 and 10),\n" +
                "  \"roadWidth\": integer (between 2 and 4),\n" +
                "  \"landmarkPlacement\": \"ridge\" | \"valley\" | \"center\",\n" +
                "  \"loreDescription\": string\n" +
                "}";

        String userContent = String.format(
                "Terrain: width=%d, depth=%d. User prompt: \"%s\"",
                map.getWidth(), map.getDepth(), (userPrompt == null || userPrompt.isBlank()) ? "Default natural village" : userPrompt
        );

        JsonObject body = new JsonObject();
        body.addProperty("model", (modelName == null || modelName.isBlank()) ? "gpt-4o-mini" : modelName);

        var messages = new com.google.gson.JsonArray();
        JsonObject msg1 = new JsonObject();
        msg1.addProperty("role", "system");
        msg1.addProperty("content", systemPrompt);
        messages.add(msg1);

        JsonObject msg2 = new JsonObject();
        msg2.addProperty("role", "user");
        msg2.addProperty("content", userContent);
        messages.add(msg2);

        body.add("messages", messages);
        body.addProperty("temperature", 0.7);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint.endsWith("/chat/completions") ? endpoint : endpoint + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("LLM API returned status " + response.statusCode() + ": " + response.body());
        }

        JsonObject respJson = JsonParser.parseString(response.body()).getAsJsonObject();
        String content = respJson.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();

        // Extract JSON block if surrounded by markdown fences
        if (content.contains("```json")) {
            content = content.substring(content.indexOf("```json") + 7);
            content = content.substring(0, content.indexOf("```"));
        } else if (content.contains("```")) {
            content = content.substring(content.indexOf("```") + 3);
            content = content.substring(0, content.indexOf("```"));
        }

        return PlanningIntent.fromJson(content.trim());
    }
}
