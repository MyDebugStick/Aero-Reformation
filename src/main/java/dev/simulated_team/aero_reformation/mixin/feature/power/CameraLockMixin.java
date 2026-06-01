package dev.simulated_team.aero_reformation.mixin.feature.power;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.aero_reformation.content.blocks.power.SeatEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(value = EntitySubLevelRotationHelper.class, remap = false)
public class CameraLockMixin {

    @Inject(method = "getSubLevelInheritedOrientation", at = @At("HEAD"), cancellable = true)
    private static void aero_reformation$fullQThrough(
            Entity cameraEntity, Function<SubLevel, Pose3dc> poseProvider,
            EntitySubLevelRotationHelper.Type type, CallbackInfoReturnable<Quaterniond> cir) {
        if (type != EntitySubLevelRotationHelper.Type.CAMERA) return;
        if (!(cameraEntity instanceof Player player)) return;
        if (!(player.getVehicle() instanceof SeatEntity seat)) return;
        if (!seat.isCameraLocked()) return;
        // Full Q through for camera lock mode
    }

    @Inject(method = "getSubLevelInheritedOrientation", at = @At("RETURN"), cancellable = true)
    private static void aero_reformation$extractRollOnly(
            Entity cameraEntity, Function<SubLevel, Pose3dc> poseProvider,
            EntitySubLevelRotationHelper.Type type, CallbackInfoReturnable<Quaterniond> cir) {
        if (type != EntitySubLevelRotationHelper.Type.CAMERA) return;
        if (!(cameraEntity instanceof Player player)) return;
        if (!(player.getVehicle() instanceof SeatEntity seat)) return;
        if (!seat.isCameraLocked()) return;
        if (!seat.isRollLocked()) return;

        Quaterniond originalQ = cir.getReturnValue();
        if (originalQ == null) return;

        float baseYaw = seat.getBaseYaw();
        double yawRad = Math.toRadians(-baseYaw);

        Vector3d worldFwd = new Vector3d(0, 0, 1).rotateY(yawRad).rotate(originalQ, new Vector3d()).normalize();
        Vector3d worldUp  = new Vector3d(0, 1, 0).rotateY(yawRad).rotate(originalQ, new Vector3d()).normalize();
        Vector3d expectedUp = new Vector3d(0, 1, 0);
        expectedUp.sub(worldFwd.mul(expectedUp.dot(worldFwd), new Vector3d())).normalize();

        double dot = Math.clamp(worldUp.dot(expectedUp), -1.0, 1.0);
        double rollAngle = Math.acos(dot);
        Vector3d cross = new Vector3d(expectedUp).cross(worldUp);
        if (cross.dot(worldFwd) < 0) rollAngle = -rollAngle;

        Quaterniond rollQ = new Quaterniond().rotateAxis(rollAngle, worldFwd.x, worldFwd.y, worldFwd.z);
        cir.setReturnValue(rollQ);
    }
}
