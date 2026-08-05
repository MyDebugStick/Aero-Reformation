package dev.simulated_team.aero_reformation.compat.cc;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.simulated_team.aero_reformation.api.RcsThrusterApi;
import dev.simulated_team.aero_reformation.content.blocks.rcs_thruster.RcsThrusterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ComputerCraft peripheral for the RCS thruster.
 *
 * <p>Exposes the same operations as {@link RcsThrusterApi} under the
 * peripheral type {@code aero_rcs}. All mutating methods run on the server
 * main thread ({@code mainThread = true}) so world edits are safe.
 *
 * <p>Example (Lua):
 * <pre>{@code
 * local rcs = peripheral.find("aero_rcs")
 * rcs.setThrust(2000)
 * rcs.setCreative(true)
 * print(rcs.getActiveThrust())
 * }</pre>
 */
public class RcsPeripheral implements IPeripheral {
    private final RcsThrusterBlockEntity blockEntity;

    public RcsPeripheral(RcsThrusterBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public String getType() {
        return "aero_rcs";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof RcsPeripheral p && p.blockEntity == blockEntity;
    }

    // ─────────────────────────── helpers ───────────────────────────

    private Level level() {
        return blockEntity.getLevel();
    }

    private BlockPos pos() {
        return blockEntity.getBlockPos();
    }

    private static Map<String, Double> posMap(@Nullable BlockPos pos) {
        Map<String, Double> map = new LinkedHashMap<>();
        if (pos == null) return map;
        map.put("x", (double) pos.getX());
        map.put("y", (double) pos.getY());
        map.put("z", (double) pos.getZ());
        return map;
    }

    // ─────────────────────────── thrust ───────────────────────────

    /** Current configured thrust in pN. */
    @LuaFunction(value = "getThrust", mainThread = true)
    public final double getThrust() {
        return RcsThrusterApi.getThrust(level(), pos());
    }

    /** Set configured thrust to the option nearest the given pN value. */
    @LuaFunction(mainThread = true)
    public final boolean setThrust(double pn) {
        return RcsThrusterApi.setThrust(level(), pos(), pn);
    }

    /** Selected thrust option index. */
    @LuaFunction(value = "getThrustIndex", mainThread = true)
    public final int getThrustIndex() {
        return RcsThrusterApi.getThrustIndex(level(), pos());
    }

    /** Set the thrust option index directly. */
    @LuaFunction(mainThread = true)
    public final boolean setThrustIndex(int index) {
        return RcsThrusterApi.setThrustIndex(level(), pos(), index);
    }

    /** Table of all selectable thrust options in pN (1-based Lua table). */
    @LuaFunction(value = "getThrustOptions", mainThread = true)
    public final Map<Integer, Integer> getThrustOptions() {
        int[] options = RcsThrusterApi.getThrustOptions();
        Map<Integer, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < options.length; i++) map.put(i + 1, options[i]);
        return map;
    }

    // ─────────────────────────── creative ───────────────────────────

    /** Whether creative (free fuel) mode is enabled. */
    @LuaFunction(value = "isCreative", mainThread = true)
    public final boolean isCreative() {
        return RcsThrusterApi.isCreative(level(), pos());
    }

    /** Enable or disable creative (free fuel) mode. */
    @LuaFunction(mainThread = true)
    public final boolean setCreative(boolean creative) {
        return RcsThrusterApi.setCreative(level(), pos(), creative);
    }

    // ─────────────────────────── angled mode ───────────────────────────

    /** Current angled-nozzle reduction mode index. */
    @LuaFunction(value = "getAngledMode", mainThread = true)
    public final int getAngledMode() {
        return RcsThrusterApi.getAngledMode(level(), pos());
    }

    /** Cycle to the next angled-nozzle reduction mode. */
    @LuaFunction(mainThread = true)
    public final boolean cycleAngledMode() {
        return RcsThrusterApi.cycleAngledMode(level(), pos());
    }

    // ─────────────────────────── runtime status ───────────────────────────

    /** Total thrust currently being output (pN). */
    @LuaFunction(value = "getActiveThrust", mainThread = true)
    public final double getActiveThrust() {
        return RcsThrusterApi.getActiveThrust(level(), pos());
    }

    /** Bitmask of firing nozzles (bit i = nozzle i). */
    @LuaFunction(value = "getActiveNozzleMask", mainThread = true)
    public final int getActiveNozzleMask() {
        return RcsThrusterApi.getActiveNozzleMask(level(), pos());
    }

    /** Whether fuel/energy was available on the last physics tick. */
    @LuaFunction(value = "hasFuel", mainThread = true)
    public final boolean hasFuel() {
        return RcsThrusterApi.hasFuel(level(), pos());
    }

    /** Whether the thruster is currently running on electricity. */
    @LuaFunction(value = "isElectric", mainThread = true)
    public final boolean isElectric() {
        return RcsThrusterApi.isElectric(level(), pos());
    }

    /** Fuel stored in the connected tank (mB). */
    @LuaFunction(value = "getFuelAmount", mainThread = true)
    public final int getFuelAmount() {
        return RcsThrusterApi.getFuelAmount(level(), pos());
    }

    // ─────────────────────────── binding ───────────────────────────

    /** Bound directional synchronizer position, or an empty table if none. */
    @LuaFunction(value = "getBoundSyncPos", mainThread = true)
    public final Map<String, Double> getBoundSyncPos() {
        return posMap(RcsThrusterApi.getBoundSyncPos(level(), pos()));
    }

    /** Bind this thruster to a directional synchronizer at the given position. */
    @LuaFunction(mainThread = true)
    public final boolean setBoundSyncPos(double x, double y, double z) {
        return RcsThrusterApi.setBoundSyncPos(level(), pos(), new BlockPos((int) x, (int) y, (int) z));
    }

    /** Clear the synchronizer binding. */
    @LuaFunction(mainThread = true)
    public final boolean clearBoundSyncPos() {
        return RcsThrusterApi.setBoundSyncPos(level(), pos(), null);
    }

    /** Bound guidance warhead position, or an empty table if none. */
    @LuaFunction(value = "getBoundWarheadPos", mainThread = true)
    public final Map<String, Double> getBoundWarheadPos() {
        return posMap(RcsThrusterApi.getBoundWarheadPos(level(), pos()));
    }

    /** Whether the thruster is in guidance (warhead-bound) mode. */
    @LuaFunction(value = "isGuidanceMode", mainThread = true)
    public final boolean isGuidanceMode() {
        return RcsThrusterApi.isGuidanceMode(level(), pos());
    }

    @Override
    public void attach(@NotNull IComputerAccess computer) {
        // no per-computer state needed
    }

    @Override
    public void detach(@NotNull IComputerAccess computer) {
        // no per-computer state needed
    }
}
