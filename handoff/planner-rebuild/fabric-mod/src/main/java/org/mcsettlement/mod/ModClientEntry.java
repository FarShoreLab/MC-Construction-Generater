package org.mcsettlement.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.mcsettlement.mod.client.gui.TacticalMapScreen;

/**
 * Client-side entrypoint.
 * Binds key 'B' to open the Tactical Bird's-Eye View Screen.
 */
public class ModClientEntry implements ClientModInitializer {
    private static KeyBinding mapKeyBinding;

    @Override
    public void onInitializeClient() {
        mapKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcsettlement.open_map",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.mcsettlement.title"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (mapKeyBinding.wasPressed()) {
                if (client.player != null && client.world != null) {
                    int px = client.player.getBlockX();
                    int pz = client.player.getBlockZ();
                    int halfSize = 48; // 96x96 box centered on player
                    client.setScreen(new TacticalMapScreen(
                            px - halfSize, pz - halfSize,
                            px + halfSize - 1, pz + halfSize - 1
                    ));
                }
            }
        });
    }
}
