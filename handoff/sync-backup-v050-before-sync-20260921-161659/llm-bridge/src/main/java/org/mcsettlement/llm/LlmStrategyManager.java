package org.mcsettlement.llm;

import org.mcsettlement.planner.terrain.HeightfieldMap;

/**
 * Unified strategy dispatcher across Antigravity CLI, OpenAI/Gemini API, and Heuristic Fallback.
 */
public class LlmStrategyManager {

    public static class LlmConfig {
        public String apiEndpoint = "https://api.openai.com/v1";
        public String apiKey = "";
        public String modelName = "gpt-4o-mini";
        public boolean preferLocalAgy = true;
    }

    public static PlanningIntent resolveIntent(HeightfieldMap map, String userPrompt, LlmConfig config) {
        int modelCalls = 0;
        // 1. Try local Antigravity CLI if enabled and available
        if (config != null && config.preferLocalAgy && AntigravityCliBridge.isAgyAvailable()) {
            try {
                System.out.println("[LLM] Invoking local Antigravity CLI (agy)...");
                modelCalls++;
                PlanningIntent intent = AntigravityCliBridge.requestIntentViaAgy(map, userPrompt);
                intent.modelCalls = modelCalls;
                return intent;
            } catch (Exception e) {
                System.err.println("[LLM] Antigravity CLI failed, attempting next provider: " + e.getMessage());
            }
        }

        // 2. Try remote OpenAI / Gemini API if Key is provided
        if (config != null && config.apiKey != null && !config.apiKey.isBlank()) {
            try {
                System.out.println("[LLM] Calling remote LLM API (" + config.apiEndpoint + ")...");
                modelCalls++;
                PlanningIntent intent = OpenAiApiBridge.requestIntent(config.apiEndpoint, config.apiKey, config.modelName, map, userPrompt);
                intent.modelCalls = modelCalls;
                return intent;
            } catch (Exception e) {
                System.err.println("[LLM] Remote API call failed, falling back to heuristics: " + e.getMessage());
            }
        }

        // 3. Guaranteed offline heuristic fallback
        System.out.println("[LLM] Using offline heuristic strategy generator.");
        PlanningIntent intent = HeuristicFallbackBridge.generateIntent(map, userPrompt);
        intent.modelCalls = modelCalls;
        return intent;
    }
}
