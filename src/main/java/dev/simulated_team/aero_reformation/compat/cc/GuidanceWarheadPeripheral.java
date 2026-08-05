package dev.simulated_team.aero_reformation.compat.cc;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.simulated_team.aero_reformation.api.GuidanceWarheadApi;
import dev.simulated_team.aero_reformation.content.blocks.guidance_warhead.GuidanceWarheadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ComputerCraft peripheral for the guidance warhead.
 *
 * <p>Peripheral type {@code aero_warhead}. Lets a computer set the target,
 * pick a search mode and tune the PID guidance parameters.
 *
 * <p>Example (Lua):
 * <pre>{@code
 * local w = peripheral.find("aero_warhead")
 * w.setManualTarget(100, 80, -200)  -- switch to manual mode + aim here
 * w.setGuidanceMode(1)              -- toggle on/off with redstone
 * w.setTuning("kp", 1.0)
 * }</pre>
 */
public class GuidanceWarheadPeripheral implements IPeripheral {
    private final GuidanceWarheadBlockEntity blockEntity;

    public GuidanceWarheadPeripheral(GuidanceWarheadBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public String getType() {
        return "aero_warhead";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof GuidanceWarheadPeripheral p && p.blockEntity == blockEntity;
    }

    private Level level() {
        return blockEntity.getLevel();
    }

    private BlockPos pos() {
        return blockEntity.getBlockPos();
    }

    private static Map<String, Double> targetMap(@Nullable Vector3d target) {
        Map<String, Double> map = new LinkedHashMap<>();
        if (target == null) return map;
        map.put("x", target.x());
        map.put("y", target.y());
        map.put("z", target.z());
        return map;
    }

    // ─────────────────────────── mode / target ───────────────────────────

    /** Current search mode: 0=mass, 1=nearest, 2=manual, 3=radar. */
    @LuaFunction(value = "getSearchMode", mainThread = true)
    public final int getSearchMode() {
        return GuidanceWarheadApi.getSearchMode(level(), pos());
    }

    /** Set the search mode (0..3). */
    @LuaFunction(mainThread = true)
    public final boolean setSearchMode(int mode) {
        return GuidanceWarheadApi.setSearchMode(level(), pos(), mode);
    }

    /** Current acquired target as {x,y,z}, or an empty table if none. */
    @LuaFunction(value = "getTargetPos", mainThread = true)
    public final Map<String, Double> getTargetPos() {
        return targetMap(GuidanceWarheadApi.getTargetPos(level(), pos()));
    }

    /** Set manual target coordinates (also switches to manual search mode). */
    @LuaFunction(mainThread = true)
    public final boolean setManualTarget(double x, double y, double z) {
        return GuidanceWarheadApi.setManualTarget(level(), pos(), x, y, z);
    }

    /** Guidance control mode: 0 = direct (redstone on), 1 = toggle (rising edge). */
    @LuaFunction(value = "getGuidanceMode", mainThread = true)
    public final int getGuidanceMode() {
        return GuidanceWarheadApi.getGuidanceMode(level(), pos());
    }

    /** Set the guidance control mode (0 or 1). */
    @LuaFunction(mainThread = true)
    public final boolean setGuidanceMode(int mode) {
        return GuidanceWarheadApi.setGuidanceMode(level(), pos(), mode);
    }

    /** Whether guidance is enabled (toggle mode state). */
    @LuaFunction(value = "isGuidanceEnabled", mainThread = true)
    public final boolean isGuidanceEnabled() {
        return GuidanceWarheadApi.isGuidanceEnabled(level(), pos());
    }

    /** Drop the current target so the next scan re-acquires one. */
    @LuaFunction(mainThread = true)
    public final boolean unlockTarget() {
        return GuidanceWarheadApi.unlockTarget(level(), pos());
    }

    // ─────────────────────────── PID tuning ───────────────────────────

    /**
     * Read a tuning value by name.
     * Names: "kp","ki","kd","maxSpeed","sidePower","maxThrustPN","cruiseAltitude",
     * "brakeCoeff","proximityRange","redstoneRange","altitudeOffset","minSearchRange","maxSearchRange".
     */
    @LuaFunction(value = "getTuning", mainThread = true)
    public final double getTuning(String name) {
        return GuidanceWarheadApi.getTuning(level(), pos(), name);
    }

    /** Write a tuning value by name (see {@link #getTuning}). */
    @LuaFunction(mainThread = true)
    public final boolean setTuning(String name, double value) {
        return GuidanceWarheadApi.setTuning(level(), pos(), name, (float) value);
    }
}
