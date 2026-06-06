package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.simulated_team.aero_reformation.config.AeroReformationConfig;

public class AnchorNamingScreen extends Screen {

    private final BlockPos pos;
    private final String defaultName;
    private EditBox nameBox;
    private int radius;
    private final int maxRadius;

    public AnchorNamingScreen(BlockPos pos, String defaultName, int radius) {
        super(Component.translatable("gui.aero_reformation.anchor_name"));
        this.pos = pos;
        this.defaultName = defaultName;
        this.radius = radius;
        this.maxRadius = AeroReformationConfig.maxAnchorRadius;
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        nameBox = new EditBox(font, cx - 80, cy - 10, 160, 18, Component.translatable("gui.aero_reformation.anchor_name"));
        nameBox.setValue(defaultName);
        nameBox.setFilter(s -> s.length() <= 32);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        // Radius: - / +
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            if (radius > 2) radius--;
        }).pos(cx - 80, cy + 40).size(18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            if (radius < maxRadius) radius++;
        }).pos(cx + 62, cy + 40).size(18, 18).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.aero_reformation.confirm"), b -> {
            PacketDistributor.sendToServer(new AnchorRenamePacket(pos, nameBox.getValue().trim(), radius));
            onClose();
        }).pos(cx - 44, cy + 16).size(88, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, height / 2 - 30, 0xFFFFFF);
        String rText = "加载半径: " + radius + " (" + (radius*2+1) + "×" + (radius*2+1) + ")";
        g.drawCenteredString(font, rText, width / 2, height / 2 + 45, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        super.onClose();
    }
}
