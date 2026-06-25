package dev.simulated_team.aero_reformation.content.blocks.guidance_warhead;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GuidanceWarheadScreenOpener {
    public static void open(BlockPos pos, float kp, float ki, float kd, float maxSpeed, float sidePower, float maxThrustPN,
                            float brakeCoeff, float proximityRange, float cruiseAltitude, float redstoneRange, float altitudeOffset,
                            int searchMode, float minSearchRange, float maxSearchRange,
                            double manualX, double manualY, double manualZ) {
        Minecraft.getInstance().setScreen(new GuidanceWarheadScreen(pos, kp, ki, kd, maxSpeed, sidePower, maxThrustPN,
                brakeCoeff, proximityRange, cruiseAltitude, redstoneRange, altitudeOffset,
                searchMode, minSearchRange, maxSearchRange, manualX, manualY, manualZ));
    }
}
