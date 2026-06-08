package dev.simulated_team.aero_reformation.content.blocks.gravity_crystal;

import net.minecraft.client.Minecraft;
import java.util.UUID;

/** Client-side only. Opens the gravity crystal settings screen. */
public class GravityCrystalScreenOpener {
    public static void open(UUID subLevelId, GravityCrystalSettings settings) {
        Minecraft.getInstance().setScreen(new GravityCrystalScreen(subLevelId, settings));
    }
}
