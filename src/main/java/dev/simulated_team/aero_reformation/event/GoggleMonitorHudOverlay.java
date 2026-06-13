package dev.simulated_team.aero_reformation.event;

import dev.simulated_team.aero_reformation.content.items.goggle_monitor.GoggleMonitorData;
import dev.simulated_team.aero_reformation.content.items.goggle_monitor.SensorMonitorEntry;
import dev.simulated_team.aero_reformation.network.GoggleMonitorSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;

import static dev.simulated_team.aero_reformation.AeroReformation.MODID;

@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class GoggleMonitorHudOverlay {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        if (GoggleMonitorData.findGoggles(mc.player).isEmpty()) return;
        List<SensorMonitorEntry> entries = GoggleMonitorData.getEntries(mc.player);
        if (entries.isEmpty()) return;

        GuiGraphics g = event.getGuiGraphics();
        int x = mc.getWindow().getGuiScaledWidth() - 130;
        int y = mc.getWindow().getGuiScaledHeight() / 2 - entries.size() * 10;

        for (SensorMonitorEntry e : entries) {
            int[] data = GoggleMonitorSyncPacket.CLIENT_DATA.get(e.pos());
            String value = data != null ? formatValue(e.sensorType(), data) : "--";
            g.drawString(mc.font, Component.literal("§e" + e.name() + ": §a" + value), x, y, 0x55FF55, false);
            y += 14;
        }
        g.drawString(mc.font, Component.literal("§7摘下护目镜以清除绑定"), x, y + 4, 0x55FF55, false);
    }

    private static String formatValue(String type, int[] data) {
        return switch (type) {
            case "altitude_sensor" -> String.format("%.1f", data[0] / 100f);
            case "velocity_sensor" -> String.format("%.1f m/s", data[1] / 100f);
            case "gimbal_sensor" -> String.format("%.1f° %.1f°", data[2] / 100f, data[3] / 100f);
            case "nav_table" -> String.format("目标距离 %.1fm", data[0] / 100f);
            case "redstone_link" -> String.format("信号 %d", data[0]);
            default -> "--";
        };
    }
}
