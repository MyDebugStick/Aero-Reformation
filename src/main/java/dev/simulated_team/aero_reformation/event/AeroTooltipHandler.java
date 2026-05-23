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

        // 导航台：红石火把
        if (id.equals(ResourceLocation.parse("simulated:navigation_table"))) {
            event.getToolTip().add(AQUA_PREFIX.copy()
                    .append(Component.translatable("aero_reformation.tooltip.nav_table").withStyle(ChatFormatting.AQUA)));
        }

        // 旋转轴承：铁锭（刚度）+ 红石火把（双驱动）+ 精度警告
        if (id.equals(ResourceLocation.parse("simulated:swivel_bearing"))) {
            event.getToolTip().add(AQUA_PREFIX.copy()
                    .append(Component.translatable("aero_reformation.tooltip.swivel.stiffness").withStyle(ChatFormatting.AQUA)));
            event.getToolTip().add(AQUA_PREFIX.copy()
                    .append(Component.translatable("aero_reformation.tooltip.swivel.swap").withStyle(ChatFormatting.AQUA)));
            event.getToolTip().add(WARN_PREFIX.copy()
                    .append(Component.translatable("aero_reformation.tooltip.swivel.warn").withStyle(ChatFormatting.AQUA)));
        }

        // 陀螺仪：红石火把
        if (id.equals(ResourceLocation.parse("simulated:gimbal_sensor"))) {
            event.getToolTip().add(AQUA_PREFIX.copy()
                    .append(Component.translatable("aero_reformation.tooltip.gimbal").withStyle(ChatFormatting.AQUA)));
        }

        // 红石扭簧：红石信号描述
        if (id.equals(ResourceLocation.parse("aero_reformation:redstone_spring"))) {
            event.getToolTip().add(AQUA_PREFIX.copy()
                    .append(Component.translatable("aero_reformation.tooltip.redstone_spring").withStyle(ChatFormatting.AQUA)));
        }

        // 浮空水晶：精准采集金镐可挖掘掉落
        if (id.equals(ResourceLocation.parse("aeronautics:levitite"))
                || id.equals(ResourceLocation.parse("aeronautics:pearlescent_levitite"))) {
            event.getToolTip().add(AQUA_PREFIX.copy()
                    .append(Component.translatable("aero_reformation.tooltip.levitite").withStyle(ChatFormatting.AQUA)));
        }
    }
}
