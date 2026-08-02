package dev.simulated_team.aero_reformation.content.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Setup screen for custom placeholders: list existing ones (click to edit,
 * click the right-side ✕ to delete) and create a new one by middle-clicking a
 * block and then picking an NBT key.
 */
public class HudPlaceholderSetupScreen extends Screen {

    private static final int ROW_H = 26;
    private int scroll;

    public HudPlaceholderSetupScreen() {
        super(Component.translatable("hud.aero_reformation.ph_title"));
    }

    @Override
    protected void init() {
        // New NBT placeholder: middle-click a block/entity, then pick an NBT key
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.ph_new_nbt"), b -> {
                    HudPickHandler.beginPlaceholderPick();
                    minecraft.setScreen(null); // await middle-click
                })
                .bounds(4, 4, 56, 18).build());
        // New placeholder-math / constant placeholders: straight to the editor
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.ph_new_math"), b -> {
                    HudPlaceholder ph = new HudPlaceholder();
                    ph.bindSource = "math";
                    minecraft.setScreen(new HudPlaceholderEditScreen(ph));
                })
                .bounds(64, 4, 48, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.ph_new_constant"), b -> {
                    HudPlaceholder ph = new HudPlaceholder();
                    ph.bindSource = "constant";
                    minecraft.setScreen(new HudPlaceholderEditScreen(ph));
                })
                .bounds(116, 4, 48, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.back"), b ->
                        minecraft.setScreen(new HudConfigScreen()))
                .bounds(168, 4, 60, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        List<HudPlaceholder> list = HudPlaceholderBoard.getPlaceholders(minecraft.player);
        if (list.isEmpty()) {
            g.drawString(font, Component.translatable("hud.aero_reformation.ph_empty"), 14, 40, 0x888888);
        }
        int y0 = 30;
        int visibleCount = Math.max(1, (this.height - y0 - 10) / ROW_H);
        int end = Math.min(list.size(), scroll + visibleCount);
        for (int i = scroll; i < end; i++) {
            HudPlaceholder p = list.get(i);
            int y = y0 + (i - scroll) * ROW_H;
            String token = "%" + p.name + "%";
            g.fill(10, y, this.width - 30, y + ROW_H, 0x14FFFFFF);
            // Line 1: name + remark
            g.drawString(font, token, 14, y + 2, 0xFFFFFF);
            if (!p.desc.isBlank()) {
                String desc = font.plainSubstrByWidth(p.desc,
                        Math.max(20, this.width - (14 + font.width(token)) - 60));
                g.drawString(font, desc, 14 + font.width(token) + 6, y + 2, 0x88AAAA);
            }
            // Line 2: live value (yellow) + target info (green)
            String live = HudPlaceholderBoard.liveValue(p);
            int lx = 14;
            if (live != null && !live.isEmpty()) {
                String vl = font.plainSubstrByWidth("值: " + live, Math.max(20, this.width - 70));
                g.drawString(font, vl, lx, y + 14, 0xFFFF55);
                lx += font.width(vl) + 8;
            }
            String pos = p.pos != null ? p.pos.toShortString() : "?";
            String src;
            if ("constant".equals(p.bindSource)) {
                src = "[" + Component.translatable("hud.aero_reformation.ph_kind_constant").getString()
                        + ":" + p.value + "]";
            } else if ("math".equals(p.bindSource)) {
                src = "[" + Component.translatable("hud.aero_reformation.ph_kind_math").getString()
                        + ":" + p.value + "]";
            } else if ("sensor".equals(p.bindSource)) {
                src = "[" + p.sensorType + "]";
            } else {
                src = p.nbtPath.isBlank() ? "" : "[" + p.nbtPath + "]";
            }
            String math = p.math.isBlank() ? "" : "  " + p.math;
            String info = (pos + " " + src + math).trim();
            if (!info.isEmpty()) {
                String ii = font.plainSubstrByWidth(info, Math.max(20, this.width - lx - 40));
                g.drawString(font, ii, lx, y + 14, 0x88FF88);
            }
            g.drawString(font, "✕", this.width - 26, y + 8, 0xFF5555);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0) {
            List<HudPlaceholder> list = HudPlaceholderBoard.getPlaceholders(minecraft.player);
            int rel = (int) (my - 30);
            if (rel >= 0) {
                int idx = scroll + rel / ROW_H;
                if (idx >= 0 && idx < list.size()) {
                    if (mx > this.width - 32) {
                        HudPlaceholderBoard.removePlaceholder(minecraft.player, list.get(idx));
                    } else {
                        minecraft.setScreen(new HudPlaceholderEditScreen(list.get(idx)));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        List<HudPlaceholder> list = HudPlaceholderBoard.getPlaceholders(minecraft.player);
        int visibleCount = Math.max(1, (this.height - 30 - 10) / ROW_H);
        int maxScroll = Math.max(0, list.size() - visibleCount);
        scroll = Mth.clamp(scroll - (int) scrollY, 0, maxScroll);
        return true;
    }
}
