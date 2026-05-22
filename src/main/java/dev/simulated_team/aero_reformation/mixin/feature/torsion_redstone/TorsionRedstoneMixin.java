package dev.simulated_team.aero_reformation.mixin.feature.torsion_redstone;

import dev.simulated_team.aero_reformation.feature.torsion_redstone.ITorsionRedstoneAccessor;
import dev.simulated_team.simulated.content.blocks.torsion_spring.TorsionSpringBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds redstone-controlled angle mode to the torsion spring.
 * When enabled, the rotation angle = (signal / 15) * configuredAngle.
 * Signal 0 = no rotation.
 * Direction always follows the input shaft (parent speed sign), never flips.
 */
@Mixin(value = TorsionSpringBlockEntity.Output.class, remap = false)
public abstract class TorsionRedstoneMixin implements ITorsionRedstoneAccessor {

    @Unique
    private boolean aero_reformation$redstoneMode = false;

    /**
     * NOTE: Depends on Output.parent field name. If Aeronautics renames this field,
     * this accessor will fail at runtime and needs to be updated.
     */
    @Accessor("parent")
    abstract TorsionSpringBlockEntity aero_reformation$getParent();

    @Override
    public boolean aero_reformation$isRedstoneMode() {
        return this.aero_reformation$redstoneMode;
    }

    @Override
    public void aero_reformation$toggleRedstoneMode() {
        this.aero_reformation$redstoneMode = !this.aero_reformation$redstoneMode;
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void aero_reformation$onWrite(CompoundTag tag, net.minecraft.core.HolderLookup.Provider regs,
                                           boolean clientPacket, CallbackInfo ci) {
        tag.putBoolean("AeroRef_TorsionRedstone", this.aero_reformation$redstoneMode);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void aero_reformation$onRead(CompoundTag tag, net.minecraft.core.HolderLookup.Provider regs,
                                          boolean clientPacket, CallbackInfo ci) {
        if (tag.contains("AeroRef_TorsionRedstone"))
            this.aero_reformation$redstoneMode = tag.getBoolean("AeroRef_TorsionRedstone");
    }

    /**
     * Modify the targetAngle passed to beginTurnTo.
     * Key fix: use parent.getSpeed() sign (input shaft) instead of lastSpringSpeed sign
     * (which may have been flipped by a previous overshoot reversal).
     */
    @ModifyArg(method = "tick",
               at = @At(value = "INVOKE",
                        target = "Ldev/simulated_team/simulated/content/blocks/torsion_spring/TorsionSpringBlockEntity$Output;beginTurnTo(D)V",
                        ordinal = 1),
               index = 0)
    private double aero_reformation$modifyBeginTurnTarget(double targetAngle) {
        if (!this.aero_reformation$redstoneMode) return targetAngle;

        TorsionSpringBlockEntity.Output self = (TorsionSpringBlockEntity.Output) (Object) this;
        Level level = self.getLevel();
        if (level == null) return targetAngle;

        BlockPos pos = self.getBlockPos();
        int signal = aero_reformation$getMaxNeighborSignal(level, pos);
        if (signal == 0) return 0;

        // Use input shaft direction, not lastSpringSpeed (which may be stale/flipped)
        TorsionSpringBlockEntity parent = aero_reformation$getParent();
        float inputDir = Math.signum(parent.getSpeed());
        if (inputDir == 0) return 0;

        // |targetAngle| = configured angle (absolute), scale by signal/15, apply input direction
        return Math.abs(targetAngle) * signal / 15.0 * inputDir;
    }

    @Unique
    private static int aero_reformation$getMaxNeighborSignal(Level level, BlockPos pos) {
        int max = 0;
        for (Direction dir : Direction.values()) {
            max = Math.max(max, level.getSignal(pos.relative(dir), dir));
        }
        return max;
    }
}
