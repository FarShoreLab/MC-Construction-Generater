package org.mcsettlement.planner.preset;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry and discovery service for Single-Building Presets.
 * Automatically loads built-in presets from resources, indexes them by category and tags,
 * and provides intelligent query resolution for settlement planning and in-game placement.
 */
public class BuildingPresetRegistry {

    private static final BuildingPresetRegistry INSTANCE = new BuildingPresetRegistry();

    public static final String[] BUILTIN_PRESET_IDS = {
            "artisan_blacksmith",
            "nordic_cottage",
            "highland_watchtower",
            "country_windmill",
            "adventurer_tavern",
            "village_chapel",
            "lumber_mill",
            "wizard_tower",
            "farmstead_granary",
            "town_hall"
    };

    private final Map<String, BuildingPreset> presets = new LinkedHashMap<>();
    private boolean initialized = false;

    public static BuildingPresetRegistry getInstance() {
        if (!INSTANCE.initialized) {
            synchronized (INSTANCE) {
                if (!INSTANCE.initialized) {
                    INSTANCE.initialize();
                }
            }
        }
        return INSTANCE;
    }

    public synchronized void initialize() {
        presets.clear();
        for (String id : BUILTIN_PRESET_IDS) {
            String path = "/presets/" + id + ".json";
            try (InputStream in = getClass().getResourceAsStream(path)) {
                if (in != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        String json = reader.lines().collect(Collectors.joining("\n"));
                        BuildingPreset preset = BuildingPreset.fromJson(json);
                        if (preset != null && preset.id != null) {
                            presets.put(preset.id, preset);
                        }
                    }
                } else {
                    System.err.println("[BuildingPresetRegistry] Warning: Could not find resource " + path);
                }
            } catch (Exception e) {
                System.err.println("[BuildingPresetRegistry] Failed to load preset from " + path + ": " + e.getMessage());
            }
        }
        initialized = true;
        System.out.printf("[BuildingPresetRegistry] Successfully initialized %d building presets.\n", presets.size());
    }

    public void registerPreset(BuildingPreset preset) {
        if (preset != null && preset.id != null) {
            presets.put(preset.id, preset);
        }
    }

    public BuildingPreset getPreset(String id) {
        return presets.get(id);
    }

    public List<BuildingPreset> getAllPresets() {
        return new ArrayList<>(presets.values());
    }

    public List<BuildingPreset> getPresetsByCategory(String category) {
        if (category == null) return getAllPresets();
        return presets.values().stream()
                .filter(p -> category.equalsIgnoreCase(p.category) || p.tags.contains(category.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Resolves the best building preset based on prompt keywords, plot tag, and settlement theme.
     */
    public BuildingPreset resolveBestPreset(String prompt, String tagOrCategory, String theme, long seed) {
        String lowerPrompt = (prompt != null) ? prompt.toLowerCase() : "";

        // 1. Direct prompt keyword matching
        if (lowerPrompt.contains("铁匠") || lowerPrompt.contains("锻造") || lowerPrompt.contains("blacksmith") || lowerPrompt.contains("forge")) {
            if (presets.containsKey("artisan_blacksmith")) return presets.get("artisan_blacksmith");
        }
        if (lowerPrompt.contains("风车") || lowerPrompt.contains("磨坊") || lowerPrompt.contains("windmill") || lowerPrompt.contains("mill")) {
            if (presets.containsKey("country_windmill")) return presets.get("country_windmill");
        }
        if (lowerPrompt.contains("酒馆") || lowerPrompt.contains("旅馆") || lowerPrompt.contains("客栈") || lowerPrompt.contains("tavern") || lowerPrompt.contains("inn")) {
            if (presets.containsKey("adventurer_tavern")) return presets.get("adventurer_tavern");
        }
        if (lowerPrompt.contains("哨塔") || lowerPrompt.contains("要塞") || lowerPrompt.contains("箭楼") || lowerPrompt.contains("watchtower") || lowerPrompt.contains("bastion")) {
            if (presets.containsKey("highland_watchtower")) return presets.get("highland_watchtower");
        }
        if (lowerPrompt.contains("教堂") || lowerPrompt.contains("礼拜堂") || lowerPrompt.contains("圣堂") || lowerPrompt.contains("chapel") || lowerPrompt.contains("church")) {
            if (presets.containsKey("village_chapel")) return presets.get("village_chapel");
        }
        if (lowerPrompt.contains("法师") || lowerPrompt.contains("巫师") || lowerPrompt.contains("炼金") || lowerPrompt.contains("wizard") || lowerPrompt.contains("alchemist") || lowerPrompt.contains("tower")) {
            if (presets.containsKey("wizard_tower")) return presets.get("wizard_tower");
        }
        if (lowerPrompt.contains("伐木") || lowerPrompt.contains("林场") || lowerPrompt.contains("锯木") || lowerPrompt.contains("lumber") || lowerPrompt.contains("logging")) {
            if (presets.containsKey("lumber_mill")) return presets.get("lumber_mill");
        }
        if (lowerPrompt.contains("谷仓") || lowerPrompt.contains("粮仓") || lowerPrompt.contains("农场") || lowerPrompt.contains("barn") || lowerPrompt.contains("granary") || lowerPrompt.contains("farm")) {
            if (presets.containsKey("farmstead_granary")) return presets.get("farmstead_granary");
        }
        if (lowerPrompt.contains("政厅") || lowerPrompt.contains("市政") || lowerPrompt.contains("议会") || lowerPrompt.contains("town hall") || lowerPrompt.contains("town_hall")) {
            if (presets.containsKey("town_hall")) return presets.get("town_hall");
        }
        if (lowerPrompt.contains("木屋") || lowerPrompt.contains("民居") || lowerPrompt.contains("住宅") || lowerPrompt.contains("cottage") || lowerPrompt.contains("house")) {
            if (presets.containsKey("nordic_cottage")) return presets.get("nordic_cottage");
        }

        // 2. Tag / Category filtering
        List<BuildingPreset> candidates = new ArrayList<>();
        if (tagOrCategory != null && !tagOrCategory.isBlank()) {
            String target = tagOrCategory.toLowerCase();
            for (BuildingPreset p : presets.values()) {
                if (p.category.equalsIgnoreCase(target) || p.tags.stream().anyMatch(t -> t.equalsIgnoreCase(target))) {
                    candidates.add(p);
                }
            }
        }

        if (candidates.isEmpty()) {
            candidates = new ArrayList<>(presets.values());
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Deterministic pick based on seed
        Random rnd = new Random(seed);
        int idx = rnd.nextInt(candidates.size());
        return candidates.get(idx);
    }
}
