package org.mcsettlement.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Mod configuration manager.
 * Persists user preferences and LLM API keys in config/mcsettlement.json.
 */
public class ModConfig {
    private static final File CONFIG_FILE = new File("config", "mcsettlement.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String llmApiEndpoint = "https://api.openai.com/v1";
    public String llmApiKey = "";
    public String llmModelName = "gpt-4o-mini";
    public boolean preferLocalAgy = true;
    public String defaultThemePrompt = "依山而建的自然村落，带石质挡土墙与等高线道路";
    public int defaultPlotTarget = 5;
    public int defaultRoadWidth = 3;

    private static ModConfig INSTANCE;

    public static ModConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, ModConfig.class);
                if (INSTANCE != null) return;
            } catch (Exception e) {
                System.err.println("[MCSettlement] Failed to read config: " + e.getMessage());
            }
        }
        INSTANCE = new ModConfig();
        save();
    }

    public static void save() {
        if (INSTANCE == null) return;
        CONFIG_FILE.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            System.err.println("[MCSettlement] Failed to save config: " + e.getMessage());
        }
    }
}
