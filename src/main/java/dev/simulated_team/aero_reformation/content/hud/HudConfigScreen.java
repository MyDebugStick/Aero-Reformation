package dev.simulated_team.aero_reformation.content.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * HUD config screen opened by pressing V while holding goggles.
 * - NEW mode: left-click a spot to create a text entry.
 * - SELECT mode: left-drag to transform. Drag handles on the selection frame
 *   resize / rotate (like a drawing app); dragging inside follows the current
 *   tool (move / scale / rotate). Right-click an entry to edit it.
 * - DRAW mode: left-drag to draw a point-to-point line.
 * A crosshair follows the mouse to help positioning.
 */
public class HudConfigScreen extends Screen {

    public enum Mode { NEW, SELECT, MOVE, DRAW }
    private enum DragOp { MOVE, SCALE_HANDLE, ROTATE_HANDLE }

    private Mode mode = Mode.SELECT;
    private HudEntry selected;
    private boolean uiHidden; // hide all other widgets while editing entries
    private Button hideButton;
    private DragOp dragOp;
    private boolean dragging;
    private double pressX, pressY;
    private float pressEntryX, pressEntryY, pressScale, pressRot;
    private float pressEndX, pressEndY;
    private float pressDist, pressAngle;

    // DRAW mode (drag a shape)
    private boolean drawDragging;
    private float drawStartX, drawStartY;
    private String currentShape = "line"; // line / rect / circle / triangle / diamond
    private boolean currentFilled;

    private HudColorPalette palette;
    private HudRenderers.ViewSnapshot lastSnap;

    public HudConfigScreen() {
        this(null);
    }

    public HudConfigScreen(HudEntry preselect) {
        super(Component.translatable("hud.aero_reformation.title"));
        this.selected = preselect;
    }

