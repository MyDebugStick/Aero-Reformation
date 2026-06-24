package dev.simulated_team.aero_reformation.content.blocks.gravity_crystal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class GravityCrystalScreen extends Screen {
    private final UUID subLevelId;
    private final GravityCrystalSettings settings;

    private float liftMul, dragMul, angularDragMul;
    private static final float STEP = 0.1f;
    private static final float DEFAULT = 1.0f;

    private Button liftBtn, dragBtn, angBtn;

    public GravityCrystalScreen(UUID subLevelId, GravityCrystalSettings settings) {
        super(Component.translatable("screen.aero_reformation.gravity_crystal"));
        this.subLevelId = subLevelId;
        this.settings = settings;
        this.liftMul = settings.liftMultiplier;
        this.dragMul = settings.dragMultiplier;
        this.angularDragMul = settings.angularDragMultiplier;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 40;

        liftBtn = addRenderableWidget(Button.builder(txt("lift", liftMul), b -> {
            liftMul = clamp(liftMul + (hasShiftDown() ? -STEP : STEP));
            b.setMessage(txt("lift", liftMul));
        }).pos(cx - 100, y).size(200, 20).build());
        y += 25;

        dragBtn = addRenderableWidget(Button.builder(txt("drag", dragMul), b -> {
            dragMul = clamp(dragMul + (hasShiftDown() ? -STEP : STEP));
            b.setMessage(txt("drag", dragMul));
        }).pos(cx - 100, y).size(200, 20).build());
        y += 25;

        angBtn = addRenderableWidget(Button.builder(txt("angDrag", angularDragMul), b -> {
            angularDragMul = clamp(angularDragMul + (hasShiftDown() ? -STEP : STEP));
            b.setMessage(txt("angDrag", angularDragMul));
        }).pos(cx - 100, y).size(200, 20).build());
        y += 25;

        addRenderableWidget(Button.builder(Component.translatable("aero_reformation.gravity_crystal.reset"), b -> {
            liftMul = DEFAULT;  // reset to 1.0 (neutral)
            dragMul = DEFAULT;
            angularDragMul = DEFAULT;
            liftBtn.setMessage(txt("lift", liftMul));
            dragBtn.setMessage(txt("drag", dragMul));
            angBtn.setMessage(txt("angDrag", angularDragMul));
        }).pos(cx - 50, y).size(100, 20).build());
        y += 30;

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .pos(cx - 50, y).size(100, 20).build());
    }

    @Override
    public void onClose() {
        settings.liftMultiplier = liftMul;
        settings.dragMultiplier = dragMul;
        settings.angularDragMultiplier = angularDragMul;
        PacketDistributor.sendToServer(new GravityCrystalSettingsPacket(subLevelId, liftMul, dragMul, angularDragMul));
        super.onClose();
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        g.drawCenteredString(font, Component.literal("Shift+Click to decrease"), width / 2, height - 20, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static float clamp(float v) { return Math.clamp(v, -2f, 2f); }

    private static Component txt(String key, float v) {
        return Component.translatable("aero_reformation.gravity_crystal." + key, String.format("%.1f", v));
    }
}
