package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.content.items.ethereal_key.EtherealKeyItem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3d;

public class AnchorChunkLoader {
    record AnchorData(SubLevel subLevel, AnchorMarkerEntity marker, ChunkPos lastTicketChunk, int ticketRadius) {}
    // Per-dimension anchor maps: dimension 闁?(BlockPos 闁?AnchorData)
    private static final Map<ResourceKey<Level>, Map<BlockPos, AnchorData>> ANCHORS = new HashMap<>();
    private static final Map<UUID, AnchorData> WARMUP = new LinkedHashMap<>();
    private static final Set<UUID> ANCHORED_SUBLEVELS = new HashSet<>();
    private static final Set<UUID> AUTO_PROTECTED = new HashSet<>();

    static Map<BlockPos, AnchorData> anchorsFor(ResourceKey<Level> dim) {
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

        // Protect the new SubLevel FIRST, then clear old ones (prevents gap)
        ANCHORED_SUBLEVELS.add(id);
        AUTO_PROTECTED.remove(id);

        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(sl);
        if (container != null) {
            for (var other : container.getAllSubLevels()) {
                UUID otherId = other.getUniqueId();
                if (!otherId.equals(id) && ANCHORED_SUBLEVELS.contains(otherId)) {
                    ANCHORED_SUBLEVELS.remove(otherId);
                    AUTO_PROTECTED.remove(otherId);
                }
            }
        }

        // Upgrade warmup entry if exists
        AnchorData warmup = WARMUP.remove(id);
        if (warmup != null) {
            // Remove warmup's ticket (keyed by marker position, not anchor pos)
            if (warmup.lastTicketChunk != null && warmup.marker != null)
                sl.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                        warmup.lastTicketChunk, warmup.ticketRadius + 1, warmup.marker.blockPosition());
            int r = getSavedRadius(sl, id);
            anchorsFor(sl.dimension()).put(pos, new AnchorData(subLevel, warmup.marker, null, r));
            ANCHORED_SUBLEVELS.add(id);
            AUTO_PROTECTED.remove(id);
            AeroReformation.LOGGER.debug("[PhysicsAnchor] Upgraded warmup anchor sub={} at {}", id, pos);
            return;
        }

        if (anchorsFor(sl.dimension()).containsKey(pos)) return;

        // If this SubLevel already has an anchor in this dimension, reuse its marker
        var dimMap = anchorsFor(sl.dimension());
        for (var entry : dimMap.entrySet()) {
            if (entry.getValue().subLevel != null && entry.getValue().subLevel.getUniqueId().equals(id)) {
                var existing = entry.getValue();
                dimMap.put(pos, new AnchorData(subLevel, existing.marker, null, getSavedRadius(sl, id)));
                AeroReformation.LOGGER.debug("[PhysicsAnchor] Shared marker for sub={} at new pos={}", id, pos);
                return;
            }
        }

        // Discard any stale marker entity bound to the same SubLevel
        for (var e : sl.getEntities().getAll()) {
            if (e instanceof AnchorMarkerEntity m
                    && id.equals(m.getSubLevelUUID())
                    && !m.isRemoved()) {
                m.forceDiscard();
                AeroReformation.LOGGER.debug("[PhysicsAnchor] Discarded stale marker sub={}", id);
            }
        }

        AnchorMarkerEntity marker = new AnchorMarkerEntity(
                dev.simulated_team.aero_reformation.registrate.AeroBlocks.ANCHOR_MARKER.get(), sl);
        marker.setSubLevelUUID(id);
        marker.setPos(pose.position().x(), pose.position().y(), pose.position().z());
        sl.addFreshEntity(marker);
        applySavedName(sl, id, marker);
        // Restore hidden state if this SubLevel was previously hidden
        if (EtherealKeyItem.HIDDEN_SUBLEVELS.contains(id)) {
            marker.setHidden(true);
        }
        int savedRadius = getSavedRadius(sl, id);

