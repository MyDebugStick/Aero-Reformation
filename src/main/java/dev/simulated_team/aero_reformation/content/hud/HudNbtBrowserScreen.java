package dev.simulated_team.aero_reformation.content.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree-style NBT browser (inspired by nbtedit): compound nodes expand/collapse
 * with an arrow, leaf nodes can be picked. Values that look like registry ids
 * (items/blocks/entities) are localized (e.g. shown in Chinese).
 */
public class HudNbtBrowserScreen extends Screen {

    private static final int ROW_H = 10;
    private static final int INDENT = 12;

    private static final class TreeNode {
        final String path;      // full dotted path (empty for the root)
        final String key;       // display key ("[i]" for list elements)
        final Tag tag;
        final int depth;
        final boolean container;
        boolean expanded;
        final List<TreeNode> children = new ArrayList<>();

        TreeNode(String path, String key, Tag tag, int depth, boolean container) {
            this.path = path;
            this.key = key;
            this.tag = tag;
            this.depth = depth;
            this.container = container;
        }
    }

    private final HudEntry entry;
    private final HudPlaceholder pendingPlaceholder;
    private final List<TreeNode> roots = new ArrayList<>();
    private final List<TreeNode> visible = new ArrayList<>();
    private int scroll;

    public HudNbtBrowserScreen(HudEntry entry) {
        super(Component.translatable("hud.aero_reformation.nbt_title"));
        this.entry = entry;
        this.pendingPlaceholder = null;
        CompoundTag snapshot = null;
        if (entry.bindEntityUuid != null) {
            snapshot = HudNbtCache.ENTITY_SNAPSHOTS.get(entry.bindEntityUuid);
        } else if (entry.bindPos != null) {
            // Full NBT fetched from the server (client BE data may be incomplete/unloaded)
            snapshot = HudNbtCache.SNAPSHOTS.get(entry.bindPos);
        }
        if (snapshot != null) {
            TreeNode root = build("", "", snapshot, 0);
            roots.addAll(root.children);
            rebuildVisible();
        }
    }

    /** Placeholder-creation mode: picking a leaf opens the placeholder edit screen. */
    public HudNbtBrowserScreen(HudPlaceholder pending) {
        super(Component.translatable("hud.aero_reformation.nbt_title"));
        this.entry = null;
        this.pendingPlaceholder = pending;
        CompoundTag snapshot = null;
        if (pending.entityUuid != null) {
            snapshot = HudNbtCache.ENTITY_SNAPSHOTS.get(pending.entityUuid);
        } else if (pending.pos != null) {
            snapshot = HudNbtCache.SNAPSHOTS.get(pending.pos);
        }
        if (snapshot != null) {
            TreeNode root = build("", "", snapshot, 0);
            roots.addAll(root.children);
            rebuildVisible();
        }
    }

    private TreeNode build(String prefix, String key, Tag tag, int depth) {
        boolean container = isContainer(tag);
        TreeNode node = new TreeNode(prefix, key, tag, depth, container);
        if (container) {
            if (tag instanceof CompoundTag ct) {
                for (String k : ct.getAllKeys()) {
                    Tag child = ct.get(k);
                    String p = prefix.isEmpty() ? k : prefix + "." + k;
                    node.children.add(build(p, k, child, depth + 1));
                }
            } else if (tag instanceof ListTag lt) {
                for (int i = 0; i < lt.size(); i++) {
                    Tag child = lt.get(i);
                    String p = prefix + "[" + i + "]";
                    node.children.add(build(p, "[" + i + "]", child, depth + 1));
                }
            }
        }
        return node;
    }

    private void rebuildVisible() {
        visible.clear();
        for (TreeNode r : roots) collectVisible(r, visible);
    }

    private void collectVisible(TreeNode n, List<TreeNode> out) {
        out.add(n);
        if (n.container && n.expanded) {
            for (TreeNode c : n.children) collectVisible(c, out);
        }
    }

    private static boolean isContainer(Tag t) {
        // In 1.21.1 CompoundTag does NOT extend CollectionTag (only ListTag does)
        return t instanceof CompoundTag || t instanceof ListTag;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("hud.aero_reformation.back"), b -> {
                    if (pendingPlaceholder != null) {
                        // placeholder mode: go back to the edit screen when rebinding an
                        // existing placeholder, otherwise to the setup screen
                        boolean editing = HudPlaceholderBoard.getPlaceholders(minecraft.player)
                                .contains(pendingPlaceholder);
                        minecraft.setScreen(editing
                                ? new HudPlaceholderEditScreen(pendingPlaceholder)
                                : new HudPlaceholderSetupScreen());
                    } else {
                        minecraft.setScreen(new HudEntryEditScreen(entry));
                    }
                })
                .bounds(4, 4, 60, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, Component.translatable("hud.aero_reformation.nbt_hint"), 10, 28, 0xFFFF55);

        if (visible.isEmpty()) {
            g.drawString(font, Component.translatable("hud.aero_reformation.nbt_empty"), 10, 50, 0xFF5555);
        }

        int y0 = 40;
        int visibleCount = Math.max(1, (this.height - y0 - 10) / ROW_H);
        int end = Math.min(visible.size(), scroll + visibleCount);
        for (int i = scroll; i < end; i++) {
            TreeNode n = visible.get(i);
            String line;
            if (n.container) {
                line = (n.expanded ? "v " : "> ") + n.key + " (" + n.children.size() + ")";
            } else {
                line = n.key + ": " + HudBindings.localize(n.tag.getAsString());
            }
            int color = n.container ? 0xFFFF55 : (n.depth > 0 ? 0xE0E0E0 : 0xFFFFFF);
            boolean selectedPath = pendingPlaceholder != null
                    ? n.path.equals(pendingPlaceholder.nbtPath)
                    : entry.bindSource.equals("nbt") && n.path.equals(entry.bindKey);
            if (selectedPath) {
                color = entry != null ? entry.bindColor : 0x55FF55;
            }
            int y = y0 + (i - scroll) * ROW_H;
            g.fill(10, y, this.width, y + ROW_H, 0x14FFFFFF);
            g.drawString(font, line, 14 + n.depth * INDENT, y, color);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0) {
            int rel = (int) (my - 40);
            if (rel >= 0) {
                int idx = scroll + rel / ROW_H;
                if (idx >= 0 && idx < visible.size()) {
                    TreeNode n = visible.get(idx);
                    if (n.container) {
                        n.expanded = !n.expanded;
                        rebuildVisible();
                    } else if (!n.path.isEmpty()) {
                        if (pendingPlaceholder != null) {
                            pendingPlaceholder.nbtPath = n.path;
                            minecraft.setScreen(new HudPlaceholderEditScreen(pendingPlaceholder));
                        } else {
                            entry.bindSource = "nbt";
                            entry.bindKey = n.path;
                            HudBoard.saveToPlayer(minecraft.player);
                            minecraft.setScreen(new HudEntryEditScreen(entry));
                        }
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int visibleCount = Math.max(1, (this.height - 40 - 10) / ROW_H);
        int maxScroll = Math.max(0, visible.size() - visibleCount);
        scroll = Mth.clamp(scroll - (int) scrollY, 0, maxScroll);
        return true;
    }
}
