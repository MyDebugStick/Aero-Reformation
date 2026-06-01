package dev.simulated_team.aero_reformation.mixin.feature.power;

import dev.simulated_team.aero_reformation.content.blocks.power.SeatEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After Sable's $rotateView modifies xRot, force it back to the event pitch (locked mode).
 * Runs AFTER Sable's inject (higher priority).
 */
@Mixin(Camera.class)
public abstract class CameraPitchFixMixin {

    @Shadow private float xRot;
    @Shadow private Entity entity;

    @Inject(method = "setRotation(FFF)V", at = @At("TAIL"))
    private void aero_reformation$fixPitch(float yaw, float pitch, float roll, CallbackInfo ci) {
        if (!(entity != null && entity.getVehicle() instanceof SeatEntity seat)) return;
        if (!seat.isCameraLocked()) return;
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            float targetPitch = -player.getXRot();
            if (entity.level().getGameTime() % 20 == 0) {
                dev.simulated_team.aero_reformation.AeroReformation.LOGGER.info(
                    String.format("[PitchFix] sY=%.1f sP=%.1f sR=%.1f camP=%.1f tgtP=%.1f plP=%.1f plY=%.1f",
                    yaw, pitch, roll, this.xRot, targetPitch, player.getXRot(), player.getYRot()));
            }
            this.xRot = targetPitch;
        }
    }
}
