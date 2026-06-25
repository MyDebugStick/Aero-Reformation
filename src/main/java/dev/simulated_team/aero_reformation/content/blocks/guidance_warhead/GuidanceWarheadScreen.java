package dev.simulated_team.aero_reformation.content.blocks.guidance_warhead;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class GuidanceWarheadScreen extends Screen {
    private final BlockPos pos;

    private float kp, ki, kd, maxSpeed, sidePower, maxThrustPN;
    private float brakeCoeff, proximityRange, cruiseAltitude, redstoneRange, altitudeOffset;
    private int searchMode;
    private float minSearchRange, maxSearchRange;
    private double manualX, manualY, manualZ;

    // Defaults
    private static final float KP_DEF = 0.8f, KI_DEF = 0.02f, KD_DEF = 0.15f;
    private static final float MAX_SPEED_DEF = 20.0f, SIDE_POWER_DEF = 0.04f, THRUST_DEF = 2000.0f;
    private static final float BRAKE_DEF = 0.15f, PROX_DEF = 50.0f, ALT_DEF = 10.0f, REDSTONE_DEF = 10.0f, OFFSET_DEF = 0.0f;
    private static final int SEARCH_MODE_DEF = 0;
    private static final float MIN_RANGE_DEF = 0.0f, MAX_RANGE_DEF = 1000.0f;
    private static final double MANUAL_X_DEF = 0.0, MANUAL_Y_DEF = 64.0, MANUAL_Z_DEF = 0.0;

    private int currentPage = 0;

    private final List<Button> pageButtons = new ArrayList<>();
    private Button tabPid, tabTarget, tabInfo;
    private EditBox inputX, inputY, inputZ;

    public GuidanceWarheadScreen(BlockPos pos, float kp, float ki, float kd, float maxSpeed, float sidePower, float maxThrustPN,
                                  float brakeCoeff, float proximityRange, float cruiseAltitude, float redstoneRange, float altitudeOffset,
                                  int searchMode, float minSearchRange, float maxSearchRange,
                                  double manualX, double manualY, double manualZ) {
        super(Component.translatable("screen.aero_reformation.guidance_warhead"));
        this.pos = pos;
        this.kp = kp; this.ki = ki; this.kd = kd;
        this.maxSpeed = maxSpeed; this.sidePower = sidePower; this.maxThrustPN = maxThrustPN;
        this.brakeCoeff = brakeCoeff; this.proximityRange = proximityRange;
        this.cruiseAltitude = cruiseAltitude; this.redstoneRange = redstoneRange;
        this.altitudeOffset = altitudeOffset;
        this.searchMode = searchMode;
        this.minSearchRange = minSearchRange;
        this.maxSearchRange = maxSearchRange;
        this.manualX = manualX;
        this.manualY = manualY;
        this.manualZ = manualZ;
    }

    @Override
    protected void init() {
        clearWidgets();
        pageButtons.clear();

        int cx = this.width / 2;
        int tabW = 54, tabH = 16;
        int tabY = 16;
        int tabStartX = cx - (tabW * 3 + 4) / 2;

        // Page tabs
        tabPid = addRenderableWidget(Button.builder(Component.translatable("aero_reformation.guidance_warhead.tab_pid"),
                b -> switchPage(0)).pos(tabStartX, tabY).size(tabW, tabH).build());
        tabTarget = addRenderableWidget(Button.builder(Component.translatable("aero_reformation.guidance_warhead.tab_target"),
                b -> switchPage(1)).pos(tabStartX + tabW + 2, tabY).size(tabW, tabH).build());
        tabInfo = addRenderableWidget(Button.builder(Component.translatable("aero_reformation.guidance_warhead.tab_info"),
                b -> switchPage(2)).pos(tabStartX + tabW * 2 + 4, tabY).size(tabW, tabH).build());

        buildCurrentPage();

        // Bottom buttons (always visible)
        int btnY = this.height - 50;
        addRenderableWidget(Button.builder(Component.translatable("aero_reformation.guidance_warhead.reset"), b -> {
            kp = KP_DEF; ki = KI_DEF; kd = KD_DEF;
            maxSpeed = MAX_SPEED_DEF; sidePower = SIDE_POWER_DEF; maxThrustPN = THRUST_DEF;
            brakeCoeff = BRAKE_DEF; proximityRange = PROX_DEF;
            cruiseAltitude = ALT_DEF; redstoneRange = REDSTONE_DEF; altitudeOffset = OFFSET_DEF;
            searchMode = SEARCH_MODE_DEF; minSearchRange = MIN_RANGE_DEF; maxSearchRange = MAX_RANGE_DEF;
            manualX = MANUAL_X_DEF; manualY = MANUAL_Y_DEF; manualZ = MANUAL_Z_DEF;
            rebuildCurrentPage();
        }).pos(cx - 102, btnY).size(100, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .pos(cx + 2, btnY).size(100, 20).build());

        updateTabStyles();
    }

    private void switchPage(int page) {
        this.currentPage = page;
        rebuildCurrentPage();
    }

    private void rebuildCurrentPage() {
        for (Button b : pageButtons) {
            removeWidget(b);
        }
        pageButtons.clear();
        if (inputX != null) { removeWidget(inputX); inputX = null; }
        if (inputY != null) { removeWidget(inputY); inputY = null; }
        if (inputZ != null) { removeWidget(inputZ); inputZ = null; }
        buildCurrentPage();
        updateTabStyles();
    }

    private void buildCurrentPage() {
        int cx = this.width / 2;
        int y = 38;
        int bw = 200, bh = 20;

        switch (currentPage) {
            case 0 -> buildPidPage(cx, y, bw, bh);
            case 1 -> buildTargetPage(cx, y, bw, bh);
            case 2 -> {} // Info page: text only, rendered in render()
        }
    }

    private void buildPidPage(int cx, int y, int bw, int bh) {
        pageButtons.add(addRenderableWidget(Button.builder(txt("kp", kp), b -> {
            kp = step(kp, 0.1f, 0.1f, 5.0f);
            b.setMessage(txt("kp", kp));
        }).pos(cx - bw/2, y).size(bw, bh).build()));
        y += 23;

        pageButtons.add(addRenderableWidget(Button.builder(txt("ki", ki), b -> {
            ki = step(ki, 0.01f, 0.0f, 1.0f);
            b.setMessage(txt("ki", ki));
        }).pos(cx - bw/2, y).size(bw, bh).build()));
        y += 23;

        pageButtons.add(addRenderableWidget(Button.builder(txt("kd", kd), b -> {
            kd = step(kd, 0.05f, 0.0f, 2.0f);
            b.setMessage(txt("kd", kd));
        }).pos(cx - bw/2, y).size(bw, bh).build()));
        y += 23;

        pageButtons.add(addRenderableWidget(Button.builder(txt("max_speed", maxSpeed), b -> {
            maxSpeed = step(maxSpeed, 1.0f, 1.0f, 100.0f);
            b.setMessage(txt("max_speed", maxSpeed));
        }).pos(cx - bw/2, y).size(bw, bh).build()));
        y += 23;

        pageButtons.add(addRenderableWidget(Button.builder(txt("side_power", sidePower), b -> {
            sidePower = step(sidePower, 0.01f, 0.01f, 0.5f);
            b.setMessage(txt("side_power", sidePower));
        }).pos(cx - bw/2, y).size(bw, bh).build()));
        y += 23;

        pageButtons.add(addRenderableWidget(Button.builder(txt("thrust", maxThrustPN), b -> {
            maxThrustPN = step(maxThrustPN, 100.0f, 100.0f, 20000.0f);
            b.setMessage(txt("thrust", maxThrustPN));
        }).pos(cx - bw/2, y).size(bw, bh).build()));
        y += 23;

        pageButtons.add(addRenderableWidget(Button.builder(txt("brake", brakeCoeff), b -> {
            brakeCoeff = step(brakeCoeff, 0.01f, 0.0f, 1.0f);
            b.setMessage(txt("brake", brakeCoeff));
        }).pos(cx - bw/2, y).size(bw, bh).build()));
    }

    private void buildTargetPage(int cx, int y, int bw, int bh) {
        // Search mode toggle: 0=Mass, 1=Nearest, 2=Manual
        pageButtons.add(addRenderableWidget(Button.builder(txtMode(searchMode), b -> {
            searchMode = (searchMode + 1) % 3;
            rebuildCurrentPage(); // show/hide coord inputs vs range sliders
        }).pos(cx - bw/2, y).size(bw, bh).build()));
        y += 23;

        if (searchMode == 2) {
            // Manual coordinate mode: show text inputs at bottom
            buildCoordInputs(cx, this.height - 75);
        } else {
            // Mass/Nearest mode: show range sliders
            pageButtons.add(addRenderableWidget(Button.builder(txt("min_range", minSearchRange), b -> {
                minSearchRange = step(minSearchRange, 100.0f, 0.0f, 4000.0f);
                b.setMessage(txt("min_range", minSearchRange));
            }).pos(cx - bw/2, y).size(bw, bh).build()));
            y += 23;

            pageButtons.add(addRenderableWidget(Button.builder(txt("max_range", maxSearchRange), b -> {
                maxSearchRange = step(maxSearchRange, 100.0f, 0.0f, 4000.0f);
                b.setMessage(txt("max_range", maxSearchRange));
            }).pos(cx - bw/2, y).size(bw, bh).build()));
            y += 23;
        }

        pageButtons.add(addRenderableWidget(Button.builder(txt("proximity", proximityRange), b -> {
            proximityRange = step(proximityRange, 5.0f, 0.0f, 200.0f);
            b.setMessage(txt("proximity", proximityRange));
        }).pos(cx - bw/2, y).size(bw, bh).build()));
        y += 23;

        pageButtons.add(addRenderableWidget(Button.builder(txt("cruise_alt", cruiseAltitude), b -> {
            cruiseAltitude = step(cruiseAltitude, 20.0f, 0.0f, 500.0f);
            b.setMessage(txt("cruise_alt", cruiseAltitude));
        }).pos(cx - bw/2, y).size(bw, bh).build()));
        y += 23;

        pageButtons.add(addRenderableWidget(Button.builder(txt("redstone", redstoneRange), b -> {
            redstoneRange = step(redstoneRange, 5.0f, 0.0f, 200.0f);
            b.setMessage(txt("redstone", redstoneRange));
        }).pos(cx - bw/2, y).size(bw, bh).build()));
        y += 23;

        pageButtons.add(addRenderableWidget(Button.builder(txt("alt_offset", altitudeOffset), b -> {
            altitudeOffset = step(altitudeOffset, 5.0f, 0.0f, 100.0f);
            b.setMessage(txt("alt_offset", altitudeOffset));
        }).pos(cx - bw/2, y).size(bw, bh).build()));
    }

    private void buildCoordInputs(int cx, int y) {
        int ew = 60, eh = 16;
        int gap = 4;
        int startX = cx - (ew * 3 + gap * 2) / 2;

        // X input
        inputX = new EditBox(font, startX, y, ew, eh, Component.literal("X"));
        inputX.setValue(formatCoord(manualX));
        inputX.setResponder(s -> { try { manualX = Double.parseDouble(s); } catch (NumberFormatException ignored) {} });
        addRenderableWidget(inputX);

        // Y input
        inputY = new EditBox(font, startX + ew + gap, y, ew, eh, Component.literal("Y"));
        inputY.setValue(formatCoord(manualY));
        inputY.setResponder(s -> { try { manualY = Double.parseDouble(s); } catch (NumberFormatException ignored) {} });
        addRenderableWidget(inputY);

        // Z input
        inputZ = new EditBox(font, startX + (ew + gap) * 2, y, ew, eh, Component.literal("Z"));
        inputZ.setValue(formatCoord(manualZ));
        inputZ.setResponder(s -> { try { manualZ = Double.parseDouble(s); } catch (NumberFormatException ignored) {} });
        addRenderableWidget(inputZ);
    }

    private void updateTabStyles() {
        tabPid.active = currentPage != 0;
        tabTarget.active = currentPage != 1;
        tabInfo.active = currentPage != 2;
    }

    @Override
    public void onClose() {
        // Parse current input values before sending
        if (inputX != null) { try { manualX = Double.parseDouble(inputX.getValue()); } catch (NumberFormatException ignored) {} }
        if (inputY != null) { try { manualY = Double.parseDouble(inputY.getValue()); } catch (NumberFormatException ignored) {} }
        if (inputZ != null) { try { manualZ = Double.parseDouble(inputZ.getValue()); } catch (NumberFormatException ignored) {} }

        PacketDistributor.sendToServer(new GuidanceWarheadSettingsPacket(pos, kp, ki, kd, maxSpeed, sidePower, maxThrustPN,
                brakeCoeff, proximityRange, cruiseAltitude, redstoneRange, altitudeOffset,
                searchMode, minSearchRange, maxSearchRange, manualX, manualY, manualZ));
        super.onClose();
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);

        if (currentPage == 1 && searchMode == 2) {
            int cx = width / 2;
            int ew = 60, gap = 4;
            int startX = cx - (ew * 3 + gap * 2) / 2;
            int labelY = this.height - 75 - 12;
            g.drawCenteredString(font, "X", startX + ew / 2, labelY, 0xAAAAAA);
            g.drawCenteredString(font, "Y", startX + ew + gap + ew / 2, labelY, 0xAAAAAA);
            g.drawCenteredString(font, "Z", startX + (ew + gap) * 2 + ew / 2, labelY, 0xAAAAAA);
        }

        if (currentPage == 2) {
            int infoX = width / 2 - 100;
            int infoY = 40;
            for (int i = 1; i <= 11; i++) {
                var line = Component.translatable("aero_reformation.guidance_warhead.info_line" + i);
                if (line.getString().isEmpty()) { infoY += 4; continue; }
                g.drawString(font, line, infoX, infoY, 0xAAAAAA);
                infoY += 12;
            }
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private float step(float v, float step, float min, float max) {
        v += hasShiftDown() ? -step : step;
        return Math.clamp(v, min, max);
    }

    private static String formatCoord(double v) {
        return v == (long) v ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    private static Component txt(String key, float v) {
        String fmt;
        if (key.equals("kp") || key.equals("max_speed")) fmt = "%.1f";
        else if (key.equals("thrust")) fmt = "%.0f";
        else if (key.equals("ki") || key.equals("side_power") || key.equals("brake")) fmt = "%.2f";
        else if (key.equals("min_range") || key.equals("max_range")) fmt = "%.0f";
        else if (key.equals("proximity") || key.equals("cruise_alt") || key.equals("redstone") || key.equals("alt_offset")) fmt = "%.0f";
        else fmt = "%.2f";
        return Component.translatable("aero_reformation.guidance_warhead." + key,
                String.format(fmt, v));
    }

    private static Component txtMode(int mode) {
        String key = switch (mode) {
            case 0 -> "search_mass";
            case 1 -> "search_nearest";
            case 2 -> "search_manual";
            default -> "search_mass";
        };
        return Component.translatable("aero_reformation.guidance_warhead." + key);
    }
}
