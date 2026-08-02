package dev.simulated_team.aero_reformation.event;

import dev.simulated_team.aero_reformation.content.items.goggle_monitor.GoggleMonitorData;
import dev.simulated_team.aero_reformation.content.items.goggle_monitor.SensorMonitorEntry;
import dev.simulated_team.aero_reformation.network.GoggleMonitorSyncPacket;
import dev.simulated_team.aero_reformation.network.HudEntrySyncPacket;
import dev.simulated_team.aero_reformation.network.HudNbtSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

import static dev.simulated_team.aero_reformation.AeroReformation.MODID;

@EventBusSubscriber(modid = MODID)
public class GoggleMonitorEventHandler {

    /** Load persisted bindings when player logs in */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        GoggleMonitorData.loadFromPlayer(event.getEntity());
    }

    // ─── Server: sync + clear on remove ───
    private static int serverTickCounter = 0;

    /** Sensor sync interval (ticks): live sensor data needs ~10 Hz for smooth HUD bars. */
    private static final int SENSOR_SYNC_INTERVAL = 2;
    /** NBT value sync interval (ticks): bound NBT is slower data; 5 ticks (4 Hz) is plenty. */
    private static final int NBT_SYNC_INTERVAL = 5;

    /** Last NBT value per (player, pos+path+entity), so only changes are sent. */
    private static final Map<UUID, Map<String, String>> LAST_NBT_VALUES = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        serverTickCounter++;
        var server = event.getServer();
        if (server == null) return;

        boolean sensorTick = serverTickCounter % SENSOR_SYNC_INTERVAL == 0;
        boolean nbtTick = serverTickCounter % NBT_SYNC_INTERVAL == 0;

        for (var player : server.getPlayerList().getPlayers()) {
            // Goggle bindings (require goggles)
            List<BlockPos> positions = new ArrayList<>();
            ItemStack goggles = GoggleMonitorData.findGoggles(player);
            if (!goggles.isEmpty()) {
                positions.addAll(GoggleMonitorData.getEntries(player).stream().map(SensorMonitorEntry::pos).toList());
            } else {
                GoggleMonitorData.clearEntries(player);
            }

            // HUD entry sensor binds (synced from client)
            positions.addAll(hudBindPositions(player));

            if (nbtTick) {
                // Sync live NBT values for bound NBT paths (only changed values)
                var nbtBinds = hudNbtBinds(player);
                if (!nbtBinds.isEmpty()) {
                    var vals = readChangedNbtValues(player, player.serverLevel(), nbtBinds);
                    if (!vals.isEmpty()) {
                        PacketDistributor.sendToPlayer(player, new HudNbtSyncPacket(vals));
                    }
                }
            }
            if (sensorTick && !positions.isEmpty()) {
                PacketDistributor.sendToPlayer(player, GoggleMonitorSyncPacket.fromPositions(positions, player.serverLevel()));
            }
        }
    }

    /** Drop the per-player NBT diff cache when the player leaves. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_NBT_VALUES.remove(event.getEntity().getUUID());
    }

    /** Sensor positions bound by HUD entries, stored on the server via HudEntrySyncPacket. */
    private static List<BlockPos> hudBindPositions(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(HudEntrySyncPacket.KEY, Tag.TAG_LONG_ARRAY)) return List.of();
        long[] arr = persistent.getLongArray(HudEntrySyncPacket.KEY);
        List<BlockPos> list = new ArrayList<>();
        for (long l : arr) list.add(BlockPos.of(l));
        return list;
    }

    /** NBT path binds (pos + path, entityId >= 0 for entities) stored on the server. */
    private static List<HudEntrySyncPacket.NbtBind> hudNbtBinds(ServerPlayer player) {
        List<HudEntrySyncPacket.NbtBind> list = new ArrayList<>();
        CompoundTag persistent = player.getPersistentData();
        if (persistent.contains(HudEntrySyncPacket.NBT_KEY, Tag.TAG_LIST)) {
            ListTag lt = persistent.getList(HudEntrySyncPacket.NBT_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < lt.size(); i++) {
                CompoundTag t = lt.getCompound(i);
                java.util.UUID uuid = t.contains("entityUuid", Tag.TAG_STRING)
                        ? java.util.UUID.fromString(t.getString("entityUuid")) : null;
                list.add(new HudEntrySyncPacket.NbtBind(BlockPos.of(t.getLong("pos")), t.getString("path"), uuid));
            }
        }
        return list;
    }

    /**
     * Read the current value of each bound NBT path. Only values that changed
     * since the last read are returned, so stable data produces no packets.
     */
    private static List<HudNbtSyncPacket.NbtValue> readChangedNbtValues(
            ServerPlayer player, ServerLevel level, List<HudEntrySyncPacket.NbtBind> binds) {
        Map<String, String> last = LAST_NBT_VALUES.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        List<HudNbtSyncPacket.NbtValue> list = new ArrayList<>();
        for (HudEntrySyncPacket.NbtBind b : binds) {
            if (b.entityUuid() != null) {
                net.minecraft.world.entity.Entity ent = level.getEntity(b.entityUuid());
                if (ent == null) continue;
                CompoundTag entTag = new CompoundTag();
                ent.saveWithoutId(entTag);
                String v = resolveNbtPath(entTag, b.path());
                if (v != null) {
                    String key = "e:" + b.entityUuid() + ":" + b.path();
                    if (v.equals(last.put(key, v))) continue; // unchanged since last read
                    list.add(new HudNbtSyncPacket.NbtValue(BlockPos.ZERO, b.path(), v, b.entityUuid()));
                }
                continue;
            }
            if (!level.isLoaded(b.pos())) continue;
            BlockEntity be = level.getBlockEntity(b.pos());
            if (be == null) continue;
            String v = resolveNbtPath(be.saveWithFullMetadata(level.registryAccess()), b.path());
            if (v != null) {
                String key = "b:" + b.pos().asLong() + ":" + b.path();
                if (v.equals(last.put(key, v))) continue; // unchanged since last read
                list.add(new HudNbtSyncPacket.NbtValue(b.pos(), b.path(), v, null));
            }
        }
        return list;
    }

    /** Resolve a dotted NBT path (with [i] indexing) and return the leaf value string. */
    private static String resolveNbtPath(CompoundTag root, String path) {
        Object cur = root;
        for (String part : path.split("\\.")) {
            if (cur instanceof CompoundTag ct) {
                String name = part;
                int idx = -1;
                int bi = part.indexOf('[');
                if (bi >= 0) {
                    name = part.substring(0, bi);
                    idx = Integer.parseInt(part.substring(bi + 1, part.indexOf(']')));
                }
                if (!ct.contains(name)) return null;
                Tag t = ct.get(name);
                if (idx >= 0) {
                    if (t instanceof ListTag lt) {
                        if (idx < 0 || idx >= lt.size()) return null;
                        cur = lt.get(idx);
                    } else if (t instanceof IntArrayTag ia) {
                        cur = ia.get(idx);
                    } else if (t instanceof ByteArrayTag ba) {
                        cur = ba.get(idx);
                    } else if (t instanceof LongArrayTag la) {
                        cur = la.get(idx);
                    } else return null;
                } else {
                    cur = t;
                }
            } else {
                return null;
            }
        }
        return cur instanceof Tag tg ? tg.getAsString() : String.valueOf(cur);
    }
}
