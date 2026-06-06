package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.mixin.feature.physics_anchor.XaeroMapAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import xaero.map.gui.GuiMap;

public class AnchorMapRenderer {

    private static int tickCount = 0;

    public static void render(GuiGraphics g, GuiMap screen, int mX, int mY, float pt) {
        var markers = AnchorMapClientData.getMarkers();
        if (markers.isEmpty()) return;

        double camX = ((XaeroMapAccessor) screen).aero$getCameraX();
        double camZ = ((XaeroMapAccessor) screen).aero$getCameraZ();
        double mapScale = ((XaeroMapAccessor) screen).aero$getScale();

        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        var window = mc.getWindow();
        double guiScale = (double) window.getScreenWidth() / window.getGuiScaledWidth();
        double interfaceScale = (double) window.getWidth() / window.getScreenWidth();
        double scale = mapScale / guiScale / interfaceScale;

        if (tickCount++ % 300 == 0) {
            var m = markers.iterator().next();
            AeroReformation.LOGGER.info("[AnchorMap] cam=({},{}), marker=({},{}), mapScale={}, scale={}, screen={}x{}",
                    (int)camX, (int)camZ, (int)m.x(), (int)m.z(), mapScale, scale, w, h);
        }

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(w / 2.0, h / 2.0, 0);
        pose.scale((float) scale, (float) scale, 1);
        pose.translate(-camX, -camZ, 0);

        int color = 0xFF64B4FF; // light blue
        int dotSize = Math.max(2, Mth.ceil(1.33 / scale));
        for (var m : markers) {
            int sx = Mth.floor(m.x());
            int sy = Mth.floor(m.z());
            g.fill(sx - dotSize, sy - dotSize, sx + dotSize, sy + dotSize, color);
            if (m.name() != null && scale > 0.1) {
                g.drawString(mc.font, m.name(), sx + 5, sy - 4, 0xFFFFFF);
            }
        }
        pose.popPose();
    }
}
