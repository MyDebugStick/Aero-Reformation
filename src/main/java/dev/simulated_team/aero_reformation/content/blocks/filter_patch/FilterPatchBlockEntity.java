package dev.simulated_team.aero_reformation.content.blocks.filter_patch;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class FilterPatchBlockEntity extends SmartBlockEntity {
    private FilteringBehaviour filtering;

    public FilterPatchBlockEntity(BlockPos pos, BlockState state) {
        super(AeroBlocks.FILTER_PATCH_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        filtering = new FilteringBehaviour(this, new FilterPatchSlotPositioning());
        filtering.showCountWhen(() -> false);
        behaviours.add(filtering);
    }

    /** Get the current filter stack (or EMPTY). */
    public ItemStack getFilterStack() {
        return filtering.getFilter();
    }

    /** Set a new filter stack. */
    public void setFilterStack(ItemStack stack) {
        filtering.setFilter(stack);
    }
}
