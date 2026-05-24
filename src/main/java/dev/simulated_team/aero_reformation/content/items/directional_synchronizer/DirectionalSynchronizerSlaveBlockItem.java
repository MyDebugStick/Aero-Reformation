package dev.simulated_team.aero_reformation.content.items.directional_synchronizer;

import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public class DirectionalSynchronizerSlaveBlockItem extends BlockItem {
    public DirectionalSynchronizerSlaveBlockItem(Properties properties) {
        super(AeroBlocks.DIRECTIONAL_SYNCHRONIZER_SLAVE.get(), properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(AeroDataComponents.BOUND_MASTER.get());
    }
}
