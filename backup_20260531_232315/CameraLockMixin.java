package dev.simulated_team.aero_reformation.mixin.feature.power;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.aero_reformation.content.blocks.power.SeatCameraHandler;
import dev.simulated_team.aero_reformation.content.blocks.power.SeatEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaterniond;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(value = EntitySubLevelRotationHelper.class, remap = false)
public class CameraLockMixin {

    @Inject(method = "getSubLevelInheritedOrientation", at = @At("HEAD"), cancellable = true)
    private static void aero_reformation$extractRollOnly(
            Entity cameraEntity, Function<SubLevel, Pose3dc> poseProvider,
            EntitySubLevelRotationHelper.Type type, CallbackInfoReturnable<Quaterniond> cir) {
        if (type != EntitySubLevelRotationHelper.Type.CAMERA) return;
        if (!(cameraEntity instanceof Player player)) return;
        if (!(player.getVehicle() instanceof SeatEntity seat)) return;
        if (!seat.isCameraLocked()) return;

        if (SeatCameraHandler.skipRollFrame) {
            SeatCameraHandler.skipRollFrame = false;
            return;
        }
        // Full Q through — Sable handles everything
    }
}
