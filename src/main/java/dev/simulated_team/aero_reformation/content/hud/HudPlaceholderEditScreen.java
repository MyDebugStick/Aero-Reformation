package dev.simulated_team.aero_reformation.content.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Edit a custom placeholder: give it a name (the %...% wrappers are added
 * automatically) and an optional math expression (x = the bound value), e.g.
 * "+10" or "*2". Saving adds/updates the placeholder in HudPlaceholderBoard.
 */
public class HudPlaceholderEditScreen extends Screen {

    /** Token chosen in the placeholder list; inserted into the math expression. */
    public static String pendingInsertToken = null;
    /** Cursor position where the pending token should be inserted. */
    public static int pendingInsertPos = 0;

    private final HudPlaceholder placeholder;
    private EditBox nameBox;
    private EditBox valueBox;   // math expression / constant value
    private EditBox mathBox;    // NBT/sensor/constant numeric transform
    private EditBox descBox;

    public HudPlaceholderEditScreen(HudPlaceholder placeholder) {
        super(Component.translatable("hud.aero_reformation.ph_edit_title"));
        this.placeholder = placeholder;
    }

    @Override
    protected void init() {
        int cw = this.width / 2;

        boolean isNbt = !"math".equals(placeholder.bindSource) && !"constant".equals(placeholder.bindSource);
        boolean isMath = "math".equals(placeholder.bindSource);
        boolean isConst = "constant".equals(placeholder.bindSource);

        // Apply a placeholder token picked in the placeholder list screen into
        // the math expression (value box), at the recorded cursor position.
        int valueCursor = -1;
        if (pendingInsertToken != null) {
            String v = placeholder.value;
            int pos = Math.min(Math.max(0, pendingInsertPos), v.length());
            placeholder.value = v.substring(0, pos) + pendingInsertToken + v.substring(pos);
            valueCursor = pos + pendingInsertToken.length();
            pendingInsertToken = null;
        }

        // Type toggle: NBT bind -> placeholder math -> fixed constant -> NBT
        addRenderableWidget(Button.builder(typeLabel(), b -> {
                    placeholder.bindSource = switch (placeholder.bindSource) {
                        case "math" -> "constant";
                        case "constant" -> "";
                        default -> "math";
                    };
                    b.setMessage(typeLabel());
                    rebuildWidgets();
                })
                .bounds(cw - 100, 10, 200, 16).build());

        // Name box: shows %name% (auto %...% completion)
        nameBox = new EditBox(this.font, cw - 100, 32, 200, 20,
                Component.translatable("hud.aero_reformation.ph_name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(placeholder.name.isEmpty() ? "" : "%" + placeholder.name + "%");
        nameBox.setFocused(true);
        nameBox.setResponder(s -> {
            String raw = s.trim();
            if (!raw.startsWith("%")) raw = "%" + raw;
            if (!raw.endsWith("%")) raw = raw + "%";
            String name = raw.length() >= 2 ? raw.substring(1, raw.length() - 1) : "";
            if (!name.isEmpty() && name.indexOf('%') < 0) {
                placeholder.name = name;
                autoSave();
            }
        });
        addRenderableWidget(nameBox);

        // Value box: math expression (placeholder math) or fixed value (constant).
        // Not shown for NBT binds (the bound value is the source instead).
        if (isMath || isConst) {
            valueBox = new EditBox(this.font, cw - 100, 64, 200, 20,
                    Component.translatable(isMath
                            ? "hud.aero_reformation.ph_math_expr"
                            : "hud.aero_reformation.ph_const_value"));
            valueBox.setMaxLength(128);
            valueBox.setValue(placeholder.value);
            valueBox.setHint(Component.translatable(isMath
                    ? "hud.aero_reformation.ph_math_expr_hint"
                    : "hud.aero_reformation.ph_const_value_hint"));
            if (valueCursor >= 0) valueBox.setCursorPosition(valueCursor);
            valueBox.setResponder(s -> {
                placeholder.value = s;
                autoSave();
            });
            addRenderableWidget(valueBox);

            // Insert a %placeholder% into the math expression (math kind only)
            if (isMath) {
                addRenderableWidget(Button.builder(
                                Component.translatable("hud.aero_reformation.ph_insert"),
                                b -> {
                                    autoSave();
                                    pendingInsertPos = valueBox.getCursorPosition();
                                    minecraft.setScreen(new HudPlaceholderListScreen(placeholder));
                                })
                        .bounds(cw + 104, 64, 44, 20).build());
            }
        }

        // Math box: numeric transform for NBT/sensor/constant values (x = value).
        // Hidden for placeholder math (the expression already is the math).
        if (!isMath) {
            mathBox = new EditBox(this.font, cw - 100, 64, 200, 20,
                    Component.translatable("hud.aero_reformation.ph_math"));
            mathBox.setMaxLength(64);
            mathBox.setValue(placeholder.math);
            mathBox.setHint(Component.translatable("hud.aero_reformation.ph_math_hint"));
            mathBox.setResponder(s -> {
                placeholder.math = s;
                autoSave();
            });
            addRenderableWidget(mathBox);
        }

        // Remark box: optional description shown in the placeholder list
        descBox = new EditBox(this.font, cw - 100, 96, 200, 20,
                Component.translatable("hud.aero_reformation.ph_desc"));
        descBox.setMaxLength(64);
        descBox.setValue(placeholder.desc);
        descBox.setResponder(s -> {
            placeholder.desc = s;
            autoSave();
        });
        addRenderableWidget(descBox);

        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.ph_save"), b -> saveAndClose())
                .bounds(cw - 100, 120, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.ph_delete"), b -> {
                    HudPlaceholderBoard.removePlaceholder(minecraft.player, placeholder);
                    minecraft.setScreen(new HudPlaceholderSetupScreen());
                })
                .bounds(cw + 10, 120, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.back"), b ->
                        minecraft.setScreen(new HudPlaceholderSetupScreen()))
                .bounds(cw - 100, 142, 200, 20).build());

        // NBT bind controls: sensor toggle + pick NBT + rebind
        if (isNbt) {
            boolean isSensor = "sensor".equals(placeholder.bindSource);
            if (!isSensor && placeholder.pos != null) {
                isSensor = !HudBindings.detectSensorType(minecraft.level, placeholder.pos).isEmpty();
            }
            if (isSensor) {
                addRenderableWidget(sourceToggleButton(cw - 100, 166, 95));
                addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.ph_pick_nbt"), b -> pickNbt())
                        .bounds(cw - 5, 166, 95, 20).build());
            } else {
                addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.ph_pick_nbt"), b -> pickNbt())
                        .bounds(cw - 100, 166, 200, 20).build());
            }
            addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.ph_rebind"), b -> rebind())
                    .bounds(cw - 100, 188, 200, 20).build());
        }
    }

    private Button sourceToggleButton(int x, int y, int w) {
        return Button.builder(sourceLabel(), b -> {
                    String sensor = placeholder.pos == null ? ""
                            : HudBindings.detectSensorType(minecraft.level, placeholder.pos);
                    if ("sensor".equals(placeholder.bindSource)) {
                        placeholder.bindSource = "";
                        placeholder.sensorType = "";
                    } else {
                        placeholder.bindSource = "sensor";
                        placeholder.sensorType = sensor;
                    }
                    b.setMessage(sourceLabel());
                })
                .bounds(x, y, w, 20).build();
    }

    /** Current live value of the bound target, for real-time preview in the edit screen. */
    private String liveValue() {
        return HudPlaceholderBoard.liveValue(placeholder);
    }

    /** Open the NBT browser to pick a path (fetches from server if no snapshot yet). */
    private void pickNbt() {
        // Picking NBT implies NBT mode: switch back from sensor data if needed
        if ("sensor".equals(placeholder.bindSource)) {
            placeholder.bindSource = "";
            placeholder.sensorType = "";
        }
        boolean haveSnapshot;
        if (placeholder.entityUuid != null) {
            haveSnapshot = HudNbtCache.ENTITY_SNAPSHOTS.containsKey(placeholder.entityUuid);
            if (!haveSnapshot) HudNbtCache.requestPlaceholderNbt(placeholder, placeholder.pos, placeholder.entityUuid);
        } else if (placeholder.pos != null) {
            haveSnapshot = HudNbtCache.SNAPSHOTS.containsKey(placeholder.pos);
            if (!haveSnapshot) HudNbtCache.requestPlaceholderNbt(placeholder, placeholder.pos, null);
        } else {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("hud.aero_reformation.ph_rebind_first"), true);
            }
            return;
        }
        if (haveSnapshot) {
            minecraft.setScreen(new HudNbtBrowserScreen(placeholder));
        } else if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("hud.aero_reformation.ph_fetching"), true);
        }
    }

    /** Re-enter middle-click picking to bind a new target; keeps name/math (same object). */
    private void rebind() {
        // Keep the current name/math across the rebind flow (same object is reused)
        String raw = nameBox.getValue().trim();
        if (!raw.startsWith("%")) raw = "%" + raw;
        if (!raw.endsWith("%")) raw = raw + "%";
        String name = raw.length() >= 2 ? raw.substring(1, raw.length() - 1) : "";
        if (!name.isEmpty() && name.indexOf('%') < 0) {
            placeholder.name = name;
            if (mathBox != null) placeholder.math = mathBox.getValue().trim();
            if (valueBox != null) placeholder.value = valueBox.getValue().trim();
        }
        HudPickHandler.beginPlaceholderPick(placeholder);
        minecraft.setScreen(null); // await middle-click
    }

    private Component sourceLabel() {
        String key = "sensor".equals(placeholder.bindSource) ? "sensor" : "nbt";
        return Component.translatable("hud.aero_reformation.ph_source_label",
                Component.translatable("hud.aero_reformation.ph_source_" + key));
    }

    private Component typeLabel() {
        String key;
        if ("math".equals(placeholder.bindSource)) key = "ph_type_math";
        else if ("constant".equals(placeholder.bindSource)) key = "ph_type_constant";
        else key = "ph_type_nbt";
        return Component.translatable("hud.aero_reformation.ph_type_label",
                Component.translatable("hud.aero_reformation." + key));
    }

    /** Persist the placeholder as soon as it has a valid name (live auto-save). */
    private void autoSave() {
        if (minecraft.player == null) return;
        if (placeholder.name.isEmpty()) return; // not named yet: keep in-memory only
        List<HudPlaceholder> list = HudPlaceholderBoard.getPlaceholders(minecraft.player);
        if (!list.contains(placeholder)) list.add(placeholder);
        HudPlaceholderBoard.setPlaceholders(minecraft.player, list);
    }

    private void saveAndClose() {
        // Auto-complete the %...% wrappers: whatever the user typed, make it %name%
        String raw = nameBox.getValue().trim();
        if (!raw.startsWith("%")) raw = "%" + raw;
        if (!raw.endsWith("%")) raw = raw + "%";
        String name = raw.length() >= 2 ? raw.substring(1, raw.length() - 1) : "";
        if (name.isEmpty() || name.indexOf('%') >= 0) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("hud.aero_reformation.ph_name_invalid"), true);
            }
            return;
        }
        placeholder.name = name;
        if (valueBox != null) placeholder.value = valueBox.getValue().trim();
        if (mathBox != null) placeholder.math = mathBox.getValue().trim();
        if (descBox != null) placeholder.desc = descBox.getValue().trim();

        List<HudPlaceholder> list = HudPlaceholderBoard.getPlaceholders(minecraft.player);
        if (!list.contains(placeholder)) {
            list.add(placeholder);
        }
        HudPlaceholderBoard.setPlaceholders(minecraft.player, list);
        minecraft.setScreen(new HudPlaceholderSetupScreen());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int cw = this.width / 2;
        boolean isMath = "math".equals(placeholder.bindSource);
        boolean isConst = "constant".equals(placeholder.bindSource);
        boolean isNbt = !isMath && !isConst;

        g.drawString(font, Component.translatable("hud.aero_reformation.ph_name_label"), cw - 100, 22, 0xDDDDDD);
        g.drawString(font, Component.translatable(isMath
                        ? "hud.aero_reformation.ph_math_expr_label"
                        : (isConst ? "hud.aero_reformation.ph_const_value_label"
                        : "hud.aero_reformation.ph_math_label")),
                cw - 100, 54, 0xDDDDDD);
        g.drawString(font, Component.translatable("hud.aero_reformation.ph_desc_label"), cw - 100, 86, 0xDDDDDD);

        if (isNbt) {
            // Bound target line
            if ("sensor".equals(placeholder.bindSource)) {
                g.drawString(font, Component.translatable("hud.aero_reformation.ph_bound_sensor",
                                placeholder.sensorType,
                                placeholder.pos == null ? "?" : placeholder.pos.toShortString()),
                        cw - 100, 210, 0x55FF55);
            } else if (placeholder.entityUuid != null) {
                String uid = font.plainSubstrByWidth(placeholder.entityUuid.toString(),
                        Math.max(10, this.width - cw - 60));
                g.drawString(font, Component.translatable("hud.aero_reformation.ph_bound_entity", uid),
                        cw - 100, 210, 0x55FF55);
            } else if (placeholder.pos != null) {
                g.drawString(font, Component.translatable("hud.aero_reformation.ph_bound_block", placeholder.pos.toShortString()),
                        cw - 100, 210, 0x55FF55);
            }
            // Second line: live value, or the NBT path / sensor source when unavailable
            String live = liveValue();
            if (live != null && !live.isEmpty()) {
                g.drawString(font, Component.translatable("hud.aero_reformation.ph_live_value", live),
                        cw - 100, 222, 0xFFFF55);
            } else if ("sensor".equals(placeholder.bindSource)) {
                g.drawString(font, Component.translatable("hud.aero_reformation.ph_source_sensor"),
                        cw - 100, 222, 0x55FFFF);
            } else if (!placeholder.nbtPath.isEmpty()) {
                g.drawString(font, Component.literal("NBT: " + placeholder.nbtPath),
                        cw - 100, 222, 0x55FFFF);
            } else if (placeholder.bindSource.isEmpty()) {
                g.drawString(font, Component.translatable("hud.aero_reformation.ph_no_live"),
                        cw - 100, 222, 0x888888);
            }
        } else {
            // Live preview of the math / constant value
            String live = liveValue();
            if (live != null && !live.isEmpty()) {
                g.drawString(font, Component.translatable("hud.aero_reformation.ph_live_value", live),
                        cw - 100, 210, 0xFFFF55);
            } else {
                g.drawString(font, Component.translatable("hud.aero_reformation.ph_no_live"),
                        cw - 100, 210, 0x888888);
            }
        }
    }
}
