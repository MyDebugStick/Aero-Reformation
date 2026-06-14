package dev.simulated_team.aero_reformation.content.blocks.gravity_crystal;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class GravityCrystalBlockItem extends BlockItem {
    public GravityCrystalBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.aero_reformation.gravity_crystal.drag_warning")
                .withStyle(ChatFormatting.DARK_PURPLE));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("block.aero_reformation.gravity_crystal")
                .withStyle(ChatFormatting.LIGHT_PURPLE);
    }
}
