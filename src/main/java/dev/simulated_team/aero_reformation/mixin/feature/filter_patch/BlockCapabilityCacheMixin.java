package dev.simulated_team.aero_reformation.mixin.feature.filter_patch;

import dev.simulated_team.aero_reformation.content.blocks.filter_patch.FilterPatchHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wraps {@link IItemHandler} capabilities returned by {@link BlockCapabilityCache}
 * so that {@code insertItem()} is gated by adjacent filter patch checks.
 * <p>
 * This intercepts ALL modded pipe/funnel/hopper insertions that go through
 * the NeoForge 1.21.1 capability system (Mekanism, Create funnels, etc.).
 */
@Mixin(BlockCapabilityCache.class)
public class BlockCapabilityCacheMixin {

    @Shadow
    private ServerLevel level;

    @Shadow
    private BlockPos pos;

    @Inject(method = "getCapability", at = @At("RETURN"), cancellable = true, remap = false)
    private <T> void aero$wrapItemHandler(CallbackInfoReturnable<T> cir) {
        if (!dev.simulated_team.aero_reformation.config.AeroReformationConfig.filterPatchEnabled) return;
        T cap = cir.getReturnValue();
        if (!(cap instanceof IItemHandler handler)) return;
        if (handler instanceof FilteredHandler) return;

        @SuppressWarnings("unchecked")
        T wrapped = (T) new FilteredHandler(level, pos, handler);
        cir.setReturnValue(wrapped);
    }

    /** IItemHandler wrapper that checks filter patches on insertItem. */
    private static class FilteredHandler implements IItemHandler {
        private final Level level;
        private final BlockPos pos;
        private final IItemHandler delegate;

        FilteredHandler(Level level, BlockPos pos, IItemHandler delegate) {
            this.level = level;
            this.pos = pos;
            this.delegate = delegate;
        }

        @Override public int getSlots() { return delegate.getSlots(); }
        @Override public @NotNull ItemStack getStackInSlot(int s) { return delegate.getStackInSlot(s); }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (!FilterPatchHandler.canInsert(level, pos, stack)) return stack;
            return delegate.insertItem(slot, stack, simulate);
        }

        @Override public @NotNull ItemStack extractItem(int s, int a, boolean sim) { return delegate.extractItem(s, a, sim); }
        @Override public int getSlotLimit(int s) { return delegate.getSlotLimit(s); }
        @Override public boolean isItemValid(int s, @NotNull ItemStack stack) {
            return FilterPatchHandler.canInsert(level, pos, stack) && delegate.isItemValid(s, stack);
        }
    }
}
