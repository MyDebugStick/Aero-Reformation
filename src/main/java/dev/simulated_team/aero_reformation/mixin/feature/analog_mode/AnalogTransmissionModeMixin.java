package dev.simulated_team.aero_reformation.mixin.feature.analog_mode;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.simulated_team.aero_reformation.feature.analog_mode.IAnalogModeAccessor;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Linear mode: right-click with redstone torch to toggle.
 * Decelerate: 1 - signal/16
 * Accelerate: 1 + signal/16
 */
@Mixin(value = AnalogTransmissionBlockEntity.class, remap = false)
public class AnalogTransmissionModeMixin implements IAnalogModeAccessor {

    @Unique
    private boolean aero_reformation$linearMode = false;

    @Override
    public boolean aero_reformation$isLinearMode() {
        return this.aero_reformation$linearMode;
    }

    @Override
    public void aero_reformation$toggleLinearMode() {
        this.aero_reformation$linearMode = !this.aero_reformation$linearMode;
        AnalogTransmissionBlockEntity self = (AnalogTransmissionBlockEntity) (Object) this;
        if (self.getLevel() != null && !self.getLevel().isClientSide()) {
            self.getLevel().updateNeighborsAt(self.getBlockPos(), self.getBlockState().getBlock());
        }
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void aero_reformation$onWrite(CompoundTag tag, net.minecraft.core.HolderLookup.Provider regs,
                                          boolean cp, CallbackInfo ci) {
        tag.putBoolean("AeroRef_AnalogLinear", this.aero_reformation$linearMode);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void aero_reformation$onRead(CompoundTag tag, net.minecraft.core.HolderLookup.Provider regs,
                                          boolean cp, CallbackInfo ci) {
        this.aero_reformation$linearMode = tag.getBoolean("AeroRef_AnalogLinear");
    }

    @Inject(method = "propagateRotationTo", at = @At("RETURN"), cancellable = true)
    private void aero_reformation$linearPropagation(
            KineticBlockEntity target,
            BlockState stateFrom,
            BlockState stateTo,
            BlockPos diff,
            boolean connectedViaAxes,
            boolean connectedViaCogs,
            CallbackInfoReturnable<Float> cir) {

        if (!this.aero_reformation$linearMode) return;

        AnalogTransmissionBlockEntity self = (AnalogTransmissionBlockEntity) (Object) this;

        int signal = self.getLevel() != null
                ? self.getLevel().getBestNeighborSignal(self.getBlockPos())
                : 0;
        if (signal == 0) return;

        float modifier;
        if (target == self.getExtraKinetics()) {
            modifier = 1.0f - signal / 16.0f;
        } else if (target == self) {
            modifier = 1.0f + signal / 16.0f;

            float extraSpeed = Math.abs(self.getExtraKinetics().getTheoreticalSpeed());
            if (extraSpeed > 0) {
                float maxMod = AllConfigs.server().kinetics.maxRotationSpeed.get() / extraSpeed;
                if (modifier > maxMod) modifier = maxMod;
            }
        } else {
            return;
        }

        cir.setReturnValue(modifier);
    }
}
