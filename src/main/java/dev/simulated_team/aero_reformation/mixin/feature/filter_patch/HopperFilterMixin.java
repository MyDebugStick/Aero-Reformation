package dev.simulated_team.aero_reformation.mixin.feature.filter_patch;

import dev.simulated_team.aero_reformation.content.blocks.filter_patch.FilterPatchHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;

/**
 * Replaces vanilla hopper logic when any of three positions have filter patches:
 * the hopper itself, the input source (above), or the output target.
 * <p>
 * Custom logic: actively scan for matching items, pull one, push one, return
 * non-matching items. No vanilla {@code suckInItems}/{@code ejectItems} calls.
 */
@Mixin(HopperBlockEntity.class)
public class HopperFilterMixin {

    @Shadow
    private int cooldownTime;

    @Inject(method = "tryMoveItems", at = @At("HEAD"), cancellable = true, remap = false)
    private static void aero$filterHopperPush(Level level, BlockPos pos, BlockState state,
                                               HopperBlockEntity hopper, BooleanSupplier shouldWork,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (!dev.simulated_team.aero_reformation.config.AeroReformationConfig.filterPatchEnabled) return;
        if (level.isClientSide) return;

        Direction outputDir = state.getValue(net.minecraft.world.level.block.HopperBlock.FACING);
        BlockPos inputPos = pos.above();
        BlockPos outputPos = pos.relative(outputDir);

        // ── Check if any of the 3 positions have filter patches ──
        boolean selfFiltered  = hasFilterPatches(level, pos);
        boolean inputFiltered = hasFilterPatches(level, inputPos);
        boolean outputFiltered = !outputPos.equals(pos) && hasFilterPatches(level, outputPos);
        if (!selfFiltered && !inputFiltered && !outputFiltered) return; // No filters — use vanilla

        Container source = level.getBlockEntity(inputPos) instanceof Container c ? c : null;
        Container target = level.getBlockEntity(outputPos) instanceof Container c ? c : null;
        // Fallback: support Create Vault and other IItemHandler-only blocks
        IItemHandler targetHandler = target == null
                ? level.getCapability(Capabilities.ItemHandler.BLOCK, outputPos, outputDir.getOpposite())
                : null;
        boolean pulled = false;
        boolean pushed = false;

        // ── Phase 1 (PULL) ──
        if (source != null && !isInventoryFull(hopper)) {
            for (int s = 0; s < source.getContainerSize(); s++) {
                ItemStack srcStack = source.getItem(s);
                if (srcStack.isEmpty()) continue;
                if (inputFiltered && !FilterPatchHandler.canInsert(level, inputPos, srcStack)) continue;
                if (selfFiltered && !FilterPatchHandler.canInsert(level, pos, srcStack)) continue;
                // Try to merge into an existing non-full slot first, then an empty slot
                if (!tryMergeInto(hopper, srcStack.copyWithCount(1))) {
                    // Merge failed — find an empty slot
                    boolean placed = false;
                    for (int i = 0; i < hopper.getContainerSize(); i++) {
                        if (hopper.getItem(i).isEmpty()) {
                            hopper.setItem(i, srcStack.split(1));
                            placed = true;
                            break;
                        }
                    }
                    if (!placed) break; // No space — stop pulling
                } else {
                    srcStack.shrink(1);
                }
                source.setChanged();
                pulled = true;
                break;
            }
        }

        // ── Phase 2 (PUSH) — runs regardless, same as vanilla ejectItems ──
        for (int i = 0; i < hopper.getContainerSize(); i++) {
            ItemStack stack = hopper.getItem(i);
            if (stack.isEmpty()) continue;
            if (outputFiltered && !FilterPatchHandler.canInsert(level, outputPos, stack)) continue;
            if (target != null || targetHandler != null) {
                ItemStack single = stack.copyWithCount(1);
                ItemStack remainder = tryInsert(target, targetHandler, single);
                if (remainder.isEmpty() || remainder.getCount() < single.getCount()) {
                    stack.shrink(1);
                    if (stack.isEmpty()) hopper.setItem(i, ItemStack.EMPTY);
                    pushed = true;
                    break;
                }
            }
        }

        // ── Cooldown: 8gt if either pull or push succeeded ──
        if (pulled || pushed) {
            ((HopperFilterMixin) (Object) hopper).cooldownTime = 8;
        }
        cir.setReturnValue(pulled || pushed);
    }

    private static boolean hasFilterPatches(Level level, BlockPos pos) {
        for (Direction face : Direction.values()) {
            BlockEntity be = level.getBlockEntity(pos.relative(face));
            if (be instanceof dev.simulated_team.aero_reformation.content.blocks.filter_patch.FilterPatchBlockEntity patch) {
                if (patch.getBlockState().getValue(net.minecraft.world.level.block.DirectionalBlock.FACING) != face.getOpposite())
                    continue;
                var fb = com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(
                        patch, com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour.TYPE);
                if (fb != null && !fb.getFilter().isEmpty()) return true;
            }
        }
        return false;
    }

    /** Try to insert into a Container or IItemHandler. Returns the remainder (empty = full success). */
    private static ItemStack tryInsert(Container target, IItemHandler handler, ItemStack stack) {
        if (target != null) {
            boolean merged = tryMergeInto(target, stack);
            if (merged) target.setChanged();
            return merged ? ItemStack.EMPTY : stack;
        }
        if (handler != null) {
            return net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(handler, stack, false);
        }
        return stack;
    }
    private static boolean tryMergeInto(Container target, ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (int s = 0; s < target.getContainerSize(); s++) {
            ItemStack slot = target.getItem(s);
            if (slot.isEmpty()) {
                target.setItem(s, stack.copy());
                return true;
            }
            if (ItemStack.isSameItemSameComponents(slot, stack)
                    && slot.getCount() < slot.getMaxStackSize()) {
                int move = Math.min(slot.getMaxStackSize() - slot.getCount(), stack.getCount());
                if (move <= 0) continue;
                slot.grow(move);
                stack.shrink(move);
                if (stack.isEmpty()) return true;
            }
        }
        return false;
    }

    private static boolean isInventoryFull(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) return false;
            if (stack.getCount() < stack.getMaxStackSize()) return false;
        }
        return true;
    }
}
