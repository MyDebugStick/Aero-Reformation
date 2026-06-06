package dev.simulated_team.aero_reformation.mixin.feature.physics_anchor;

import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.content.blocks.physics_anchor.AnchorMapClientData;
import dev.simulated_team.aero_reformation.content.blocks.physics_anchor.AnchorMapRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.GuiGraphics;
import xaero.map.gui.GuiMap;

@Mixin(GuiMap.class)
public abstract class XaeroMapMixin {
    @Unique private boolean aero$failedToRender = false;

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"), require = 0)
    private void aero$renderAnchorMarkers(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        if (aero$failedToRender) return;
        try {
            var markers = AnchorMapClientData.getMarkers();
            if (markers.isEmpty()) return;
            AnchorMapRenderer.render(g, (GuiMap) (Object) this, mx, my, pt);
        } catch (Throwable e) {
            AeroReformation.LOGGER.error("Failed to render Xaero map anchors:", e);
            aero$failedToRender = true;
        }
    }
}
