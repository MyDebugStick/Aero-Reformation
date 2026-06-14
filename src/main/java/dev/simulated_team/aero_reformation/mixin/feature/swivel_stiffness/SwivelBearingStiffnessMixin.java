package dev.simulated_team.aero_reformation.mixin.feature.swivel_stiffness;

import dev.simulated_team.aero_reformation.feature.swivel_stiffness.ISwivelStiffnessAccessor;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds adjustable stiffness (kP) level to SwivelBearingBlockEntity.
 *
 * Five levels: 1x (default), 3x (medium), 8x (heavy), 0.5x (soft), 0.25x (light).
 *
 * The setMotor target descriptor uses ConstraintJointAxis (enum) as first param,
 * NOT int: (L...ConstraintJointAxis;DDDZD)V
 */
@Mixin(value = SwivelBearingBlockEntity.class, remap = false)
public class SwivelBearingStiffnessMixin implements ISwivelStiffnessAccessor {

    /** Stiffness (kP) level: 0=1x, 1=3x, 2=8x, 3=0.5x, 4=0.25x */
    @Unique
    private int aero_reformation$stiffnessLevel = 0;

    /** Multipliers for each stiffness level */
    @Unique
    private static final double[] STIFFNESS_MULTIPLIERS = {1.0, 3.0, 8.0, 0.5, 0.25};

    @Override
    public int aero_reformation$getStiffnessLevel() { return this.aero_reformation$stiffnessLevel; }

    @Override
    public void aero_reformation$cycleStiffnessLevel() {
        this.aero_reformation$stiffnessLevel = (this.aero_reformation$stiffnessLevel + 1) % STIFFNESS_MULTIPLIERS.length;
    }

    @Override
    public double aero_reformation$getStiffnessMultiplier() {
        return STIFFNESS_MULTIPLIERS[Math.clamp(this.aero_reformation$stiffnessLevel, 0, STIFFNESS_MULTIPLIERS.length - 1)];
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void aero_reformation$onWrite(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries,
                                           boolean clientPacket, CallbackInfo ci) {
        tag.putInt("AeroRef_SwivelStiffness", this.aero_reformation$stiffnessLevel);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void aero_reformation$onRead(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries,
                                          boolean clientPacket, CallbackInfo ci) {
        this.aero_reformation$stiffnessLevel = tag.getInt("AeroRef_SwivelStiffness");
    }

    /**
     * Multiply kP (stiffness) in the servo setMotor call (ordinal=1).
     * index=2 = kP (0=ConstraintJointAxis, 1=target, 2=stiffness, 3=damping, 4=hasMaxForce, 5=maxForce).
     */
    @ModifyArg(
        method = "updateServoCoefficients",
        at = @At(value = "INVOKE",
                 target = "Ldev/ryanhcode/sable/api/physics/constraint/rotary/RotaryConstraintHandle;setMotor(Ldev/ryanhcode/sable/api/physics/constraint/ConstraintJointAxis;DDDZD)V",
                 ordinal = 1),
        index = 2
    )
    private double aero_reformation$modifyKP(double kP) {
        return kP * this.aero_reformation$getStiffnessMultiplier();
    }
}
