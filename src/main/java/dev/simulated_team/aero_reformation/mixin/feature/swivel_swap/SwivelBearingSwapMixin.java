package dev.simulated_team.aero_reformation.mixin.feature.swivel_swap;

import dev.simulated_team.aero_reformation.feature.swivel_swap.ISwivelSwapAccessor;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dual-drive mode: the shaft directly drives the bearing target angle.
 * Cogwheel speed is set to 0; targetAngleDegrees is accumulated from shaft speed.
 */
@Mixin(value = SwivelBearingBlockEntity.class, remap = false)
public class SwivelBearingSwapMixin implements ISwivelSwapAccessor {

    @Unique
    private boolean aero_reformation$swapped = false;

    @Override public boolean aero_reformation$isSwapped() { return this.aero_reformation$swapped; }
    @Override public void aero_reformation$toggleSwapped() {
        this.aero_reformation$swapped = !this.aero_reformation$swapped;
        SwivelBearingBlockEntity self = (SwivelBearingBlockEntity) (Object) this;
        if (self.getLevel() != null)
            self.getLevel().updateNeighborsAt(self.getBlockPos(), self.getBlockState().getBlock());
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void aero_reformation$onWrite(CompoundTag tag, net.minecraft.core.HolderLookup.Provider regs,
                                           boolean cp, CallbackInfo ci) {
        tag.putBoolean("AeroRef_SwivelSwapped", this.aero_reformation$swapped);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void aero_reformation$onRead(CompoundTag tag, net.minecraft.core.HolderLookup.Provider regs,
                                          boolean cp, CallbackInfo ci) {
        this.aero_reformation$swapped = tag.getBoolean("AeroRef_SwivelSwapped");
    }

    /** In dual-drive mode, cogwheel speed is zeroed; angle is driven directly by the shaft. */
    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "limitCogSpeed"), index = 0)
    private float aero_reformation$dualDriveSpeed(float original) {
        if (!this.aero_reformation$swapped) return original;
        return ((SwivelBearingBlockEntity) (Object) this).getSpeed();
    }

    /** When enabled, shaft triggers assembly. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void aero_reformation$tickHead(CallbackInfo ci) {
        if (!this.aero_reformation$swapped) return;
        SwivelBearingBlockEntity self = (SwivelBearingBlockEntity) (Object) this;
        if (self.getLevel() == null || self.getLevel().isClientSide()) return;
        if (self.getSpeed() != 0.0f && !self.isAssembled())
            self.assembleNextTick = true;
    }
}
