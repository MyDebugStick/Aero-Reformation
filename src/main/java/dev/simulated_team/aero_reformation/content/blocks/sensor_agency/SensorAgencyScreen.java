package dev.simulated_team.aero_reformation.content.blocks.sensor_agency;

import dev.simulated_team.aero_reformation.network.SensorAgencyConfigPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class SensorAgencyScreen extends AbstractContainerScreen<SensorAgencyMenu> {

    private static final int ROW_H = 22;
    private static final int SEC1_Y = 72, SEC2_Y = 122, SEC3_Y = 172;
    private static final int BOX_X = 40, BOX_W = 34, BTN_X = 6, TOGGLE_X = 110;
    private EditBox gimbal1, gimbal2, altLow, altHigh, velMax;
    private Button gimbalToggle, navToggle, altToggle;

    public SensorAgencyScreen(SensorAgencyMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 200;
        this.imageHeight = 245;
        this.inventoryLabelY = -100;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        var c = menu.config;
        var synced = dev.simulated_team.aero_reformation.network.SensorAgencyConfigPacket.CLIENT_SYNC;
        dev.simulated_team.aero_reformation.network.SensorAgencyConfigPacket.CLIENT_SYNC = null;
        if (synced != null) {
            c.gimbalPrimaryLimit = synced.gimbalPrimary();
            c.gimbalSecondaryLimit = synced.gimbalSecondary();
            c.gimbalInverted = synced.gimbalInverted();
            c.altitudeLowWorld = synced.altLow();
            c.altitudeHighWorld = synced.altHigh();
            c.velocityMaxSpeed = synced.velMax();
            c.altitudeInverted = synced.altInverted();
            c.navInverted = synced.navInverted();
        }
        int rx = leftPos, ry = topPos;
        var cfg = menu.config;

        gimbal1 = addBox(rx + BOX_X, ry + SEC1_Y, cfg.gimbalPrimaryLimit);
        addBtn(rx + BTN_X, ry + SEC1_Y, "-", 0);
        addBtn(rx + BTN_X + 16, ry + SEC1_Y, "+", 1);
        addToggleBtn(rx + TOGGLE_X, ry + SEC1_Y - 1, cfg.gimbalInverted, 10);
        gimbalToggle = (Button) children().get(children().size() - 1);

        gimbal2 = addBox(rx + BOX_X, ry + SEC1_Y + ROW_H, cfg.gimbalSecondaryLimit);
        addBtn(rx + BTN_X, ry + SEC1_Y + ROW_H, "-", 2);
        addBtn(rx + BTN_X + 16, ry + SEC1_Y + ROW_H, "+", 3);

        altLow = addBox(rx + BOX_X, ry + SEC2_Y, cfg.altitudeLowWorld);
        addBtn(rx + BTN_X, ry + SEC2_Y, "-", 4);
        addBtn(rx + BTN_X + 16, ry + SEC2_Y, "+", 5);

        altHigh = addBox(rx + BOX_X, ry + SEC2_Y + ROW_H, cfg.altitudeHighWorld);
        addBtn(rx + BTN_X, ry + SEC2_Y + ROW_H, "-", 6);
        addBtn(rx + BTN_X + 16, ry + SEC2_Y + ROW_H, "+", 7);
        addToggleBtn(rx + TOGGLE_X, ry + SEC2_Y - 1, cfg.altitudeInverted, 12);
        altToggle = (Button) children().get(children().size() - 1);

        velMax = addBox(rx + BOX_X, ry + SEC3_Y, cfg.velocityMaxSpeed);
        addBtn(rx + BTN_X, ry + SEC3_Y, "-", 8);
        addBtn(rx + BTN_X + 16, ry + SEC3_Y, "+", 9);

        addToggleBtn(rx + TOGGLE_X, ry + SEC3_Y + ROW_H + 2, cfg.navInverted, 11);
        navToggle = (Button) children().get(children().size() - 1);

        // Confirm button
        addRenderableWidget(Button.builder(Component.translatable("gui.aero_reformation.confirm"),
                b -> syncAll()).pos(rx + 56, ry + 220).size(88, 18).build());
    }

    private EditBox addBox(int x, int y, int val) {
        EditBox box = new EditBox(font, x, y, BOX_W, 14, Component.empty());
        box.setValue(String.valueOf(val));
        box.setFilter(s -> s.matches("-?\\d*"));
        box.setResponder(s -> {
            if (!s.isEmpty() && !s.equals("-")) try {
                Integer.parseInt(s); // validate
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(box);
        return box;
    }

    private void addBtn(int x, int y, String text, int btnId) {
        addRenderableWidget(Button.builder(Component.literal(text), b -> clickButton(btnId))
                .pos(x, y).size(14, 14).build());
    }

    private void addToggleBtn(int x, int y, boolean state, int btnId) {
        addRenderableWidget(Button.builder(
                Component.translatable(state ? "gui.aero_reformation.inverted_on" : "gui.aero_reformation.inverted_off"),
                b -> clickButton(btnId)).pos(x, y).size(75, 18).build());
    }

    private void clickButton(int id) {
        applyButton(id); // instant local feedback
        if (minecraft != null && minecraft.getConnection() != null) {
            minecraft.getConnection().send(new net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket(menu.containerId, id));
        }
    }

    private void applyButton(int id) {
        var c = menu.config;
        switch (id) {
            case 0: c.gimbalPrimaryLimit = Math.clamp(c.gimbalPrimaryLimit - 5, 1, 90); break;
            case 1: c.gimbalPrimaryLimit = Math.clamp(c.gimbalPrimaryLimit + 5, 1, 90); break;
            case 2: c.gimbalSecondaryLimit = Math.clamp(c.gimbalSecondaryLimit - 5, 1, 90); break;
            case 3: c.gimbalSecondaryLimit = Math.clamp(c.gimbalSecondaryLimit + 5, 1, 90); break;
            case 4: c.altitudeLowWorld = Math.clamp(c.altitudeLowWorld - 1, -64, 800); break;
            case 5: c.altitudeLowWorld = Math.clamp(c.altitudeLowWorld + 1, -64, 800); break;
            case 6: c.altitudeHighWorld = Math.clamp(c.altitudeHighWorld - 1, -64, 800); break;
            case 7: c.altitudeHighWorld = Math.clamp(c.altitudeHighWorld + 1, -64, 800); break;
            case 8: c.velocityMaxSpeed = Math.clamp(c.velocityMaxSpeed - 1, 1, 50); break;
            case 9: c.velocityMaxSpeed = Math.clamp(c.velocityMaxSpeed + 1, 1, 50); break;
            case 10: c.gimbalInverted = !c.gimbalInverted; break;
            case 11: c.navInverted = !c.navInverted; break;
            case 12: c.altitudeInverted = !c.altitudeInverted; break;
        }
        if (gimbal1 != null) gimbal1.setValue(String.valueOf(c.gimbalPrimaryLimit));
        if (gimbal2 != null) gimbal2.setValue(String.valueOf(c.gimbalSecondaryLimit));
        if (altLow != null) altLow.setValue(String.valueOf(c.altitudeLowWorld));
        if (altHigh != null) altHigh.setValue(String.valueOf(c.altitudeHighWorld));
        if (velMax != null) velMax.setValue(String.valueOf(c.velocityMaxSpeed));
        if (gimbalToggle != null) gimbalToggle.setMessage(Component.translatable(
                c.gimbalInverted ? "gui.aero_reformation.inverted_on" : "gui.aero_reformation.inverted_off"));
        if (navToggle != null) navToggle.setMessage(Component.translatable(
                c.navInverted ? "gui.aero_reformation.inverted_on" : "gui.aero_reformation.inverted_off"));
        if (altToggle != null) altToggle.setMessage(Component.translatable(
                c.altitudeInverted ? "gui.aero_reformation.inverted_on" : "gui.aero_reformation.inverted_off"));
    }

    private void syncAll() {
        // Read EditBox values into config
        var c = menu.config;
        try { c.gimbalPrimaryLimit = Math.clamp(Integer.parseInt(gimbal1.getValue()), 1, 90); } catch (NumberFormatException ignored) {}
        try { c.gimbalSecondaryLimit = Math.clamp(Integer.parseInt(gimbal2.getValue()), 1, 90); } catch (NumberFormatException ignored) {}
        try { c.altitudeLowWorld = Math.clamp(Integer.parseInt(altLow.getValue()), -64, 800); } catch (NumberFormatException ignored) {}
        try { c.altitudeHighWorld = Math.clamp(Integer.parseInt(altHigh.getValue()), -64, 800); } catch (NumberFormatException ignored) {}
        try { c.velocityMaxSpeed = Math.clamp(Integer.parseInt(velMax.getValue()), 1, 50); } catch (NumberFormatException ignored) {}
        // Send full config to server
        if (minecraft != null && minecraft.getConnection() != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    SensorAgencyConfigPacket.fromConfig(
                            dev.simulated_team.aero_reformation.network.SensorAgencyConfigPacket.LAST_POS, c));
        }
        // Update boxes with clamped values
        gimbal1.setValue(String.valueOf(c.gimbalPrimaryLimit));
        gimbal2.setValue(String.valueOf(c.gimbalSecondaryLimit));
        altLow.setValue(String.valueOf(c.altitudeLowWorld));
        altHigh.setValue(String.valueOf(c.altitudeHighWorld));
        velMax.setValue(String.valueOf(c.velocityMaxSpeed));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int rx = leftPos, ry = topPos;
        g.fill(rx, ry, rx + imageWidth, ry + imageHeight, 0xFFC6C6C6);
        g.fill(rx + 3, ry + 3, rx + imageWidth - 3, ry + imageHeight - 3, 0xFF8B8B8B);
        g.fill(rx + 91, ry + 19, rx + 109, ry + 37, 0xFF373737);
        g.fill(rx + 6, ry + 68, rx + imageWidth - 6, ry + 69, 0xFF555555);
        g.fill(rx + 6, ry + 118, rx + imageWidth - 6, ry + 119, 0xFF555555);
        g.fill(rx + 6, ry + 168, rx + imageWidth - 6, ry + 169, 0xFF555555);
        drawBoxBg(g, rx + BOX_X, ry + SEC1_Y);
        drawBoxBg(g, rx + BOX_X, ry + SEC1_Y + ROW_H);
        drawBoxBg(g, rx + BOX_X, ry + SEC2_Y);
        drawBoxBg(g, rx + BOX_X, ry + SEC2_Y + ROW_H);
        drawBoxBg(g, rx + BOX_X, ry + SEC3_Y);
    }

    private void drawBoxBg(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + BOX_W + 1, y + 15, 0xFF000000);
        g.fill(x, y, x + BOX_W, y + 14, 0xFF222222);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        int lx = BOX_X + BOX_W + 4;
        g.drawString(font, Component.translatable("gui.aero_reformation.compass_slot"), 40, 23, 0x404040);
        g.drawString(font, Component.translatable("gui.aero_reformation.gimbal_section"), 6, SEC1_Y - 13, 0xFF_222222);
        g.drawString(font, "°", lx, SEC1_Y + 5, 0x404040);
        g.drawString(font, "°", lx, SEC1_Y + ROW_H + 5, 0x404040);
        g.drawString(font, Component.translatable("gui.aero_reformation.altitude_section"), 6, SEC2_Y - 13, 0xFF_222222);
        g.drawString(font, Component.translatable("gui.aero_reformation.altitude_low"), BOX_X + BOX_W + 4, SEC2_Y + 4, 0x404040);
        g.drawString(font, Component.translatable("gui.aero_reformation.altitude_high"), BOX_X + BOX_W + 4, SEC2_Y + ROW_H + 4, 0x404040);
        g.drawString(font, Component.translatable("gui.aero_reformation.velocity_section"), 6, SEC3_Y - 13, 0xFF_222222);
        g.drawString(font, "m/s", lx, SEC3_Y + 5, 0x404040);
        g.drawString(font, Component.translatable("gui.aero_reformation.nav_section"), 6, SEC3_Y + ROW_H + 6, 0xFF_222222);
    }
}