    @Override
    protected void init() {
        // Face due north (level) while the config screen is open so HUD
        // placement stays consistent.
        if (minecraft.player != null) {
            minecraft.player.setYRot(0.0F);
            minecraft.player.setXRot(0.0F);
        }
        int bw = 44, bh = 18, x = 4, y = 4, gap = 4;
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.new"), b -> mode = Mode.NEW)
                .bounds(x, y, bw, bh).build());
        x += bw + gap;
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.select"), b -> mode = Mode.SELECT)
                .bounds(x, y, bw, bh).build());
        x += bw + gap;
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.move"), b -> mode = Mode.MOVE)
                .bounds(x, y, bw, bh).build());
        x += bw + gap;
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.draw"), b -> mode = Mode.DRAW)
                .bounds(x, y, bw, bh).build());
        // Row 2: undo / delete / clear all / hide
        int y2 = y + bh + 4;
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.undo"), b -> doUndo())
                .bounds(4, y2, bw, bh).build());
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.delete"), b -> deleteSelected())
                .bounds(4 + bw + gap, y2, bw, bh).build());
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.clear"), b -> clearAll())
                .bounds(4 + (bw + gap) * 2, y2, bw, bh).build());
        hideButton = Button.builder(Component.translatable("hud.aero_reformation.hide"), b -> toggleHidden())
                .bounds(4 + (bw + gap) * 3, y2, bw, bh).build();
        addRenderableWidget(hideButton);
        // Row 3: draw shape kind + hollow/solid toggle
        int y3 = y2 + bh + 4;
        addRenderableWidget(Button.builder(shapeLabel(), b -> {
                    currentShape = switch (currentShape) {
                        case "line" -> "rect";
                        case "rect" -> "circle";
                        case "circle" -> "triangle";
                        case "triangle" -> "diamond";
                        default -> "line";
                    };
                    b.setMessage(shapeLabel());
                })
                .bounds(4, y3, 112, bh).build());
        addRenderableWidget(Button.builder(fillLabel(), b -> {
                    currentFilled = !currentFilled;
                    b.setMessage(fillLabel());
                })
                .bounds(4 + 112 + gap, y3, 60, bh).build());
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.ph_button"), b ->
                        minecraft.setScreen(new HudPlaceholderSetupScreen()))
                .bounds(4 + 112 + 60 + gap * 2, y3, 70, bh).build());
        // Row 4: helmet presets (save current HUD to the worn helmet / load from it)
        int y4 = y3 + bh + 4;
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.preset_save"), b -> savePresetToHelmet())
                .bounds(4, y4, 92, bh).build());
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.preset_load"), b -> loadPresetFromHelmet())
                .bounds(4 + 92 + gap, y4, 92, bh).build());

        // Main color palette (top-right, for the selected entry / new entries)
        palette = new HudColorPalette(this.width - 130, 4, 0xFFFFFFFF, false, true,
                c -> {
                    if (selected != null) {
                        selected.rainbow = false;
                        selected.color = c;
                        HudBoard.saveToPlayer(minecraft.player);
                    }
                },
                () -> {
                    if (selected != null) {
                        selected.rainbow = true;
                        HudBoard.saveToPlayer(minecraft.player);
                    }
                });
        addRenderableWidget(palette);
    }

    // ── helmet presets ──

    private void savePresetToHelmet() {
        if (!hasHelmet()) {
            minecraft.player.displayClientMessage(
                    Component.translatable("hud.aero_reformation.preset_no_helmet"), true);
            return;
        }
        HudPresetStore.savePreset(minecraft.player);
        minecraft.player.displayClientMessage(
                Component.translatable("hud.aero_reformation.preset_saved"), true);
    }

    private void loadPresetFromHelmet() {
        if (!hasHelmet()) {
            minecraft.player.displayClientMessage(
                    Component.translatable("hud.aero_reformation.preset_no_helmet"), true);
            return;
        }
        // Ask the server for the preset bound to this exact helmet; response applies it
        HudPresetStore.requestLoad(minecraft.player);
        minecraft.player.displayClientMessage(
                Component.translatable("hud.aero_reformation.preset_loading"), true);
    }

    private boolean hasHelmet() {
        return minecraft.player != null
                && !minecraft.player.getInventory().getArmor(3).isEmpty();
    }

    // ── UI hiding ──

    /** Hide/show every widget except the toggle button; the active mode is kept. */
    private void toggleHidden() {
        uiHidden = !uiHidden;
        for (Renderable r : this.renderables) {
            if (r instanceof AbstractWidget aw && aw != hideButton) {
                aw.visible = !uiHidden;
            }
        }
        hideButton.setMessage(Component.translatable(
                uiHidden ? "hud.aero_reformation.show" : "hud.aero_reformation.hide"));
        if (uiHidden && minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("hud.aero_reformation.hidden_on"), true);
        }
    }

    // ── creation ──

    private void deleteSelected() {
        if (selected != null) {
            HudBoard.pushUndo(minecraft.player);
            HudBoard.removeEntry(minecraft.player, selected);
            selected = null;
        }
    }

    private void clearAll() {
        HudBoard.pushUndo(minecraft.player);
        HudBoard.clearEntries(minecraft.player);
        selected = null;
    }

    private void doUndo() {
        if (HudBoard.undo(minecraft.player)) {
            selected = null;
        }
    }

    private void createTextEntry(double mx, double my) {
        HudBoard.pushUndo(minecraft.player);
        HudEntry e = new HudEntry();
        e.x = (float) mx;
        e.y = (float) my;
        e.text = "新条目";
        e.color = palette.getCurrentColor();
        HudBoard.addEntry(minecraft.player, e);
        selected = e;
    }

    private void createLineEntry(float x1, float y1, float x2, float y2) {
        HudBoard.pushUndo(minecraft.player);
        HudEntry e = new HudEntry();
        e.type = HudEntry.Type.LINE;
        // Normalize so x/y is always the top-left and endX/endY the bottom-right.
        // Reversed (dragged right-to-left / bottom-to-top) lines would otherwise
        // render mirrored from their peers, which looks like an even/odd split.
        e.x = Math.min(x1, x2);
        e.y = Math.min(y1, y2);
        e.endX = Math.max(x1, x2);
        e.endY = Math.max(y1, y2);
        e.lineWidth = 2;
        e.color = palette.getCurrentColor();
        HudBoard.addEntry(minecraft.player, e);
        selected = e;
    }

    private void createShapeEntry(float x1, float y1, float x2, float y2) {
        HudBoard.pushUndo(minecraft.player);
        HudEntry e = new HudEntry();
        e.type = HudEntry.Type.SHAPE;
        e.x = Math.min(x1, x2);   // normalize so x/y is top-left
        e.y = Math.min(y1, y2);
        e.endX = Math.max(x1, x2);
        e.endY = Math.max(y1, y2);
        e.shape = currentShape;
        e.filled = currentFilled;
        e.lineWidth = 2;
        e.color = palette.getCurrentColor();
        HudBoard.addEntry(minecraft.player, e);
        selected = e;
    }

    private Component shapeLabel() {
        return Component.translatable("hud.aero_reformation.shape_label")
                .append(Component.translatable("hud.aero_reformation.shape_" + currentShape));
    }

    private Component fillLabel() {
        return Component.translatable("hud.aero_reformation.fill_label")
                .append(Component.translatable("hud.aero_reformation." + (currentFilled ? "filled" : "hollow")));
    }

    // ── hit testing ──

    /**
     * Run a block with a COPY of the entry placed at its resolved projected
     * position (and roll-compensated rotation), so hit-testing, selection
     * frames and handles match the entry's actual on-screen spot without ever
     * mutating the stored entry. Anchored entries behind the camera run nothing.
     */
    private void withProjected(HudEntry e, java.util.function.Consumer<HudEntry> c) {
        withProjected(e, lastSnap, c);
    }

    private void withProjected(HudEntry e, HudRenderers.ViewSnapshot snap, java.util.function.Consumer<HudEntry> c) {
        if (snap == null) {
            c.accept(e);
            return;
        }
        Vector2f sp = HudRenderers.resolveScreenPos(e, minecraft, snap);
        if (sp == null) return;
        if ("screen".equals(e.anchor)) {
            c.accept(e);
            return;
        }
        // Top-left model: sp is the entry's top-left, endpoint follows
        HudEntry tmp = e.copy();
        tmp.x = sp.x();
        tmp.y = sp.y();
        tmp.endX = e.endX + (sp.x() - e.x);
        tmp.endY = e.endY + (sp.y() - e.y);
        tmp.rotation = e.rotation + HudRenderers.screenRotation(e, minecraft, snap);
        c.accept(tmp);
    }

    private HudEntry hitTest(double mx, double my) {
        List<HudEntry> entries = HudBoard.getEntries(minecraft.player);
        for (int i = entries.size() - 1; i >= 0; i--) {
            HudEntry e = entries.get(i);
            boolean[] hit = {false};
            withProjected(e, tmp -> {
                if (hitEntryAt(tmp, mx, my)) hit[0] = true;
            });
            if (hit[0]) return e;
        }
        return null;
    }

    private boolean hitEntryAt(HudEntry e, double mx, double my) {
        if (e.type == HudEntry.Type.LINE) {
            return HudRenderers.containsLine(e, minecraft, mx, my, Math.max(5.0, e.lineWidth + 2));
        }
        return HudRenderers.getBounds(e, minecraft).contains(mx, my);
    }

    /** Find an entry whose selection handle (rotate/resize) is under the cursor. */
    private HudEntry findHandleEntry(double mx, double my) {
        List<HudEntry> entries = HudBoard.getEntries(minecraft.player);
        for (int i = entries.size() - 1; i >= 0; i--) {
            HudEntry e = entries.get(i);
            boolean[] hit = {false};
            withProjected(e, tmp -> {
                if (handleAt(tmp, mx, my)) hit[0] = true;
            });
            if (hit[0]) return e;
        }
        return null;
    }

    private boolean handleAt(HudEntry e, double mx, double my) {
        if (near(HudRenderers.getRotateHandle(e, minecraft), mx, my)) return true;
        for (HudRenderers.Handle h : HudRenderers.getResizeHandles(e, minecraft)) {
            if (near(h, mx, my)) return true;
        }
        return false;
    }

    private boolean near(HudRenderers.Handle h, double mx, double my) {
        return Math.abs(mx - h.x()) <= 4 && Math.abs(my - h.y()) <= 4;
    }

    private boolean nearAny(HudRenderers.Handle[] hs, double mx, double my) {
        for (HudRenderers.Handle h : hs) {
            if (near(h, mx, my)) return true;
        }
        return false;
    }

    /**
     * Constrain the drag endpoint when Shift is held (drawing-app style):
     * lines snap to 0/45/90 degree steps, shapes become squares/circles.
     */
    private float[] constrainDrawPoint(float sx, float sy, double mx, double my) {
        if (!Screen.hasShiftDown()) return new float[]{(float) mx, (float) my};
        double dx = mx - sx, dy = my - sy;
        if ("line".equals(currentShape)) {
            double ang = Math.toDegrees(Math.atan2(dy, dx));
            double snapped = Math.round(ang / 45.0) * 45.0;
            double len = Math.hypot(dx, dy);
            double rad = Math.toRadians(snapped);
            return new float[]{(float) (sx + len * Math.cos(rad)), (float) (sy + len * Math.sin(rad))};
        }
        // Shapes: force |dx| == |dy| so rectangles become squares / circles
        double m = Math.max(Math.abs(dx), Math.abs(dy));
        return new float[]{(float) (sx + Math.signum(dx) * m), (float) (sy + Math.signum(dy) * m)};
    }

    private float distFromCenter(HudEntry e, double mx, double my) {
        return (float) Math.hypot(mx - HudRenderers.centerX(e, minecraft), my - HudRenderers.centerY(e, minecraft));
    }

    private float angleFromCenter(HudEntry e, double mx, double my) {
        return (float) Math.toDegrees(Math.atan2(my - HudRenderers.centerY(e, minecraft),
                mx - HudRenderers.centerX(e, minecraft)));
    }

    // ── input ──

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Let buttons consume clicks first
        if (super.mouseClicked(mx, my, button)) return true;

        if (mode == Mode.NEW && button == 0) {
            createTextEntry(mx, my);
            return true;
        }
        if (mode == Mode.NEW && button == 1) {
            // Right-click also opens the editor on an existing entry
            HudEntry hit = hitTest(mx, my);
            if (hit != null) {
                selected = hit;
                minecraft.setScreen(new HudEntryEditScreen(hit));
            }
            return true;
        }
        if (mode == Mode.DRAW && button == 0) {
            drawDragging = true;
            drawStartX = (float) mx;
            drawStartY = (float) my;
            return true;
        }
        if (mode == Mode.DRAW && button == 1) {
            // Right-click also opens the editor on an existing entry
            HudEntry hit = hitTest(mx, my);
            if (hit != null) {
                selected = hit;
                minecraft.setScreen(new HudEntryEditScreen(hit));
            }
            return true;
        }
        if (mode == Mode.SELECT || mode == Mode.MOVE) {
            if (button == 0) {
                if (mode == Mode.MOVE) {
                    // Move tool: pick the entry (no handles) and start moving it only
                    HudEntry hit = hitTest(mx, my);
                    if (hit != null) {
                        selected = hit;
                        HudBoard.pushUndo(minecraft.player);
                        dragging = true;
                        pressX = mx;
                        pressY = my;
                        pressEntryX = hit.x;
                        pressEntryY = hit.y;
                        pressEndX = hit.endX;
                        pressEndY = hit.endY;
                        dragOp = DragOp.MOVE;
                    } else {
                        selected = null;
                    }
                    return true;
                }
                HudEntry hit = findHandleEntry(mx, my);
                if (hit == null) hit = hitTest(mx, my);
                if (hit != null) {
                    selected = hit;
                    HudBoard.pushUndo(minecraft.player);
                    dragging = true;
                    pressX = mx;
                    pressY = my;
                    pressEntryX = hit.x;
                    pressEntryY = hit.y;
                    pressEndX = hit.endX;
                    pressEndY = hit.endY;
                    pressScale = hit.scale;
                    pressRot = hit.rotation;
                    pressDist = distFromCenter(hit, mx, my);
                    pressAngle = angleFromCenter(hit, mx, my);
                    if (near(HudRenderers.getRotateHandle(hit, minecraft), mx, my)) {
                        dragOp = DragOp.ROTATE_HANDLE;
                    } else if (nearAny(HudRenderers.getResizeHandles(hit, minecraft), mx, my)) {
                        dragOp = DragOp.SCALE_HANDLE;
                    } else {
                        dragOp = DragOp.MOVE;
                    }
                } else {
                    selected = null;
                }
                return true;
            }
            if (button == 1) {
                HudEntry hit = hitTest(mx, my);
                if (hit != null) {
                    selected = hit;
                    minecraft.setScreen(new HudEntryEditScreen(hit));
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (drawDragging && button == 0) {
            float[] p = constrainDrawPoint(drawStartX, drawStartY, mx, my);
            if ("line".equals(currentShape)) {
                createLineEntry(drawStartX, drawStartY, p[0], p[1]);
            } else {
                createShapeEntry(drawStartX, drawStartY, p[0], p[1]);
            }
            drawDragging = false;
            return true;
        }
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && selected != null && (mode == Mode.SELECT || mode == Mode.MOVE) && button == 0) {
            switch (dragOp) {
                case MOVE -> {
                    // Move the whole entry: shift the endpoint too so shapes/lines
                    // translate instead of stretching.
                    selected.x = pressEntryX + (float) (mx - pressX);
                    selected.y = pressEntryY + (float) (my - pressY);
                    selected.endX = pressEndX + (float) (mx - pressX);
                    selected.endY = pressEndY + (float) (my - pressY);
                }
                case SCALE_HANDLE -> {
                    if (Screen.hasControlDown()) {
                        // Proportional (uniform) scale, like drawing apps
                        float d = distFromCenter(selected, mx, my);
                        selected.scale = Math.max(0.2F, pressScale + (d - pressDist) / 50F);
                    } else if (selected.type == HudEntry.Type.SHAPE || selected.type == HudEntry.Type.LINE) {
                        // Free resize around the entry's on-screen center
                        Vector2f sp = HudRenderers.resolveScreenPos(selected, minecraft, lastSnap);
                        if (sp != null && selected.scale > 1e-6f) {
                            // Top-left model: sp is the top-left, center = sp + half size
                            float halfW = Math.abs(selected.endX - selected.x) / 2f * selected.scale;
                            float halfH = Math.abs(selected.endY - selected.y) / 2f * selected.scale;
                            float scx = sp.x() + halfW;
                            float scy = sp.y() + halfH;
                            float newHW = Math.max(1, Math.abs((float) mx - scx));
                            float newHH = Math.max(1, Math.abs((float) my - scy));
                            // keep center, move both ends
                            float cxp = (selected.x + selected.endX) / 2f;
                            float cyp = (selected.y + selected.endY) / 2f;
                            selected.x = cxp - newHW;
                            selected.y = cyp - newHH;
                            selected.endX = cxp + newHW;
                            selected.endY = cyp + newHH;
                        }
                    } else {
                        // TEXT/BAR: proportional scale only
                        float d = distFromCenter(selected, mx, my);
                        selected.scale = Math.max(0.2F, pressScale + (d - pressDist) / 50F);
                    }
                }
                case ROTATE_HANDLE -> {
                    float a = angleFromCenter(selected, mx, my);
                    selected.rotation = (pressRot + (a - pressAngle)) % 360F;
                }
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_Z && Screen.hasControlDown()) {
            doUndo();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── rendering ──

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No dimming: the config screen keeps the world clearly visible so the
        // player can place / draw entries over the scene.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Entries (with editor backplate so they stay readable)
        lastSnap = HudRenderers.captureStable(minecraft);
        List<HudEntry> entries = HudBoard.getEntries(minecraft.player);
        for (HudEntry e : entries) {
            HudRenderers.renderProjected(g, e, minecraft, true, lastSnap);
        }

        // Selection frame (follows rotation) + handles, drawn on the SAME
        // projected copy as the entry itself, so the frame never drifts.
        // The selected entry is re-rendered here together with its frame so it
        // stays clearly visible above the other entries.
        if (selected != null) {
            withProjected(selected, lastSnap, tmp -> {
                HudRenderers.renderEntry(g, tmp, minecraft, true);
                HudRenderers.renderSelectionFrame(g, tmp, minecraft, 0xFFFFFF55);
                if (mode != Mode.MOVE) {
                    // Move tool hides the handles so entries cannot be resized by accident
                    for (HudRenderers.Handle h : HudRenderers.getResizeHandles(tmp, minecraft)) {
                        HudRenderers.renderHandle(g, h, 0xFFFFFFFF);
                    }
                    HudRenderers.renderHandle(g, HudRenderers.getRotateHandle(tmp, minecraft), 0xFF55FF55);
                }
            });
        }

        // Draw mode: live shape preview (Shift applies the same constraints)
        if (mode == Mode.DRAW && drawDragging) {
            float[] p = constrainDrawPoint(drawStartX, drawStartY, mouseX, mouseY);
            if ("line".equals(currentShape)) {
                HudRenderers.drawLineFast(g, drawStartX, drawStartY, p[0], p[1], 2, 0x88FFFFFF);
            } else {
                HudRenderers.drawShape(g, Math.min(drawStartX, p[0]), Math.min(drawStartY, p[1]),
                        Math.max(drawStartX, p[0]), Math.max(drawStartY, p[1]),
                        currentShape, currentFilled, 2, 0x88FFFFFF);
            }
            HudRenderers.renderHandle(g, new HudRenderers.Handle(drawStartX, drawStartY), 0xFFFFFFFF);
        }

        // Crosshair following the mouse
        g.fill(0, mouseY, this.width, mouseY + 1, 0x80FFFFFF);
        g.fill(mouseX, 0, mouseX + 1, this.height, 0x80FFFFFF);

        // Sync palette with the selected entry (hidden while UI is collapsed)
        palette.setCurrentColor(selected != null ? selected.color : 0xFFFFFFFF);
        palette.setRainbow(selected != null && selected.rainbow);
        palette.visible = !uiHidden && selected != null;

        // Buttons on top, then hint text
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(minecraft.font, hintText(), 4, this.height - 12, 0xFFFFFFFF);
    }

    private Component hintText() {
        return switch (mode) {
            case NEW -> Component.translatable("hud.aero_reformation.hint_new");
            case DRAW -> Component.translatable("hud.aero_reformation.hint_draw");
            case SELECT -> Component.translatable("hud.aero_reformation.hint_select");
            case MOVE -> Component.translatable("hud.aero_reformation.hint_move");
        };
    }
}
