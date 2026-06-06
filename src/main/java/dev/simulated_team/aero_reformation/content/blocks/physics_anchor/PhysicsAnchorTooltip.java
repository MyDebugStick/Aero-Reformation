package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(Dist.CLIENT)
public class PhysicsAnchorTooltip {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!event.getItemStack().is(AeroBlocks.PHYSICS_ANCHOR_ITEM.get())) return;

        event.getToolTip().add(Component.translatable("tooltip.aero_reformation.physics_anchor.load")
                .withStyle(ChatFormatting.AQUA));

        if (Screen.hasShiftDown()) {
            event.getToolTip().add(Component.translatable("tooltip.aero_reformation.physics_anchor.gui")
                    .withStyle(ChatFormatting.AQUA));
            event.getToolTip().add(Component.translatable("tooltip.aero_reformation.physics_anchor.command")
                    .withStyle(ChatFormatting.AQUA));
            event.getToolTip().add(Component.translatable("tooltip.aero_reformation.physics_anchor.dim_warn")
                    .withStyle(ChatFormatting.RED));
        } else {
            event.getToolTip().add(Component.translatable("tooltip.aero_reformation.physics_anchor.shift")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
