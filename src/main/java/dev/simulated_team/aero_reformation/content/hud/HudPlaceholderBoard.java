package dev.simulated_team.aero_reformation.content.hud;

import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.network.HudEntrySyncPacket;
import dev.simulated_team.aero_reformation.network.HudNbtSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player list of custom placeholders, persisted to player persistent data
 * and a client-local file (like HudBoard). Their (pos, nbtPath) pairs are also
 * registered with the server for live NBT value sync.
 */
public final class HudPlaceholderBoard {

    static final Map<UUID, List<HudPlaceholder>> PLAYER_PLACEHOLDERS = new ConcurrentHashMap<>();
    private static final String PERSIST_KEY = "aero_reformation_hud_placeholders";

    private HudPlaceholderBoard() {}

    public static List<HudPlaceholder> getPlaceholders(Player player) {
        return PLAYER_PLACEHOLDERS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
    }

    /** Current live value of a placeholder (sensor data or NBT), or null when unavailable. */
    public static String liveValue(HudPlaceholder p) {
        if ("constant".equals(p.bindSource) || "math".equals(p.bindSource)) {
            return HudPlaceholders.resolvePlaceholder(p, Minecraft.getInstance());
        }
        if ("sensor".equals(p.bindSource)) {
            if (p.pos == null || p.sensorType.isEmpty()) return null;
            int[] data = dev.simulated_team.aero_reformation.network.GoggleMonitorSyncPacket.CLIENT_DATA.get(p.pos);
            if (data == null) return null;
            return HudBindings.formatSensor(p.sensorType, data);
        }
        if (p.nbtPath.isEmpty()) return null;
        HudNbtSyncPacket.NbtKey nk;
        if (p.entityUuid != null) {
            nk = new HudNbtSyncPacket.NbtKey(BlockPos.ZERO, p.nbtPath, p.entityUuid);
        } else if (p.pos != null) {
            nk = new HudNbtSyncPacket.NbtKey(p.pos, p.nbtPath, null);
        } else {
            return null;
        }
        return HudNbtSyncPacket.CLIENT_VALUES.get(nk);
    }

    public static void addPlaceholder(Player player, HudPlaceholder p) {
        getPlaceholders(player).add(p);
        saveToPlayer(player);
    }

    public static void removePlaceholder(Player player, HudPlaceholder p) {
        getPlaceholders(player).remove(p);
        saveToPlayer(player);
    }

    /** Replace the full list (used by the setup screen's delete / edit flows). */
    public static void setPlaceholders(Player player, List<HudPlaceholder> list) {
        PLAYER_PLACEHOLDERS.put(player.getUUID(), new ArrayList<>(list));
        saveToPlayer(player);
    }

    public static void saveToPlayer(Player player) {
        List<HudPlaceholder> list = getPlaceholders(player);
        CompoundTag persistent = player.getPersistentData();
        if (list.isEmpty()) {
            persistent.remove(PERSIST_KEY);
        } else {
            ListTag lt = new ListTag();
            for (HudPlaceholder p : list) lt.add(p.toNBT());
            persistent.put(PERSIST_KEY, lt);
        }
        if (player instanceof LocalPlayer lp) {
            saveToFile(lp);
        }
        HudBoard.syncBindsToServer(player);
    }

    /**
     * Load placeholders on login. The login event can deliver a ServerPlayer on
     * an integrated server, so always fall back to the local file by UUID.
     */
    public static void loadFromPlayer(Player player) {
        CompoundTag persistent = player.getPersistentData();
        if (persistent.contains(PERSIST_KEY, Tag.TAG_LIST)) {
            ListTag lt = persistent.getList(PERSIST_KEY, Tag.TAG_COMPOUND);
            List<HudPlaceholder> list = new ArrayList<>();
            for (int i = 0; i < lt.size(); i++) {
                HudPlaceholder p = HudPlaceholder.fromNBT(lt.getCompound(i));
                if (p != null && !p.name.isEmpty()) list.add(p);
            }
            if (!list.isEmpty()) {
                PLAYER_PLACEHOLDERS.put(player.getUUID(), list);
                return;
            }
        }
        loadFromFile(player.getUUID());
    }

    private static File placeholderFile(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        File dir = new File(mc.gameDirectory, "hud_data");
        return new File(dir, "placeholders_" + uuid + ".dat");
    }

    private static void saveToFile(LocalPlayer player) {
        try {
            ListTag lt = new ListTag();
            for (HudPlaceholder p : getPlaceholders(player)) {
                lt.add(p.toNBT());
            }
            CompoundTag root = new CompoundTag();
            root.put(PERSIST_KEY, lt);
            File f = placeholderFile(player.getUUID());
            f.getParentFile().mkdirs();
            NbtIo.writeCompressed(root, f.toPath());
        } catch (IOException ex) {
            AeroReformation.LOGGER.warn("Failed to save HUD placeholders: {}", ex.toString());
        }
    }

    private static void loadFromFile(UUID uuid) {
        try {
            File f = placeholderFile(uuid);
            if (!f.exists()) return;
            CompoundTag root = NbtIo.readCompressed(f.toPath(), NbtAccounter.unlimitedHeap());
            if (root == null || !root.contains(PERSIST_KEY, Tag.TAG_LIST)) return;
            ListTag lt = root.getList(PERSIST_KEY, Tag.TAG_COMPOUND);
            List<HudPlaceholder> list = new ArrayList<>();
            for (int i = 0; i < lt.size(); i++) {
                HudPlaceholder p = HudPlaceholder.fromNBT(lt.getCompound(i));
                if (p != null && !p.name.isEmpty()) list.add(p);
            }
            if (!list.isEmpty()) {
                PLAYER_PLACEHOLDERS.put(uuid, list);
            }
        } catch (IOException ex) {
            AeroReformation.LOGGER.warn("Failed to load HUD placeholders: {}", ex.toString());
        }
    }

    /** All placeholder NBT binds (block or entity), merged into the HUD entry sync packet. */
    static List<HudEntrySyncPacket.NbtBind> collectNbtBinds(Player player) {
        List<HudEntrySyncPacket.NbtBind> binds = new ArrayList<>();
        for (HudPlaceholder p : getPlaceholders(player)) {
            if ("sensor".equals(p.bindSource)) continue;
            if (p.nbtPath.isEmpty()) continue;
            if (p.entityUuid != null) {
                binds.add(new HudEntrySyncPacket.NbtBind(BlockPos.ZERO, p.nbtPath, p.entityUuid));
            } else if (p.pos != null) {
                binds.add(new HudEntrySyncPacket.NbtBind(p.pos, p.nbtPath, null));
            }
        }
        return binds;
    }

    /** Sensor positions bound by sensor placeholders (for live sensor data sync). */
    static List<BlockPos> collectSensorBinds(Player player) {
        List<BlockPos> list = new ArrayList<>();
        for (HudPlaceholder p : getPlaceholders(player)) {
            if ("sensor".equals(p.bindSource) && p.pos != null) {
                list.add(p.pos);
            }
        }
        return list;
    }
}
