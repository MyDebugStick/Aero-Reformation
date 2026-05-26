package dev.simulated_team.aero_reformation.mixin.feature.nav_inverted;

import dev.simulated_team.aero_reformation.feature.nav_inverted.INavTableAccessor;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.NavigationTarget;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NavTableBlockEntity.class, remap = false)
public class NavTableBlockEntityMixin implements INavTableAccessor {

    @Unique
    private boolean aero_reformation$inverted = false;

    /**
     * Check if this navigation table has inverted redstone output enabled.
     */
    @Unique
    public boolean aero_reformation$isInverted() {
        return this.aero_reformation$inverted;
    }

    /**
     * Toggle the inverted redstone output mode.
     */
    @Unique
    public void aero_reformation$toggleInverted() {
        this.aero_reformation$inverted = !this.aero_reformation$inverted;
        // Force a neighbor update to recalculate redstone
        NavTableBlockEntity self = (NavTableBlockEntity) (Object) this;
        if (self.getLevel() != null) {
            self.getLevel().updateNeighborsAt(self.getBlockPos(), self.getBlockState().getBlock());
        }
    }

    /**
     * Save the inverted state to NBT.
     */
    @Inject(method = "write", at = @At("TAIL"))
    private void aero_reformation$onWrite(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        tag.putBoolean("AeroReformation_Inverted", this.aero_reformation$inverted);
    }

    /**
     * Read the inverted state from NBT.
     */
    @Inject(method = "read", at = @At("TAIL"))
    private void aero_reformation$onRead(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        this.aero_reformation$inverted = tag.getBoolean("AeroReformation_Inverted");
    }

    /**
     * Modify getRedstoneStrength to invert the output when inverted mode is active.
     * In inverted mode: directions NOT pointing to target = 15, direction pointing to target = 0.
     */
    @Inject(method = "getRedstoneStrength", at = @At("HEAD"), cancellable = true)
    private void aero_reformation$onGetRedstoneStrength(Direction direction, CallbackInfoReturnable<Integer> cir) {
        if (!this.aero_reformation$inverted) {
            return; // Not inverted, use original logic
        }

        NavTableBlockEntity self = (NavTableBlockEntity) (Object) this;

        // If no valid target, all sides output max power (15)
        NavigationTarget nti = self.getNavTableItem();
        if (nti == null || self.getTargetPosition(false) == null) {
            cir.setReturnValue(15);
            return;
        }

        // Calculate the direction-based strength (avoiding potential recursion
        // if NavigationTarget subclasses call back into getRedstoneStrength)
        int originalStrength = nti.calculateSideStrength(self, direction, self.getHeldItem());

        // Invert: if pointing toward target (strength > 0), output 0
        //         if NOT pointing toward target (strength == 0), output 15
        cir.setReturnValue(originalStrength > 0 ? 0 : 15);
    }
}
