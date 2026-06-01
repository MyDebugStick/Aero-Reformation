package dev.simulated_team.aero_reformation.content.blocks.power;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Client-only helper. Called from PowerBlock.useItemOn inside if (level.isClientSide).
 */
public class PowerGuiOpener {

    public static void open(BlockPos pos, Level level) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        int yawMax = 90;
        int pitchMax = 45;
        double seatHeight = 0.0;
        if (level.getBlockEntity(pos) instanceof PowerBlockEntity be) {
            yawMax = be.getYawMax();
            pitchMax = be.getPitchMax();
            seatHeight = be.getSeatHeight();
        }
        mc.setScreen(new PowerConfigScreen(pos, yawMax, pitchMax, seatHeight));
    }
}
