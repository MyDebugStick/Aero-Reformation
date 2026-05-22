package dev.simulated_team.aero_reformation.mixin.feature.gimbal_inverted;

import dev.simulated_team.aero_reformation.feature.gimbal_inverted.IGimbalInvertedAccessor;
import dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GimbalSensorBlockEntity.class, remap = false)
public class GimbalSensorBEMixin implements IGimbalInvertedAccessor {

    @Unique
    private boolean aero_reformation$inverted = false;

    @Override public boolean aero_reformation$isInverted() { return this.aero_reformation$inverted; }
    @Override public void aero_reformation$toggleInverted() {
        this.aero_reformation$inverted = !this.aero_reformation$inverted;
        GimbalSensorBlockEntity self = (GimbalSensorBlockEntity) (Object) this;
        if (self.getLevel() != null && !self.getLevel().isClientSide())
            self.getLevel().updateNeighborsAt(self.getBlockPos(), self.getBlockState().getBlock());
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void aero_reformation$onWrite(CompoundTag tag, net.minecraft.core.HolderLookup.Provider regs,
                                           boolean cp, CallbackInfo ci) {
        tag.putBoolean("AeroRef_GimbalInverted", this.aero_reformation$inverted);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void aero_reformation$onRead(CompoundTag tag, net.minecraft.core.HolderLookup.Provider regs,
                                          boolean cp, CallbackInfo ci) {
        this.aero_reformation$inverted = tag.getBoolean("AeroRef_GimbalInverted");
    }

    /** Invert output: 15 - original. */
    @Inject(method = "getPower", at = @At("RETURN"), cancellable = true)
    private void aero_reformation$invertPower(Direction dir, CallbackInfoReturnable<Integer> cir) {
        if (this.aero_reformation$inverted) {
            cir.setReturnValue(15 - cir.getReturnValue());
        }
    }
}
