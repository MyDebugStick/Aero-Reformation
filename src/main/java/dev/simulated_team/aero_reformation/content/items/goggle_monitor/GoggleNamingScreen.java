package dev.simulated_team.aero_reformation.content.items.goggle_monitor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GoggleNamingScreen extends Screen {

    private final String sensorType;
    private final Runnable onConfirm;
    private EditBox nameBox;

    public GoggleNamingScreen(String sensorType, String defaultName, Runnable onConfirm) {
        super(Component.translatable("gui.aero_reformation.goggle_monitor"));
        this.sensorType = sensorType;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        nameBox = new EditBox(font, cx - 80, cy - 10, 160, 18, Component.empty());
        nameBox.setValue(GoggleMonitorData.sensorDefaultName(sensorType));
        nameBox.setFilter(s -> s.length() <= 32);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.aero_reformation.confirm"), b -> {
            onConfirm.run();
        }).pos(cx - 44, cy + 16).size(88, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (minecraft == null) return;
        renderBackground(g, mx, my, pt);
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, height / 2 - 30, 0xFFFFFF);
    }

    @Override
    public void tick() {
        if (minecraft != null && minecraft.level == null) {
            onClose();
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            super.onClose();
        }
    }
}
