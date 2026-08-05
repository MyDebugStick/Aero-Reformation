package dev.simulated_team.aero_reformation.compat.cc;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.simulated_team.aero_reformation.api.PowerBlockApi;
import dev.simulated_team.aero_reformation.content.blocks.power.PowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * ComputerCraft peripheral for the pilot "power" block.
 *
 * <p>Exposes the same operations as {@link PowerBlockApi} under the peripheral
 * type {@code aero_power}. Mutating methods run on the server main thread.
 */
public class PowerPeripheral implements IPeripheral {
    private final PowerBlockEntity blockEntity;

    public PowerPeripheral(PowerBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public String getType() {
        return "aero_power";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof PowerPeripheral p && p.blockEntity == blockEntity;
    }

    private Level level() {
        return blockEntity.getLevel();
    }

    private BlockPos pos() {
        return blockEntity.getBlockPos();
    }

    // ─────────────────────────── yaw ───────────────────────────

    /** Maximum yaw limit in degrees. */
    @LuaFunction(value = "getYawMax", mainThread = true)
    public final double getYawMax() {
        return PowerBlockApi.getYawMax(level(), pos());
    }

    /** Set the maximum yaw limit (clamped 1..180). */
    @LuaFunction(mainThread = true)
    public final boolean setYawMax(double degrees) {
        return PowerBlockApi.setYawMax(level(), pos(), degrees);
    }

    // ─────────────────────────── pitch ───────────────────────────

    /** Maximum pitch limit in degrees. */
    @LuaFunction(value = "getPitchMax", mainThread = true)
    public final double getPitchMax() {
        return PowerBlockApi.getPitchMax(level(), pos());
    }

    /** Set the maximum pitch limit (clamped 1..90). */
    @LuaFunction(mainThread = true)
    public final boolean setPitchMax(double degrees) {
        return PowerBlockApi.setPitchMax(level(), pos(), degrees);
    }

    // ─────────────────────────── seat height ───────────────────────────

    /** Seat height offset (blocks, clamped -0.2..0.2). */
    @LuaFunction(value = "getSeatHeight", mainThread = true)
    public final double getSeatHeight() {
        return PowerBlockApi.getSeatHeight(level(), pos());
    }

    /** Set the seat height offset (clamped -0.2..0.2). */
    @LuaFunction(mainThread = true)
    public final boolean setSeatHeight(double height) {
        return PowerBlockApi.setSeatHeight(level(), pos(), height);
    }
}
