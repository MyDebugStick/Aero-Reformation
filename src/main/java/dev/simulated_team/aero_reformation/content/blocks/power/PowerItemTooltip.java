package dev.simulated_team.aero_reformation.content.blocks.power;

import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(Dist.CLIENT)
public class PowerItemTooltip {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().is(AeroBlocks.POWER_ITEM.get())) {
            event.getToolTip().add(Component.translatable("tooltip.aero_reformation.power.info")
                    .withStyle(ChatFormatting.AQUA));
            event.getToolTip().add(Component.translatable("tooltip.aero_reformation.power.alt")
                    .withStyle(ChatFormatting.YELLOW));
            event.getToolTip().add(Component.translatable("tooltip.aero_reformation.power.warning")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }
}
