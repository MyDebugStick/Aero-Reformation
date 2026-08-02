package dev.simulated_team.aero_reformation.content.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Full-width editor for long entry content (text or suffix). Opens from the
 * entry edit screen via the "Enlarge" buttons so if-else expressions and long
 * placeholder strings are easy to type. Placeholders can be inserted from the
 * placeholder list; the token is put back at the recorded cursor position.
 */
public class HudBigTextScreen extends Screen {

    /** Pending placeholder insertion from HudPlaceholderListScreen. */
    public static String pendingInsertToken;
    public static int pendingInsertPos = -1;

    private final HudEntry entry;
    private final boolean editSuffix;
    private EditBox bigBox;

    public HudBigTextScreen(HudEntry entry, boolean editSuffix) {
        super(Component.translatable("hud.aero_reformation.big_text_title"));
        this.entry = entry;
        this.editSuffix = editSuffix;
    }

    @Override
    protected void init() {
        // Insert a placeholder token chosen in the list screen at the cursor position
        if (pendingInsertToken != null) {
            String target = editSuffix ? entry.suffix : entry.text;
            int pos = pendingInsertPos >= 0 ? Math.min(pendingInsertPos, target.length())
                    : target.length();
            target = target.substring(0, pos) + pendingInsertToken + target.substring(pos);
            if (editSuffix) {
                entry.suffix = target;
            } else {
                entry.text = target;
            }
            pendingInsertToken = null;
            pendingInsertPos = -1;
        }

        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.ph_button"), b -> {
                    // Cache whatever the user already typed before leaving
                    if (editSuffix) {
                        entry.suffix = bigBox.getValue();
                    } else {
                        entry.text = bigBox.getValue();
                    }
                    pendingInsertPos = bigBox.getCursorPosition();
                    minecraft.setScreen(new HudPlaceholderListScreen(entry, true, editSuffix));
                })
                .bounds(20, 32, 100, 20).build());

        bigBox = new EditBox(this.font, 20, 56, this.width - 40, 20,
                Component.translatable("hud.aero_reformation.text"));
        bigBox.setMaxLength(1024);
        bigBox.setValue(editSuffix ? entry.suffix : entry.text);
        bigBox.setFocused(true);
        this.setInitialFocus(bigBox);
        addRenderableWidget(bigBox);

        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.ph_save"), b -> confirm())
                .bounds(this.width / 2 - 110, 92, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.cancel"), b ->
                        minecraft.setScreen(new HudEntryEditScreen(entry)))
                .bounds(this.width / 2 + 10, 92, 100, 20).build());
    }

    private void confirm() {
        if (editSuffix) {
            entry.suffix = bigBox.getValue();
        } else {
            entry.text = bigBox.getValue();
        }
        HudBoard.saveToPlayer(minecraft.player);
        minecraft.setScreen(new HudEntryEditScreen(entry));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / Numpad Enter
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, Component.translatable("hud.aero_reformation.big_text_hint"), 20, 120, 0x888888);
        g.drawString(font, Component.translatable("hud.aero_reformation.if_hint"), 20, 132, 0x55AAFF);
    }
}
