package dev.simulated_team.aero_reformation.api;

import dev.simulated_team.aero_reformation.content.blocks.guidance_warhead.GuidanceWarheadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Cross-mod API for the guidance warhead (target acquisition + PID tuning).
 *
 * <p>All mutators are server-side and sync the block entity after writing.
 * The warhead itself is bound to an RCS thruster in guidance mode.
 */
public final class GuidanceWarheadApi {
    private GuidanceWarheadApi() {}

    /** Search modes (mirrors {@link GuidanceWarheadBlockEntity}). */
    public static final int SEARCH_MASS = 0;
    public static final int SEARCH_NEAREST = 1;
    public static final int SEARCH_MANUAL = 2;
    public static final int SEARCH_RADAR = 3;

    /** Locate the guidance warhead block entity at the given position, if any. */
    @Nullable
    public static GuidanceWarheadBlockEntity get(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof GuidanceWarheadBlockEntity warhead ? warhead : null;
    }

    private static boolean sync(Level level, BlockPos pos, GuidanceWarheadBlockEntity be) {
        if (be == null) return false;
        AeroApiUtils.syncBlockEntity(be);
        return true;
    }

    // ─────────────────────────── target / mode ───────────────────────────

    /** Current search mode (0..3). */
    public static int getSearchMode(Level level, BlockPos pos) {
        GuidanceWarheadBlockEntity be = get(level, pos);
        return be != null ? be.searchMode : -1;
    }

    /** Set the search mode (0..3). @return true if updated */
    public static boolean setSearchMode(Level level, BlockPos pos, int mode) {
        GuidanceWarheadBlockEntity be = get(level, pos);
        if (be == null) return false;
        be.searchMode = Math.max(0, Math.min(3, mode));
        return sync(level, pos, be);
    }

    /** Current guidance target, or null if none acquired. */
    @Nullable
    public static Vector3d getTargetPos(Level level, BlockPos pos) {
        GuidanceWarheadBlockEntity be = get(level, pos);
        return be != null ? be.getTargetPos() : null;
    }

    /** Manual target coordinates (used in {@link #SEARCH_MANUAL} mode). */
    public static double getManualTargetX(Level level, BlockPos pos) {
        GuidanceWarheadBlockEntity be = get(level, pos);
        return be != null ? be.manualTargetX : 0.0;
    }
    public static double getManualTargetY(Level level, BlockPos pos) {
        GuidanceWarheadBlockEntity be = get(level, pos);
        return be != null ? be.manualTargetY : 0.0;
    }
    public static double getManualTargetZ(Level level, BlockPos pos) {
        GuidanceWarheadBlockEntity be = get(level, pos);
        return be != null ? be.manualTargetZ : 0.0;
    }

    /** Set the manual target coordinates and switch to manual search mode. @return true if updated */
    public static boolean setManualTarget(Level level, BlockPos pos, double x, double y, double z) {
        GuidanceWarheadBlockEntity be = get(level, pos);
        if (be == null) return false;
        be.manualTargetX = x;
        be.manualTargetY = y;
        be.manualTargetZ = z;
        be.searchMode = SEARCH_MANUAL;
        be.unlockTarget();
        return sync(level, pos, be);
    }

    /** Guidance control mode: 0 = direct (while redstone on), 1 = toggle (rising edge). */
    public static int getGuidanceMode(Level level, BlockPos pos) {
        GuidanceWarheadBlockEntity be = get(level, pos);
        return be != null ? be.guidanceMode : 0;
    }

    /** Set the guidance control mode (0 or 1). @return true if updated */
    public static boolean setGuidanceMode(Level level, BlockPos pos, int mode) {
        GuidanceWarheadBlockEntity be = get(level, pos);
        if (be == null) return false;
        be.guidanceMode = mode == 0 ? 0 : 1;
        return sync(level, pos, be);
    }

    /** Whether guidance is currently enabled (toggle mode state). */
    public static boolean isGuidanceEnabled(Level level, BlockPos pos) {
        GuidanceWarheadBlockEntity be = get(level, pos);
        return be != null && be.guidanceEnabled;
    }

    /** Drop the current target so the next scan re-acquires one. @return true if updated */
    public static boolean unlockTarget(Level level, BlockPos pos) {
        GuidanceWarheadBlockEntity be = get(level, pos);
        if (be == null) return false;
        be.unlockTarget();
        return sync(level, pos, be);
    }

    // ─────────────────────────── PID / tuning ───────────────────────────

    /** Read a float tuning field by name ("kp","ki","kd","maxSpeed","sidePower","maxThrustPN","cruiseAltitude","brakeCoeff","proximityRange","redstoneRange","altitudeOffset","minSearchRange","maxSearchRange"). */
    public static float getTuning(Level level, BlockPos pos, String name) {
        GuidanceWarheadBlockEntity be = get(level, pos);
        if (be == null) return 0.0f;
        return switch (name) {
            case "kp" -> be.kp;
            case "ki" -> be.ki;
            case "kd" -> be.kd;
            case "maxSpeed" -> be.maxSpeed;
            case "sidePower" -> be.sidePower;
            case "maxThrustPN" -> be.maxThrustPN;
            case "cruiseAltitude" -> be.cruiseAltitude;
            case "brakeCoeff" -> be.brakeCoeff;
            case "proximityRange" -> be.proximityRange;
            case "redstoneRange" -> be.redstoneRange;
            case "altitudeOffset" -> be.altitudeOffset;
            case "minSearchRange" -> be.minSearchRange;
            case "maxSearchRange" -> be.maxSearchRange;
            default -> 0.0f;
        };
    }

    /** Write a float tuning field by name (see {@link #getTuning}). @return true if updated */
    public static boolean setTuning(Level level, BlockPos pos, String name, float value) {
        GuidanceWarheadBlockEntity be = get(level, pos);
        if (be == null) return false;
        switch (name) {
            case "kp" -> be.kp = value;
            case "ki" -> be.ki = value;
            case "kd" -> be.kd = value;
            case "maxSpeed" -> be.maxSpeed = value;
            case "sidePower" -> be.sidePower = value;
            case "maxThrustPN" -> be.maxThrustPN = value;
            case "cruiseAltitude" -> be.cruiseAltitude = value;
            case "brakeCoeff" -> be.brakeCoeff = value;
            case "proximityRange" -> be.proximityRange = value;
            case "redstoneRange" -> be.redstoneRange = value;
            case "altitudeOffset" -> be.altitudeOffset = value;
            case "minSearchRange" -> be.minSearchRange = value;
            case "maxSearchRange" -> be.maxSearchRange = value;
            default -> { return false; }
        }
        return sync(level, pos, be);
    }
}
