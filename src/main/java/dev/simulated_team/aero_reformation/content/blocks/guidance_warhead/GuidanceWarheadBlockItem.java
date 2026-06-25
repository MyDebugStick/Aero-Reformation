package dev.simulated_team.aero_reformation.content.blocks.guidance_warhead;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class GuidanceWarheadBlockItem extends BlockItem {
    public GuidanceWarheadBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.aero_reformation.guidance_warhead.desc1")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.aero_reformation.guidance_warhead.desc2")
                .withStyle(ChatFormatting.AQUA));
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(ChatFormatting.AQUA);
    }
}
