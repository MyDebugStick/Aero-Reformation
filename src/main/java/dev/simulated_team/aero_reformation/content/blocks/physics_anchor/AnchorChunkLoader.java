package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.config.AeroReformationConfig;
import dev.simulated_team.aero_reformation.content.items.ethereal_key.EtherealKeyItem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a chunk-loading footprint centred on an {@link AnchorMarkerEntity}, and keeps that
 * marker glued to the world position of the SubLevel it protects.
 *
 * <h2>Binding model</h2>
 * The binding is identified by the <b>SubLevel UUID</b>, never by a cached object reference.
 * Sable unloads and later re-allocates sub-levels; the {@link SubLevel} instance that was
 * alive when the anchor was placed can therefore become a detached husk whose
 * {@code logicalPose()} never changes again. Every tick we re-resolve the live instance
 * from the container by UUID and re-attach when it has been replaced.
 *
 * <h2>Ticket model</h2>
 * Sable &gt;= 2.0.3 loads sub-levels through its own UUID-keyed ticket table
 * ({@code SubLevelTicketsSavedData} + {@code SubLevelTicketLoadingSystem}), not through
 * vanilla chunk tickets. A ticket therefore has to be registered <i>before</i> the
 * sub-level is loaded — which means it cannot go through
 * {@code addForceLoadTicket(ServerSubLevel, …)}, since that call requires the very object
 * whose absence we are trying to fix. We write into the persistent table directly instead.
 *
 * <p>All Sable plugin calls are reflective, so the mod still links against older Sable
 * builds where this API does not exist.
 */
public class AnchorChunkLoader {

    /* =====================================================================
     * Reflection bridge to Sable
     * =================================================================== */

    /** Sable &gt;= 2.0: {@code ServerSubLevelContainer#addForceLoadTicket / #removeForceLoadTicket}. */
    private static final boolean TICKET_API;
    /** Sable &gt;= 2.0.3: UUID-keyed persistent ticket table. */
    private static final boolean PERSISTENT_TICKETS;

    private static Class<?> C_CONTAINER;
    private static Class<?> C_SERVER_SUBLEVEL;
    private static Class<?> C_TICKET_TYPE;
    private static Method M_ADD_TICKET;
    private static Method M_REMOVE_TICKET;
    private static Method M_GET_ALL_TICKETS;
    private static Method M_LOAD_TICKETS;
    private static Method M_TICKET_INFO_TICKETS;
    private static Method M_SAVED_DATA;
    private static Constructor<?> C_TICKET_INFO;
    private static Constructor<?> C_LOADING_TICKET;
    private static Constructor<?> C_FAST_MAP;
    private static Object T_COMMAND_FORCED;

    static {
        boolean ticketApi = false;
        boolean persistent = false;
        try {
            C_TICKET_TYPE = Class.forName("dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType");
            C_CONTAINER = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
            C_SERVER_SUBLEVEL = Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel");
            T_COMMAND_FORCED = C_TICKET_TYPE.getField("COMMAND_FORCED").get(null);

            M_ADD_TICKET = C_CONTAINER.getMethod("addForceLoadTicket",
                    C_SERVER_SUBLEVEL, C_TICKET_TYPE, Object.class);
            M_REMOVE_TICKET = C_CONTAINER.getMethod("removeForceLoadTicket",
                    C_SERVER_SUBLEVEL, C_TICKET_TYPE, Object.class);
            ticketApi = true;
        } catch (Throwable t) {
            logQuietly("[PhysicsAnchor] Sable force-load ticket API unavailable ({}). "
                    + "Anchors fall back to vanilla chunk tickets only.", t.toString());
        }

        if (ticketApi) {
            try {
                Class<?> infoClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ticket.SubLevelTicketInfo");
                Class<?> ticketClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicket");
                Class<?> fastMapInterface = Class.forName("it.unimi.dsi.fastutil.objects.Object2ObjectMap");
                Class<?> fastMapImpl = Class.forName("it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap");
                Class<?> savedDataClass = Class.forName("dev.ryanhcode.sable.sublevel.storage.SubLevelTicketsSavedData");

                M_GET_ALL_TICKETS = C_CONTAINER.getMethod("getAllTickets");
                M_LOAD_TICKETS = C_CONTAINER.getMethod("loadTickets", fastMapInterface);
                C_TICKET_INFO = infoClass.getConstructor();
                M_TICKET_INFO_TICKETS = infoClass.getMethod("tickets");
                C_LOADING_TICKET = ticketClass.getConstructor(C_TICKET_TYPE, UUID.class, Object.class);
                M_SAVED_DATA = savedDataClass.getMethod("getOrLoad", ServerLevel.class);
                C_FAST_MAP = fastMapImpl.getConstructor();
                persistent = true;
            } catch (Throwable t) {
                logQuietly("[PhysicsAnchor] Sable persistent ticket table unavailable ({}). "
                        + "Anchors can only be force-loaded while the sub-level is already live.", t.toString());
            }
        }

        TICKET_API = ticketApi;
        PERSISTENT_TICKETS = persistent;
    }

    /** Logging must never be the reason a static initialiser blows up. */
    private static void logQuietly(String fmt, Object... args) {
        try {
            AeroReformation.LOGGER.warn(fmt, args);
        } catch (Throwable ignored) {
            // logger not constructed yet — nothing sensible to do
        }
    }

    /* =====================================================================
     * Binding state
     * =================================================================== */

    /**
     * One anchor binding.
     *
     * <p>{@code subLevelId} is the durable identity. {@code subLevel} is only a cache of the
     * currently-live instance and is refreshed from the container on every tick; it is
     * {@code null} while the sub-level is unloaded (the WARMUP state).
     */
    static final class AnchorData {
        private final UUID subLevelId;
        private final AnchorMarkerEntity marker;
        private final BlockPos anchorPos;
        private volatile SubLevel subLevel;
        private volatile ChunkPos lastTicketChunk;
        private volatile int ticketRadius;

        AnchorData(UUID subLevelId, BlockPos anchorPos, SubLevel subLevel,
                   AnchorMarkerEntity marker, ChunkPos lastTicketChunk, int ticketRadius) {
            this.subLevelId = subLevelId;
            this.anchorPos = anchorPos;
            this.subLevel = subLevel;
            this.marker = marker;
            this.lastTicketChunk = lastTicketChunk;
            this.ticketRadius = ticketRadius;
        }

        UUID subLevelId() { return this.subLevelId; }
        BlockPos anchorPos() { return this.anchorPos; }
        SubLevel subLevel() { return this.subLevel; }
        AnchorMarkerEntity marker() { return this.marker; }
        ChunkPos lastTicketChunk() { return this.lastTicketChunk; }
        int ticketRadius() { return this.ticketRadius; }

        void setSubLevel(@Nullable SubLevel subLevel) { this.subLevel = subLevel; }
        void setLastTicketChunk(@Nullable ChunkPos pos) { this.lastTicketChunk = pos; }
        void setTicketRadius(int radius) { this.ticketRadius = radius; }
    }

    /** Per-dimension anchor maps: dimension -> (anchor block pos -> binding). */
    private static final Map<ResourceKey<Level>, Map<BlockPos, AnchorData>> ANCHORS = new ConcurrentHashMap<>();
    /** Bindings whose sub-level is not loaded yet, keyed by SubLevel UUID. */
    private static final Map<UUID, AnchorData> WARMUP = new ConcurrentHashMap<>();
    private static final Set<UUID> ANCHORED_SUBLEVELS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> AUTO_PROTECTED = ConcurrentHashMap.newKeySet();
    /** Dimensions whose last sync carried at least one marker (drives the empty-set push). */
    private static final Set<ResourceKey<Level>> SYNCED_NON_EMPTY = ConcurrentHashMap.newKeySet();

    static Map<BlockPos, AnchorData> anchorsFor(ResourceKey<Level> dim) {
        return ANCHORS.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
    }

    /* =====================================================================
     * Sable sub-level resolution
     * =================================================================== */

