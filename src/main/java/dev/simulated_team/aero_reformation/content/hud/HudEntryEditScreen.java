package dev.simulated_team.aero_reformation.content.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Edit screen for a single HUD entry: text content, render type, color palette
 * (with rainbow), info color palette, line width (LINE), block binding, live preview.
 */
public class HudEntryEditScreen extends Screen {

    /** Pending placeholder insertion set by the placeholder list screen. */
    public static String pendingInsertToken = null;
    public static int pendingInsertPos = 0;
    public static boolean pendingInsertToSuffix = false;

    private final HudEntry entry;
    private EditBox textBox;
    private EditBox suffixBox;
    private HudColorPalette colorPalette;
    private HudColorPalette infoPalette;
    private Button lineWidthButton;
    private Button shapeTypeButton;
    private Button filledButton;
    private EditBox barMaxBox;
    private Button textEnlargeButton;
    private Button suffixEnlargeButton;
    private Button anchorButton;
    private Button horizonDistButton;
    private Button worldPosButton;
    private boolean focusApplied;

    /** Anchor mode staged by the toggle; applied only when "Done" is pressed. */
    private String pendingAnchor = null;
    /** Horizon distance staged the same way; -1 = unchanged. */
    private double pendingHorizonDist = -1;

    public HudEntryEditScreen(HudEntry entry) {
        super(Component.translatable("hud.aero_reformation.edit_title"));
        this.entry = entry;
    }

