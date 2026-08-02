package dev.simulated_team.aero_reformation.content.hud;

import dev.simulated_team.aero_reformation.network.HudNbtRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds NBT snapshots fetched from the server for the NBT browser, plus the
 * entry that is waiting for a snapshot (the browser opens on response).
 */
public final class HudNbtCache {

    public static final Map<BlockPos, CompoundTag> SNAPSHOTS = new HashMap<>();
    public static final Map<UUID, CompoundTag> ENTITY_SNAPSHOTS = new HashMap<>();

    private static HudEntry pendingEntry;
    private static HudPlaceholder pendingPlaceholder;

    private HudNbtCache() {}

    /** Ask the server for the block entity NBT; the browser opens when the response arrives. */
    public static void requestNbt(HudEntry entry, BlockPos pos) {
        pendingEntry = entry;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        PacketDistributor.sendToServer(new HudNbtRequestPacket(pos, null));
    }

    /** Ask the server for an entity's NBT; the browser opens when the response arrives. */
    public static void requestNbtEntity(HudEntry entry, UUID entityUuid) {
        pendingEntry = entry;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        PacketDistributor.sendToServer(new HudNbtRequestPacket(entry.bindPos, entityUuid));
    }

    /** Ask the server for the block/entity NBT during custom placeholder creation. */
    public static void requestPlaceholderNbt(HudPlaceholder ph, BlockPos pos, UUID entityUuid) {
        pendingPlaceholder = ph;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        PacketDistributor.sendToServer(new HudNbtRequestPacket(pos, entityUuid));
    }

    public static HudEntry takePending(BlockPos pos) {
        HudEntry e = pendingEntry;
        if (e != null && e.bindEntityUuid == null && pos.equals(e.bindPos)) {
            pendingEntry = null;
            return e;
        }
        return null;
    }

    public static HudEntry takePendingEntity(UUID entityUuid) {
        HudEntry e = pendingEntry;
        if (e != null && entityUuid != null && entityUuid.equals(e.bindEntityUuid)) {
            pendingEntry = null;
            return e;
        }
        return null;
    }

    public static HudPlaceholder takePendingPlaceholder(BlockPos pos) {
        HudPlaceholder p = pendingPlaceholder;
        if (p != null && p.entityUuid == null && pos.equals(p.pos)) {
            pendingPlaceholder = null;
            return p;
        }
        return null;
    }

    public static HudPlaceholder takePendingPlaceholderEntity(UUID entityUuid) {
        HudPlaceholder p = pendingPlaceholder;
        if (p != null && entityUuid != null && entityUuid.equals(p.entityUuid)) {
            pendingPlaceholder = null;
            return p;
        }
        return null;
    }
}
