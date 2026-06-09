package dev.simulated_team.aero_reformation.mixin;

import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.physics.floating_block.*;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.aero_reformation.content.blocks.gravity_crystal.GravityCrystalSettings;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Only affects aero_reformation:gravity_crystal (scale_with_pressure=true). */
@Mixin(value = FloatingBlockController.class, remap = false)
public abstract class FloatingBlockControllerMixin {

    @Shadow private static Vector3d liftingTorque;
    @Shadow private static Vector3d averageForcePos;
    @Shadow private ServerSubLevel subLevel;
    @Shadow private List<FloatingClusterContainer> containers;
    @Unique private boolean isOurs;

    @Invoker("recordForce")
    abstract void callRecordForce(FloatingClusterContainer c, FloatingBlockCluster cl, QueuedForceGroup fg, Vector3d f);

    private boolean hasOurMaterial() {
        for (FloatingClusterContainer c : containers)
            for (FloatingBlockCluster cl : c.clusters)
                if (cl.getMaterial().scaleWithPressure()) return true;
        return false;
    }

    @Inject(method = "applyLift", at = @At("HEAD"), remap = false)
    private void detectApplyLift(CallbackInfo ci) {
        isOurs = hasOurMaterial();
        if (isOurs) {
            GravityCrystalSettings.CRYSTAL_SUBLEVELS.add(subLevel.getUniqueId());
            GravityCrystalSettings.get(subLevel.getUniqueId()).active = true;
        } else {
            // Clean up stale entry when gravity crystal is removed
            GravityCrystalSettings.CRYSTAL_SUBLEVELS.remove(subLevel.getUniqueId());
        }
    }

    @Inject(method = "physicsTick", at = @At("HEAD"), remap = false)
    private void detectPhysTick(CallbackInfo ci) {
        isOurs = hasOurMaterial();
        if (isOurs) {
            GravityCrystalSettings.CRYSTAL_SUBLEVELS.add(subLevel.getUniqueId());
            GravityCrystalSettings.get(subLevel.getUniqueId()).active = true;
        } else {
            GravityCrystalSettings.CRYSTAL_SUBLEVELS.remove(subLevel.getUniqueId());
        }
    }

    @Redirect(method = "applyLift", at = @At(value = "INVOKE",
            target = "Lorg/joml/Vector3d;div(DLorg/joml/Vector3d;)Lorg/joml/Vector3d;", ordinal = 0), remap = false)
    private Vector3d zeroAvgPos(Vector3d s, double f, Vector3d d) {
        if (isOurs) return d.zero();
        return s.div(f, d);
    }

    @Redirect(method = "applyLift", at = @At(value = "INVOKE",
            target = "Lorg/joml/Vector3d;cross(Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)Lorg/joml/Vector3d;", ordinal = 1), remap = false)
    private Vector3d zeroTorque(Vector3d s, Object v, Object d) {
        if (isOurs) { liftingTorque.zero(); return liftingTorque; }
        return s.cross((Vector3dc) v, (Vector3d) d);
    }

    /** Zero friction torque so drag forces don't create rotation around COM. */
    @ModifyArg(method = "physicsTick", at = @At(value = "INVOKE",
            target = "Lorg/joml/Vector3d;fma(DLorg/joml/Vector3dc;)Lorg/joml/Vector3d;", ordinal = 1), index = 1, remap = false)
    private Vector3dc zeroFrictionTorque(Vector3dc frictionTorque) {
        if (isOurs) return new Vector3d();
        return frictionTorque;
    }

    @Redirect(method = "applyLift", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/floating_block/FloatingBlockController;recordForce(Ldev/ryanhcode/sable/physics/floating_block/FloatingClusterContainer;Ldev/ryanhcode/sable/physics/floating_block/FloatingBlockCluster;Ldev/ryanhcode/sable/api/physics/force/QueuedForceGroup;Lorg/joml/Vector3d;)V", ordinal = 1), remap = false)
    private void recLift(FloatingBlockController self, FloatingClusterContainer c, FloatingBlockCluster cl, QueuedForceGroup fg, Vector3d f) {
        if (!isOurs) { callRecordForce(c, cl, fg, f); return; }
        fg.recordPointForce(subLevel.getMassTracker().getCenterOfMass(), f);
    }

    @Redirect(method = "physicsTick", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/floating_block/FloatingBlockController;recordForce(Ldev/ryanhcode/sable/physics/floating_block/FloatingClusterContainer;Ldev/ryanhcode/sable/physics/floating_block/FloatingBlockCluster;Ldev/ryanhcode/sable/api/physics/force/QueuedForceGroup;Lorg/joml/Vector3d;)V"), remap = false)
    private void recDrag(FloatingBlockController self, FloatingClusterContainer c, FloatingBlockCluster cl, QueuedForceGroup fg, Vector3d f) {
        if (!isOurs) { callRecordForce(c, cl, fg, f); return; }
        fg.recordPointForce(subLevel.getMassTracker().getCenterOfMass(), f);
    }
}



