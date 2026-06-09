package dev.simulated_team.aero_reformation.content.blocks.com_offset;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class ComOffsetBlockItem extends BlockItem {
    public ComOffsetBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("block.aero_reformation.com_offset")
                .withStyle(ChatFormatting.RED);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.aero_reformation.com_offset.line1")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.aero_reformation.com_offset.line2")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.aero_reformation.com_offset.line3")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.aero_reformation.com_offset.line4")
                .withStyle(ChatFormatting.YELLOW));
    }
}
