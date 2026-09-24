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
        // 1. Try local Antigravity CLI if enabled and available
        if (config != null && config.preferLocalAgy && AntigravityCliBridge.isAgyAvailable()) {
            try {
                System.out.println("[LLM] Invoking local Antigravity CLI (agy)...");
                return AntigravityCliBridge.requestIntentViaAgy(map, userPrompt);
            } catch (Exception e) {
                System.err.println("[LLM] Antigravity CLI failed, attempting next provider: " + e.getMessage());
            }
        }

        // 2. Try remote OpenAI / Gemini API if Key is provided
        if (config != null && config.apiKey != null && !config.apiKey.isBlank()) {
            try {
                System.out.println("[LLM] Calling remote LLM API (" + config.apiEndpoint + ")...");
                return OpenAiApiBridge.requestIntent(config.apiEndpoint, config.apiKey, config.modelName, map, userPrompt);
            } catch (Exception e) {
                System.err.println("[LLM] Remote API call failed, falling back to heuristics: " + e.getMessage());
            }
        }

        // 3. Guaranteed offline heuristic fallback
        System.out.println("[LLM] Using offline heuristic strategy generator.");
        return HeuristicFallbackBridge.generateIntent(map, userPrompt);
    }
}
