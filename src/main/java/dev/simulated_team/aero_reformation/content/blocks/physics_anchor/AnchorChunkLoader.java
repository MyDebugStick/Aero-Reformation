package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.*;

public class AnchorChunkLoader {
    private record AnchorData(SubLevel subLevel, AnchorMarkerEntity marker, ChunkPos lastTicketChunk, int ticketRadius) {}
    // Per-dimension anchor maps: dimension → (BlockPos → AnchorData)
    private static final Map<ResourceKey<Level>, Map<BlockPos, AnchorData>> ANCHORS = new HashMap<>();
    private static final Map<UUID, AnchorData> WARMUP = new LinkedHashMap<>();
    private static final Set<UUID> ANCHORED_SUBLEVELS = new HashSet<>();
    private static final Set<UUID> AUTO_PROTECTED = new HashSet<>();

    private static Map<BlockPos, AnchorData> anchorsFor(ResourceKey<Level> dim) {
        return ANCHORS.computeIfAbsent(dim, k -> new LinkedHashMap<>());
    }

    public static void addAnchor(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel sl)) return;
        var be = sl.getBlockEntity(pos);
        SubLevel subLevel = be != null ? Sable.HELPER.getContaining(be) : Sable.HELPER.getContaining(sl, pos);
        if (subLevel == null) return;

        UUID id = subLevel.getUniqueId();
        var pose = subLevel.logicalPose();

        // Upgrade warmup entry if exists
        AnchorData warmup = WARMUP.remove(id);
        if (warmup != null) {
            anchorsFor(sl.dimension()).put(pos, new AnchorData(subLevel, warmup.marker, null, 2));
            ANCHORED_SUBLEVELS.add(id);
            AUTO_PROTECTED.remove(id);
            AeroReformation.LOGGER.info("[PhysicsAnchor] Upgraded warmup anchor sub={} at {}", id, pos);
            return;
        }

        if (anchorsFor(sl.dimension()).containsKey(pos)) return;

        // If this SubLevel already has an anchor in this dimension, reuse its marker
        var dimMap = anchorsFor(sl.dimension());
        for (var entry : dimMap.entrySet()) {
            if (entry.getValue().subLevel != null && entry.getValue().subLevel.getUniqueId().equals(id)) {
                var existing = entry.getValue();
                dimMap.put(pos, new AnchorData(subLevel, existing.marker, null, 2));
                AeroReformation.LOGGER.info("[PhysicsAnchor] Shared marker for sub={} at new pos={}", id, pos);
                return;
            }
        }

        // Discard any stale marker entity bound to the same SubLevel
        for (var e : sl.getEntities().getAll()) {
            if (e instanceof AnchorMarkerEntity m
                    && id.equals(m.getSubLevelUUID())
                    && !m.isRemoved()) {
                m.discard();
                AeroReformation.LOGGER.info("[PhysicsAnchor] Discarded stale marker sub={}", id);
            }
        }

        AnchorMarkerEntity marker = new AnchorMarkerEntity(
                dev.simulated_team.aero_reformation.registrate.AeroBlocks.ANCHOR_MARKER.get(), sl);
        marker.setSubLevelUUID(id);
        marker.setPos(pose.position().x(), pose.position().y(), pose.position().z());
        sl.addFreshEntity(marker);
        applySavedName(sl, id, marker);

        anchorsFor(sl.dimension()).put(pos, new AnchorData(subLevel, marker, null, 2));
        ANCHORED_SUBLEVELS.add(id);
        AUTO_PROTECTED.remove(id);
        AnchorSavedData.get(sl).add(id, pose.position().x(), pose.position().y(), pose.position().z());
        AeroReformation.LOGGER.info("[PhysicsAnchor] Anchor added at world {},{} sub={} pos={}",
                (int)pose.position().x(), (int)pose.position().z(), id, pos);
    }

    public static void removeAnchor(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        var dim = level.dimension();
        var map = anchorsFor(dim);
        AnchorData data = map.remove(pos);
        if (data == null) return;

        if (level instanceof ServerLevel sl) {
            UUID id = data.subLevel != null ? data.subLevel.getUniqueId() : null;
            // Remove PORTAL ticket for this anchor
            if (data.lastTicketChunk != null) {
                sl.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                        data.lastTicketChunk, data.ticketRadius, pos);
            }
            boolean sameSubHasOther = map.values().stream().anyMatch(
                    a -> a.subLevel != null && id != null && a.subLevel.getUniqueId().equals(id));
            if (!sameSubHasOther) {
                if (data.marker != null) data.marker.discard();
                ANCHORED_SUBLEVELS.remove(id);
                AnchorSavedData.get(sl).remove(id);
            }

            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(sl);
            if (container != null) {
                boolean anyAnchorLeft = map.values().stream().anyMatch(
                        a -> a.subLevel != null && !a.subLevel.isRemoved()
                                && container.getAllSubLevels().contains(a.subLevel));
                if (!anyAnchorLeft) {
                    for (var sl2 : container.getAllSubLevels()) {
                        UUID sid = sl2.getUniqueId();
                        if (AUTO_PROTECTED.remove(sid)) {
                            ANCHORED_SUBLEVELS.remove(sid);
                            AeroReformation.LOGGER.info("[PhysicsAnchor] Cleaned auto-protected sub={}", sid);
                        }
                    }
                }
            }
        }
        // Immediately sync clients so map markers disappear without delay
        if (level instanceof ServerLevel sl)
            syncToClients(sl);
        AeroReformation.LOGGER.info("[PhysicsAnchor] Anchor removed at {}", pos);
    }

    /** Get all anchor positions in a specific dimension. */
    public static Set<BlockPos> getAllAnchorPositions(ResourceKey<Level> dim) {
        return Collections.unmodifiableSet(anchorsFor(dim).keySet());
    }

    public static void clearAll() {
        for (var map : ANCHORS.values()) {
            for (var data : map.values()) {
                if (data.marker != null) data.marker.discard();
            }
        }
        for (var data : WARMUP.values()) {
            if (data.marker != null) data.marker.discard();
        }
        AeroReformation.LOGGER.info("[PhysicsAnchor] Cleared {}A + {}W on server stop", ANCHORS.size(), WARMUP.size());
        ANCHORS.clear();
        WARMUP.clear();
        ANCHORED_SUBLEVELS.clear();
        AUTO_PROTECTED.clear();
    }

    /** Discard all AnchorMarkerEntity instances in the level (called on server start). */
    public static void discardStaleEntities(ServerLevel serverLevel) {
        int count = 0;
        for (var e : serverLevel.getEntities().getAll()) {
            if (e instanceof AnchorMarkerEntity m && !m.isRemoved()) {
                m.discard();
                count++;
            }
        }
        if (count > 0)
            AeroReformation.LOGGER.info("[PhysicsAnchor] Discarded {} stale marker entities on start", count);
    }

    /** Discard all marker entities and clear all anchor data. Returns count. */
    public static int discardAllMarkers(ServerLevel serverLevel) {
        int count = 0;
        for (var e : serverLevel.getEntities().getAll()) {
            if (e instanceof AnchorMarkerEntity m && !m.isRemoved()) {
                m.discard();
                count++;
            }
        }
        if (count > 0) {
            AeroReformation.LOGGER.info("[PhysicsAnchor] Command: discarded {} marker entities", count);
        }
        // Also clear internal maps so ANCHORS won't reference dead entities
        for (var map : ANCHORS.values()) {
            for (var data : map.values()) {
                if (data.marker != null) data.marker.discard();
            }
        }
        for (var data : WARMUP.values()) {
            if (data.marker != null) data.marker.discard();
        }
        ANCHORS.clear();
        WARMUP.clear();
        ANCHORED_SUBLEVELS.clear();
        AUTO_PROTECTED.clear();
        // Immediately tell clients there are no markers
        syncToClients(serverLevel);
        return count;
    }

    public static boolean hasAnchoredSibling(SubLevel subLevel) {
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(subLevel.getLevel());
        if (container == null) return false;
        for (var sl : container.getAllSubLevels()) {
            if (sl != subLevel && ANCHORED_SUBLEVELS.contains(sl.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    public static void protectSubLevel(UUID id) {
        if (ANCHORED_SUBLEVELS.add(id)) { AUTO_PROTECTED.add(id); }
    }

    public static boolean hasAnchor(UUID subLevelId) {
        return ANCHORED_SUBLEVELS.contains(subLevelId);
    }

    public static AnchorMarkerEntity getMarker(Level level, BlockPos pos) {
        AnchorData data = anchorsFor(level.dimension()).get(pos);
        return data != null ? data.marker : null;
    }

    public static int getRadius(Level level, BlockPos pos) {
        AnchorData data = anchorsFor(level.dimension()).get(pos);
        return data != null ? data.ticketRadius : 2;
    }

    public static void renameAnchor(Level level, BlockPos pos, String name) {
        if (level.isClientSide()) return;
        AnchorMarkerEntity marker = getMarker(level, pos);
        if (marker == null) return;
        marker.setCustomName(name.isEmpty() ? null : net.minecraft.network.chat.Component.literal(name));
        marker.setCustomNameVisible(false);
        AnchorData data = anchorsFor(level.dimension()).get(pos);
        if (data != null && data.subLevel != null && level instanceof ServerLevel sl)
            AnchorSavedData.get(sl).setMarkerName(data.subLevel.getUniqueId(), name.isEmpty() ? null : name);
        AeroReformation.LOGGER.info("[PhysicsAnchor] Renamed marker at {} to '{}'", pos, name);
    }

    private static void applySavedName(ServerLevel sl, UUID subLevelId, AnchorMarkerEntity marker) {
        AnchorSavedData.StoredEntry e = AnchorSavedData.get(sl).getEntry(subLevelId);
        if (e != null && e.markerName() != null) {
            marker.setCustomName(net.minecraft.network.chat.Component.literal(e.markerName()));
            marker.setCustomNameVisible(false);
        }
    }

    public static void saveAllPositions(ServerLevel serverLevel) {
        for (var entry : anchorsFor(serverLevel.dimension()).entrySet()) {
            SubLevel sl = entry.getValue().subLevel;
            if (sl != null && !sl.isRemoved()) {
                var pose = sl.logicalPose();
                AnchorSavedData.get(serverLevel).add(sl.getUniqueId(),
                        pose.position().x(), pose.position().y(), pose.position().z());
            }
        }
    }

    public static void tick(ServerLevel serverLevel) {
        boolean every20 = serverLevel.getServer().getTickCount() % 20 == 0;

        if (every20) {
            for (AnchorSavedData.StoredEntry e : AnchorSavedData.get(serverLevel).getEntries()) {
                UUID id = e.subLevelId();
                if (!ANCHORED_SUBLEVELS.contains(id)) {
                    AnchorMarkerEntity marker = new AnchorMarkerEntity(
                            dev.simulated_team.aero_reformation.registrate.AeroBlocks.ANCHOR_MARKER.get(), serverLevel);
                    marker.setSubLevelUUID(id);
                    marker.setPos(e.worldX(), e.worldY(), e.worldZ());
                    serverLevel.addFreshEntity(marker);
                    applySavedName(serverLevel, id, marker);

                    WARMUP.put(id, new AnchorData(null, marker, null, 2));
                    ANCHORED_SUBLEVELS.add(id);
                    AeroReformation.LOGGER.info("[PhysicsAnchor] Warmup marker at {} {} sub={}",
                            (int)e.worldX(), (int)e.worldZ(), id);
                }
            }
        }

        for (var it = anchorsFor(serverLevel.dimension()).entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            AnchorData data = entry.getValue();
            SubLevel sl = data.subLevel;
            if (sl == null) continue;

            if (sl.isRemoved()) {
                if (data.marker != null) data.marker.discard();
                if (data.lastTicketChunk != null)
                    serverLevel.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                            data.lastTicketChunk, data.ticketRadius, entry.getKey());
                ANCHORED_SUBLEVELS.remove(sl.getUniqueId());
                it.remove();
                continue;
            }

            var pose = sl.logicalPose();
            double wx = pose.position().x(), wy = pose.position().y(), wz = pose.position().z();
            ChunkPos curChunk = new ChunkPos((int)Math.floor(wx/16), (int)Math.floor(wz/16));

            if (data.marker != null && !data.marker.isRemoved())
                data.marker.setPos(wx, wy, wz);

            // Adaptive radius based on SubLevel physical extent
            int radius = 2;
            try {
                var bb = sl.boundingBox();
                double extent = Math.max(bb.maxX() - bb.minX(), bb.maxZ() - bb.minZ());
                radius = Math.max(2, (int) Math.ceil(extent / 32.0));
            } catch (Exception ignored) {}

            // PORTAL ticket: refresh on chunk change OR every 5 seconds OR radius changed
            boolean chunkChanged = data.lastTicketChunk == null || !data.lastTicketChunk.equals(curChunk);
            boolean radiusChanged = data.ticketRadius != radius;
            boolean fiveSec = serverLevel.getServer().getTickCount() % 100 == 0;
            if (chunkChanged || fiveSec || radiusChanged) {
                // Always remove old ticket first to ensure proper timeout refresh
                if (data.lastTicketChunk != null) {
                    serverLevel.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                            data.lastTicketChunk, data.ticketRadius, entry.getKey());
                }
                serverLevel.getChunkSource().addRegionTicket(TicketType.PORTAL,
                        curChunk, radius, entry.getKey());
                anchorsFor(serverLevel.dimension()).put(entry.getKey(), new AnchorData(sl, data.marker, curChunk, radius));
                if (chunkChanged || radiusChanged || serverLevel.getServer().getTickCount() % 600 == 0) {
                    AeroReformation.LOGGER.info("[PhysicsAnchor] Ticket {}→{} radius={} loaded={}",
                            data.lastTicketChunk, curChunk, radius, serverLevel.getChunkSource().getLoadedChunksCount());
                }
            }

            if (every20)
                AnchorSavedData.get(serverLevel).add(sl.getUniqueId(), wx, wy, wz);
        }

        if (serverLevel.getServer().getTickCount() % 3 == 0) syncToClients(serverLevel);
    }

    private static void syncToClients(ServerLevel serverLevel) {
        List<AnchorMapSyncPacket.MarkerEntry> entries = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (var entry : anchorsFor(serverLevel.dimension()).entrySet()) {
            var data = entry.getValue();
            if (data.subLevel == null || data.subLevel.isRemoved()) continue;
            UUID id = data.subLevel.getUniqueId();
            if (!seen.add(id)) continue; // deduplicate: one entry per SubLevel
            var pose = data.subLevel.logicalPose();
            String name = data.marker != null && data.marker.hasCustomName()
                    ? data.marker.getCustomName().getString() : null;
            entries.add(new AnchorMapSyncPacket.MarkerEntry(
                    id,
                    pose.position().x(), pose.position().y(), pose.position().z(),
                    name, data.ticketRadius));
        }
        if (!entries.isEmpty()) {
            var packet = new AnchorMapSyncPacket(entries);
            for (var player : serverLevel.players()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        (net.minecraft.server.level.ServerPlayer) player, packet);
            }
        }
        // Clean stale warmups
        for (var it = WARMUP.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (entry.getValue().marker != null && !entry.getValue().marker.isAlive()) {
                ANCHORED_SUBLEVELS.remove(entry.getKey());
                it.remove();
            }
        }
    }
}