package dev.simulated_team.aero_reformation.content.items.mushroom_shell;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Simple delayed explosion scheduler, processed every server tick.
 */
public class DelayedExplosionManager {

    private static class PendingBoom {
        final ServerLevel level;
        final BlockPos pos;
        int ticksLeft;

        PendingBoom(ServerLevel level, BlockPos pos, int ticksLeft) {
            this.level = level;
            this.pos = pos;
            this.ticksLeft = ticksLeft;
        }
    }

    private static final List<PendingBoom> PENDING = new ArrayList<>();

    public static void schedule(ServerLevel level, BlockPos pos, int delayTicks) {
        PENDING.add(new PendingBoom(level, pos, delayTicks));
    }

    /** Called every server tick. */
    public static void tick(ServerLevel serverLevel) {
        for (Iterator<PendingBoom> it = PENDING.iterator(); it.hasNext(); ) {
            PendingBoom pb = it.next();
            if (pb.level != serverLevel) continue;
            if (--pb.ticksLeft <= 0) {
                pb.level.explode(null,
                        pb.pos.getX() + 0.5, pb.pos.getY() + 0.5, pb.pos.getZ() + 0.5,
                        8.0f, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
                it.remove();
            }
        }
    }
}
