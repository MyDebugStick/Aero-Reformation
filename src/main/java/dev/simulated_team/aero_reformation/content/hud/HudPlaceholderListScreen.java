package dev.simulated_team.aero_reformation.content.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen list of all placeholders (built-in + custom). Click one to insert
 * it into the entry's text box (the edit screen reopens with the token inserted
 * at the position where the "Placeholders" button was pressed).
 */
public class HudPlaceholderListScreen extends Screen {

    private static final int ROW_H = 12;

    private final HudEntry entry;
    private final boolean fromBig;   // opened from the enlarged editor
    private final boolean editSuffix; // target box when fromBig
    private final HudPlaceholder ph; // non-null when inserting into a placeholder math expression
    private final List<String> keys = new ArrayList<>();
    private int scroll;

    public HudPlaceholderListScreen(HudEntry entry) {
        this(entry, false, false);
    }

    public HudPlaceholderListScreen(HudEntry entry, boolean fromBig, boolean editSuffix) {
        super(Component.translatable("hud.aero_reformation.ph_list_title"));
        this.entry = entry;
        this.fromBig = fromBig;
        this.editSuffix = editSuffix;
        this.ph = null;
        keys.addAll(HudPlaceholders.keys(Minecraft.getInstance()));
    }

    /** Open the list to insert a %token% into a placeholder's math expression. */
    public HudPlaceholderListScreen(HudPlaceholder ph) {
        super(Component.translatable("hud.aero_reformation.ph_list_title"));
        this.entry = null;
        this.fromBig = false;
        this.editSuffix = false;
        this.ph = ph;
        keys.addAll(HudPlaceholders.keys(Minecraft.getInstance()));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.back"), b ->
                        minecraft.setScreen(ph != null
                                ? new HudPlaceholderEditScreen(ph)
                                : (fromBig
                                        ? new HudBigTextScreen(entry, editSuffix)
                                        : new HudEntryEditScreen(entry))))
                .bounds(4, 4, 60, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, Component.translatable("hud.aero_reformation.ph_list_hint"), 10, 24, 0x888888);
        if (keys.isEmpty()) {
            g.drawString(font, Component.translatable("hud.aero_reformation.ph_empty"), 14, 40, 0x888888);
            return;
        }
        int y0 = 36;
        int visibleCount = Math.max(1, (this.height - y0 - 10) / ROW_H);
        int end = Math.min(keys.size(), scroll + visibleCount);
        Minecraft mc = Minecraft.getInstance();
        for (int i = scroll; i < end; i++) {
            int y = y0 + (i - scroll) * ROW_H;
            g.fill(10, y, this.width - 20, y + ROW_H, 0x14FFFFFF);
            String line = "%" + keys.get(i) + "%";
            g.drawString(font, line, 14, y + 1, 0x55FF55);
            String desc = HudPlaceholders.description(keys.get(i), mc);
            if (desc != null && !desc.isEmpty()) {
                int tx = 14 + font.width(line) + 8;
                String shown = font.plainSubstrByWidth(desc, Math.max(20, this.width - tx - 16));
                g.drawString(font, shown, tx, y + 1, 0x88AAAA);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0) {
            int y0 = 36;
            int rel = (int) (my - y0);
            if (rel >= 0) {
                int idx = scroll + rel / ROW_H;
                if (idx >= 0 && idx < keys.size()) {
                    String token = "%" + keys.get(idx) + "%";
                    if (ph != null) {
                        HudPlaceholderEditScreen.pendingInsertToken = token;
                        minecraft.setScreen(new HudPlaceholderEditScreen(ph));
                    } else if (fromBig) {
                        HudBigTextScreen.pendingInsertToken = token;
                        minecraft.setScreen(new HudBigTextScreen(entry, editSuffix));
                    } else {
                        HudEntryEditScreen.pendingInsertToken = token;
                        minecraft.setScreen(new HudEntryEditScreen(entry));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int y0 = 36;
        int visibleCount = Math.max(1, (this.height - y0 - 10) / ROW_H);
        int maxScroll = Math.max(0, keys.size() - visibleCount);
        scroll = Mth.clamp(scroll - (int) scrollY, 0, maxScroll);
        return true;
    }
}
