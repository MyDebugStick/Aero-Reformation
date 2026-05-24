package dev.simulated_team.aero_reformation.event;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import static dev.simulated_team.aero_reformation.AeroReformation.MODID;

@EventBusSubscriber(modid = MODID)
public class AeroTooltipHandler {

    private static final Component AQUA_PREFIX = Component.literal("▶ ").withStyle(ChatFormatting.AQUA);
    private static final Component WARN_PREFIX = Component.literal("⚠ ").withStyle(ChatFormatting.AQUA);

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ResourceLocation id = stack.getItemHolder().getKey().location();

        // Nav Table: Redstone Torch
        if (id.equals(ResourceLocation.parse("simulated:navigation_table"))) {
            event.getToolTip().add(AQUA_PREFIX.copy()
                    .append(Component.translatable("aero_reformation.tooltip.nav_table").withStyle(ChatFormatting.AQUA)));
        }

        // Swivel Bearing: Iron Ingot (stiffness) + Redstone Torch (dual-drive) + precision warning
        if (id.equals(ResourceLocation.parse("simulated:swivel_bearing"))) {
            event.getToolTip().add(AQUA_PREFIX.copy()
                    .append(Component.translatable("aero_reformation.tooltip.swivel.stiffness").withStyle(ChatFormatting.AQUA)));
            event.getToolTip().add(AQUA_PREFIX.copy()
                    .append(Component.translatable("aero_reformation.tooltip.swivel.swap").withStyle(ChatFormatting.AQUA)));
            event.getToolTip().add(WARN_PREFIX.copy()
                    .append(Component.translatable("aero_reformation.tooltip.swivel.warn").withStyle(ChatFormatting.AQUA)));
        }

        // Gimbal Sensor: Redstone Torch
        if (id.equals(ResourceLocation.parse("simulated:gimbal_sensor"))) {
            event.getToolTip().add(AQUA_PREFIX.copy()
                    .append(Component.translatable("aero_reformation.tooltip.gimbal").withStyle(ChatFormatting.AQUA)));
        }

        // Redstone Spring: signal description
        if (id.equals(ResourceLocation.parse("aero_reformation:redstone_spring"))) {
            event.getToolTip().add(AQUA_PREFIX.copy()
                    .append(Component.translatable("aero_reformation.tooltip.redstone_spring").withStyle(ChatFormatting.AQUA)));
        }

        // Levitite: Silk Touch Golden Pickaxe
        if (id.equals(ResourceLocation.parse("aeronautics:levitite"))
                || id.equals(ResourceLocation.parse("aeronautics:pearlescent_levitite"))) {
            event.getToolTip().add(AQUA_PREFIX.copy()
                    .append(Component.translatable("aero_reformation.tooltip.levitite").withStyle(ChatFormatting.AQUA)));
        }

        // Analog Transmission: Redstone Torch toggles linear mode
        if (id.equals(ResourceLocation.parse("simulated:analog_transmission"))) {
            event.getToolTip().add(AQUA_PREFIX.copy()
                    .append(Component.translatable("aero_reformation.tooltip.analog_transmission").withStyle(ChatFormatting.AQUA)));
        }

        // Directional Synchronizer Slave
        if (id.equals(ResourceLocation.parse("aero_reformation:directional_synchronizer_slave"))) {
            event.getToolTip().add(AQUA_PREFIX.copy()
                    .append(Component.translatable("aero_reformation.tooltip.synchronizer_slave").withStyle(ChatFormatting.AQUA)));
        }

        // Directional Synchronizer Master
        if (id.equals(ResourceLocation.parse("aero_reformation:directional_synchronizer_master"))) {
            event.getToolTip().add(AQUA_PREFIX.copy()
                    .append(Component.translatable("aero_reformation.tooltip.synchronizer_master").withStyle(ChatFormatting.AQUA)));
        }

        // Ender Compass
        if (id.equals(ResourceLocation.parse("aero_reformation:ender_compass"))) {
            event.getToolTip().add(AQUA_PREFIX.copy()
                    .append(Component.translatable("aero_reformation.tooltip.ender_compass").withStyle(ChatFormatting.AQUA)));
        }
    }
}
