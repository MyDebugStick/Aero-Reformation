package dev.simulated_team.aero_reformation.content.blocks.gravity_crystal;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class GravityCrystalBlockItem extends BlockItem {
    public GravityCrystalBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("block.aero_reformation.gravity_crystal")
                .withStyle(ChatFormatting.LIGHT_PURPLE);
    }
}
