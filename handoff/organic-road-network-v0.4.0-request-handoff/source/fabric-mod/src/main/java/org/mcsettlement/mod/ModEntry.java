package org.mcsettlement.mod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Fabric Mod Entrypoint.
 */
public class ModEntry implements ModInitializer {
    public static final String MOD_ID = "mcsettlement";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[MCSettlement] Initializing Terrain-Adaptive Settlement Generator Mod...");
        ModConfig.load();
        org.mcsettlement.mod.command.PresetCommands.register();
    }
}
