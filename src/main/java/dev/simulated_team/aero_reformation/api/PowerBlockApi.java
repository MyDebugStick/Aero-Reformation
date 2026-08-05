package dev.simulated_team.aero_reformation.api;

import dev.simulated_team.aero_reformation.content.blocks.power.PowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Cross-mod API for the pilot "power" block (yaw/pitch limits and seat height).
 *
 * <p>Mutators are server-side and clamp to the same ranges used by the GUI.
 */
public final class PowerBlockApi {
    private PowerBlockApi() {}

    /** Locate the power block entity at the given position, if any. */
    private static PowerBlockEntity get(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof PowerBlockEntity power ? power : null;
    }

    // ─────────────────────────────── Yaw ───────────────────────────────

    /** Maximum yaw limit in degrees. */
    public static double getYawMax(Level level, BlockPos pos) {
        PowerBlockEntity be = get(level, pos);
        return be != null ? be.getYawMax() : 0.0;
    }

    /** Set the maximum yaw limit (clamped 1..180). @return true if updated */
    public static boolean setYawMax(Level level, BlockPos pos, double degrees) {
        PowerBlockEntity be = get(level, pos);
        if (be == null) return false;
        be.setYawMax((int) Math.round(degrees));
        return true;
    }

    // ─────────────────────────────── Pitch ─────────────────────────────

    /** Maximum pitch limit in degrees. */
    public static double getPitchMax(Level level, BlockPos pos) {
        PowerBlockEntity be = get(level, pos);
        return be != null ? be.getPitchMax() : 0.0;
    }

    /** Set the maximum pitch limit (clamped 1..90). @return true if updated */
    public static boolean setPitchMax(Level level, BlockPos pos, double degrees) {
        PowerBlockEntity be = get(level, pos);
        if (be == null) return false;
        be.setPitchMax((int) Math.round(degrees));
        return true;
    }

    // ─────────────────────────────── Seat height ───────────────────────

    /** Seat height offset (blocks, clamped -0.2..0.2). */
    public static double getSeatHeight(Level level, BlockPos pos) {
        PowerBlockEntity be = get(level, pos);
        return be != null ? be.getSeatHeight() : 0.0;
    }

    /** Set the seat height offset (clamped -0.2..0.2). @return true if updated */
    public static boolean setSeatHeight(Level level, BlockPos pos, double height) {
        PowerBlockEntity be = get(level, pos);
        if (be == null) return false;
        be.setSeatHeight(height);
        return true;
    }
}
