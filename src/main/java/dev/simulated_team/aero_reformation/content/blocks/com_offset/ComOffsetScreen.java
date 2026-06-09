package dev.simulated_team.aero_reformation.content.blocks.com_offset;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

public class ComOffsetScreen extends Screen {
    private final BlockPos pos;
    private final double initialX, initialY, initialZ;
    private EditBox fieldX, fieldY, fieldZ;

    public ComOffsetScreen(BlockPos pos, double x, double y, double z) {
        super(Component.translatable("gui.aero_reformation.com_config"));
        this.pos = pos;
        this.initialX = x;
        this.initialY = y;
        this.initialZ = z;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 40;
        int boxW = 60, boxH = 20;
        int labelW = 20;
        int totalW = boxW + labelW;         // 80px total row width
        int left = cx - totalW / 2;          // centered

        fieldX = new EditBox(font, left, y, boxW, boxH, Component.literal("X"));
        fieldX.setValue(String.format("%.1f", initialX));
        addRenderableWidget(fieldX);
        addRenderableWidget(Button.builder(Component.literal("X"), b -> {}).pos(left + boxW, y).size(labelW, boxH).build());

        y += 25;
        fieldY = new EditBox(font, left, y, boxW, boxH, Component.literal("Y"));
        fieldY.setValue(String.format("%.1f", initialY));
        addRenderableWidget(fieldY);
        addRenderableWidget(Button.builder(Component.literal("Y"), b -> {}).pos(left + boxW, y).size(labelW, boxH).build());

        y += 25;
        fieldZ = new EditBox(font, left, y, boxW, boxH, Component.literal("Z"));
        fieldZ.setValue(String.format("%.1f", initialZ));
        addRenderableWidget(fieldZ);
        addRenderableWidget(Button.builder(Component.literal("Z"), b -> {}).pos(left + boxW, y).size(labelW, boxH).build());

        y += 35;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .pos(cx - 50, y).size(100, 20).build());
    }

    @Override
    public void onClose() {
        try {
            double x = Double.parseDouble(fieldX.getValue());
            double y = Double.parseDouble(fieldY.getValue());
            double z = Double.parseDouble(fieldZ.getValue());
            PacketDistributor.sendToServer(new ComConfigPayload(pos, x, y, z));
        } catch (NumberFormatException ignored) {}
        super.onClose();
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
