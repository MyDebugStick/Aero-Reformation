package dev.simulated_team.aero_reformation.content.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class HudKeyBindings {

    public static final String CATEGORY = "key.categories.aero_reformation";

    public static final KeyMapping HUD_CONFIG_KEY = new KeyMapping(
            "key.aero_reformation.hud_config",
            KeyConflictContext.IN_GAME,
            net.neoforged.neoforge.client.settings.KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY);

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(HUD_CONFIG_KEY);
    }
}