    @Override
    protected void init() {
        // Record pre-edit state so undo can also revert these changes
        HudBoard.pushUndo(minecraft.player);

        int cw = this.width / 2;

        // Opacity slider (top row, applies to every entry type)
        addRenderableWidget(new HudAlphaSlider(cw - 100, 8, 200, 20, entry));

        // Apply a placeholder token chosen in the placeholder list screen,
        // inserted into whichever box had focus, at the recorded cursor position.
        int insertCursor = -1;
        if (pendingInsertToken != null) {
            String target = pendingInsertToSuffix ? entry.suffix : entry.text;
            int pos = Math.min(Math.max(0, pendingInsertPos), target.length());
            target = target.substring(0, pos) + pendingInsertToken + target.substring(pos);
            if (pendingInsertToSuffix) {
                entry.suffix = target;
            } else {
                entry.text = target;
            }
            insertCursor = pos + pendingInsertToken.length();
            pendingInsertToken = null;
        }
        textBox = new EditBox(this.font, cw - 100, 30, 200, 20,
                Component.translatable("hud.aero_reformation.text"));
        textBox.setMaxLength(64);
        textBox.setHint(Component.translatable("hud.aero_reformation.text_hint"));
        textBox.setValue(entry.text);
        // Enlarge: open the full-width text editor for long if-else expressions
        textEnlargeButton = Button.builder(Component.translatable("hud.aero_reformation.enlarge"), b -> {
                    entry.text = textBox.getValue();
                    minecraft.setScreen(new HudBigTextScreen(entry, false));
                })
                .bounds(cw + 102, 30, 44, 20).build();
        addRenderableWidget(textEnlargeButton);
        if (!pendingInsertToSuffix && insertCursor >= 0) {
            textBox.setCursorPosition(insertCursor);
        }
        textBox.setFocused(true);
        addRenderableWidget(textBox);

        // Placeholder insertion button: opens the full placeholder list (right side)
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.ph_insert"), b -> {
                    save(); // cache whatever the user already typed before leaving
                    if (suffixBox != null && suffixBox.isFocused()) {
                        pendingInsertToSuffix = true;
                        pendingInsertPos = suffixBox.getCursorPosition();
                    } else {
                        pendingInsertToSuffix = false;
                        pendingInsertPos = textBox.getCursorPosition();
                    }
                    minecraft.setScreen(new HudPlaceholderListScreen(entry));
                })
                .bounds(this.width - 130, 140, 100, 18).build());

        // Extra content appended after the bound info (or directly after text when unbound)
        suffixBox = new EditBox(this.font, cw - 100, 70, 200, 20,
                Component.translatable("hud.aero_reformation.suffix"));
        suffixBox.setMaxLength(64);
        suffixBox.setHint(Component.translatable("hud.aero_reformation.suffix_hint"));
        suffixBox.setValue(entry.suffix);
        suffixEnlargeButton = Button.builder(Component.translatable("hud.aero_reformation.enlarge"), b -> {
                    entry.suffix = suffixBox.getValue();
                    minecraft.setScreen(new HudBigTextScreen(entry, true));
                })
                .bounds(cw + 102, 70, 44, 20).build();
        addRenderableWidget(suffixEnlargeButton);
        if (pendingInsertToSuffix && insertCursor >= 0) {
            suffixBox.setCursorPosition(insertCursor);
        }
        addRenderableWidget(suffixBox);

        // Type toggle (TEXT -> BAR -> LINE -> TEXT)
        addRenderableWidget(Button.builder(
                        typeLabel(),
                        b -> {
                            entry.type = switch (entry.type) {
                                case TEXT -> HudEntry.Type.BAR;
                                case BAR -> HudEntry.Type.LINE;
                                case LINE -> HudEntry.Type.SHAPE;
                                case SHAPE -> HudEntry.Type.TEXT;
                            };
                            b.setMessage(typeLabel());
                            save();
                        })
                .bounds(cw - 100, 50, 200, 20).build());

        // Main color palette (right side, colors the text before the ':') with rainbow cell
        colorPalette = new HudColorPalette(this.width - 130, 34, entry.color, entry.rainbow, true,
                c -> {
                    entry.rainbow = false;
                    entry.color = c;
                    save();
                },
                () -> {
                    entry.rainbow = true;
                    save();
                });
        addRenderableWidget(colorPalette);

        // Info (bound data, after the ':') color palette - TEXT only
        infoPalette = new HudColorPalette(this.width - 130, 100, entry.bindColor, false, false,
                c -> {
                    entry.bindColor = c;
                    save();
                },
                () -> { });
        addRenderableWidget(infoPalette);

        // Line width - LINE only
        lineWidthButton = Button.builder(lineWidthLabel(), b -> {
                    entry.lineWidth = switch (entry.lineWidth) {
                        case 1 -> 2;
                        case 2 -> 4;
                        case 4 -> 8;
                        default -> 1;
                    };
                    b.setMessage(lineWidthLabel());
                    save();
                })
                .bounds(cw - 100, 90, 200, 20)
                .build();
        addRenderableWidget(lineWidthButton);

        // Shape kind - SHAPE only (same row as the suffix box, which is TEXT-only)
        shapeTypeButton = Button.builder(shapeTypeLabel(), b -> {
                    entry.shape = switch (entry.shape) {
                        case "rect" -> "circle";
                        case "circle" -> "triangle";
                        case "triangle" -> "diamond";
                        default -> "rect";
                    };
                    b.setMessage(shapeTypeLabel());
                    save();
                })
                .bounds(cw - 100, 70, 200, 20)
                .build();
        addRenderableWidget(shapeTypeButton);

        // Hollow / solid toggle - SHAPE only
        filledButton = Button.builder(filledLabel(), b -> {
                    entry.filled = !entry.filled;
                    b.setMessage(filledLabel());
                    save();
                })
                .bounds(cw - 100, 110, 200, 20)
                .build();
        addRenderableWidget(filledButton);

        // Bar max value - BAR only: direct numeric input, saved live
        barMaxBox = new EditBox(this.font, cw - 100, 90, 200, 20,
                Component.translatable("hud.aero_reformation.bar_max"));
        barMaxBox.setMaxLength(10);
        barMaxBox.setHint(Component.translatable("hud.aero_reformation.bar_max_hint"));
        barMaxBox.setValue(formatBarMax(entry.barMax));
        barMaxBox.setResponder(s -> {
            try {
                float v = Float.parseFloat(s.trim());
                if (v > 0 && Float.isFinite(v)) {
                    entry.barMax = v;
                    save();
                }
            } catch (NumberFormatException ignored) {
            }
        });
        addRenderableWidget(barMaxBox);

        // Anchor mode: screen / horizon / world. The toggle only STAGES the next
        // mode (preview + controls react via effectiveAnchor()); the entry itself
        // is not touched until "Done"/Enter commits it, so the HUD is not
        // modified live while editing.
        anchorButton = Button.builder(anchorLabel(), b -> {
                    pendingAnchor = switch (effectiveAnchor()) {
                        case "screen" -> "horizon";
                        case "horizon" -> "world";
                        default -> "screen";
                    };
                    b.setMessage(anchorLabel());
                })
                .bounds(cw - 100, 130, 200, 20)
                .build();
        addRenderableWidget(anchorButton);

        // Horizon distance - horizon only (staged like the anchor mode)
        horizonDistButton = Button.builder(horizonDistLabel(), b -> {
                    double next = 8;
                    for (double d : HORIZON_DISTS) {
                        if (d > effectiveHorizonDist() + 0.01) {
                            next = d;
                            break;
                        }
                    }
                    pendingHorizonDist = next;
                    b.setMessage(horizonDistLabel());
                })
                .bounds(cw - 100, 150, 200, 20)
                .build();
        addRenderableWidget(horizonDistButton);

        // World anchor controls - world only: pick a block (auto-attaches to its physics body).
        // The pick flow needs the world mode active, so any staged anchor is committed first.
        worldPosButton = Button.builder(Component.translatable("hud.aero_reformation.pick_world"), b -> {
                    commitAnchorStaging();
                    HudPickHandler.beginWorldAnchorPick(entry);
                    onClose();                // save text first
                    minecraft.setScreen(null); // close GUI, await middle click
                })
                .bounds(cw - 100, 150, 200, 20)
                .build();
        addRenderableWidget(worldPosButton);

        // Bind to target (block or entity)
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.bind_block"), b -> {
                    HudPickHandler.beginPick(entry);
                    onClose();                // save text first
                    minecraft.setScreen(null); // close GUI, await middle click
                })
                .bounds(cw - 100, 170, 200, 20).build());

        if (entry.bindPos != null || entry.bindEntityUuid != null) {
            if (entry.bindEntityUuid == null) {
                // Fill in a default info source for older entries (block binds only)
                if (entry.bindSource.isEmpty() && minecraft.level != null) {
                    HudBindings.BindOption def = HudBindings.defaultOption(
                            HudBindings.detectOptions(minecraft.level, entry.bindPos));
                    if (def != null) {
                        entry.bindSource = def.source();
                        entry.bindKey = def.key();
                    }
                }
                // Cycle through the available info sources (block binds only)
                addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.cycle_info"), b -> cycleInfo())
                        .bounds(cw - 100, 190, 90, 20).build());
                // Open the NBT browser to pick a specific NBT key
                addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.nbt_browse"), b ->
                                HudNbtCache.requestNbt(entry, entry.bindPos))
                        .bounds(cw - 10, 190, 110, 20).build());
            } else {
                // Entity binds always read an NBT path picked in the browser
                addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.nbt_browse"), b ->
                                HudNbtCache.requestNbtEntity(entry, entry.bindEntityUuid))
                        .bounds(cw - 100, 190, 200, 20).build());
            }
            addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.unbind"), b -> {
                        entry.bindPos = null;
                        entry.bindEntityUuid = null;
                        entry.bindSource = "";
                        entry.bindKey = "";
                        save();
                    })
                    .bounds(cw - 100, 210, 200, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.delete"), b -> {
                    HudBoard.removeEntry(minecraft.player, entry);
                    minecraft.setScreen(new HudConfigScreen());
                })
                .bounds(cw - 100, 232, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.done"), b -> {
                    commitAnchorStaging(); // apply the staged anchor mode/distance
                    save();                // commit the text / suffix edits before returning
                    minecraft.setScreen(new HudConfigScreen(entry));
                })
                .bounds(cw + 10, 232, 90, 20).build());
    }

    private Component typeLabel() {
        return Component.translatable("hud.aero_reformation.type_label")
                .append(switch (entry.type) {
                    case TEXT -> Component.translatable("hud.aero_reformation.type_text");
                    case BAR -> Component.translatable("hud.aero_reformation.type_bar");
                    case LINE -> Component.translatable("hud.aero_reformation.type_line");
                    case SHAPE -> Component.translatable("hud.aero_reformation.type_shape");
                });
    }

    private Component shapeTypeLabel() {
        return Component.translatable("hud.aero_reformation.shape_label")
                .append(Component.translatable("hud.aero_reformation.shape_" + entry.shape));
    }

    private Component filledLabel() {
        return Component.translatable("hud.aero_reformation.fill_label")
                .append(Component.translatable("hud.aero_reformation." + (entry.filled ? "filled" : "hollow")));
    }

    private static final double[] HORIZON_DISTS = {8, 16, 24, 32, 48, 64, 96};

    /** Anchor mode currently effective (staged value wins over the entry's). */
    private String effectiveAnchor() {
        return pendingAnchor != null ? pendingAnchor : entry.anchor;
    }

    /** Horizon distance currently effective (staged value wins over the entry's). */
    private double effectiveHorizonDist() {
        return pendingHorizonDist >= 0 ? pendingHorizonDist : entry.horizonDist;
    }

    /** Apply the staged anchor mode/distance to the entry (with position conversion). */
    private void commitAnchorStaging() {
        if (pendingAnchor != null) {
            applyAnchorChange(entry, pendingAnchor);
            entry.anchor = pendingAnchor;
            pendingAnchor = null;
        }
        if (pendingHorizonDist >= 0) {
            entry.horizonDist = pendingHorizonDist;
            pendingHorizonDist = -1;
        }
    }

    /** Convert an entry to the given anchor mode while keeping its screen spot/size. */
    private void applyAnchorChange(HudEntry e, String next) {
        if (next.equals(e.anchor)) return;
        boolean isShapeLike = e.type == HudEntry.Type.LINE || e.type == HudEntry.Type.SHAPE;
        var cur = HudRenderers.resolveScreenPos(e, minecraft); // old-mode position
        if ("screen".equals(next)) {
            // Back to screen: keep the current on-screen spot as the top-left;
            // the endpoint follows so the shape keeps size.
            float sizeX = e.endX - e.x;
            float sizeY = e.endY - e.y;
            e.x = cur != null ? cur.x() : 0;
            e.y = cur != null ? cur.y() : 0;
            if (isShapeLike) {
                e.endX = e.x + sizeX;
                e.endY = e.y + sizeY;
            }
        } else {
            // Horizon/world: capture the drawn size FIRST (before x/y are rewritten
            // to relative offsets) so shapes keep their real dimensions.
            float sizeX = Math.abs(e.endX - e.x);
            float sizeY = Math.abs(e.endY - e.y);
            boolean dirX = e.endX >= e.x;
            boolean dirY = e.endY >= e.y;
            if (cur != null) {
                var snap = HudRenderers.ViewSnapshot.capture(minecraft);
                if (snap != null) {
                    double hd = e.horizonDist;
                    e.horizonDist = effectiveHorizonDist();
                    var anchor = HudRenderers.projectToScreen(
                            HudRenderers.horizonAnchor(e, snap), snap, minecraft);
                    e.horizonDist = hd;
                    if (anchor != null) {
                        e.x = cur.x() - anchor.x;
                        e.y = cur.y() - anchor.y;
                    }
                }
            }
            if (isShapeLike) {
                e.endX = e.x + (dirX ? sizeX : -sizeX);
                e.endY = e.y + (dirY ? sizeY : -sizeY);
            }
        }
    }

    private Component anchorLabel() {
        return Component.translatable("hud.aero_reformation.anchor_label")
                .append(Component.translatable("hud.aero_reformation.anchor_" + effectiveAnchor()));
    }

    private Component horizonDistLabel() {
        return Component.translatable("hud.aero_reformation.horizon_dist", (int) effectiveHorizonDist());
    }

    private static String formatBarMax(float v) {
        return v == (int) v ? String.valueOf((int) v) : String.valueOf(v);
    }

    private Component lineWidthLabel() {
        return Component.translatable("hud.aero_reformation.line_width", entry.lineWidth);
    }

    private void cycleInfo() {
        if (minecraft.level == null || entry.bindPos == null) return;
        var options = HudBindings.detectOptions(minecraft.level, entry.bindPos);
        if (options.isEmpty()) return;
        int idx = 0;
        for (int i = 0; i < options.size(); i++) {
            var o = options.get(i);
            if (o.source().equals(entry.bindSource) && o.key().equals(entry.bindKey)) {
                idx = i;
                break;
            }
        }
        var next = options.get((idx + 1) % options.size());
        entry.bindSource = next.source();
        entry.bindKey = next.key();
        save();
    }

    private String currentInfoLabel() {
        if (entry.bindPos == null) return "";
        if (minecraft.level == null) return entry.bindKey;
        for (var o : HudBindings.detectOptions(minecraft.level, entry.bindPos)) {
            if (o.source().equals(entry.bindSource) && o.key().equals(entry.bindKey)) return o.label();
        }
        return entry.bindKey;
    }

    private void save() {
        entry.text = textBox.getValue();
        entry.suffix = suffixBox.getValue();
        HudBoard.saveToPlayer(minecraft.player);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / Numpad Enter
            commitAnchorStaging();
            save();
            minecraft.setScreen(new HudConfigScreen(entry));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No dimming: keep the world visible behind the edit screen.
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return super.mouseClicked(mx, my, button);
    }

    /** Render a static preview of the entry's current appearance in the left panel. */
    private void renderPreview(GuiGraphics g) {
        int cw = this.width / 2;
        int px = 10, py = 30, pw = Math.max(40, cw - 120), ph = 110;
        g.fill(px, py, px + pw, py + ph, 0x70000000);
        g.drawString(font, Component.translatable("hud.aero_reformation.preview"),
                px + 2, py + 2, 0x888888);

        // Temporarily apply the staged anchor mode/distance so the preview shows
        // what the entry will look like once committed. Capture the ORIGINAL
        // geometry first so every field (x/y/endX/endY too) is restored below.
        String oldAnchor = entry.anchor;
        double oldDist = entry.horizonDist;
        float ox = entry.x, oy = entry.y, oeX = entry.endX, oeY = entry.endY;
        boolean staged = pendingAnchor != null || pendingHorizonDist >= 0;
        if (staged) {
            entry.horizonDist = effectiveHorizonDist();
            if (pendingAnchor != null) {
                applyAnchorChange(entry, pendingAnchor);
                entry.anchor = pendingAnchor;
            }
        }

        // Center the ENTIRE entry (its bounds, scale included) on the panel center
        float cx = px + pw / 2f, cy = py + ph / 2f;
        HudRenderers.Bounds b = HudRenderers.getBounds(entry, minecraft);
        float dx = cx - (b.x() + b.w() / 2f);
        float dy = cy - (b.y() + b.h() / 2f);
        entry.x += dx;
        entry.y += dy;
        entry.endX += dx;
        entry.endY += dy;
        try {
            HudRenderers.renderEntry(g, entry, minecraft, false);
        } finally {
            entry.x = ox;
            entry.y = oy;
            entry.endX = oeX;
            entry.endY = oeY;
            if (staged) {
                entry.anchor = oldAnchor;
                entry.horizonDist = oldDist;
            }
        }
    }

    @Override
    public void onClose() {
        save();
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Screen.init() auto-focuses the next tabbable widget after our init() and
        // steals focus from the text box. Re-apply focus once, on the first frame.
        if (!focusApplied && textBox != null) {
            focusApplied = true;
            textBox.setFocused(true);
            this.setInitialFocus(textBox);
        }

        // Sync palette state and show/hide type-specific rows
        colorPalette.setCurrentColor(entry.color);
        colorPalette.setRainbow(entry.rainbow);
        infoPalette.setCurrentColor(entry.bindColor);
        infoPalette.visible = entry.type == HudEntry.Type.TEXT;
        suffixBox.visible = entry.type == HudEntry.Type.TEXT;
        suffixEnlargeButton.visible = entry.type == HudEntry.Type.TEXT;
        lineWidthButton.visible = entry.type == HudEntry.Type.LINE || entry.type == HudEntry.Type.SHAPE;
        shapeTypeButton.visible = entry.type == HudEntry.Type.SHAPE;
        filledButton.visible = entry.type == HudEntry.Type.SHAPE;
        barMaxBox.visible = entry.type == HudEntry.Type.BAR;
        boolean horizonMode = "horizon".equals(effectiveAnchor());
        boolean worldMode = "world".equals(effectiveAnchor());
        anchorButton.visible = true;
        horizonDistButton.visible = horizonMode;
        worldPosButton.visible = worldMode;

        // Bar max label + x-variable hint (BAR mode only)
        if (barMaxBox.visible) {
            g.drawString(font, Component.translatable("hud.aero_reformation.bar_max_label"),
                    this.width / 2 - 100, 78, 0x888888);
            g.drawString(font, Component.translatable("hud.aero_reformation.bar_x_hint"),
                    this.width / 2 - 100, 112, 0x6666AA);
        }

        // Static effect preview in the left panel (the screen is dimmed now)
        renderPreview(g);

        super.render(g, mouseX, mouseY, partialTick);

        // Right-side palette labels: which part each palette colors
        g.drawString(font, Component.translatable("hud.aero_reformation.color_before"),
                this.width - 130, 24, 0xDDDDDD);
        if (infoPalette.visible) {
            g.drawString(font, Component.translatable("hud.aero_reformation.color_after"),
                    this.width - 130, 90, 0xDDDDDD);
        }


        // Bound target info
        int cw = this.width / 2;
        int infoY = 250;
        if (entry.bindEntityUuid != null) {
            String uid = font.plainSubstrByWidth(entry.bindEntityUuid.toString(),
                    Math.max(10, this.width - cw - 60));
            g.drawString(font,
                    Component.translatable("hud.aero_reformation.entry_bound_entity", uid),
                    cw - 100, infoY, entry.bindColor);
        } else if (entry.bindPos != null) {
            String label = currentInfoLabel();
            if (!label.isEmpty()) {
                g.drawString(font,
                        Component.translatable("hud.aero_reformation.showing", label),
                        cw - 100, infoY, entry.bindColor);
            }
        } else {
            g.drawString(font, Component.translatable("hud.aero_reformation.unbound"),
                    cw - 100, infoY, 0x777777);
        }

        // Anchor info
        int anchorY = infoY + 12;
        if (worldMode) {
            if (!entry.physBodyId.isEmpty()) {
                var sub = HudRenderers.findPhysBody(minecraft, entry.physBodyId);
                String name = sub != null ? sub.getName()
                        : entry.physBodyId.substring(0, Math.min(8, entry.physBodyId.length()));
                g.drawString(font, Component.translatable("hud.aero_reformation.phys_body", name),
                        cw - 100, anchorY, 0x55FFFF);
            } else {
                g.drawString(font, Component.literal(
                                String.format("(%.1f, %.1f, %.1f)", entry.worldX, entry.worldY, entry.worldZ)),
                        cw - 100, anchorY, 0x55FFFF);
            }
        } else if (horizonMode) {
            g.drawString(font, Component.translatable("hud.aero_reformation.horizon_dist", (int) entry.horizonDist),
                    cw - 100, anchorY, 0x55FFFF);
        }

        // Placeholder help line
        g.drawString(font, Component.translatable("hud.aero_reformation.ph_help"),
                cw - 100, this.height - 12, 0x666666);
    }
}
