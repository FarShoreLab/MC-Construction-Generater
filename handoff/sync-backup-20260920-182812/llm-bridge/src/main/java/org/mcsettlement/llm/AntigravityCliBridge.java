package org.mcsettlement.llm;

import org.mcsettlement.planner.terrain.HeightfieldMap;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Local Antigravity CLI ('agy') bridge.
 */
public class AntigravityCliBridge {

    public static boolean isAgyAvailable() {
        try {
            Process process = new ProcessBuilder("agy", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static PlanningIntent requestIntentViaAgy(HeightfieldMap map, String userPrompt) throws Exception {
        String prompt = String.format(
                "Generate settlement planning intent for terrain [%dx%d]. User Prompt: '%s'. Return pure JSON matching PlanningIntent fields.",
                map.getWidth(), map.getDepth(), userPrompt
        );

        ProcessBuilder pb = new ProcessBuilder("agy", "--prompt", prompt);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("agy CLI exited with code " + exitCode);
        }

        String res = output.toString().trim();
        if (res.contains("```json")) {
            res = res.substring(res.indexOf("```json") + 7);
            res = res.substring(0, res.indexOf("```"));
        }
        return PlanningIntent.fromJson(res.trim());
    }
}
