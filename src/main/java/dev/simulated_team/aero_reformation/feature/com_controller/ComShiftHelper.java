package dev.simulated_team.aero_reformation.feature.com_controller;

import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.api.physics.mass.MergedMassTracker;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.aero_reformation.content.blocks.com_offset.ComOffsetBlock;
import dev.simulated_team.aero_reformation.content.blocks.com_offset.ComOffsetBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ComShiftHelper {

    /** Per-sublevel: currently applied offset (so we can undo it before applying a new one). */
    private static final Map<UUID, Vector3d> APPLIED_OFFSET = new ConcurrentHashMap<>();

    /** Per-sublevel: cached positions of ComOffsetBlock instances. */
    private static final Map<UUID, List<BlockPos>> BLOCK_CACHE = new ConcurrentHashMap<>();

    /** Per-sublevel: scan timer, in ticks. When <= 0, a full scan is triggered. */
    private static final Map<UUID, Integer> SCAN_TIMERS = new ConcurrentHashMap<>();

    /** Set of sublevels known to contain at least one ComOffsetBlock. */
    private static final Set<UUID> HAS_OFFSET_BLOCKS = ConcurrentHashMap.newKeySet();

    /** How often (in ticks) to perform a full scan for ComOffsetBlock positions. */
    private static final int SCAN_INTERVAL = 20;

    private static final double EPSILON = 0.001;

    /**
     * Invalidates the cached data for a sublevel.
     * Called when a ComOffsetBlock is placed, removed, or its value changes,
     * ensuring the new state is picked up on the next tick.
     */
    public static void invalidateCache(final UUID subLevelUUID) {
        BLOCK_CACHE.remove(subLevelUUID);
        SCAN_TIMERS.remove(subLevelUUID);
        HAS_OFFSET_BLOCKS.remove(subLevelUUID);
    }

    /**
     * Finds the sublevel containing the given position and invalidates its cache.
     * Called from block/blockentity events.
     */
    public static void invalidateAt(final Level level, final BlockPos pos) {
        if (!(level instanceof final ServerLevel serverLevel)) return;
        final SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) return;

        for (final SubLevel sl : container.getAllSubLevels()) {
            if (sl.getPlot().getBoundingBox().contains(pos)) {
                invalidateCache(sl.getUniqueId());
                return;
            }
        }
    }

    public static void applyComShift(final ServerSubLevel subLevel) {
        final UUID id = subLevel.getUniqueId();

        // Fast path: no ComOffsetBlock known → skip entirely
        if (!HAS_OFFSET_BLOCKS.contains(id)) {
            clearAppliedOffset(subLevel, id);
            return;
        }

        final MassTracker tracker = getSelfTracker(subLevel);
        if (tracker == null || tracker.getCenterOfMass() == null) return;

        // Scan at intervals, not every tick
        final int timer = SCAN_TIMERS.getOrDefault(id, 0);
        if (timer <= 0) {
            rebuildCache(subLevel, id);
            SCAN_TIMERS.put(id, SCAN_INTERVAL);
        } else {
            SCAN_TIMERS.put(id, timer - 1);
        }

        final List<BlockPos> cached = BLOCK_CACHE.get(id);
        if (cached == null || cached.isEmpty()) {
            HAS_OFFSET_BLOCKS.remove(id);
            clearAppliedOffset(subLevel, id);
            return;
        }

        // Read current offset values from block entities (only cached positions)
        final ServerLevel level = subLevel.getLevel();
        final Vector3d desired = new Vector3d();

        for (final BlockPos pos : cached) {
            if (level.getBlockEntity(pos) instanceof final ComOffsetBlockEntity coe) {
                final double cx = Math.clamp(coe.getComX(), -100, 100);
                final double cy = Math.clamp(coe.getComY(), -100, 100);
                final double cz = Math.clamp(coe.getComZ(), -100, 100);
                desired.add(cx, cy, cz);
            }
        }

        final double totalWeight = cached.size();
        if (totalWeight <= 0 || desired.lengthSquared() < EPSILON) {
            HAS_OFFSET_BLOCKS.remove(id);
            clearAppliedOffset(subLevel, id);
            return;
        }

        desired.div(totalWeight);

        final Vector3d applied = APPLIED_OFFSET.get(id);
        if (applied != null && desired.distanceSquared(applied) < EPSILON) return;

        // naturalCom = currentCom - oldApplied, then newCom = naturalCom + desired
        final Vector3dc cur = tracker.getCenterOfMass();
        final Vector3d newCom = new Vector3d(cur);
        if (applied != null) newCom.sub(applied);   // undo old offset → natural COM
        newCom.add(desired);                         // apply new offset

        APPLIED_OFFSET.put(id, new Vector3d(desired));
        tracker.moveCenterOfMass(newCom);
    }

    /** Clears any previously applied offset, restoring the natural center of mass. */
    private static void clearAppliedOffset(final ServerSubLevel subLevel, final UUID id) {
        final Vector3d applied = APPLIED_OFFSET.remove(id);
        if (applied != null) {
            final MassTracker tracker = getSelfTracker(subLevel);
            if (tracker != null && tracker.getCenterOfMass() != null) {
                tracker.moveCenterOfMass(new Vector3d(tracker.getCenterOfMass()).sub(applied));
            }
        }
    }

    /** Extracts the self (non-merged) MassTracker from a sublevel. */
    private static MassTracker getSelfTracker(final ServerSubLevel subLevel) {
        final var massData = subLevel.getMassTracker();
        if (massData instanceof final MassTracker mt) return mt;
        if (massData instanceof final MergedMassTracker mmt) return mmt.getSelfMassTracker();
        return null;
    }

    /** Scans the entire sublevel bounding box to find all ComOffsetBlock positions. */
    private static void rebuildCache(final ServerSubLevel subLevel, final UUID id) {
        final BoundingBox3ic bb = subLevel.getPlot().getBoundingBox();
        final ServerLevel level = subLevel.getLevel();
        final BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        final List<BlockPos> found = new ArrayList<>();

        for (int x = bb.minX(); x <= bb.maxX(); x++) {
            for (int y = bb.minY(); y <= bb.maxY(); y++) {
                for (int z = bb.minZ(); z <= bb.maxZ(); z++) {
                    mPos.set(x, y, z);
                    final BlockState state = level.getBlockState(mPos);
                    if (state.getBlock() instanceof ComOffsetBlock) {
                        found.add(mPos.immutable());
                    }
                }
            }
        }

        if (found.isEmpty()) {
            HAS_OFFSET_BLOCKS.remove(id);
            BLOCK_CACHE.remove(id);
        } else {
            HAS_OFFSET_BLOCKS.add(id);
            BLOCK_CACHE.put(id, found);
        }
    }
}