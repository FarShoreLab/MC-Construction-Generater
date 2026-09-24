package org.mcsettlement.planner.preset;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BuildingPresetTest {

    @BeforeAll
    public static void setup() {
        BuildingPresetRegistry.getInstance().initialize();
    }

    @Test
    public void testAllCorePresetsLoadSuccessfully() {
        BuildingPresetRegistry registry = BuildingPresetRegistry.getInstance();
        List<BuildingPreset> presets = registry.getAllPresets();

        assertEquals(10, presets.size(), "All 10 core presets should be discovered and loaded");

        for (BuildingPreset preset : presets) {
            assertNotNull(preset.id, "Preset ID must not be null");
            assertNotNull(preset.name, "Preset name must not be null");
            assertTrue(preset.sizeX > 0, "sizeX must be > 0: " + preset.id);
            assertTrue(preset.sizeY > 0, "sizeY must be > 0: " + preset.id);
            assertTrue(preset.sizeZ > 0, "sizeZ must be > 0: " + preset.id);
            assertNotNull(preset.entrance, "Entrance spec must not be null");
            assertNotNull(preset.palette, "Palette must not be null");
            assertFalse(preset.layers.isEmpty(), "Layers must not be empty");

            assertEquals(preset.sizeY, preset.layers.size(), "Layer count must equal sizeY for " + preset.id);

            // Verify each layer row has correct width and depth
            for (int y = 0; y < preset.sizeY; y++) {
                List<String> rows = preset.layers.get(y);
                assertEquals(preset.sizeZ, rows.size(), "Row count in layer " + y + " must equal sizeZ for " + preset.id);
                for (int z = 0; z < preset.sizeZ; z++) {
                    String row = rows.get(z);
                    assertEquals(preset.sizeX, row.length(), "Row length in layer " + y + ", row " + z + " must equal sizeX for " + preset.id);
                }
            }

            // Test grid conversion
            String[][][] grid = preset.toBlockGrid(null);
            assertNotNull(grid);
            assertEquals(preset.sizeX, grid.length);
            assertEquals(preset.sizeY, grid[0].length);
            assertEquals(preset.sizeZ, grid[0][0].length);
        }
    }

    @Test
    public void testPresetRotation() {
        BuildingPresetRegistry registry = BuildingPresetRegistry.getInstance();
        BuildingPreset blacksmith = registry.getPreset("artisan_blacksmith");
        assertNotNull(blacksmith);

        assertEquals("SOUTH", blacksmith.entrance.facing);

        // Rotate 90 degrees clockwise: SOUTH -> WEST
        BuildingPreset r90 = blacksmith.rotateClockwise(90);
        assertEquals("WEST", r90.entrance.facing);
        assertEquals(blacksmith.sizeX, r90.sizeZ);
        assertEquals(blacksmith.sizeZ, r90.sizeX);

        // Rotate 180 degrees: SOUTH -> NORTH
        BuildingPreset r180 = blacksmith.rotateClockwise(180);
        assertEquals("NORTH", r180.entrance.facing);

        // Rotate 270 degrees: SOUTH -> EAST
        BuildingPreset r270 = blacksmith.rotateClockwise(270);
        assertEquals("EAST", r270.entrance.facing);

        // Rotate to specific facing directly
        BuildingPreset rNorth = blacksmith.rotateToFacing("NORTH");
        assertEquals("NORTH", rNorth.entrance.facing);
    }

    @Test
    public void testThemeMaterialSubstitution() {
        BuildingPresetRegistry registry = BuildingPresetRegistry.getInstance();
        BuildingPreset cottage = registry.getPreset("nordic_cottage");
        assertNotNull(cottage);

        // Default has spruce_planks
        String[][][] defaultGrid = cottage.toBlockGrid(null);
        boolean hasSpruceDefault = false;
        for (int x = 0; x < cottage.sizeX; x++) {
            for (int y = 0; y < cottage.sizeY; y++) {
                for (int z = 0; z < cottage.sizeZ; z++) {
                    if ("minecraft:spruce_planks".equals(defaultGrid[x][y][z])) {
                        hasSpruceDefault = true;
                        break;
                    }
                }
            }
        }
        assertTrue(hasSpruceDefault);

        // Under medieval_rustic, spruce_planks is replaced with oak_planks
        String[][][] rusticGrid = cottage.toBlockGrid("medieval_rustic");
        boolean hasOakPlanks = false;
        boolean hasSprucePlanks = false;
        for (int x = 0; x < cottage.sizeX; x++) {
            for (int y = 0; y < cottage.sizeY; y++) {
                for (int z = 0; z < cottage.sizeZ; z++) {
                    if ("minecraft:oak_planks".equals(rusticGrid[x][y][z])) {
                        hasOakPlanks = true;
                    }
                    if ("minecraft:spruce_planks".equals(rusticGrid[x][y][z])) {
                        hasSprucePlanks = true;
                    }
                }
            }
        }
        assertTrue(hasOakPlanks, "Theme substitution should introduce oak_planks");
        assertFalse(hasSprucePlanks, "All spruce_planks should have been replaced");
    }

    @Test
    public void testKeywordResolution() {
        BuildingPresetRegistry registry = BuildingPresetRegistry.getInstance();

        assertEquals("artisan_blacksmith", registry.resolveBestPreset("帮我建一个铁匠铺", null, null, 1).id);
        assertEquals("country_windmill", registry.resolveBestPreset("这里需要一个风车磨坊", null, null, 1).id);
        assertEquals("adventurer_tavern", registry.resolveBestPreset("我想来一杯麦酒，去酒馆坐坐", null, null, 1).id);
        assertEquals("highland_watchtower", registry.resolveBestPreset("在山脊上修筑守卫要塞哨塔", null, null, 1).id);
        assertEquals("village_chapel", registry.resolveBestPreset("村庄祈福礼拜堂", null, null, 1).id);
        assertEquals("wizard_tower", registry.resolveBestPreset("法师草药炼金之塔", null, null, 1).id);
        assertEquals("lumber_mill", registry.resolveBestPreset("森林伐木工坊", null, null, 1).id);
        assertEquals("town_hall", registry.resolveBestPreset("小镇政厅藏书阁", null, null, 1).id);
    }
}
