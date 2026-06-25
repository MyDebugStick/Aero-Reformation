package dev.simulated_team.aero_reformation.content.blocks.gravity_crystal;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class GravityCrystalScreenOpener {
    public static void open(UUID subLevelId, GravityCrystalSettings settings) {
        Minecraft.getInstance().setScreen(new GravityCrystalScreen(subLevelId, settings));
    }
}
