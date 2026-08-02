package dev.simulated_team.aero_reformation.content.hud;

import dev.simulated_team.aero_reformation.network.HudPresetLoadPacket;
import dev.simulated_team.aero_reformation.network.HudPresetSavePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HUD presets keyed by the item id of the helmet the player is wearing.
 * Presets are stored on the SERVER (shared across players): saving sends the
 * current entries + custom placeholders to the server under the worn helmet's
 * item id, loading requests it back so any player wearing the same helmet can
 * apply the same preset. The helmet itself is only used as a key (item id).
 */
public final class HudPresetStore {

    /** Local cache of presets saved during this session (fast path only). */
    private static final Map<String, CompoundTag> CACHE = new HashMap<>();

    private HudPresetStore() {}

    /** Serialize the current entries + custom placeholders and send them to the server. */
    public static void savePreset(Player player) {
        CompoundTag preset = new CompoundTag();
        ListTag entries = new ListTag();
        for (HudEntry e : HudBoard.getEntries(player)) {
            entries.add(e.toNBT());
        }
        preset.put("entries", entries);
        ListTag placeholders = new ListTag();
        for (HudPlaceholder p : HudPlaceholderBoard.getPlaceholders(player)) {
            placeholders.add(p.toNBT());
        }
        preset.put("placeholders", placeholders);

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PacketDistributor.sendToServer(new HudPresetSavePacket(preset));
        }
    }

    /** Ask the server for the preset bound to the worn helmet. */
    public static void requestLoad(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        PacketDistributor.sendToServer(new HudPresetLoadPacket());
    }

    /** Client-side: apply the preset returned by the server (or report missing). */
    public static void onLoadResponse(CompoundTag preset) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (preset == null) {
            mc.player.displayClientMessage(
                    Component.translatable("hud.aero_reformation.preset_not_found"), true);
            return;
        }
        applyPreset(mc.player, preset);
        mc.player.displayClientMessage(
                Component.translatable("hud.aero_reformation.preset_loaded"), true);
    }

    /** Replace the current entries and placeholders with the preset's. */
    public static void applyPreset(Player player, CompoundTag preset) {
        List<HudEntry> entries = new ArrayList<>();
        ListTag el = preset.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < el.size(); i++) {
            HudEntry e = HudEntry.fromNBT(el.getCompound(i));
            if (e != null) entries.add(e);
        }
        List<HudPlaceholder> placeholders = new ArrayList<>();
        ListTag pl = preset.getList("placeholders", Tag.TAG_COMPOUND);
        for (int i = 0; i < pl.size(); i++) {
            HudPlaceholder p = HudPlaceholder.fromNBT(pl.getCompound(i));
            if (p != null) placeholders.add(p);
        }
        HudBoard.setEntries(player, entries);
        HudPlaceholderBoard.setPlaceholders(player, placeholders);
    }
}
