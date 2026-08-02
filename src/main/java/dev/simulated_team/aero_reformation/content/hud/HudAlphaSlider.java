package dev.simulated_team.aero_reformation.content.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** Opacity slider for a HUD entry (0-255). Live-updates while dragging, saves on release. */
public class HudAlphaSlider extends AbstractSliderButton {

    private final HudEntry entry;

    public HudAlphaSlider(int x, int y, int width, int height, HudEntry entry) {
        super(x, y, width, height, Component.empty(), entry.alpha / 255.0);
        this.entry = entry;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable("hud.aero_reformation.alpha", (int) Math.round(value * 255)));
    }

    @Override
    protected void applyValue() {
        entry.alpha = (int) Math.round(value * 255);
    }

    @Override
    public void onRelease(double mx, double my) {
        super.onRelease(mx, my);
        entry.alpha = (int) Math.round(value * 255);
        HudBoard.saveToPlayer(Minecraft.getInstance().player);
    }
}
