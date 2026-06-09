package dev.simulated_team.aero_reformation.feature.com_controller;

import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.api.physics.mass.MergedMassTracker;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.aero_reformation.content.blocks.com_offset.ComOffsetBlock;
import dev.simulated_team.aero_reformation.content.blocks.com_offset.ComOffsetBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ComShiftHelper {
    /** Per-sublevel: currently applied offset (so we can undo it before applying a new one). */
    private static final Map<UUID, Vector3d> APPLIED_OFFSET = new ConcurrentHashMap<>();
    private static final double EPSILON = 0.001;

    public static void applyComShift(ServerSubLevel subLevel) {
        UUID id = subLevel.getUniqueId();
        var massData = subLevel.getMassTracker();
        MassTracker tracker = null;

        if (massData instanceof MassTracker mt) {
            tracker = mt;
        } else if (massData instanceof MergedMassTracker mmt) {
            tracker = mmt.getSelfMassTracker();
        }

        if (tracker == null || tracker.getCenterOfMass() == null) return;

        BoundingBox3ic bb = subLevel.getPlot().getBoundingBox();
        Vector3d desired = new Vector3d();
        double totalWeight = 0;
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();

        for (int x = bb.minX(); x <= bb.maxX(); x++) {
            for (int y = bb.minY(); y <= bb.maxY(); y++) {
                for (int z = bb.minZ(); z <= bb.maxZ(); z++) {
                    mPos.set(x, y, z);
                    ServerLevel level = subLevel.getLevel();
                    BlockState state = level.getBlockState(mPos);
                    if (!(state.getBlock() instanceof ComOffsetBlock)) continue;

                    if (level.getBlockEntity(mPos) instanceof ComOffsetBlockEntity coe) {
                        double cx = Math.clamp(coe.getComX(), -100, 100);
                        double cy = Math.clamp(coe.getComY(), -100, 100);
                        double cz = Math.clamp(coe.getComZ(), -100, 100);
                        desired.add(cx, cy, cz);
                        totalWeight += 1.0;
                    }
                }
            }
        }

        Vector3d applied = APPLIED_OFFSET.get(id);

        // No offset blocks → restore natural COM
        if (totalWeight <= 0 || desired.lengthSquared() < EPSILON) {
            if (applied != null) {
                // currentCom - applied = natural COM
                Vector3dc cur = tracker.getCenterOfMass();
                tracker.moveCenterOfMass(new Vector3d(cur).sub(applied));
                APPLIED_OFFSET.remove(id);
            }
            return;
        }

        desired.div(totalWeight);

        // Same desired → nothing to do
        if (applied != null && desired.distanceSquared(applied) < EPSILON) return;

        // naturalCom = currentCom - oldApplied, then newCom = naturalCom + desired
        Vector3dc cur = tracker.getCenterOfMass();
        Vector3d newCom = new Vector3d(cur);
        if (applied != null) newCom.sub(applied);   // undo old offset → natural COM
        newCom.add(desired);                         // apply new offset

        APPLIED_OFFSET.put(id, new Vector3d(desired));
        tracker.moveCenterOfMass(newCom);
    }
}
