package dev.simulated_team.aero_reformation.api;

import dev.simulated_team.aero_reformation.content.blocks.rcs_thruster.RcsThrusterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Shared helpers for the Aero Reformation cross-mod API.
 *
 * <p>All API entry points take a {@link Level} + {@link BlockPos} so that other mods
 * (and ComputerCraft) can call them without importing our block entity classes.
 * Every mutator is server-side and schedules a block update so clients see the change.
 */
public final class AeroApiUtils {
    private AeroApiUtils() {}

    /** Locate the RCS thruster block entity at the given position, if any. */
    @Nullable
    public static RcsThrusterBlockEntity getRcsThruster(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof RcsThrusterBlockEntity rcs ? rcs : null;
    }

    /** Mark a block entity changed and push an update to clients. Safe to call from API methods. */
    public static void syncBlockEntity(BlockEntity be) {
        if (be == null) return;
        be.setChanged();
        Level level = be.getLevel();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    /** Clamp a double to [min, max]. */
    public static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
