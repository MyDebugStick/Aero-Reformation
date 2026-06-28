package dev.simulated_team.aero_reformation.mixin.radar_fix;

import com.happysg.radar.block.radar.behavior.RadarScanningBlockBehavior;
import dev.simulated_team.aero_reformation.config.AeroReformationConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Optimizes Create Radar block entity scanning by increasing AABB split size.
 * Controlled by AeroReformationConfig.radarScanSplitSize.
 */
@Mixin(value = RadarScanningBlockBehavior.class, remap = false)
public abstract class RadarScanSplitMixin {

    @ModifyArg(method = "scanForEntityTracks", at = @At(value = "INVOKE",
            target = "Lcom/happysg/radar/block/radar/behavior/RadarScanningBlockBehavior;splitAABB(Lnet/minecraft/world/phys/AABB;D)Ljava/util/List;"),
            index = 1)
    private double patchEntitySplit(double size) {
        return AeroReformationConfig.radarEnabled ? (double) AeroReformationConfig.radarScanSplitSize : size;
    }

    @ModifyArg(method = "scanForSableTracks", at = @At(value = "INVOKE",
            target = "Lcom/happysg/radar/block/radar/behavior/RadarScanningBlockBehavior;splitAABB(Lnet/minecraft/world/phys/AABB;D)Ljava/util/List;"),
            index = 1)
    private double patchSableSplit(double size) {
        return AeroReformationConfig.radarEnabled ? (double) AeroReformationConfig.radarScanSplitSize : size;
    }
}