    /** Resolve the live {@link SubLevel} for a UUID, or {@code null} when it is not loaded. */
    private static @Nullable SubLevel resolveLive(ServerLevel level, UUID subLevelId) {
        try {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) return null;
            SubLevel sub = container.getSubLevel(subLevelId);
            if (sub == null || sub.isRemoved()) return null;
            return sub;
        } catch (Throwable t) {
            AeroReformation.LOGGER.error("[PhysicsAnchor] Failed to resolve sub-level {}", subLevelId, t);
            return null;
        }
    }

    /* =====================================================================
     * Tickets
     * =================================================================== */

    /** Attach a command-forced ticket to an already-loaded sub-level. */
    private static void addForceLoadTicket(ServerLevel level, SubLevel subLevel) {
        if (!TICKET_API || subLevel == null) return;
        try {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container != null && subLevel instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel ssl) {
                M_ADD_TICKET.invoke(container, ssl, T_COMMAND_FORCED, Unit.INSTANCE);
            }
        } catch (Throwable t) {
            AeroReformation.LOGGER.error("[PhysicsAnchor] addForceLoadTicket failed for sub={}",
                    subLevel.getUniqueId(), t);
        }
    }

    private static void removeForceLoadTicket(ServerLevel level, SubLevel subLevel) {
        if (!TICKET_API || subLevel == null) return;
        try {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container != null && subLevel instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel ssl) {
                M_REMOVE_TICKET.invoke(container, ssl, T_COMMAND_FORCED, Unit.INSTANCE);
            }
        } catch (Throwable t) {
            AeroReformation.LOGGER.error("[PhysicsAnchor] removeForceLoadTicket failed for sub={}",
                    subLevel.getUniqueId(), t);
        }
    }

    /**
     * Register the command-forced ticket in Sable's persistent, UUID-keyed table.
     *
     * <p>This is the one path that works while the sub-level is <i>not</i> loaded.
     * {@code SubLevelTicketLoadingSystem.onSubLevelAdded} picks the ticket up and moves it
     * into {@code activeTickets} as soon as the sub-level comes back, which is what breaks
     * the old "need the object to force-load the object" deadlock.
     *
     * @return true when a new ticket was actually registered
     */
    private static boolean registerPersistentTicket(ServerLevel level, UUID subLevelId) {
        if (!TICKET_API || !PERSISTENT_TICKETS) return false;
        try {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) return false;

            @SuppressWarnings("unchecked")
            Map<UUID, Object> existing = (Map<UUID, Object>) M_GET_ALL_TICKETS.invoke(container);

            Object info = existing.get(subLevelId);
            if (info == null) info = C_TICKET_INFO.newInstance();

            @SuppressWarnings("unchecked")
            Set<Object> tickets = (Set<Object>) M_TICKET_INFO_TICKETS.invoke(info);

            boolean added = tickets.add(
                    C_LOADING_TICKET.newInstance(T_COMMAND_FORCED, subLevelId, Unit.INSTANCE));
            if (!added) return false;

            Map<UUID, Object> merged = new HashMap<>(existing);
            merged.put(subLevelId, info);

            Map<Object, Object> fastMap = castFastMap(C_FAST_MAP.newInstance());
            fastMap.putAll(merged);

            M_LOAD_TICKETS.invoke(container, fastMap);
            M_SAVED_DATA.invoke(null, level);   // touch SavedData so it gets marked dirty
            AeroReformation.LOGGER.debug("[PhysicsAnchor] Registered persistent ticket for sub={}", subLevelId);
            return true;
        } catch (Throwable t) {
            AeroReformation.LOGGER.error("[PhysicsAnchor] registerPersistentTicket failed for sub={}",
                    subLevelId, t);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> castFastMap(Object instance) {
        return (Map<Object, Object>) instance;
    }

    /** Called by EtherealKeyItem when hide state changes. */
    public static void onHideStateChanged(ServerLevel level, UUID subLevelId, boolean hidden) {
        if (!TICKET_API) return;
        SubLevel live = resolveLive(level, subLevelId);
        if (live == null) {
            // Not loaded: the persistent ticket table is the right lever.
            if (hidden) {
                removePersistentTicket(level, subLevelId);
            } else {
                registerPersistentTicket(level, subLevelId);
            }
            return;
        }
        if (hidden) {
            removeForceLoadTicket(level, live);
        } else {
            addForceLoadTicket(level, live);
        }
    }

    private static boolean removePersistentTicket(ServerLevel level, UUID subLevelId) {
        if (!TICKET_API || !PERSISTENT_TICKETS) return false;
        try {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) return false;

            @SuppressWarnings("unchecked")
            Map<UUID, Object> existing = (Map<UUID, Object>) M_GET_ALL_TICKETS.invoke(container);
            Object info = existing.get(subLevelId);
            if (info == null) return false;

            @SuppressWarnings("unchecked")
            Set<Object> tickets = (Set<Object>) M_TICKET_INFO_TICKETS.invoke(info);
            tickets.removeIf(t -> {
                try {
                    Object id = t.getClass().getMethod("getSubLevelId").invoke(t);
                    Object type = t.getClass().getMethod("getType").invoke(t);
                    return subLevelId.equals(id) && type == T_COMMAND_FORCED;
                } catch (Throwable t2) {
                    return false;
                }
            });

            Map<UUID, Object> merged = new HashMap<>(existing);
            if (tickets.isEmpty()) {
                merged.remove(subLevelId);
            } else {
                merged.put(subLevelId, info);
            }

            Map<Object, Object> fastMap = castFastMap(C_FAST_MAP.newInstance());
            fastMap.putAll(merged);
            M_LOAD_TICKETS.invoke(container, fastMap);
            M_SAVED_DATA.invoke(null, level);
            return true;
        } catch (Throwable t) {
            AeroReformation.LOGGER.error("[PhysicsAnchor] removePersistentTicket failed for sub={}",
                    subLevelId, t);
            return false;
        }
    }

    /* =====================================================================
     * Sub-level connection chains
     * =================================================================== */

    /**
     * Get all SubLevels connected by constraints (bearings etc.) to the given SubLevel.
     * Always returns at least the SubLevel itself.
     */
    private static List<SubLevel> getConnectedChain(SubLevel subLevel) {
        try {
            var chain = dev.ryanhcode.sable.api.SubLevelHelper.getConnectedChain(subLevel);
            if (chain != null && !chain.isEmpty()) return new ArrayList<>(chain);
        } catch (Throwable t) {
            AeroReformation.LOGGER.debug("[PhysicsAnchor] getConnectedChain failed: {}", t.toString());
        }
        return Collections.singletonList(subLevel);
    }

    /* =====================================================================
     * Anchor registration
     * =================================================================== */

    public static void addAnchor(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel sl)) return;

        final var be = sl.getBlockEntity(pos);
        final SubLevel subLevel = be != null
                ? Sable.HELPER.getContaining(be)
                : Sable.HELPER.getContaining(sl, pos);

        if (subLevel == null) {
            // Expected during cold start: the block entity exists but its sub-level has not
            // been re-allocated yet. onSubLevelLoaded() will finish the job. Kept at INFO
            // because this is the exact point where the old code failed silently — a stuck
            // anchor and a healthy one look identical without this line.
            AeroReformation.LOGGER.info(
                    "[PhysicsAnchor] addAnchor deferred at {} — sub-level not loaded yet", pos);
            return;
        }

        final UUID id = subLevel.getUniqueId();
        final var pose = subLevel.logicalPose();
        final var dimMap = anchorsFor(sl.dimension());

        // Protect the new SubLevel FIRST, then clear auto-protected ones (prevents a gap).
        // Only AUTO_PROTECTED SubLevels (from bearing splits) are displaced; manually
        // anchored SubLevels keep their anchors intact.
        ANCHORED_SUBLEVELS.add(id);
        AUTO_PROTECTED.remove(id);

        try {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(sl);
            if (container != null) {
                for (var other : container.getAllSubLevels()) {
                    UUID otherId = other.getUniqueId();
                    if (!otherId.equals(id) && AUTO_PROTECTED.contains(otherId)) {
                        ANCHORED_SUBLEVELS.remove(otherId);
                        AUTO_PROTECTED.remove(otherId);
                        final AnchorData displaced = WARMUP.remove(otherId);
                        if (displaced != null) releaseWarmupTicket(sl, displaced);
                        AeroReformation.LOGGER.debug(
                                "[PhysicsAnchor] Displaced auto-protected sub={} for new anchor sub={}",
                                otherId, id);
                    }
                }
            }
        } catch (Throwable t) {
            AeroReformation.LOGGER.error("[PhysicsAnchor] Failed scanning container while anchoring {}", id, t);
        }

        // Upgrade a warmup entry created from AnchorSavedData.
        final AnchorData warmup = WARMUP.remove(id);
        if (warmup != null) {
            // The live anchor below takes over chunk keeping, so hand the warmup ticket back
            // instead of leaking it.
            releaseWarmupTicket(sl, warmup);
            final int r = getSavedRadius(sl, id);
            final AnchorMarkerEntity marker = warmup.marker() != null ? warmup.marker() : createMarker(sl, id, pose);
            final AnchorData data = new AnchorData(id, pos, subLevel, marker, null, r);
            dimMap.put(pos, data);
            ANCHORED_SUBLEVELS.add(id);
            AUTO_PROTECTED.remove(id);
            AnchorSavedData.get(sl).add(id, pose.position().x(), pose.position().y(), pose.position().z(),
                    null, r, pos);
            addForceLoadTicket(sl, subLevel);
            applySavedNameToBody(sl, id, subLevel);
            AeroReformation.LOGGER.info("[PhysicsAnchor] Upgraded warmup anchor sub={} at {}", id, pos);
            anchorConnectedChain(sl, subLevel, id, pos, r);
            return;
        }

        if (dimMap.containsKey(pos)) return;

        // If this SubLevel already has an anchor in this dimension, reuse its marker.
        for (var entry : dimMap.entrySet()) {
            AnchorData existing = entry.getValue();
            if (id.equals(existing.subLevelId())) {
                final int r = getSavedRadius(sl, id);
                dimMap.put(pos, new AnchorData(id, pos, subLevel, existing.marker(), null, r));
                applySavedNameToBody(sl, id, subLevel);
                AeroReformation.LOGGER.debug("[PhysicsAnchor] Shared marker for sub={} at new pos={}", id, pos);
                return;
            }
        }

        discardMarkersFor(sl, id, "re-anchored: stale marker for this sub-level was replaced");

        final AnchorMarkerEntity marker = createMarker(sl, id, pose);
        final int savedRadius = getSavedRadius(sl, id);
        dimMap.put(pos, new AnchorData(id, pos, subLevel, marker, null, savedRadius));
        ANCHORED_SUBLEVELS.add(id);
        AUTO_PROTECTED.remove(id);
        AnchorSavedData.get(sl).add(id, pose.position().x(), pose.position().y(), pose.position().z(),
                null, savedRadius, pos);

        if (!EtherealKeyItem.HIDDEN_SUBLEVELS.contains(id)) {
            addForceLoadTicket(sl, subLevel);
        }

        AeroReformation.LOGGER.info("[PhysicsAnchor] Anchor added at world {},{} sub={} pos={}",
                (int) pose.position().x(), (int) pose.position().z(), id, pos);

        anchorConnectedChain(sl, subLevel, id, pos, savedRadius);
    }

    /** Anchor every constraint-connected SubLevel that is not anchored yet. */
    private static void anchorConnectedChain(ServerLevel sl, SubLevel subLevel, UUID id,
                                             BlockPos pos, int radius) {
        final boolean hidden = EtherealKeyItem.HIDDEN_SUBLEVELS.contains(id);
        for (var connected : getConnectedChain(subLevel)) {
            final UUID cid = connected.getUniqueId();
            if (cid.equals(id)) continue;
            if (!ANCHORED_SUBLEVELS.add(cid)) continue;

            final var cpose = connected.logicalPose();
            final ChunkPos cchunk = new ChunkPos(
                    Mth.floor(cpose.position().x() / 16.0), Mth.floor(cpose.position().z() / 16.0));
            sl.getChunkSource().addRegionTicket(TicketType.PORTAL, cchunk, radius + 1, pos);
            if (!hidden) addForceLoadTicket(sl, connected);
            AeroReformation.LOGGER.debug("[PhysicsAnchor] Anchored connected sub={} via anchor at {}", cid, pos);
        }
    }

    private static AnchorMarkerEntity createMarker(ServerLevel sl, UUID id, dev.ryanhcode.sable.companion.math.Pose3d pose) {
        AnchorMarkerEntity marker = new AnchorMarkerEntity(
                dev.simulated_team.aero_reformation.registrate.AeroBlocks.ANCHOR_MARKER.get(), sl);
        marker.setSubLevelUUID(id);
        marker.setPos(pose.position().x(), pose.position().y(), pose.position().z());
        sl.addFreshEntity(marker);
        applySavedName(sl, id, marker);
        if (EtherealKeyItem.HIDDEN_SUBLEVELS.contains(id)) marker.setHidden(true);
        return marker;
    }

    /** Force-discard every live marker entity bound to this SubLevel, recording why. */
    private static void discardMarkersFor(ServerLevel sl, UUID id, String reason) {
        for (var e : sl.getEntities().getAll()) {
            if (e instanceof AnchorMarkerEntity m && id.equals(m.getSubLevelUUID()) && !m.isRemoved()) {
                m.forceDiscard(reason);
            }
        }
    }

    /* =====================================================================
     * Anchor removal
     * =================================================================== */

    /**
     * Called when the anchor block leaves the world.
     *
     * <p><b>This deliberately does NOT destroy the persisted binding.</b>
     *
     * <p>Sable removes blocks inside a plot for reasons that have nothing to do with the
     * player: unloading a sub-level, splitting one apart, tearing down a heat-map region.
     * {@code ServerLevelPlot.destroyAllBlocks()} literally calls
     * {@code level.destroyBlock(...)} over the whole plot, so every one of those events
     * fires {@code Block#onRemove} here. The old code treated each one as "the player broke
     * the anchor": it deleted the {@link AnchorSavedData} row — the only record of which
     * block owned the binding — and the anchor could never come back. That is the server-side
     * binding loss, and it is why singleplayer looked fine: in singleplayer the plot is never
     * torn down underneath you, so {@code removeAnchor} simply never ran.
     *
     * <p>What we must <b>not</b> do is keep the anchor's resources alive "just in case". A
     * removed anchor that still owns a marker entity, or still holds chunk tickets, is a bug
     * the player sees immediately — and a persisted Sable ticket survives a restart, so the
     * chunks keep loading forever.
     *
     * <p>So: tear down everything eagerly and unconditionally, and keep only the
     * {@link AnchorSavedData} row, which records which block owned the binding. That single
     * row is enough for {@code PhysicsAnchorBlockEntity#onLoad} to re-register a teardown
     * that Sable reverses a moment later, while {@link #verifySavedAnchors} reclaims it once
     * the block is confirmed gone.
     */
    public static void removeAnchor(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel sl)) return;

        final var dimMap = anchorsFor(level.dimension());

        // The binding may be live, or parked in WARMUP because its sub-level is unloaded.
        // The two keep their chunk ticket under DIFFERENT keys — live anchors under the anchor
        // block position, warmup entries under the marker's position — and removing a region
        // ticket with the wrong key silently does nothing. So remember which one we took.
        AnchorData data = dimMap.remove(pos);
        boolean fromWarmup = false;
        UUID id = data != null ? data.subLevelId() : null;

        if (id == null) {
            for (var it = WARMUP.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                if (!pos.equals(entry.getValue().anchorPos())) continue;
                id = entry.getKey();
                data = entry.getValue();
                it.remove();
                fromWarmup = true;
                break;
            }
        }
        if (id == null) return;

        final UUID bindingId = id;
        final AnchorData removed = data;
        final boolean wasWarmup = fromWarmup;

        // Another anchor may still own this sub-level. The marker and the force-load ticket
        // are shared per sub-level, so nothing below may be torn down in that case.
        final boolean sameSubHasOther = dimMap.values().stream()
                .anyMatch(a -> bindingId.equals(a.subLevelId()))
                || WARMUP.containsKey(bindingId);

        if (sameSubHasOther) {
            AnchorSavedData.get(sl).setAnchorPos(bindingId, pos);
            sweepAutoProtected(sl);
            syncToClients(sl);
            AeroReformation.LOGGER.info(
                    "[PhysicsAnchor] Anchor at {} removed; sub={} is still anchored elsewhere",
                    pos, bindingId);
            return;
        }

        // --- Tear down eagerly: marker, tickets, bookkeeping. ---------------------------
        if (removed != null) {
            if (wasWarmup) {
                releaseWarmupTicket(sl, removed);
            } else if (removed.lastTicketChunk() != null) {
                sl.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                        removed.lastTicketChunk(), removed.ticketRadius() + 1, pos);
            }
            removeForceLoadTicket(sl, removed.subLevel());
        }
        removePersistentTicket(sl, bindingId);
        discardMarkersFor(sl, bindingId, "anchor block removed by the player");

        ANCHORED_SUBLEVELS.remove(bindingId);
        final AnchorData stillParked = WARMUP.remove(bindingId);
        if (stillParked != null) releaseWarmupTicket(sl, stillParked);
        LAST_POS.remove(bindingId);
        RUNAWAY_STRIKE.remove(bindingId);

        // Recovery seed only — see verifySavedAnchors().
        AnchorSavedData.get(sl).setAnchorPos(bindingId, pos);

        sweepAutoProtected(sl);
        syncToClients(sl);
        AeroReformation.LOGGER.info("[PhysicsAnchor] Anchor removed at {} (sub={})", pos, bindingId);
    }

    /**
     * Reclaim bindings whose anchor block is genuinely gone.
     *
     * <p>This is the other half of the park-don't-destroy policy in {@link #removeAnchor}.
     * We ask the world directly rather than guessing: if the block at the recorded position
     * is no longer a physics anchor, the player really did break it and the row goes away.
     * If the chunk is not loaded we keep the row untouched — we cannot tell, and a false
     * positive here would reintroduce exactly the bug we are fixing.
     */
    private static void verifySavedAnchors(ServerLevel serverLevel) {
        final var saved = AnchorSavedData.get(serverLevel);
        final var dimMap = anchorsFor(serverLevel.dimension());

        for (AnchorSavedData.StoredEntry e : new ArrayList<>(saved.getEntries())) {
            final BlockPos anchorPos = e.anchorPos();
            if (anchorPos == null) continue;
            // Still registered as a live anchor — nothing to reclaim.
            if (dimMap.containsKey(anchorPos)) continue;
            // Another anchor may still own this sub-level. Markers are shared per sub-level,
            // so reclaiming here would tear down a binding that is perfectly healthy.
            if (dimMap.values().stream().anyMatch(a -> e.subLevelId().equals(a.subLevelId()))) continue;
            // Not loaded: unknown. Leave it alone; the block entity will re-register it.
            if (!serverLevel.isLoaded(anchorPos)) continue;
            if (serverLevel.getBlockState(anchorPos)
                    .is(dev.simulated_team.aero_reformation.registrate.AeroBlocks.PHYSICS_ANCHOR.get())) {
                continue;
            }

            discardBinding(serverLevel, e.subLevelId(), anchorPos);
        }
    }

    /** Tear a parked binding down for real (the anchor block is confirmed gone). */
    private static void discardBinding(ServerLevel serverLevel, UUID id, BlockPos anchorPos) {
        final AnchorData parked = WARMUP.remove(id);
        if (parked != null) releaseWarmupTicket(serverLevel, parked);
        ANCHORED_SUBLEVELS.remove(id);
        AUTO_PROTECTED.remove(id);
        LAST_POS.remove(id);
        RUNAWAY_STRIKE.remove(id);
        EtherealKeyItem.HIDDEN_SUBLEVELS.remove(id);
        removePersistentTicket(serverLevel, id);
        discardMarkersFor(serverLevel, id, "binding reclaimed — no anchor block at the recorded position");
        AnchorSavedData.get(serverLevel).remove(id);

        AeroReformation.LOGGER.info(
                "[PhysicsAnchor] Reclaimed binding for sub={} — no anchor block at {} anymore", id, anchorPos);
    }

    /** Drop AUTO_PROTECTED bookkeeping when no anchor is left in this dimension. */
    private static void sweepAutoProtected(ServerLevel sl) {
        try {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(sl);
            if (container == null) return;
            final var map = anchorsFor(sl.dimension());
            boolean anyAnchorLeft = map.values().stream()
                    .anyMatch(a -> a.subLevel() != null && !a.subLevel().isRemoved()
                            && container.getAllSubLevels().contains(a.subLevel()));
            if (anyAnchorLeft) return;
            for (var sub : container.getAllSubLevels()) {
                UUID sid = sub.getUniqueId();
                if (AUTO_PROTECTED.remove(sid)) {
                    ANCHORED_SUBLEVELS.remove(sid);
                    AeroReformation.LOGGER.debug("[PhysicsAnchor] Cleaned auto-protected sub={}", sid);
                }
            }
        } catch (Throwable t) {
            AeroReformation.LOGGER.error("[PhysicsAnchor] sweepAutoProtected failed", t);
        }
    }

    /* =====================================================================
     * SubLevel lifecycle hooks (driven by SubLevelAutoAnchorObserver)
     * =================================================================== */

    /**
     * A sub-level became live. Either re-attach a stale cached instance, or promote the
     * warmup marker that has been waiting for exactly this moment.
     */
    public static void onSubLevelLoaded(ServerLevel sl, SubLevel subLevel) {
        final UUID id = subLevel.getUniqueId();
        final var dimMap = anchorsFor(sl.dimension());

        // Case A — anchor exists, its cached instance was replaced.
        for (var entry : dimMap.entrySet()) {
            AnchorData data = entry.getValue();
            if (!id.equals(data.subLevelId())) continue;
            if (data.subLevel() != subLevel) {
                data.setSubLevel(subLevel);
                // New instance: restore the anchor's name onto it, or /sable info would report a
                // bare UUID for an anchor that the player has already named.
                applySavedNameToBody(sl, id, subLevel);
                AeroReformation.LOGGER.info(
                        "[PhysicsAnchor] Re-attached live instance for anchor {} sub={}", entry.getKey(), id);
            }
            addForceLoadTicket(sl, subLevel);
            return;
        }

        // Case B — a warmup marker is waiting.
        final AnchorData warmup = WARMUP.remove(id);
        if (warmup == null) return;
        if (warmup.marker() == null || warmup.marker().isRemoved()) return;

        // Hand the warmup ticket back before re-filing this entry under the live anchor map.
        // Warmup tickets are keyed by the marker's position, live-anchor tickets by the anchor
        // block position — carrying the stale ChunkPos across would leak the old ticket, and
        // the live loop would then try to remove it with a key that does not match.
        releaseWarmupTicket(sl, warmup);

        final AnchorSavedData.StoredEntry stored = AnchorSavedData.get(sl).getEntry(id);
        final BlockPos anchorPos = stored != null && stored.anchorPos() != null
                ? stored.anchorPos()
                : warmup.marker().blockPosition();

        warmup.setSubLevel(subLevel);
        dimMap.put(anchorPos, warmup);
        ANCHORED_SUBLEVELS.add(id);
        AUTO_PROTECTED.remove(id);
        addForceLoadTicket(sl, subLevel);

        // Re-apply the anchor's name to the instance we just adopted. Sable swaps the instance on
        // reload, so without this step a named anchor would show a bare UUID again after every
        // unload/reload cycle.
        applySavedNameToBody(sl, id, subLevel);

        if (stored == null || stored.anchorPos() == null) {
            AeroReformation.LOGGER.warn("[PhysicsAnchor] Promoted warmup sub={} using a marker-derived "
                    + "anchor position {} — the owning block position was not recorded. Re-place the "
                    + "anchor to persist it.", id, anchorPos);
        } else {
            AeroReformation.LOGGER.info("[PhysicsAnchor] Promoted warmup marker to live anchor sub={} at {}",
                    id, anchorPos);
        }
    }

    /**
     * A sub-level was unloaded (not destroyed). Freeze the binding into WARMUP state and make
     * sure the persistent ticket survives, so the anchor survives the round-trip.
     */
    public static void onSubLevelUnloaded(ServerLevel sl, SubLevel subLevel) {
        final UUID id = subLevel.getUniqueId();
        final var dimMap = anchorsFor(sl.dimension());

        for (var entry : dimMap.entrySet()) {
            AnchorData data = entry.getValue();
            if (!id.equals(data.subLevelId())) continue;
            if (data.subLevel() == subLevel) data.setSubLevel(null);
            // Release the live anchor's ticket before parking. It is keyed by the anchor block
            // position, whereas a parked entry's ticket is keyed by the marker's position — the
            // old one could never be removed afterwards and would load that chunk forever.
            if (data.lastTicketChunk() != null) {
                sl.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                        data.lastTicketChunk(), data.ticketRadius() + 1, entry.getKey());
            }
            // Move it out of the live map; it comes back through onSubLevelLoaded.
            dimMap.remove(entry.getKey());
            WARMUP.put(id, new AnchorData(id, entry.getKey(), null, data.marker(),
                    null, data.ticketRadius()));
            AeroReformation.LOGGER.info("[PhysicsAnchor] Anchor {} parked in warmup state, sub={} unloaded",
                    entry.getKey(), id);
            break;
        }

        registerPersistentTicket(sl, id);
    }

    /**
     * A sub-level was destructively removed. Tear the binding down for real.
     */
    public static void onSubLevelRemoved(ServerLevel sl, SubLevel subLevel) {
        final UUID id = subLevel.getUniqueId();
        final var dimMap = anchorsFor(sl.dimension());

        final AnchorData parked = WARMUP.remove(id);
        if (parked != null) releaseWarmupTicket(sl, parked);
        ANCHORED_SUBLEVELS.remove(id);
        AUTO_PROTECTED.remove(id);
        LAST_POS.remove(id);
        RUNAWAY_STRIKE.remove(id);
        EtherealKeyItem.HIDDEN_SUBLEVELS.remove(id);
        removePersistentTicket(sl, id);
        AnchorSavedData.get(sl).remove(id);

        dimMap.entrySet().removeIf(entry -> {
            AnchorData data = entry.getValue();
            if (!id.equals(data.subLevelId())) return false;
            if (data.lastTicketChunk() != null) {
                sl.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                        data.lastTicketChunk(), data.ticketRadius() + 1, entry.getKey());
            }
            if (data.marker() != null) data.marker().forceDiscard("sub-level was destructively removed");
            return true;
        });

        syncToClients(sl);
        AeroReformation.LOGGER.debug("[PhysicsAnchor] Binding torn down for removed sub={}", id);
    }

    /* =====================================================================
     * Queries
     * =================================================================== */

    public static Set<BlockPos> getAllAnchorPositions(ResourceKey<Level> dim) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(anchorsFor(dim).keySet()));
    }

    public static boolean hasAnchor(UUID subLevelId) {
        return ANCHORED_SUBLEVELS.contains(subLevelId);
    }

    public static boolean hasAnchoredSibling(SubLevel subLevel) {
        try {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(subLevel.getLevel());
            if (container == null) return false;
            for (var sl : container.getAllSubLevels()) {
                if (sl != subLevel && ANCHORED_SUBLEVELS.contains(sl.getUniqueId())) return true;
            }
        } catch (Throwable t) {
            AeroReformation.LOGGER.error("[PhysicsAnchor] hasAnchoredSibling failed", t);
        }
        return false;
    }

    public static void protectSubLevel(UUID id) {
        if (ANCHORED_SUBLEVELS.add(id)) AUTO_PROTECTED.add(id);
    }

    public static AnchorMarkerEntity getMarker(Level level, BlockPos pos) {
        AnchorData data = anchorsFor(level.dimension()).get(pos);
        if (data != null && data.marker() != null && !data.marker().isRemoved()) {
            return data.marker();
        }
        return recoverMarker(level, pos);
    }

    /**
     * What the goggle tooltip should show for an anchor block, resolved on whichever side
     * is asking.
     *
     * <p>The binding tables are server-side state. On a dedicated server the client's copies
     * are empty, and the server-only {@link #recoverMarker} fallback cannot help either — so
     * the tooltip used to report "no marker" on a perfectly healthy anchor. On the client we
     * therefore read the synced mirror instead.
     *
     * @return null when this block genuinely has no binding
     */
    public static @Nullable AnchorDisplay describeAnchor(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            var entry = AnchorMapClientData.getEntry(pos);
            if (entry == null) return null;
            return new AnchorDisplay(entry.name(), entry.radius());
        }

        AnchorMarkerEntity marker = getMarker(level, pos);
        if (marker == null || marker.isRemoved()) return null;
        String name = marker.hasCustomName() ? marker.getCustomName().getString() : null;
        return new AnchorDisplay(name, getRadius(level, pos));
    }

    /** Side-agnostic view of an anchor binding, for UI. */
    public record AnchorDisplay(@Nullable String markerName, int radius) {}

    /** Attempt to recover a marker entity that exists in the world but lost its map entry. */
    @Nullable
    private static AnchorMarkerEntity recoverMarker(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel sl)) return null;
        var be = sl.getBlockEntity(pos);
        if (be == null) return null;
        var subLevel = Sable.HELPER.getContaining(be);
        if (subLevel == null) return null;

        final UUID id = subLevel.getUniqueId();
        for (var e : sl.getEntities().getAll()) {
            if (e instanceof AnchorMarkerEntity m && id.equals(m.getSubLevelUUID()) && !m.isRemoved()) {
                final int r = getSavedRadius(sl, id);
                anchorsFor(sl.dimension()).put(pos, new AnchorData(id, pos, subLevel, m, null, r));
                ANCHORED_SUBLEVELS.add(id);
                AeroReformation.LOGGER.warn("[PhysicsAnchor] Recovered lost marker for sub={} at anchor={}", id, pos);
                return m;
            }
        }
        return null;
    }

    public static int getRadius(Level level, BlockPos pos) {
        AnchorData data = anchorsFor(level.dimension()).get(pos);
        return data != null ? data.ticketRadius() : 2;
    }

    public static void renameAnchor(Level level, BlockPos pos, String name, int radius) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel sl)) return;

        final String stored = name.isEmpty() ? null : name;

        // Marker name first, but do NOT abort when the marker is absent: its chunk may simply be
        // unloaded, and the rest of the rename (radius, saved data, physics body) must still
        // apply. The old early-return silently dropped every one of those.
        final AnchorMarkerEntity marker = getMarker(level, pos);
        if (marker != null) {
            marker.setCustomName(stored == null ? null : net.minecraft.network.chat.Component.literal(stored));
            marker.setCustomNameVisible(false);
        }

        final AnchorData data = anchorsFor(level.dimension()).get(pos);
        if (data == null) return;

        final int clamped = Mth.clamp(radius, 2,
                dev.simulated_team.aero_reformation.config.AeroReformationConfig.maxAnchorRadius);
        data.setTicketRadius(clamped);
        AnchorSavedData.get(sl).setMarkerName(data.subLevelId(), stored);
        AnchorSavedData.get(sl).setRadius(data.subLevelId(), clamped);

        // Mirror the anchor's name onto the physics body itself, so /sable info and the forceload
        // listing show something meaningful instead of a raw UUID. ServerSubLevel.setName pushes
        // ClientboundChangeSubLevelNamePacket, and SubLevelSerializer persists it as
        // "display_name", so this survives a save/load round trip with no extra work.
        final SubLevel sub = data.subLevel() != null ? data.subLevel() : resolveLive(sl, data.subLevelId());
        if (sub != null && !sub.isRemoved()) {
            sub.setName(stored);
            AeroReformation.LOGGER.info(
                    "[PhysicsAnchor] Renamed anchor at {} to '{}' — mirrored onto sub={}",
                    pos, name, data.subLevelId());
        } else {
            AeroReformation.LOGGER.info(
                    "[PhysicsAnchor] Renamed anchor at {} to '{}' — sub={} is not loaded, "
                            + "physics body name deferred",
                    pos, name, data.subLevelId());
        }
    }

    private static void applySavedName(ServerLevel sl, UUID subLevelId, AnchorMarkerEntity marker) {
        AnchorSavedData.StoredEntry e = AnchorSavedData.get(sl).getEntry(subLevelId);
        if (e == null || e.markerName() == null) return;
        marker.setCustomName(net.minecraft.network.chat.Component.literal(e.markerName()));
        marker.setCustomNameVisible(false);
        applySavedNameToBody(sl, subLevelId, resolveLive(sl, subLevelId));
    }

    /**
     * Mirror the anchor's stored name onto the physics body itself.
     *
     * <p>Sable keeps this as the sub-level's {@code display_name} — it persists it on save and
     * pushes {@code ClientboundChangeSubLevelNamePacket} to clients — so a named anchor then shows
     * up by name in {@code /sable info} and the forceload listing instead of as a raw UUID.
     *
     * <p>Call this at every point where a body is (re)bound, because Sable swaps the instance on
     * reload: the name has to be re-applied to each new instance or it silently reverts to a UUID.
     */
    private static void applySavedNameToBody(ServerLevel sl, UUID subLevelId, SubLevel body) {
        if (body == null || body.isRemoved()) return;
        final AnchorSavedData.StoredEntry e = AnchorSavedData.get(sl).getEntry(subLevelId);
        if (e == null || e.markerName() == null) return;
        body.setName(e.markerName());
    }

    private static int getSavedRadius(ServerLevel sl, UUID subLevelId) {
        AnchorSavedData.StoredEntry e = AnchorSavedData.get(sl).getEntry(subLevelId);
        return e != null ? e.radius() : 2;
    }

    /* =====================================================================
     * Bulk maintenance
     * =================================================================== */

    public static void saveAllPositions(ServerLevel level) {
        for (var entry : anchorsFor(level.dimension()).values()) {
            SubLevel sl = entry.subLevel();
            if (sl != null && !sl.isRemoved()) {
                var pose = sl.logicalPose();
                AnchorSavedData.get(level).add(entry.subLevelId(),
                        pose.position().x(), pose.position().y(), pose.position().z());
            }
        }
    }

    public static void clearAll() {
        for (var map : ANCHORS.values()) {
            for (var data : map.values()) {
                if (data.marker() != null) data.marker().forceDiscard("server stopping");
            }
        }
        for (var data : WARMUP.values()) {
            if (data.marker() != null) data.marker().forceDiscard("server stopping");
        }
        AeroReformation.LOGGER.debug("[PhysicsAnchor] Cleared {} dimension map(s) + {} warmup entries on server stop",
                ANCHORS.size(), WARMUP.size());
        ANCHORS.clear();
        WARMUP.clear();
        ANCHORED_SUBLEVELS.clear();
        AUTO_PROTECTED.clear();
        LAST_POS.clear();
        RUNAWAY_STRIKE.clear();
        SYNCED_NON_EMPTY.clear();
    }

    /** Discard all AnchorMarkerEntity instances in the level (called on server start). */
    public static void discardStaleEntities(ServerLevel serverLevel) {
        int count = 0;
        for (var e : serverLevel.getEntities().getAll()) {
            if (e instanceof AnchorMarkerEntity m && !m.isRemoved()) {
                m.forceDiscard("startup sweep — stale marker left over from a previous run");
                count++;
            }
        }
        if (count > 0)
            AeroReformation.LOGGER.debug("[PhysicsAnchor] Discarded {} stale marker entities on start", count);
    }

    /**
     * Cleanup orphan marker entities: any AnchorMarkerEntity no longer referenced by an
     * anchor or warmup entry is a stale leftover and is force-discarded immediately instead
     * of lingering until the next restart.
     */
    public static void cleanupOrphanMarkers(ServerLevel serverLevel) {
        int removed = 0;
        for (var e : serverLevel.getEntities().getAll()) {
            if (!(e instanceof AnchorMarkerEntity m) || m.isRemoved()) continue;
            if (isMarkerReferenced(serverLevel, m)) continue;
            m.forceDiscard("orphan sweep — no anchor entry references it");
            removed++;
        }
        if (removed > 0)
            AeroReformation.LOGGER.debug("[PhysicsAnchor] Cleaned {} orphan marker(s)", removed);
    }

    /**
     * True if the marker is still referenced by an anchor or warmup entry.
     *
     * <p>Compares by SubLevel UUID, not by object identity. A marker whose chunk was unloaded and
     * later reloaded comes back as a NEW entity instance while the tables still hold the old one —
     * an identity check would then classify the live marker as an orphan and the five-second
     * sweep would delete it, forever, no matter how many times it was rebuilt.
     */
    private static boolean isMarkerReferenced(ServerLevel serverLevel, AnchorMarkerEntity marker) {
        final UUID id = marker.getSubLevelUUID();
        if (id == null) return false;
        for (var data : anchorsFor(serverLevel.dimension()).values()) {
            if (id.equals(data.subLevelId())) return true;
        }
        return WARMUP.containsKey(id);
    }

    /**
     * Command entry point: discard every marker entity for this level and tear down every
     * anchor binding it owns.
     *
     * <p>This used to just clear the in-memory maps and force-discard the entities. That left
     * the chunk tickets armed and Sable's force-load tickets on disk, so the area kept loading
     * even though nothing referenced it any more — and only a restart appeared to fix it. Now
     * each binding goes through {@link #tearDownAnchor}, which is the single place that knows
     * how to release everything.
     */
    public static int discardAllMarkers(ServerLevel serverLevel) {
        int count = 0;
        final var dimMap = anchorsFor(serverLevel.dimension());

        // Snapshot: tearDownAnchor mutates the map while we iterate.
        for (var data : new ArrayList<>(dimMap.values())) {
            if (data.marker() != null && !data.marker().isRemoved()) count++;
            tearDownAnchor(serverLevel, data.anchorPos(), data);
        }

        // Parked entries hold tickets too, and they live in a dimension-agnostic map.
        for (var entry : new ArrayList<>(WARMUP.entrySet())) {
            final AnchorData data = entry.getValue();
            final UUID id = entry.getKey();
            if (data.marker() != null && !data.marker().isRemoved()) count++;

            releaseWarmupTicket(serverLevel, data);
            removeForceLoadTicket(serverLevel, data.subLevel());
            removePersistentTicket(serverLevel, id);
            ANCHORED_SUBLEVELS.remove(id);
            AUTO_PROTECTED.remove(id);
            LAST_POS.remove(id);
            RUNAWAY_STRIKE.remove(id);
            EtherealKeyItem.HIDDEN_SUBLEVELS.remove(id);
            AnchorSavedData.get(serverLevel).remove(id);
            WARMUP.remove(id);
        }

        // Sweep any marker entity that is not covered by the tables above.
        for (var e : serverLevel.getEntities().getAll()) {
            if (e instanceof AnchorMarkerEntity m && !m.isRemoved()) {
                m.forceDiscard("command: removemarkers");
                count++;
            }
        }

        // NOTE: do NOT clear SYNCED_NON_EMPTY here. syncToClients() needs to see that this
        // dimension last pushed a non-empty set, so it pushes the now-empty one and clients
        // drop their stale markers. Clearing it first would suppress exactly that packet.
        syncToClients(serverLevel);

        AeroReformation.LOGGER.info(
                "[PhysicsAnchor] Command: discarded {} marker entities and released all bindings", count);
        return count;
    }

    /**
     * Command entry point: release every vanilla chunk ticket this mod has armed.
     *
     * <p>Two kinds of ticket exist in the anchor system, and this touches exactly one of them:
     * <ul>
     *   <li>the vanilla {@code PORTAL} ticket — keeps an anchor's <em>neighbourhood</em> resident.
     *       It is our own bookkeeping and is what this command releases.</li>
     *   <li>the {@code sable:command_forced} ticket — keeps the <em>physics body</em> loaded. That
     *       one belongs to Sable, and clearing it would defeat the anchor, so it is deliberately
     *       left alone.</li>
     * </ul>
     *
     * <p>Tickets are keyed, and the key depends on where the binding lives: a live anchor keys on
     * its block position, a parked entry on its marker's position. Both are covered. The cached
     * {@code lastTicketChunk} is cleared afterwards so the next tick re-arms it for anchors that
     * are still valid — this command resets the tickets, it does not disable them.
     *
     * <p>It cannot reach a ticket whose entry has already been dropped from memory: removal needs
     * the original key, and once the bookkeeping is gone the key is gone with it. Those expire on
     * their own (vanilla tickets are not persisted) and a restart clears them.
     */
    public static int cleanTickets(ServerLevel serverLevel) {
        int released = 0;

        for (var data : anchorsFor(serverLevel.dimension()).values()) {
            if (data.lastTicketChunk() == null || data.anchorPos() == null) continue;
            serverLevel.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                    data.lastTicketChunk(), data.ticketRadius() + 1, data.anchorPos());
            data.setLastTicketChunk(null);
            released++;
        }

        for (var data : WARMUP.values()) {
            final AnchorMarkerEntity marker = data.marker();
            if (data.lastTicketChunk() == null || marker == null) continue;
            serverLevel.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                    data.lastTicketChunk(), data.ticketRadius() + 1, marker.blockPosition());
            data.setLastTicketChunk(null);
            released++;
        }

        AeroReformation.LOGGER.info(
                "[PhysicsAnchor] Command: released {} vanilla chunk ticket(s); "
                        + "Sable force-load tickets left untouched",
                released);
        return released;
    }

    /* =====================================================================
     * Per-tick driver
     * =================================================================== */

    public static void tick(ServerLevel serverLevel) {
        final long tickCount = serverLevel.getServer().getTickCount();
        final boolean every20 = tickCount % 20 == 0;
        final boolean fiveSec = tickCount % 100 == 0;
        final var dimMap = anchorsFor(serverLevel.dimension());

        // --- 1. cold start: rebuild markers from AnchorSavedData -----------
        if (every20) {
            promoteSavedAnchors(serverLevel);
        }

        // --- 2. live anchors ----------------------------------------------
        for (var entry : dimMap.entrySet()) {
            final BlockPos anchorPos = entry.getKey();
            final AnchorData data = entry.getValue();

            // PATCH 1 — throttled. A binding whose anchor block is gone is a corpse, and a corpse
            // keeps its chunk ticket alive forever. Sable removes plot blocks without firing
            // Block#onRemove, so waiting for that callback is not enough and the world has to be
            // asked directly. But this is a plot-space blockstate lookup, so it runs once a
            // second per anchor instead of twenty times — a block that disappears is not
            // something the player needs to see handled within 50 ms.
            if (every20
                    && serverLevel.isLoaded(anchorPos)
                    && !serverLevel.getBlockState(anchorPos)
                            .is(dev.simulated_team.aero_reformation.registrate.AeroBlocks.PHYSICS_ANCHOR.get())) {
                AeroReformation.LOGGER.info(
                        "[PhysicsAnchor] Anchor block at {} is gone — tearing down binding (sub={})",
                        anchorPos, data.subLevelId());
                tearDownAnchor(serverLevel, anchorPos, data);
                continue;
            }

            // Trust the cached instance, exactly like the original implementation did. The
            // force-load ticket armed in addAnchor keeps the sub-level resident, so Sable never
            // swaps the instance out from under us and a plain field read is enough. Re-resolving
            // through the container on every tick was only needed while the park policy could
            // drop that ticket mid-flight; that policy is gone, so the lookup was pure waste.
            final SubLevel cached = data.subLevel();
            SubLevel live = cached;

            if (live == null || live.isRemoved()) {
                // Slow path, and now the ONLY place a container lookup happens: either this
                // binding has never been resolved, or the instance we hold is dead. Both rare.
                live = resolveLive(serverLevel, data.subLevelId());
            }

            if (live == null) {
                // Not loaded (or mid-transition). Keep the marker frozen where it is, keep the
                // persistent ticket on file so the sub-level comes back, AND keep the anchor's
                // neighbourhood loaded — the anchor block itself lives in that area.
                if (cached != null && cached.isRemoved()) {
                    tearDownAnchor(serverLevel, anchorPos, data);
                    continue;
                }
                registerPersistentTicket(serverLevel, data.subLevelId());
                if (data.marker() == null || data.marker().isRemoved()) {
                    // The marker is the only thing that anchors this footprint. Once it is gone
                    // the anchor is dead: clear the sub-level's force-load, drop the entry and
                    // release the chunk ticket, instead of leaving a ticket armed for an anchor
                    // that no longer exists.
                    AeroReformation.LOGGER.info(
                            "[PhysicsAnchor] Marker at {} is gone — tearing down binding (sub={})",
                            anchorPos, data.subLevelId());
                    tearDownAnchor(serverLevel, anchorPos, data);
                    continue;
                }
                refreshChunkTicket(serverLevel, anchorPos, data,
                        new ChunkPos(data.marker().blockPosition()), fiveSec);
                continue;
            }

            if (live != cached) {
                data.setSubLevel(live);
                addForceLoadTicket(serverLevel, live);
                AeroReformation.LOGGER.info(
                        "[PhysicsAnchor] Re-attached live sub-level instance sub={} anchor={}",
                        data.subLevelId(), anchorPos);
            }

            final var pose = live.logicalPose();
            final double wx = pose.position().x(), wy = pose.position().y(), wz = pose.position().z();
            final ChunkPos curChunk = new ChunkPos(Mth.floor(wx / 16.0), Mth.floor(wz / 16.0));

            if (data.marker() == null || data.marker().isRemoved()) {
                // Same death case as above, one branch further along: the sub-level is live but
                // nothing tracks the footprint any more. Clear the force-load, drop the entry
                // and release the ticket rather than refresh it forever.
                AeroReformation.LOGGER.info(
                        "[PhysicsAnchor] Marker at {} is gone — tearing down binding (sub={})",
                        anchorPos, data.subLevelId());
                tearDownAnchor(serverLevel, anchorPos, data);
                continue;
            }

            // Skip the move when nothing actually moved: setPos recomputes the bounding box and
            // can re-index the entity's section, which is pure waste for a stationary craft.
            final AnchorMarkerEntity marker = data.marker();
            if (marker.getX() != wx || marker.getY() != wy || marker.getZ() != wz) {
                marker.setPos(wx, wy, wz);
            }

            // Keep the anchor's neighbourhood loaded.
            refreshChunkTicket(serverLevel, anchorPos, data, curChunk, fiveSec);

            if (every20) {
                AnchorSavedData.get(serverLevel).add(data.subLevelId(), wx, wy, wz);
            }
        }

        // --- 3. warmup entries ---------------------------------------------
        tickWarmup(serverLevel, fiveSec);

        // --- 4. network + health -------------------------------------------
        if (tickCount % 3 == 0) syncToClients(serverLevel);
        if (fiveSec) {
            verifySavedAnchors(serverLevel);
            selfCheckAnchors(serverLevel);
            cleanupOrphanMarkers(serverLevel);
        }
        checkRunaway(serverLevel);
    }

    /**
     * (Re)arm the vanilla chunk ticket that keeps an anchor's surroundings loaded.
     *
     * <p>Called both while the sub-level is live (centred on its world position) and while it
     * is away (centred on the marker's last known position). The old code only refreshed the
     * ticket on the live path and bailed out with {@code continue} on every other tick, so the
     * ticket lapsed and the neighbourhood unloaded.
     *
     * <p>Purely diagnostic on top of that: a moved centre is logged at INFO so a test run can
     * confirm the ticket is actually being armed.
     */
    private static void refreshChunkTicket(ServerLevel serverLevel, BlockPos key,
                                           AnchorData data, ChunkPos centre, boolean force) {
        final int radius = data.ticketRadius() + 1;
        final boolean moved = data.lastTicketChunk() == null || !data.lastTicketChunk().equals(centre);
        if (!moved && !force) return;

        if (data.lastTicketChunk() != null) {
            serverLevel.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                    data.lastTicketChunk(), data.ticketRadius() + 1, key);
        }
        serverLevel.getChunkSource().addRegionTicket(TicketType.PORTAL, centre, radius, key);
        data.setLastTicketChunk(centre);

        // Diagnostic only, and only while debugging: this runs every five seconds per anchor, so
        // anything louder than DEBUG turns the log into a wall of ticket noise. Turn the debug
        // log on when a run needs to prove the ticket is really being honoured.
        if (AeroReformation.LOGGER.isDebugEnabled()) {
            final boolean resident = serverLevel.getChunkSource().getChunkNow(centre.x, centre.z) != null;
            AeroReformation.LOGGER.debug(
                    "[PhysicsAnchor] Chunk ticket {} sub={} centre=({},{}) radius={} resident={}",
                    moved ? "armed" : "refreshed", data.subLevelId(), centre.x, centre.z, radius, resident);
        }
    }

    /** Rebuild WARMUP markers from persisted data for anchors not currently live. */
    private static void promoteSavedAnchors(ServerLevel serverLevel) {
        for (AnchorSavedData.StoredEntry e : AnchorSavedData.get(serverLevel).getEntries()) {
            final UUID id = e.subLevelId();
            if (ANCHORED_SUBLEVELS.contains(id)) continue;
            if (WARMUP.containsKey(id)) continue;

            // Never resurrect a marker for a block that is gone. This runs every second, so
            // without the check a removed anchor's marker reappears almost immediately and
            // the player can never get rid of it.
            final BlockPos anchorPos = e.anchorPos();
            if (anchorPos != null && serverLevel.isLoaded(anchorPos)
                    && !serverLevel.getBlockState(anchorPos)
                            .is(dev.simulated_team.aero_reformation.registrate.AeroBlocks.PHYSICS_ANCHOR.get())) {
                continue;
            }

            AnchorMarkerEntity marker = new AnchorMarkerEntity(
                    dev.simulated_team.aero_reformation.registrate.AeroBlocks.ANCHOR_MARKER.get(), serverLevel);
            marker.setSubLevelUUID(id);
            marker.setPos(e.worldX(), e.worldY(), e.worldZ());
            serverLevel.addFreshEntity(marker);
            applySavedName(serverLevel, id, marker);
            if (EtherealKeyItem.HIDDEN_SUBLEVELS.contains(id)) marker.setHidden(true);

            final BlockPos key = anchorPos != null ? anchorPos : marker.blockPosition();

            WARMUP.put(id, new AnchorData(id, key, null, marker, null, e.radius()));
            ANCHORED_SUBLEVELS.add(id);

            // Register the persistent ticket up front. Without this the sub-level can never
            // come back, because Sable >= 2.0.3 no longer loads sub-levels off vanilla
            // chunk tickets — and addForceLoadTicket() needs the very object we lack.
            registerPersistentTicket(serverLevel, id);
        }
    }

    /**
     * Release the vanilla chunk ticket a parked (WARMUP) entry is holding.
     *
     * <p>Warmup entries keep the marker's chunk loaded while their sub-level is away. Dropping
     * the map entry without dropping the ticket leaked it — and because the ticket key is
     * persisted alongside the anchor, the leak survived restarts, so the chunks kept loading
     * forever after the anchor was gone. Every path that removes a warmup entry must call
     * this first.
     */
    private static void releaseWarmupTicket(ServerLevel serverLevel, AnchorData data) {
        final ChunkPos chunk = data.lastTicketChunk();
        final AnchorMarkerEntity marker = data.marker();
        if (chunk == null || marker == null) return;
        serverLevel.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                chunk, data.ticketRadius() + 1, marker.blockPosition());
        data.setLastTicketChunk(null);
    }

    private static void tickWarmup(ServerLevel serverLevel, boolean fiveSec) {
        for (var it = WARMUP.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            AnchorData data = e.getValue();
            if (data.marker() == null || data.marker().isRemoved()) {
                // Dead warmup. Drop the ticket AND the saved row: leaving the row would let
                // promoteSavedAnchors resurrect this entry a second later, and we would
                // oscillate between creating and destroying the marker.
                releaseWarmupTicket(serverLevel, data);
                ANCHORED_SUBLEVELS.remove(e.getKey());
                AnchorSavedData.get(serverLevel).remove(e.getKey());
                it.remove();
                continue;
            }

            // The owning block must still exist. Otherwise this entry is the corpse of a removed
            // anchor, and promoting it back to life would make the marker un-removable — which is
            // exactly what used to happen. Checked on the five-second beat rather than every
            // tick: a parked binding is by definition not urgent.
            final BlockPos owner = data.anchorPos();
            if (fiveSec
                    && owner != null && serverLevel.isLoaded(owner)
                    && !serverLevel.getBlockState(owner)
                            .is(dev.simulated_team.aero_reformation.registrate.AeroBlocks.PHYSICS_ANCHOR.get())) {
                releaseWarmupTicket(serverLevel, data);
                discardMarkersFor(serverLevel, e.getKey(), "warmup entry whose anchor block is gone");
                ANCHORED_SUBLEVELS.remove(e.getKey());
                it.remove();
                continue;
            }

            // A sub-level may have quietly come back without the observer firing (e.g. it was
            // already loaded when we restarted). Poll for it so recovery is not observer-only —
            // but on the five-second beat, not every tick: this is a container lookup per parked
            // entry, and onSubLevelAdded() already covers the normal case.
            if (fiveSec) {
                final SubLevel live = resolveLive(serverLevel, e.getKey());
                if (live != null) {
                    onSubLevelLoaded(serverLevel, live);
                    continue;
                }
            }

            final BlockPos markerPos = data.marker().blockPosition();
            refreshChunkTicket(serverLevel, markerPos, data, new ChunkPos(markerPos), fiveSec);
        }
    }

    /** Remove an anchor whose sub-level was destructively removed. */
    private static void tearDownAnchor(ServerLevel serverLevel, BlockPos anchorPos, AnchorData data) {
        final var dimMap = anchorsFor(serverLevel.dimension());
        if (data.lastTicketChunk() != null) {
            serverLevel.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                    data.lastTicketChunk(), data.ticketRadius() + 1, anchorPos);
        }
        if (data.marker() != null) data.marker().forceDiscard("binding torn down");
        removeForceLoadTicket(serverLevel, data.subLevel());
        removePersistentTicket(serverLevel, data.subLevelId());

        ANCHORED_SUBLEVELS.remove(data.subLevelId());
        final AnchorData parked = WARMUP.remove(data.subLevelId());
        if (parked != null) releaseWarmupTicket(serverLevel, parked);
        LAST_POS.remove(data.subLevelId());
        RUNAWAY_STRIKE.remove(data.subLevelId());
        EtherealKeyItem.HIDDEN_SUBLEVELS.remove(data.subLevelId());
        AnchorSavedData.get(serverLevel).remove(data.subLevelId());
        dimMap.remove(anchorPos);

        AeroReformation.LOGGER.info("[PhysicsAnchor] Torn down anchor {} — sub-level {} was removed",
                anchorPos, data.subLevelId());
    }

    /* =====================================================================
     * Runaway protection
     * =================================================================== */

    /** Last observed world position per anchored sub-level. */
    private static final Map<UUID, Vector3d> LAST_POS = new ConcurrentHashMap<>();

    /**
     * Overspeed strikes, as {@code UUID -> (strikeCount, lastStrikeTick)}.
     *
     * <p>The guard is two-step on purpose: one teleport-sized jump must not nuke a healthy
     * craft. The previous implementation stored a bare "seen once" flag that was cleared on
     * <em>any</em> tick below the threshold — and a physically jittering craft dips below the
     * threshold constantly, so the flag never survived long enough to escalate and the guard
     * effectively never fired. Counting strikes inside a rolling window keeps the "repeated
     * evidence" requirement while staying robust to that jitter.
     */
    private static final Map<UUID, StrikeRecord> RUNAWAY_STRIKE = new ConcurrentHashMap<>();

    private record StrikeRecord(int strikes, long tick) {}

    private static final double MAX_COORD = 29_999_984.0;   // just inside the world border
    private static final double MIN_Y = -20_000.0;          // Sable's own remove bounds, padded
    private static final double MAX_Y = 200_000.0;

    private static void checkRunaway(ServerLevel serverLevel) {
        if (!AeroReformationConfig.runawayProtectionEnabled) return;

        final var dimMap = anchorsFor(serverLevel.dimension());
        // Fast path: this is called every tick for every dimension, and most dimensions have
        // no anchors at all. Bail before touching the clock or the config.
        if (dimMap.isEmpty()) return;

        final long now = serverLevel.getServer().getTickCount();
        final int window = AeroReformationConfig.runawayStrikeWindowTicks;
        final int strikesToKill = AeroReformationConfig.runawayStrikesToKill;
        final double maxSpeed = AeroReformationConfig.runawayMaxSpeed;
        final double maxSpeedSq = maxSpeed * maxSpeed;

        for (var entry : dimMap.entrySet()) {
            final AnchorData data = entry.getValue();
            final SubLevel sl = data.subLevel();
            if (sl == null || sl.isRemoved()) continue;

            final UUID id = data.subLevelId();
            final var pose = sl.logicalPose();
            final double x = pose.position().x(), y = pose.position().y(), z = pose.position().z();

            // --- hard bounds ---------------------------------------------------
            // X/Z against the world border. Y is nominally bounded by Sable's
            // sub_level_remove_min/max, but a runaway craft can leave that window before
            // Sable notices, so check it here too.
            if (Math.abs(x) > MAX_COORD || Math.abs(z) > MAX_COORD || y < MIN_Y || y > MAX_Y) {
                AeroReformation.LOGGER.warn(
                        "[PhysicsAnchor] RUNAWAY sub={} left the world at ({}, {}, {}) — self-destructing",
                        id, (int) x, (int) y, (int) z);
                destroySubLevelAnchors(serverLevel, dimMap, id, sl);
                continue;
            }

            // --- speed check ---------------------------------------------------
            final Vector3d last = LAST_POS.get(id);
            if (last == null) {
                LAST_POS.computeIfAbsent(id, k -> new Vector3d()).set(x, y, z);
                continue;
            }

            final double dx = x - last.x, dy = y - last.y, dz = z - last.z;
            final double distSq = dx * dx + dy * dy + dz * dz;   // no sqrt in the hot path

            if (distSq <= maxSpeedSq) {
                // Below the threshold. Decay the record only once the whole window has passed,
                // instead of erasing it outright on a single sub-threshold tick.
                final StrikeRecord rec = RUNAWAY_STRIKE.get(id);
                if (rec != null && now - rec.tick() > window) {
                    RUNAWAY_STRIKE.remove(id);
                }
                last.set(x, y, z);
                continue;
            }

            final StrikeRecord rec = RUNAWAY_STRIKE.get(id);
            final int strikes = (rec == null || now - rec.tick() > window) ? 1 : rec.strikes() + 1;

            if (strikes >= strikesToKill) {
                AeroReformation.LOGGER.warn(
                        "[PhysicsAnchor] RUNAWAY sub={} moved {} blocks in one tick "
                                + "({} strikes within {} ticks) — self-destructing",
                        id, (int) Math.sqrt(distSq), strikes, window);
                RUNAWAY_STRIKE.remove(id);
                destroySubLevelAnchors(serverLevel, dimMap, id, sl);
                continue;
            }

            RUNAWAY_STRIKE.put(id, new StrikeRecord(strikes, now));
            AeroReformation.LOGGER.warn(
                    "[PhysicsAnchor] Overspeed strike {}/{} on sub={} — moved {} blocks this tick. "
                            + "Not destroying yet; this may be an ordinary teleport.",
                    strikes, strikesToKill, id, (int) Math.sqrt(distSq));
            last.set(x, y, z);   // reuse the vector already in the map, no allocation
        }

        // Periodic sweep so departed sub-levels do not keep entries alive forever.
        if (now % 100 == 0) {
            LAST_POS.keySet().removeIf(uid -> !ANCHORED_SUBLEVELS.contains(uid));
            RUNAWAY_STRIKE.keySet().removeIf(uid -> !ANCHORED_SUBLEVELS.contains(uid));
        }
    }

    /**
     * Remove all anchors for a runaway SubLevel, discarding the marker and cleaning tickets.
     *
     * <p>A runaway craft can sit at the head of a constraint chain, so every connected
     * sub-level goes too — otherwise the rest of the chain keeps simulating the wreck and the
     * server keeps paying for it.
     *
     * <p>Everything here routes through {@link #tearDownAnchor} so there is exactly one
     * definition of "released". This used to hand-roll the cleanup, and promptly drifted: the
     * warmup ticket was never released and the auto-protect / hidden sets were never touched.
     */
    private static void destroySubLevelAnchors(ServerLevel serverLevel,
                                               Map<BlockPos, AnchorData> dimMap,
                                               UUID id, SubLevel sl) {
        final Set<UUID> toDestroy = new HashSet<>();
        toDestroy.add(id);
        for (var connected : getConnectedChain(sl)) {
            toDestroy.add(connected.getUniqueId());
        }

        // One pass over the anchor map, not one removeIf() per doomed UUID. The old shape was
        // O(chainLength x anchorCount) and this runs on the server thread.
        int torn = 0;
        for (var entry : new ArrayList<>(dimMap.entrySet())) {
            final AnchorData data = entry.getValue();
            if (!toDestroy.contains(data.subLevelId())) continue;
            tearDownAnchor(serverLevel, entry.getKey(), data);
            torn++;
        }

        // tearDownAnchor handles the live map; the rest of the bookkeeping is ours.
        for (UUID destroyId : toDestroy) {
            final AnchorData parked = WARMUP.remove(destroyId);
            if (parked != null) releaseWarmupTicket(serverLevel, parked);
            AUTO_PROTECTED.remove(destroyId);
            EtherealKeyItem.HIDDEN_SUBLEVELS.remove(destroyId);
        }

        syncToClients(serverLevel);
        AeroReformation.LOGGER.warn(
                "[PhysicsAnchor] Runaway teardown complete: {} anchor(s) released across {} sub-level(s)",
                torn, toDestroy.size());
    }

    /* =====================================================================
     * Client map sync
     * =================================================================== */

    private static void syncToClients(ServerLevel serverLevel) {
        List<AnchorMapSyncPacket.MarkerEntry> entries = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();

        for (var data : anchorsFor(serverLevel.dimension()).values()) {
            final SubLevel sub = data.subLevel();
            if (sub == null || sub.isRemoved()) continue;
            // Defensive: anchorPos is non-null at every construction site, but a null here
            // would NPE on the network thread while writing the packet. Drop the entry
            // instead of killing the connection.
            if (data.anchorPos() == null) continue;
            final UUID id = data.subLevelId();
            if (EtherealKeyItem.HIDDEN_SUBLEVELS.contains(id)) continue;
            if (!seen.add(id)) continue;

            final var pose = sub.logicalPose();
            final String name = data.marker() != null && data.marker().hasCustomName()
                    ? data.marker().getCustomName().getString() : null;
            entries.add(new AnchorMapSyncPacket.MarkerEntry(
                    id, data.anchorPos(),
                    pose.position().x(), pose.position().y(), pose.position().z(),
                    name, data.ticketRadius()));
        }

        // Push the empty set too, but only on the transition. Without it, removing the last
        // anchor left stale markers on every client's map forever, and the goggle tooltip
        // kept reporting a binding that no longer existed.
        final boolean wasNonEmpty = SYNCED_NON_EMPTY.contains(serverLevel.dimension());
        if (!entries.isEmpty() || wasNonEmpty) {
            var packet = new AnchorMapSyncPacket(entries);
            for (var player : serverLevel.players()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        (net.minecraft.server.level.ServerPlayer) player, packet);
            }
            if (entries.isEmpty()) {
                SYNCED_NON_EMPTY.remove(serverLevel.dimension());
            } else {
                SYNCED_NON_EMPTY.add(serverLevel.dimension());
            }
        }

        for (var it = WARMUP.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            AnchorMarkerEntity marker = entry.getValue().marker();
            if (marker != null && !marker.isAlive()) {
                releaseWarmupTicket(serverLevel, entry.getValue());
                ANCHORED_SUBLEVELS.remove(entry.getKey());
                it.remove();
            }
        }
    }

    /**
     * Self-check every 5 seconds: if the marker has drifted far from the SubLevel's real
     * position, the tracking has desynced. Reposition in place — do not discard and
     * recreate, which would spawn duplicates.
     */
    private static void selfCheckAnchors(ServerLevel serverLevel) {
        final double MAX_DIST_SQ = 64.0 * 64.0;

        for (var data : anchorsFor(serverLevel.dimension()).values()) {
            final SubLevel sub = data.subLevel();
            if (sub == null || sub.isRemoved()) continue;
            if (data.marker() == null || data.marker().isRemoved()) continue;

            final var pose = sub.logicalPose();
            final double dx = data.marker().getX() - pose.position().x();
            final double dy = data.marker().getY() - pose.position().y();
            final double dz = data.marker().getZ() - pose.position().z();
            if (dx * dx + dy * dy + dz * dz <= MAX_DIST_SQ) continue;

            data.marker().setPos(pose.position().x(), pose.position().y(), pose.position().z());
        }
    }
}
