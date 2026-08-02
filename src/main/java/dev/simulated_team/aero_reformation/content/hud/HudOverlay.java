package dev.simulated_team.aero_reformation.content.hud;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;

/**
 * Renders all HUD entries in-game. Skipped while the config screen is open
 * (the screen renders them itself).
 */
@EventBusSubscriber(modid = AeroReformation.MODID, value = Dist.CLIENT)
public class HudOverlay {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        // Any open screen (config/edit) renders the entries itself
        if (mc.screen != null) return;

        List<HudEntry> entries = HudBoard.getEntries(mc.player);
        if (entries.isEmpty()) return;

        GuiGraphics g = event.getGuiGraphics();
        // One shared, stable view snapshot for the whole pass so every
        // horizon/world entry is projected with identical eye/look/camera/roll
        // data and the render time reference stays fixed.
        HudRenderers.ViewSnapshot snap = HudRenderers.captureStable(mc);
        if (snap == null) return;
        for (HudEntry e : entries) {
            HudRenderers.renderProjected(g, e, mc, false, snap);
        }
    }
}
