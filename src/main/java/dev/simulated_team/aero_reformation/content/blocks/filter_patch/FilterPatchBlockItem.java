package dev.simulated_team.aero_reformation.content.blocks.filter_patch;

import dev.simulated_team.aero_reformation.config.AeroReformationConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class FilterPatchBlockItem extends BlockItem {
    public FilterPatchBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (!AeroReformationConfig.filterPatchEnabled) {
            tooltip.add(Component.translatable("tooltip.aero_reformation.filter_patch.disabled")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        tooltip.add(Component.translatable("tooltip.aero_reformation.filter_patch.line1")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.aero_reformation.filter_patch.line2")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.aero_reformation.filter_patch.line3")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.aero_reformation.filter_patch.line4")
                .withStyle(ChatFormatting.AQUA));
    }
}
