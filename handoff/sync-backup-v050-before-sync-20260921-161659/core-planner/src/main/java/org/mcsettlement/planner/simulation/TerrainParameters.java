package org.mcsettlement.planner.simulation;

import java.util.Map;
import java.util.Set;

/**
 * Parameters of the EXISTING four terrain formulas, not a second generator.
 * Immutable and range checked. Defaults reproduce v0.4.1 voxel-for-voxel.
 * The browser/HTTP parameter descriptions live in terrain-controls.json;
 * TerrainControlsMain checks that its ranges and defaults agree with this API.
 */
public record TerrainParameters(
        double horizontalScale, double detailStrength, double warpStrength,
        double slopeStrength, double waterLevelRatio, double treeDensity,
        int treeSpacing, double ridgeSharpness, double valleyWidth, int terraceLevels) {
    public static final Set<String> KEYS = Set.of("horizontalScale", "detailStrength", "warpStrength",
            "slopeStrength", "waterLevelRatio", "treeDensity", "treeSpacing", "ridgeSharpness",
            "valleyWidth", "terraceLevels");

    public TerrainParameters {
        range("horizontalScale", horizontalScale, .25, 4);
        range("detailStrength", detailStrength, 0, 2);
        range("warpStrength", warpStrength, 0, 2);
        range("slopeStrength", slopeStrength, 0, 2);
        range("waterLevelRatio", waterLevelRatio, 0, 1);
        range("treeDensity", treeDensity, 0, 2);
        range("treeSpacing", treeSpacing, 2, 10);
        range("ridgeSharpness", ridgeSharpness, .5, 3);
        range("valleyWidth", valleyWidth, .5, 2);
        range("terraceLevels", terraceLevels, 2, 12);
    }

    private static void range(String key, double value, double low, double high) {
        if (!Double.isFinite(value) || value < low || value > high)
            throw new IllegalArgumentException("INVALID_TERRAIN_PARAMETER: " + key + " must be finite and in [" + low + ", " + high + "]");
    }

    public static TerrainParameters defaults(String terrainType) {
        var type = TerrainBlockGenerator.TerrainType.from(terrainType);
        return new TerrainParameters(1, 1, 1, 1,
                type == TerrainBlockGenerator.TerrainType.VALLEY ? .28 : .22, 1, 4, 1.65, 1, 5);
    }

    /** Omitted keys use the selected type's original defaults; unknown keys are errors. */
    public static TerrainParameters fromOverrides(String terrainType, Map<String, String> values) {
        if (values == null) throw new IllegalArgumentException("Terrain overrides must not be null");
        for (var entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null)
                throw new IllegalArgumentException("Terrain parameter keys and values must not be null");
            if (!KEYS.contains(entry.getKey()))
                throw new IllegalArgumentException("UNKNOWN_TERRAIN_PARAMETER: " + entry.getKey());
        }
        var d = defaults(terrainType);
        return new TerrainParameters(
                number(values, "horizontalScale", d.horizontalScale), number(values, "detailStrength", d.detailStrength),
                number(values, "warpStrength", d.warpStrength), number(values, "slopeStrength", d.slopeStrength),
                number(values, "waterLevelRatio", d.waterLevelRatio), number(values, "treeDensity", d.treeDensity),
                integer(values, "treeSpacing", d.treeSpacing), number(values, "ridgeSharpness", d.ridgeSharpness),
                number(values, "valleyWidth", d.valleyWidth), integer(values, "terraceLevels", d.terraceLevels));
    }
    private static double number(Map<String,String> m, String key, double fallback) {
        return m.containsKey(key) ? Double.parseDouble(m.get(key)) : fallback;
    }
    private static int integer(Map<String,String> m, String key, int fallback) {
        return m.containsKey(key) ? Integer.parseInt(m.get(key)) : fallback;
    }
}
