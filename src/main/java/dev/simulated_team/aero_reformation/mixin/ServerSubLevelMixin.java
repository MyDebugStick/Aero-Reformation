package dev.simulated_team.aero_reformation.mixin;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.aero_reformation.content.blocks.gravity_crystal.GravityCrystalSettings;
import dev.simulated_team.aero_reformation.feature.com_controller.ComShiftHelper;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts the final impulse application to the rigid body.
 * For gravity crystals: scales lift, applies velocity-based damping,
 * and removes position-dependent torque.
 */
@Mixin(value = ServerSubLevel.class, remap = false)
public abstract class ServerSubLevelMixin {

    /** Apply COM offset before physics calculations. */
    @Inject(method = "prePhysicsTick", at = @At("HEAD"), remap = false)
    private void applyComShift(dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem physicsSystem,
                                RigidBodyHandle handle, double timeStep, CallbackInfo ci) {
        ComShiftHelper.applyComShift((ServerSubLevel) (Object) this);
    }

    /** Redirect to apply scaled lift + velocity-based damping. */
    @Redirect(method = "prePhysicsTick",
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;applyLinearAndAngularImpulse(Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;Z)V"),
            remap = false)
    private void applyImpulseWithDamping(RigidBodyHandle handle, Vector3dc linearImpulse,
                                         Vector3dc angularImpulse, boolean wakeUp) {
        GravityCrystalSettings s = getSettings();
        if (s == null) {
            handle.applyLinearAndAngularImpulse(linearImpulse, angularImpulse, wakeUp);
            return;
        }

        // Scale lift, keep angular — crystal torque is already zeroed by FloatingBlockControllerMixin
        Vector3d scaledLin = new Vector3d(linearImpulse).mul(s.liftMultiplier);
        handle.applyLinearAndAngularImpulse(scaledLin, angularImpulse, wakeUp);

        // Extra linear drag
        int comparison = Float.compare(s.dragMultiplier, 1f);
        if (comparison > 0) {
            Vector3d linVel = handle.getLinearVelocity(new Vector3d());
            double dragStr = (s.dragMultiplier - 1f) * 0.05;
            handle.addLinearAndAngularVelocity(
                    new Vector3d(linVel).mul(-dragStr),
                    new Vector3d()
            );
        } else if (comparison < 0) {
            // Below 1: reduce Sable's default drag (counteract a portion)
            Vector3d linVel = handle.getLinearVelocity(new Vector3d());
            double dragStr = (1f - s.dragMultiplier) * 0.05;
            handle.addLinearAndAngularVelocity(
                    new Vector3d(linVel).mul(dragStr),
                    new Vector3d()
            );
        }

        // Angular velocity damping
        double angKeep = Math.pow(0.9, s.angularDragMultiplier);
        Vector3d angVel = handle.getAngularVelocity(new Vector3d());
        handle.addLinearAndAngularVelocity(
                new Vector3d(),
                new Vector3d(angVel).mul(angKeep - 1.0)
        );
    }

    private GravityCrystalSettings getSettings() {
        ServerSubLevel self = (ServerSubLevel) (Object) this;
        if (!GravityCrystalSettings.CRYSTAL_SUBLEVELS.contains(self.getUniqueId())) {
            return null;
        }
        GravityCrystalSettings s = GravityCrystalSettings.get(self.getUniqueId());
        return s.active ? s : null;
    }
}
