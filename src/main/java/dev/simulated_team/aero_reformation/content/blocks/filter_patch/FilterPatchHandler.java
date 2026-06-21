package dev.simulated_team.aero_reformation.content.blocks.filter_patch;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Checks adjacent filter patches to determine if an item can be inserted.
 */
public class FilterPatchHandler {

    /**
     * Check all filter patches attached to the container at {@code pos}.
     * If any patch has a filter, the item must match at least one filter to be allowed.
     * If no patches have filters, all items are allowed.
     */
    public static boolean canInsert(Level level, BlockPos pos, ItemStack candidate) {
        boolean hasFilter = false;
        boolean anyMatch = false;

        for (Direction face : Direction.values()) {
            BlockPos neighborPos = pos.relative(face);
            BlockEntity be = level.getBlockEntity(neighborPos);
            if (!(be instanceof FilterPatchBlockEntity patch)) continue;
            if (patch.getBlockState().getValue(net.minecraft.world.level.block.DirectionalBlock.FACING) != face.getOpposite())
                continue;

            FilteringBehaviour fb = BlockEntityBehaviour.get(patch, FilteringBehaviour.TYPE);
            if (fb == null) continue;

            ItemStack filterStack = fb.getFilter();
            if (filterStack.isEmpty()) continue;

            hasFilter = true;
            var filter = FilterItemStack.of(filterStack);
            if (filter.test(level, candidate)) {
                anyMatch = true;
                break; // Any matching filter allows the item
            }
        }

        // If no filters are configured, allow everything. Otherwise, require at least one match.
        return !hasFilter || anyMatch;
    }
}
