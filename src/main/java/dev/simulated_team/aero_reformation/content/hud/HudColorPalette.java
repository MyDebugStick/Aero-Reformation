package dev.simulated_team.aero_reformation.content.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

/**
 * A 16-color grid palette with an extra rainbow cell. Picking a normal color
 * fires {@code onPick}; picking the rainbow cell fires {@code onRainbow}.
 */
public class HudColorPalette extends AbstractWidget {

    public static final int[] COLORS = {
            0xFFFFFFFF, 0xFFAAAAAA, 0xFF555555, 0xFF000000,
            0xFFFF5555, 0xFFFFAA55, 0xFFFFFF55, 0xFF55FF55,
            0xFF55FFFF, 0xFF55AAFF, 0xFFAA55FF, 0xFFFF55FF,
            0xFFFFAAAA, 0xFFAAFFAA, 0xFFAAAAFF, 0xFFFFDDBB
    };

    private static final int COLS = 8;
    private static final int CELL = 12;
    private static final int GAP = 2;
    private static final int RAINBOW_INDEX = 16; // row 2, col 0

    private final boolean withRainbow;
    private final Consumer<Integer> onPick;
    private final Runnable onRainbow;
    private int currentColor;
    private boolean rainbow;

    public HudColorPalette(int x, int y, int currentColor, boolean rainbow,
                           boolean withRainbow, Consumer<Integer> onPick, Runnable onRainbow) {
        super(x, y, COLS * (CELL + GAP) + GAP,
                (withRainbow ? 3 : 2) * (CELL + GAP) + GAP, Component.literal("palette"));
        this.currentColor = currentColor;
        this.rainbow = rainbow;
        this.withRainbow = withRainbow;
        this.onPick = onPick;
        this.onRainbow = onRainbow;
    }

    public int getCurrentColor() {
        return currentColor;
    }

    public void setCurrentColor(int color) {
        this.currentColor = color;
    }

    public void setRainbow(boolean rainbow) {
        this.rainbow = rainbow;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        for (int i = 0; i < COLORS.length; i++) {
            int cx = getX() + GAP + (i % COLS) * (CELL + GAP);
            int cy = getY() + GAP + (i / COLS) * (CELL + GAP);
            g.fill(cx, cy, cx + CELL, cy + CELL, COLORS[i]);
            if (!rainbow && COLORS[i] == currentColor) {
                g.renderOutline(cx - 1, cy - 1, CELL + 2, CELL + 2, 0xFFFFFFFF);
            }
        }
        if (withRainbow) {
            int rx = getX() + GAP;
            int ry = getY() + GAP + 2 * (CELL + GAP);
            drawRainbow(g, rx, ry, CELL, CELL);
            if (rainbow) {
                g.renderOutline(rx - 1, ry - 1, CELL + 2, CELL + 2, 0xFFFFFFFF);
            }
        }
    }

    private void drawRainbow(GuiGraphics g, int x, int y, int w, int h) {
        int bands = 7;
        for (int i = 0; i < bands; i++) {
            float hue = i / (float) bands;
            int color = 0xFF000000 | Mth.hsvToRgb(hue, 1.0f, 1.0f);
            int x0 = x + i * w / bands;
            int x1 = x + (i + 1) * w / bands;
            g.fill(x0, y, x1, y + h, color);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (this.active && this.visible && button == 0 && isMouseOver(mx, my)) {
            int i = pickCell(mx, my);
            if (i == RAINBOW_INDEX && withRainbow) {
                rainbow = true;
                onRainbow.run();
                return true;
            }
            if (i >= 0 && i < COLORS.length) {
                rainbow = false;
                currentColor = COLORS[i];
                onPick.accept(currentColor);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private int pickCell(double mx, double my) {
        int col = (int) ((mx - getX() - GAP) / (CELL + GAP));
        int row = (int) ((my - getY() - GAP) / (CELL + GAP));
        if (col < 0 || col >= COLS || row < 0 || row >= (withRainbow ? 3 : 2)) return -1;
        return row * COLS + col;
    }
}
