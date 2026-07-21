package dev.simulated_team.aero_reformation.feature.com_controller;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.api.physics.mass.MergedMassTracker;
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ComShiftHelper {
    /** Per-sublevel: currently applied offset (so we can undo it before applying a new one). */
    private static final Map<UUID, Vector3d> APPLIED_OFFSET = new ConcurrentHashMap<>();

    /** Per-sublevel: cached positions of ComOffsetBlocks, populated via block events. */
    static final Map<UUID, List<BlockPos>> BLOCK_POSITIONS = new ConcurrentHashMap<>();

    private static final double EPSILON = 0.001;

    private static Method RECOVER_METHOD;
    private static boolean RECOVER_RESOLVED = false;

    /**
     * Reflectively calls SubLevelPhysicsSystem.recoverSubLevel() to fully rebuild
     * the physics body in the engine, clearing all stale constraint state.
     */
    private static void recoverSubLevelPhysics(ServerSubLevel subLevel) {
        if (!RECOVER_RESOLVED) {
            RECOVER_RESOLVED = true;
            try {
                RECOVER_METHOD = Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem")
                        .getMethod("recoverSubLevel", ServerSubLevel.class);
            } catch (Exception ignored) {}
        }
        if (RECOVER_METHOD == null) return;
        try {
            ServerLevel level = subLevel.getLevel();
            Object container = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer")
                    .getMethod("getContainer", Level.class).invoke(null, level);
            Object physicsSystem = container.getClass().getMethod("physicsSystem").invoke(container);
            RECOVER_METHOD.invoke(physicsSystem, subLevel);
        } catch (Exception ignored) {}
    }

    public static void onBlockPlaced(ServerLevel level, BlockPos pos) {
        SubLevel sub = Sable.HELPER.getContaining(level, pos);
        if (sub == null) return;
        BLOCK_POSITIONS.computeIfAbsent(sub.getUniqueId(), k -> new ArrayList<>()).add(pos.immutable());
    }

    public static void onBlockBroken(ServerLevel level, BlockPos pos) {
        SubLevel sub = Sable.HELPER.getContaining(level, pos);
        if (sub == null) return;
        List<BlockPos> positions = BLOCK_POSITIONS.get(sub.getUniqueId());
        if (positions != null) {
            positions.remove(pos);
            if (positions.isEmpty()) {
                BLOCK_POSITIONS.remove(sub.getUniqueId());
            }
        }
        // Fully rebuild physics body to clear stale constraint state (physics staff jitter)
        if (sub instanceof ServerSubLevel ssl) {
            recoverSubLevelPhysics(ssl);
            APPLIED_OFFSET.remove(sub.getUniqueId());
        }
    }

    public static void applyComShift(ServerSubLevel subLevel) {
        UUID id = subLevel.getUniqueId();
        List<BlockPos> comPositions = BLOCK_POSITIONS.get(id);
        if (comPositions == null || comPositions.isEmpty()) {
            Vector3d applied = APPLIED_OFFSET.remove(id);
            if (applied != null) {
                var massData = subLevel.getMassTracker();
                MassTracker tracker = resolveTracker(massData);
                if (tracker != null && tracker.getCenterOfMass() != null) {
                    Vector3dc cur = tracker.getCenterOfMass();
                    tracker.moveCenterOfMass(new Vector3d(cur).sub(applied));
                }
            }
            return;
        }

        var massData = subLevel.getMassTracker();
        MassTracker tracker = resolveTracker(massData);
        if (tracker == null || tracker.getCenterOfMass() == null) return;

        ServerLevel level = subLevel.getLevel();
        Vector3d desired = new Vector3d();
        double totalWeight = 0;

        for (BlockPos comPos : comPositions) {
            BlockState state = level.getBlockState(comPos);
            if (!(state.getBlock() instanceof ComOffsetBlock)) continue;
            if (level.getBlockEntity(comPos) instanceof ComOffsetBlockEntity coe) {
                desired.add(Math.clamp(coe.getComX(), -100, 100),
                            Math.clamp(coe.getComY(), -100, 100),
                            Math.clamp(coe.getComZ(), -100, 100));
                totalWeight += 1.0;
            }
        }

        if (totalWeight <= 0) {
            BLOCK_POSITIONS.remove(id);
            Vector3d applied = APPLIED_OFFSET.remove(id);
            if (applied != null && tracker.getCenterOfMass() != null) {
                recoverSubLevelPhysics(subLevel);
            }
            return;
        }

        desired.div(totalWeight);

        Vector3d applied = APPLIED_OFFSET.get(id);

        // Detect stale APPLIED_OFFSET (e.g. after external recoverSubLevel / buildMassTracker).
        // If the current COM minus our expected offset doesn't match the applied offset,
        // our cache is stale → treat as no applied offset.
        if (applied != null) {
            Vector3d predictedNatural = new Vector3d(tracker.getCenterOfMass()).sub(applied);
            // Re-apply the same offset to get expected COM
            Vector3d expectedCom = new Vector3d(predictedNatural).add(applied);
            if (expectedCom.distanceSquared(tracker.getCenterOfMass()) > EPSILON) {
                APPLIED_OFFSET.remove(id);
                applied = null;
            }
        }

        if (applied != null && desired.distanceSquared(applied) < EPSILON) return;

        Vector3dc cur = tracker.getCenterOfMass();
        Vector3d newCom = new Vector3d(cur);
        if (applied != null) newCom.sub(applied);
        newCom.add(desired);

        APPLIED_OFFSET.put(id, new Vector3d(desired));
        tracker.moveCenterOfMass(newCom);
    }

    public static void clearOffsetCache(UUID subLevelId) {
        APPLIED_OFFSET.remove(subLevelId);
    }

    private static MassTracker resolveTracker(Object massData) {
        if (massData instanceof MassTracker mt) return mt;
        if (massData instanceof MergedMassTracker mmt) return mmt.getSelfMassTracker();
        return null;
    }
}
