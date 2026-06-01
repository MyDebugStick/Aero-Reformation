package dev.simulated_team.aero_reformation.content.blocks.power;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class PowerKeyBindings {
    public static final String CATEGORY = "key.category.aero_reformation.power";

    public static final KeyMapping VIEW_SYNC = new KeyMapping(
            "key.aero_reformation.power.view_sync",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            CATEGORY);

    public static final KeyMapping CAMERA_LOCK = new KeyMapping(
            "key.aero_reformation.power.camera_lock",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            CATEGORY);

    public static final KeyMapping ROLL_LOCK = new KeyMapping(
            "key.aero_reformation.power.roll_lock",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY);

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(VIEW_SYNC);
        event.register(CAMERA_LOCK);
        event.register(ROLL_LOCK);
    }
}
