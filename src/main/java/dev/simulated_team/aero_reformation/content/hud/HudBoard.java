package dev.simulated_team.aero_reformation.content.hud;

import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.network.HudEntrySyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player list of HUD entries, persisted into the player's persistent data
 * (same pattern as GoggleMonitorData).
 */
public class HudBoard {

    static final Map<UUID, List<HudEntry>> PLAYER_ENTRIES = new ConcurrentHashMap<>();

    static final Map<UUID, Deque<List<HudEntry>>> UNDO_STACKS = new ConcurrentHashMap<>();

    private static final String PERSIST_KEY = "aero_reformation_hud_entries";
    private static final int MAX_UNDO = 50;

    public static List<HudEntry> getEntries(Player player) {
        return PLAYER_ENTRIES.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
    }

    public static void addEntry(Player player, HudEntry entry) {
        getEntries(player).add(entry);
        saveToPlayer(player);
    }

    /** Replace the whole entry list (used when applying a helmet preset). */
    public static void setEntries(Player player, List<HudEntry> entries) {
        PLAYER_ENTRIES.put(player.getUUID(), new ArrayList<>(entries));
        saveToPlayer(player);
    }

    public static void removeEntry(Player player, HudEntry entry) {
        getEntries(player).remove(entry);
        saveToPlayer(player);
    }

    public static void clearEntries(Player player) {
        PLAYER_ENTRIES.remove(player.getUUID());
        saveToPlayer(player);
    }

    /**
     * Load entries from player persistent data on login. The local client file
     * is the authoritative store across restarts; the login event can deliver a
     * ServerPlayer on an integrated server, so always fall back to the file by
     * player UUID instead of gating on LocalPlayer.
     */
    public static void loadFromPlayer(Player player) {
        CompoundTag persistent = player.getPersistentData();
        if (persistent.contains(PERSIST_KEY, Tag.TAG_LIST)) {
            ListTag list = persistent.getList(PERSIST_KEY, Tag.TAG_COMPOUND);
            List<HudEntry> entries = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                HudEntry e = HudEntry.fromNBT(list.getCompound(i));
                if (e != null) entries.add(e);
            }
            if (!entries.isEmpty()) {
                PLAYER_ENTRIES.put(player.getUUID(), entries);
                return;
            }
        }
        loadFromFile(player.getUUID());
    }

    /** Save entries into player persistent data and mirror them to a local client file. */
    public static void saveToPlayer(Player player) {
        List<HudEntry> entries = getEntries(player);
        CompoundTag persistent = player.getPersistentData();
        if (entries.isEmpty()) {
            persistent.remove(PERSIST_KEY);
        } else {
            ListTag list = new ListTag();
            for (HudEntry e : entries) {
                list.add(e.toNBT());
            }
            persistent.put(PERSIST_KEY, list);
        }
        if (player instanceof LocalPlayer lp) {
            saveToFile(lp);
        }
        syncBindsToServer(player);
    }

    private static File hudDataFile(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        File dir = new File(mc.gameDirectory, "hud_data");
        return new File(dir, uuid + ".dat");
    }

    /** Persist entries to a client-local file so they survive quitting the game. */
    private static void saveToFile(LocalPlayer player) {
        try {
            ListTag list = new ListTag();
            for (HudEntry e : getEntries(player)) {
                list.add(e.toNBT());
            }
            CompoundTag root = new CompoundTag();
            root.put(PERSIST_KEY, list);
            File f = hudDataFile(player.getUUID());
            f.getParentFile().mkdirs();
            NbtIo.writeCompressed(root, f.toPath());
        } catch (IOException ex) {
            AeroReformation.LOGGER.warn("Failed to save HUD data: {}", ex.toString());
        }
    }

    /** Load entries from the client-local file after a game restart. */
    private static void loadFromFile(UUID uuid) {
        try {
            File f = hudDataFile(uuid);
            if (!f.exists()) return;
            CompoundTag root = NbtIo.readCompressed(f.toPath(), NbtAccounter.unlimitedHeap());
            if (root == null || !root.contains(PERSIST_KEY, Tag.TAG_LIST)) return;
            ListTag list = root.getList(PERSIST_KEY, Tag.TAG_COMPOUND);
            List<HudEntry> entries = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                HudEntry e = HudEntry.fromNBT(list.getCompound(i));
                if (e != null) entries.add(e);
            }
            if (!entries.isEmpty()) {
                PLAYER_ENTRIES.put(uuid, entries);
            }
        } catch (IOException ex) {
            AeroReformation.LOGGER.warn("Failed to load HUD data: {}", ex.toString());
        }
    }

    /** Push bound positions/paths to the server so it can sync live data back (client only). */
    public static void syncBindsToServer(Player player) {
        if (!(player instanceof LocalPlayer)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        List<BlockPos> sensors = new ArrayList<>();
        List<HudEntrySyncPacket.NbtBind> nbtBinds = new ArrayList<>();
        for (HudEntry e : getEntries(player)) {
            if (e.bindPos == null && e.bindEntityUuid == null) continue;
            if (e.bindSource.equals("sensor") && e.bindEntityUuid == null) {
                sensors.add(e.bindPos);
            } else if (e.bindSource.equals("nbt") && !e.bindKey.isEmpty()) {
                if (e.bindEntityUuid != null) {
                    nbtBinds.add(new HudEntrySyncPacket.NbtBind(BlockPos.ZERO, e.bindKey, e.bindEntityUuid));
                } else {
                    nbtBinds.add(new HudEntrySyncPacket.NbtBind(e.bindPos, e.bindKey, null));
                }
            }
        }
        // Custom placeholders also register their binds for live value sync
        nbtBinds.addAll(HudPlaceholderBoard.collectNbtBinds(player));
        sensors.addAll(HudPlaceholderBoard.collectSensorBinds(player));
        PacketDistributor.sendToServer(new HudEntrySyncPacket(sensors, nbtBinds));
    }

    // ── undo support ──

    /** Deep copy of the current entry list. */
    public static List<HudEntry> snapshot(Player player) {
        List<HudEntry> copy = new ArrayList<>();
        for (HudEntry e : getEntries(player)) {
            copy.add(e.copy());
        }
        return copy;
    }

    /** Replace the current entry list with a snapshot (deep copy). */
    public static void restore(Player player, List<HudEntry> snap) {
        List<HudEntry> copy = new ArrayList<>();
        for (HudEntry e : snap) {
            copy.add(e.copy());
        }
        PLAYER_ENTRIES.put(player.getUUID(), copy);
        saveToPlayer(player);
    }

    /** Push the current state onto the undo stack. */
    public static void pushUndo(Player player) {
        Deque<List<HudEntry>> stack = UNDO_STACKS.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        stack.push(snapshot(player));
        while (stack.size() > MAX_UNDO) {
            stack.removeLast();
        }
    }

    /** Pop and restore the last state; returns false if nothing to undo. */
    public static boolean undo(Player player) {
        Deque<List<HudEntry>> stack = UNDO_STACKS.get(player.getUUID());
        if (stack == null || stack.isEmpty()) return false;
        restore(player, stack.pop());
        return true;
    }
}
