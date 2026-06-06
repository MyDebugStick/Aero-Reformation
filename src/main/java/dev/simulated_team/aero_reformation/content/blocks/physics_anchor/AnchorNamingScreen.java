package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class AnchorNamingScreen extends Screen {

    private final BlockPos pos;
    private final String defaultName;
    private EditBox nameBox;

    public AnchorNamingScreen(BlockPos pos, String defaultName) {
        super(Component.translatable("gui.aero_reformation.anchor_name"));
        this.pos = pos;
        this.defaultName = defaultName;
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        nameBox = new EditBox(font, cx - 80, cy - 10, 160, 18, Component.translatable("gui.aero_reformation.anchor_name"));
        nameBox.setValue(defaultName);
        nameBox.setFilter(s -> s.length() <= 32);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.aero_reformation.confirm"), b -> {
            PacketDistributor.sendToServer(new AnchorRenamePacket(pos, nameBox.getValue().trim()));
            onClose();
        }).pos(cx - 44, cy + 16).size(88, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, height / 2 - 30, 0xFFFFFF);
        int radius = AnchorMapClientData.getRadius(pos);
        String rText = "加载半径: " + radius + " chunks (" + (radius*2+1) + "×" + (radius*2+1) + ")";
        g.drawCenteredString(font, rText, width / 2, height / 2 + 40, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        super.onClose();
    }
}
