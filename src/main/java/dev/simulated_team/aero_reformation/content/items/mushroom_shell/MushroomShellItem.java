package dev.simulated_team.aero_reformation.content.items.mushroom_shell;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import rbasamoyai.createbigcannons.munitions.FuzedProjectileBlockItem;

import java.util.List;

public class MushroomShellItem extends FuzedProjectileBlockItem {

    public MushroomShellItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("block.aero_reformation.mushroom_shell.tooltip.behaviour1")
                .withStyle(ChatFormatting.GRAY));
    }
}