        anchorsFor(sl.dimension()).put(pos, new AnchorData(subLevel, marker, null, savedRadius));
        ANCHORED_SUBLEVELS.add(id);
        AUTO_PROTECTED.remove(id);
        AnchorSavedData.get(sl).add(id, pose.position().x(), pose.position().y(), pose.position().z(), null, savedRadius);
        AeroReformation.LOGGER.debug("[PhysicsAnchor] Anchor added at world {},{} sub={} pos={}",
                (int)pose.position().x(), (int)pose.position().z(), id, pos);
    }

    public static void removeAnchor(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        var dim = level.dimension();
        var map = anchorsFor(dim);
        AnchorData data = map.remove(pos);
        if (data == null) return;

        if (level instanceof ServerLevel sl) {
            // Resolve UUID from SubLevel first, fall back to marker entity
            UUID id = data.subLevel != null ? data.subLevel.getUniqueId()
                    : (data.marker != null ? data.marker.getSubLevelUUID() : null);
            // Remove PORTAL ticket for this anchor (+1 to match add compensation)
            if (data.lastTicketChunk != null) {
                sl.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                        data.lastTicketChunk, data.ticketRadius + 1, pos);
            }
            boolean sameSubHasOther = map.values().stream().anyMatch(
                    a -> a.subLevel != null && id != null && a.subLevel.getUniqueId().equals(id));
            if (!sameSubHasOther && id != null) {
                // Sweep ALL marker entities for this SubLevel (not just data.marker)
                for (var e : sl.getEntities().getAll()) {
                    if (e instanceof AnchorMarkerEntity m
                            && id.equals(m.getSubLevelUUID())
                            && !m.isRemoved()) {
                        m.forceDiscard();
                    }
                }
                ANCHORED_SUBLEVELS.remove(id);
                WARMUP.remove(id);
                LAST_POS.remove(id);
                EtherealKeyItem.HIDDEN_SUBLEVELS.remove(id);
                AnchorSavedData.get(sl).remove(id);
            } else if (data.marker != null) {
                // SubLevel gone/unavailable but marker still exists — discard just this one
                data.marker.forceDiscard();
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
                            AeroReformation.LOGGER.debug("[PhysicsAnchor] Cleaned auto-protected sub={}", sid);
                        }
                    }
                }
            }
        }
        // Immediately sync clients so map markers disappear without delay
        if (level instanceof ServerLevel sl)
            syncToClients(sl);
        AeroReformation.LOGGER.debug("[PhysicsAnchor] Anchor removed at {}", pos);
    }

    /** Get all anchor positions in a specific dimension. */
    public static Set<BlockPos> getAllAnchorPositions(ResourceKey<Level> dim) {
        return Collections.unmodifiableSet(anchorsFor(dim).keySet());
    }

    public static void clearAll() {
        for (var map : ANCHORS.values()) {
            for (var data : map.values()) {
                if (data.marker != null) data.marker.forceDiscard();
            }
        }
        for (var data : WARMUP.values()) {
            if (data.marker != null) data.marker.forceDiscard();
        }
        AeroReformation.LOGGER.debug("[PhysicsAnchor] Cleared {}A + {}W on server stop", ANCHORS.size(), WARMUP.size());
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
                m.forceDiscard();
                count++;
            }
        }
        if (count > 0)
            AeroReformation.LOGGER.debug("[PhysicsAnchor] Discarded {} stale marker entities on start", count);
    }

    /** Discard all marker entities and clear all anchor data. Returns count. */
    public static int discardAllMarkers(ServerLevel serverLevel) {
        int count = 0;
        for (var e : serverLevel.getEntities().getAll()) {
            if (e instanceof AnchorMarkerEntity m && !m.isRemoved()) {
                m.forceDiscard();
                count++;
            }
        }
        if (count > 0) {
            AeroReformation.LOGGER.debug("[PhysicsAnchor] Command: discarded {} marker entities", count);
        }
        // Also clear internal maps so ANCHORS won't reference dead entities
        for (var map : ANCHORS.values()) {
            for (var data : map.values()) {
                if (data.marker != null) data.marker.forceDiscard();
            }
        }
        for (var data : WARMUP.values()) {
            if (data.marker != null) data.marker.forceDiscard();
        }
        ANCHORS.clear();
        WARMUP.clear();
        ANCHORED_SUBLEVELS.clear();
        AUTO_PROTECTED.clear();
        // Also clear SavedData so warmup doesn't recreate markers
        AnchorSavedData.get(serverLevel).clearAll();
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

    static void removeAnchoredSubLevel(UUID id) {
        ANCHORED_SUBLEVELS.remove(id);
        AUTO_PROTECTED.remove(id);
        LAST_POS.remove(id);
        WARMUP.remove(id);
        EtherealKeyItem.HIDDEN_SUBLEVELS.remove(id);
    }

    public static AnchorMarkerEntity getMarker(Level level, BlockPos pos) {
        AnchorData data = anchorsFor(level.dimension()).get(pos);
        return data != null ? data.marker : null;
    }

    public static int getRadius(Level level, BlockPos pos) {
        AnchorData data = anchorsFor(level.dimension()).get(pos);
        return data != null ? data.ticketRadius : 2;
    }

    public static void renameAnchor(Level level, BlockPos pos, String name, int radius) {
        if (level.isClientSide()) return;
        AnchorMarkerEntity marker = getMarker(level, pos);
        if (marker == null) return;
        marker.setCustomName(name.isEmpty() ? null : net.minecraft.network.chat.Component.literal(name));
        marker.setCustomNameVisible(false);
        AnchorData data = anchorsFor(level.dimension()).get(pos);
        if (data != null && data.subLevel != null && level instanceof ServerLevel sl) {
            int clamped = Mth.clamp(radius, 2, dev.simulated_team.aero_reformation.config.AeroReformationConfig.maxAnchorRadius);
            anchorsFor(level.dimension()).put(pos, new AnchorData(data.subLevel, data.marker, data.lastTicketChunk, clamped));
            AnchorSavedData.get(sl).setMarkerName(data.subLevel.getUniqueId(), name.isEmpty() ? null : name);
            AnchorSavedData.get(sl).setRadius(data.subLevel.getUniqueId(), clamped);
        }
        AeroReformation.LOGGER.debug("[PhysicsAnchor] Renamed marker at {} to '{}' radius={}", pos, name, radius);
    }

    private static void applySavedName(ServerLevel sl, UUID subLevelId, AnchorMarkerEntity marker) {
        AnchorSavedData.StoredEntry e = AnchorSavedData.get(sl).getEntry(subLevelId);
        if (e != null) {
            if (e.markerName() != null) {
                marker.setCustomName(net.minecraft.network.chat.Component.literal(e.markerName()));
                marker.setCustomNameVisible(false);
            }
        }
    }

    private static int getSavedRadius(ServerLevel sl, UUID subLevelId) {
        AnchorSavedData.StoredEntry e = AnchorSavedData.get(sl).getEntry(subLevelId);
        return e != null ? e.radius() : 2;
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
                    if (EtherealKeyItem.HIDDEN_SUBLEVELS.contains(id)) {
                        marker.setHidden(true);
                    }

                    WARMUP.put(id, new AnchorData(null, marker, null, e.radius()));
                    ANCHORED_SUBLEVELS.add(id);
                }
            }
        }

        for (var it = anchorsFor(serverLevel.dimension()).entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            AnchorData data = entry.getValue();
            SubLevel sl = data.subLevel;
            if (sl == null) continue;

            if (sl.isRemoved()) {
                UUID sid = sl.getUniqueId();
                if (data.marker != null) data.marker.forceDiscard();
                if (data.lastTicketChunk != null)
                    serverLevel.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                            data.lastTicketChunk, data.ticketRadius + 1, entry.getKey());
                ANCHORED_SUBLEVELS.remove(sid);
                WARMUP.remove(sid);
                LAST_POS.remove(sid);
                EtherealKeyItem.HIDDEN_SUBLEVELS.remove(sid);
                AnchorSavedData.get(serverLevel).remove(sid);
                it.remove();
                continue;
            }

            var pose = sl.logicalPose();
            double wx = pose.position().x(), wy = pose.position().y(), wz = pose.position().z();
            ChunkPos curChunk = new ChunkPos((int)Math.floor(wx/16), (int)Math.floor(wz/16));

            if (data.marker != null && !data.marker.isRemoved())
                data.marker.setPos(wx, wy, wz);

            // Use stored radius (set via GUI, defaults to 2)
            // +1 compensates for Minecraft ticket system off-by-one:
            // addRegionTicket(radius=N) loads chunks within Chebyshev distance N,
            // which yields (2N+1) chunks per side only after +1 correction.
            int radius = data.ticketRadius + 1;

            // PORTAL ticket: refresh on chunk change OR every 5 seconds OR radius changed
            boolean chunkChanged = data.lastTicketChunk == null || !data.lastTicketChunk.equals(curChunk);
            boolean fiveSec = serverLevel.getServer().getTickCount() % 100 == 0;
            if (chunkChanged || fiveSec) {
                // Always remove old ticket first to ensure proper timeout refresh
                if (data.lastTicketChunk != null) {
                    serverLevel.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                            data.lastTicketChunk, data.ticketRadius + 1, entry.getKey());
                }
                serverLevel.getChunkSource().addRegionTicket(TicketType.PORTAL,
                        curChunk, radius, entry.getKey());
                anchorsFor(serverLevel.dimension()).put(entry.getKey(), new AnchorData(sl, data.marker, curChunk, data.ticketRadius));
            }

            if (every20)
                AnchorSavedData.get(serverLevel).add(sl.getUniqueId(), wx, wy, wz);
        }

        // Also create tickets for warmup entries so chunks load before player arrives
        for (var it = WARMUP.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            AnchorData data = e.getValue();
            if (data.marker == null || data.marker.isRemoved()) {
                ANCHORED_SUBLEVELS.remove(e.getKey());
                it.remove();
                continue;
            }
            BlockPos markerPos = data.marker.blockPosition();
            ChunkPos curChunk = new ChunkPos(markerPos);
            boolean fiveSec = serverLevel.getServer().getTickCount() % 100 == 0;
            if (data.lastTicketChunk == null || !data.lastTicketChunk.equals(curChunk) || fiveSec) {
                int warmupRadius = data.ticketRadius + 1;
                if (data.lastTicketChunk != null)
                    serverLevel.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                            data.lastTicketChunk, warmupRadius, markerPos);
                serverLevel.getChunkSource().addRegionTicket(TicketType.PORTAL,
                        curChunk, warmupRadius, markerPos);
                WARMUP.put(e.getKey(), new AnchorData(null, data.marker, curChunk, data.ticketRadius));
            }
        }

        if (serverLevel.getServer().getTickCount() % 3 == 0) syncToClients(serverLevel);

        // === Self-check every 5 seconds ===
        if (serverLevel.getServer().getTickCount() % 100 == 0)
            selfCheckAnchors(serverLevel);

        // === Runaway protection every tick ===
        checkRunaway(serverLevel);
    }

    // === Runaway protection: last known position per anchored SubLevel (for velocity calc) ===
    private static final Map<UUID, Vector3d> LAST_POS = new ConcurrentHashMap<>();
    private static final double MAX_COORD = 29_999_984.0;  // just inside world border
    private static final double MAX_SPEED = 500.0;          // blocks per tick (10k blocks/sec)

    private static void checkRunaway(ServerLevel serverLevel) {
        var dimMap = anchorsFor(serverLevel.dimension());

        for (var entry : dimMap.entrySet()) {
            AnchorData data = entry.getValue();
            SubLevel sl = data.subLevel;
            if (sl == null || sl.isRemoved()) continue;

            UUID id = sl.getUniqueId();
            var pose = sl.logicalPose();
            double x = pose.position().x(), y = pose.position().y(), z = pose.position().z();

            // Check extreme coordinates
            if (Math.abs(x) > MAX_COORD || Math.abs(z) > MAX_COORD) {
                AeroReformation.LOGGER.warn("[PhysicsAnchor] RUNAWAY: sub={} at ({}, {}, {}) exceeded world border, self-destructing",
                        id, (int) x, (int) y, (int) z);
                destroySubLevelAnchors(serverLevel, dimMap, id, sl);
                continue;
            }

            // Check extreme velocity
            Vector3d last = LAST_POS.get(id);
            if (last != null) {
                double dx = x - last.x, dy = y - last.y, dz = z - last.z;
                double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (speed > MAX_SPEED) {
                    AeroReformation.LOGGER.warn("[PhysicsAnchor] RUNAWAY: sub={} speed {} blocks/tick, self-destructing",
                            id, (int) speed);
                    destroySubLevelAnchors(serverLevel, dimMap, id, sl);
                    continue;
                }
            }
            LAST_POS.computeIfAbsent(id, k -> new Vector3d()).set(x, y, z);
        }

        // Purge stale entries every 100 ticks
        if (serverLevel.getServer().getTickCount() % 100 == 0)
            LAST_POS.keySet().removeIf(uid -> !ANCHORED_SUBLEVELS.contains(uid)
                    || dimMap.values().stream().noneMatch(a -> a.subLevel != null && a.subLevel.getUniqueId().equals(uid)));
    }

    /** Remove all anchors for a runaway SubLevel, discarding marker and cleaning up tickets. */
    private static void destroySubLevelAnchors(ServerLevel serverLevel,
                                                Map<BlockPos, AnchorData> dimMap,
                                                UUID id, SubLevel sl) {
        // Find and remove all anchors pointing to this SubLevel
        var it = dimMap.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getValue().subLevel != null && e.getValue().subLevel.getUniqueId().equals(id)) {
                AnchorData ad = e.getValue();
                if (ad.marker != null) ad.marker.forceDiscard();
                if (ad.lastTicketChunk != null)
                    serverLevel.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                            ad.lastTicketChunk, ad.ticketRadius + 1, e.getKey());
                it.remove();
            }
        }
        ANCHORED_SUBLEVELS.remove(id);
        LAST_POS.remove(id);
        // Remove from warmup too
        WARMUP.remove(id);
        AnchorSavedData.get(serverLevel).remove(id);
        syncToClients(serverLevel);
    }

    private static void syncToClients(ServerLevel serverLevel) {
        List<AnchorMapSyncPacket.MarkerEntry> entries = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (var entry : anchorsFor(serverLevel.dimension()).entrySet()) {
            var data = entry.getValue();
            if (data.subLevel == null || data.subLevel.isRemoved()) continue;
            UUID id = data.subLevel.getUniqueId();
            // Skip hidden SubLevels — don't send map markers for them
            if (EtherealKeyItem.HIDDEN_SUBLEVELS.contains(id)) continue;
            if (!seen.add(id)) continue;
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

    /**
     * Self-check every 5 seconds: if both SubLevel and marker entity exist but the marker
     * is too far from the SubLevel's actual position, the tracking has desynced.
     * Discard the stale marker, regenerate directly at the SubLevel position.
     */
    private static void selfCheckAnchors(ServerLevel serverLevel) {
        var dimMap = anchorsFor(serverLevel.dimension());
        final double MAX_DIST_SQ = 64.0 * 64.0;

        for (var entry : dimMap.entrySet()) {
            AnchorData data = entry.getValue();

            if (data.subLevel == null || data.subLevel.isRemoved()) continue;
            if (data.marker == null || data.marker.isRemoved()) continue;

            var pose = data.subLevel.logicalPose();
            double sx = pose.position().x(), sy = pose.position().y(), sz = pose.position().z();
            double mx = data.marker.getX(), my = data.marker.getY(), mz = data.marker.getZ();

            double dx = mx - sx, dy = my - sy, dz = mz - sz;
            if (dx * dx + dy * dy + dz * dz <= MAX_DIST_SQ) continue;

            // Just reposition the marker — don't discard/recreate (avoids duplicates)
            data.marker.setPos(sx, sy, sz);
        }
    }
}
