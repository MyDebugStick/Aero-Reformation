package dev.simulated_team.aero_reformation.content.blocks.power;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class PowerConfigScreen extends Screen {

    private final BlockPos pos;
    private final int initialYawMax;
    private final int initialPitchMax;
    private final double initialSeatHeight;
    private EditBox yawBox;
    private EditBox pitchBox;
    private EditBox heightBox;

    public PowerConfigScreen(BlockPos pos, int yawMax, int pitchMax, double seatHeight) {
        super(Component.translatable("gui.aero_reformation.power_config"));
        this.pos = pos;
        this.initialYawMax = yawMax;
        this.initialPitchMax = pitchMax;
        this.initialSeatHeight = seatHeight;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int startY = height / 2 - 40;
        int boxW = 60, boxH = 18, rowH = 30;

        yawBox = new EditBox(font, cx - boxW / 2, startY, boxW, boxH, Component.empty());
        yawBox.setValue(String.valueOf(initialYawMax));
        yawBox.setFilter(s -> s.matches("\\d{0,3}"));
        addRenderableWidget(yawBox);

        pitchBox = new EditBox(font, cx - boxW / 2, startY + rowH, boxW, boxH, Component.empty());
        pitchBox.setValue(String.valueOf(initialPitchMax));
        pitchBox.setFilter(s -> s.matches("\\d{0,3}"));
        addRenderableWidget(pitchBox);

        heightBox = new EditBox(font, cx - boxW / 2, startY + rowH * 2, boxW, boxH, Component.empty());
        heightBox.setValue(String.format("%.2f", initialSeatHeight));
        heightBox.setFilter(s -> s.matches("-?\\d{0,2}(\\.\\d{0,2})?"));
        addRenderableWidget(heightBox);

        addRenderableWidget(Button.builder(Component.translatable("gui.aero_reformation.confirm"), b -> {
            try {
                int yaw = Math.clamp(Integer.parseInt(yawBox.getValue()), 1, 180);
                int pitch = Math.clamp(Integer.parseInt(pitchBox.getValue()), 1, 90);
                double h = Math.clamp(Double.parseDouble(heightBox.getValue()), -0.2, 0.2);
                PacketDistributor.sendToServer(new PowerConfigPayload(pos, yaw, pitch, h));
            } catch (NumberFormatException ignored) {}
            onClose();
        }).pos(cx - 44, startY + rowH * 3 + 6).size(88, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);

        int cx = width / 2;
        int startY = height / 2 - 40;
        int boxW = 60, rowH = 30;
        int labelX = cx - boxW / 2 - 80;

        g.drawString(font, Component.translatable("gui.aero_reformation.power_yaw_max"), labelX, startY + 4, 0x55AAFF);
        g.drawString(font, Component.translatable("gui.aero_reformation.power_pitch_max"), labelX, startY + rowH + 4, 0xFFAA55);
        g.drawString(font, Component.translatable("gui.aero_reformation.power_seat_height"), labelX, startY + rowH * 2 + 4, 0x55FF55);
        g.drawString(font, "°", cx + boxW / 2 + 6, startY + 4, 0xAAAAAA);
        g.drawString(font, "°", cx + boxW / 2 + 6, startY + rowH + 4, 0xAAAAAA);
        g.drawCenteredString(font, Component.literal("1-180"), cx + 88, startY + 4, 0x666666);
        g.drawCenteredString(font, Component.literal("1-90"), cx + 88, startY + rowH + 4, 0x666666);
        g.drawCenteredString(font, Component.literal("±0.20"), cx + 88, startY + rowH * 2 + 4, 0x666666);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
