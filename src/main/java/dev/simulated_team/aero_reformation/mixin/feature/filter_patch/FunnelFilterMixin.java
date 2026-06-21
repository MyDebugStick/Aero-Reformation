package dev.simulated_team.aero_reformation.mixin.feature.filter_patch;

import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import dev.simulated_team.aero_reformation.content.blocks.filter_patch.FilterPatchHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts {@code AbstractFunnelBlock.tryInsert} — the central insertion
 * point for ALL Create funnels (both andesite collect and brass extract).
 * <p>
 * Check filter patches on the container BEHIND the funnel (opposite the
 * funnel's facing direction).
 */
@Mixin(AbstractFunnelBlock.class)
public class FunnelFilterMixin {

    @Inject(method = "tryInsert", at = @At("HEAD"), cancellable = true, remap = false)
    private static void aero$filterFunnelInsert(Level level, BlockPos pos, ItemStack toInsert,
                                                  boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (!dev.simulated_team.aero_reformation.config.AeroReformationConfig.filterPatchEnabled) return;
        if (level.isClientSide || simulate) return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AbstractFunnelBlock funnelBlock)) return;
        Direction facing = AbstractFunnelBlock.getFunnelFacing(state);
        if (facing == null) return;

        // Container is on the opposite side of the funnel's facing
        BlockPos containerPos = pos.relative(facing.getOpposite());
        if (!FilterPatchHandler.canInsert(level, containerPos, toInsert)) {
            cir.setReturnValue(toInsert);
        }
    }
}
