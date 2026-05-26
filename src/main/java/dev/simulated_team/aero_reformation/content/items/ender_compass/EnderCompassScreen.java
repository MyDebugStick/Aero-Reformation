package dev.simulated_team.aero_reformation.content.items.ender_compass;

import dev.simulated_team.aero_reformation.network.EnderCompassSyncPacket;
import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class EnderCompassScreen extends Screen {

    private final ItemStack compass;
    private final String channel;
    private EditBox xBox, yBox, zBox;

    public EnderCompassScreen(ItemStack compass) {
        super(Component.translatable("item.aero_reformation.ender_compass"));
        this.compass = compass;
        EnderCompassData data = compass.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
        this.channel = data.channel();
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int startY = height / 2 - 40;
        int boxW = 90, boxH = 18, rowH = 28;

        EnderCompassData data = compass.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
        int x = data.target().map(t -> t.pos().getX()).orElse(0);
        int y = data.target().map(t -> t.pos().getY()).orElse(64);
        int z = data.target().map(t -> t.pos().getZ()).orElse(0);

        xBox = addBox(cx - boxW / 2, startY, boxW, boxH, x);
        yBox = addBox(cx - boxW / 2, startY + rowH, boxW, boxH, y);
        zBox = addBox(cx - boxW / 2, startY + rowH * 2, boxW, boxH, z);

        addRenderableWidget(Button.builder(Component.translatable("gui.aero_reformation.confirm"), b -> {
            try {
                int nx = Integer.parseInt(xBox.getValue());
                int ny = Integer.parseInt(yBox.getValue());
                int nz = Integer.parseInt(zBox.getValue());
                if (minecraft != null && minecraft.getConnection() != null) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            new EnderCompassSyncPacket(new BlockPos(nx, ny, nz), channel));
                }
                onClose();
            } catch (NumberFormatException ignored) {}
        }).pos(cx - 44, startY + rowH * 3 + 6).size(88, 18).build());
    }

    private EditBox addBox(int x, int y, int w, int h, int val) {
        EditBox box = new EditBox(font, x, y, w, h, Component.empty());
        box.setValue(String.valueOf(val));
        box.setFilter(s -> s.matches("-?\\d*"));
        addRenderableWidget(box);
        return box;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 22, 0xFFFFFF);
        g.drawCenteredString(font, Component.literal("Channel: " + channel), width / 2, 38, 0xAAAAAA);

        int cx = width / 2;
        int startY = height / 2 - 40, rowH = 28, boxW = 90;
        int labelX = cx - boxW / 2 - 16;
        g.drawString(font, "X", labelX, startY + 4, 0xFF5555);
        g.drawString(font, "Y", labelX, startY + rowH + 4, 0x55FF55);
        g.drawString(font, "Z", labelX, startY + rowH * 2 + 4, 0x5555FF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
