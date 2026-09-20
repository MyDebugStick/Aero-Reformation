package dev.simulated_team.aero_reformation.api;

import dev.simulated_team.aero_reformation.content.blocks.rcs_thruster.RcsThrusterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Cross-mod API for the RCS thruster block.
 *
 * <p>Other mods can call these methods directly; ComputerCraft exposes the same
 * operations through the {@code aero_rcs} peripheral. All mutators are
 * server-side (they no-op safely when the thruster is missing or on the client).
 */
public final class RcsThrusterApi {
    private RcsThrusterApi() {}

    // ─────────────────────────────── Thrust ───────────────────────────────

    /** Current configured thrust in pN (from the scroll value). */
    public static double getThrust(Level level, BlockPos pos) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        return be != null ? be.getConfiguredThrust() : 0.0;
    }

    /**
     * Set the configured thrust by selecting the option nearest to {@code pn}.
     * @return true if a thruster was found and updated
     */
    public static boolean setThrust(Level level, BlockPos pos, double pn) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        if (be == null) return false;
        int[] options = RcsThrusterBlockEntity.getThrustOptions();
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < options.length; i++) {
            double d = Math.abs(options[i] - pn);
            if (d < bestDist) { bestDist = d; best = i; }
        }
        be.setThrustIndex(best);
        AeroApiUtils.syncBlockEntity(be);
        return true;
    }

    /** Selected thrust option index. */
    public static int getThrustIndex(Level level, BlockPos pos) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        return be != null ? be.getThrustIndex() : -1;
    }

    /** Set the thrust option index (clamped). @return true if updated */
    public static boolean setThrustIndex(Level level, BlockPos pos, int index) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        if (be == null) return false;
        be.setThrustIndex(index);
        AeroApiUtils.syncBlockEntity(be);
        return true;
    }

    /** All selectable thrust options in pN (copy). */
    public static int[] getThrustOptions() {
        return RcsThrusterBlockEntity.getThrustOptions();
    }

    // ─────────────────────────────── Creative ─────────────────────────────

    /** Whether the thruster runs in creative (free fuel) mode. */
    public static boolean isCreative(Level level, BlockPos pos) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        return be != null && be.isCreativeMode();
    }

    /** Enable/disable creative (free fuel) mode. @return true if updated */
    public static boolean setCreative(Level level, BlockPos pos, boolean creative) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        if (be == null) return false;
        be.setCreativeMode(creative);
        AeroApiUtils.syncBlockEntity(be);
        return true;
    }

    // ─────────────────────────────── Angled nozzles ───────────────────────

    /** Current angled-nozzle reduction mode index (0..{@code ANGLED_REDUCTION.length-1}). */
    public static int getAngledMode(Level level, BlockPos pos) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        return be != null ? be.getAngledMode() : 0;
    }

    /** Cycle to the next angled-nozzle reduction mode. @return true if updated */
    public static boolean cycleAngledMode(Level level, BlockPos pos) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        if (be == null) return false;
        be.cycleAngledMode();
        AeroApiUtils.syncBlockEntity(be);
        return true;
    }

    // ─────────────────────────────── Runtime status ───────────────────────

    /** Total thrust currently being output (pN, synced for HUD). */
    public static double getActiveThrust(Level level, BlockPos pos) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        return be != null ? be.getCurrentThrustPN() : 0.0;
    }

    /** Bitmask of firing nozzles this physics tick. */
    public static int getActiveNozzleMask(Level level, BlockPos pos) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        return be != null ? be.getActiveNozzleMask() : 0;
    }

    /** Whether fuel/energy was available on the last physics tick. */
    public static boolean hasFuel(Level level, BlockPos pos) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        return be != null && be.isFuelAvailable();
    }

    /** Whether the thruster is currently running on electricity. */
    public static boolean isElectric(Level level, BlockPos pos) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        return be != null && be.isElectricMode();
    }

    // ─────────────────────────────── Binding ──────────────────────────────

    /** Position of the bound directional synchronizer, or null. */
    @Nullable
    public static BlockPos getBoundSyncPos(Level level, BlockPos pos) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        return be != null ? be.getBoundSync() : null;
    }

    /** Bind this thruster to a directional synchronizer (overrides warhead binding). @return true if updated */
    public static boolean setBoundSyncPos(Level level, BlockPos pos, @Nullable BlockPos syncPos) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        if (be == null) return false;
        be.setBoundSync(syncPos);
        AeroApiUtils.syncBlockEntity(be);
        return true;
    }

    /** Position of the bound guidance warhead, or null. */
    @Nullable
    public static BlockPos getBoundWarheadPos(Level level, BlockPos pos) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        return be != null ? be.getBoundWarhead() : null;
    }

    /** Whether the thruster is in guidance (warhead-bound) mode. */
    public static boolean isGuidanceMode(Level level, BlockPos pos) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        return be != null && be.isGuidanceMode();
    }

    /** Fuel available in the connected tank (mB), for informational purposes. */
    public static int getFuelAmount(Level level, BlockPos pos) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        return be != null ? be.getFuelAmount() : 0;
    }

    // ──────────────────────── Per-nozzle thrust override ────────────────────────
    //
    // The redstone path exposes 0..15 per synchronizer face, one nozzle per face,
    // which a computer cannot use to dial individual nozzles. These accessors give
    // a caller direct, float-precision authority over each of the five nozzles.
    //
    // Nozzle indices: 0 = forward, 1 = right, 2 = left, 3 = up, 4 = down.

    /**
     * Whether the per-nozzle override currently drives the nozzles.
     *
     * <p>While enabled, redstone is ignored entirely. While disabled the block
     * behaves exactly as before.
     */
    public static boolean isNozzleOverrideEnabled(Level level, BlockPos pos) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        return be != null && be.isRawNozzleMode();
    }

    /**
     * Engages or releases the per-nozzle override.
     *
     * <p>Engaging clears every nozzle, so the block stays silent until a caller
     * commands one — a mode switch never reuses a stale commanded thrust.
     *
     * @return true if a thruster was found and updated
     */
    public static boolean setNozzleOverrideEnabled(Level level, BlockPos pos, boolean enabled) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        if (be == null) return false;
        be.setRawNozzleMode(enabled);
        AeroApiUtils.syncBlockEntity(be);
        return true;
    }

    /**
     * Thrust fraction currently commanded for one nozzle.
     *
     * @return 0.0..1.0, or -1 when there is no thruster at {@code pos}
     */
    public static double getNozzleThrust(Level level, BlockPos pos, int nozzleIdx) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        if (be == null) return -1.0;
        return be.getRawNozzle(nozzleIdx);
    }

    /**
     * Command one nozzle directly.
     *
     * @param nozzleIdx 0..4 (forward/right/left/up/down)
     * @param fraction  0.0..1.0 of the configured thrust; clamped
     * @return true if a thruster was found and the index was valid
     */
    public static boolean setNozzleThrust(Level level, BlockPos pos, int nozzleIdx, double fraction) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        if (be == null) return false;
        boolean ok = be.setRawNozzle(nozzleIdx, fraction);
        if (ok) AeroApiUtils.syncBlockEntity(be);
        return ok;
    }

    /**
     * Command all five nozzles in one call.
     *
     * @param fractions index 0..4 as above; shorter arrays leave the rest at 0
     * @return true if a thruster was found and updated
     */
    public static boolean setAllNozzleThrust(Level level, BlockPos pos, double[] fractions) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        if (be == null) return false;
        be.setAllRawNozzles(fractions);
        AeroApiUtils.syncBlockEntity(be);
        return true;
    }

    /** Snapshot of all five nozzle commands; all zeros when no thruster is present. */
    public static double[] getAllNozzleThrust(Level level, BlockPos pos) {
        RcsThrusterBlockEntity be = AeroApiUtils.getRcsThruster(level, pos);
        return be != null ? be.getRawNozzles() : new double[RcsThrusterBlockEntity.NOZZLE_COUNT];
    }

    /** Number of nozzles on a thruster block. */
    public static int getNozzleCount() {
        return RcsThrusterBlockEntity.NOZZLE_COUNT;
    }

    /**
     * Human-readable name of a nozzle index.
     *
     * @return "forward" / "right" / "left" / "up" / "down", or "unknown"
     */
    public static String getNozzleName(int nozzleIdx) {
        return RcsThrusterBlockEntity.getNozzleName(nozzleIdx);
    }

    /**
     * Thrust direction of one nozzle in the block's own frame (model faces north).
     *
     * <p>A caller combining this with the sub-level pose can work out the world
     * direction of each nozzle and therefore the torque it produces — the piece
     * that makes real force allocation possible instead of guessing.
     *
     * @return a fresh {x, y, z} unit vector, or {0,0,0} for an invalid index
     */
    public static double[] getNozzleLocalDirection(int nozzleIdx) {
        return RcsThrusterBlockEntity.getNozzleLocalDirection(nozzleIdx);
    }
}
