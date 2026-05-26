/*
 * Gimbal power formula in this file is derived from the Simulated mod.
 * MIT License - Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Source: https://github.com/TheSimulatedTeam/Simulated-Project
 */
package dev.simulated_team.aero_reformation.content.blocks.sensor_agency;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared state between Sensor Agency BEs and bound sensor mixins.
 * The agency writes proxied values here; sensor mixins read from here.
 */
public class SensorProxyData {

    public static final Map<BlockPos, Integer> ALTITUDE = new ConcurrentHashMap<>();
    public static final Map<BlockPos, Map<Direction, Integer>> VELOCITY = new ConcurrentHashMap<>();
    public static final Map<BlockPos, Map<Direction, Integer>> GIMBAL = new ConcurrentHashMap<>();
    public static final Map<BlockPos, Map<Direction, Integer>> NAV = new ConcurrentHashMap<>();

    // ── Raw data for goggle HUD (float values, not 0-15 signals) ──
    public static final Map<BlockPos, Float> ALTITUDE_RAW = new ConcurrentHashMap<>();
    public static final Map<BlockPos, Float> VELOCITY_RAW = new ConcurrentHashMap<>();
    public static final Map<BlockPos, Float> GIMBAL_X_RAW = new ConcurrentHashMap<>();
    public static final Map<BlockPos, Float> GIMBAL_Z_RAW = new ConcurrentHashMap<>();

    // ── Binding ownership: sensor pos → agency pos (prevents cross-binding) ──
    private static final Map<BlockPos, BlockPos> BOUND_SENSORS = new ConcurrentHashMap<>();

    /** Check if a sensor is already bound to any agency. */
    public static boolean isSensorBound(BlockPos sensorPos) {
        return BOUND_SENSORS.containsKey(sensorPos);
    }

    /** Try to claim a sensor. Returns true if successful (not already bound to another agency). */
    public static boolean claimSensor(BlockPos sensorPos, BlockPos agencyPos) {
        BlockPos existing = BOUND_SENSORS.get(sensorPos);
        if (existing != null && !existing.equals(agencyPos)) return false;
        BOUND_SENSORS.put(sensorPos, agencyPos);
        return true;
    }

    /** Release all sensors bound to this agency. */
    public static void releaseAgency(BlockPos agencyPos) {
        BOUND_SENSORS.entrySet().removeIf(e -> e.getValue().equals(agencyPos));
    }

    /** Release a specific sensor. */
    public static void releaseSensor(BlockPos sensorPos) {
        BOUND_SENSORS.remove(sensorPos);
        clear(sensorPos);
    }

    public static void clear(BlockPos pos) {
        ALTITUDE.remove(pos);
        VELOCITY.remove(pos);
        GIMBAL.remove(pos);
        NAV.remove(pos);
    }

    // ── Altitude ──

    public static void setAltitude(BlockPos pos, int signal) {
        ALTITUDE.put(pos, signal);
    }

    public static int getAltitude(BlockPos pos) {
        return ALTITUDE.getOrDefault(pos, -1);
    }

    // ── Velocity ──

    public static void setVelocity(BlockPos pos, Direction dir, int signal) {
        VELOCITY.computeIfAbsent(pos, k -> new EnumMap<>(Direction.class)).put(dir, signal);
    }

    public static int getVelocity(BlockPos pos, Direction dir) {
        Map<Direction, Integer> m = VELOCITY.get(pos);
        return m != null ? m.getOrDefault(dir, -1) : -1;
    }

    // ── Gimbal: per-direction from raw XAngle/ZAngle ──

    public static void setGimbalAngles(BlockPos pos, double xAngle, double zAngle, double angleLimitRad, boolean inverted) {
        Map<Direction, Integer> dirMap = new EnumMap<>(Direction.class);
        int e = gimbalPower(inverted ? zAngle : -zAngle, angleLimitRad);
        int w = gimbalPower(inverted ? -zAngle : zAngle, angleLimitRad);
        int s = gimbalPower(inverted ? xAngle : -xAngle, angleLimitRad);
        int n = gimbalPower(inverted ? -xAngle : xAngle, angleLimitRad);
        dirMap.put(Direction.EAST, e);
        dirMap.put(Direction.WEST, w);
        dirMap.put(Direction.SOUTH, s);
        dirMap.put(Direction.NORTH, n);
        dirMap.put(Direction.UP, 0);
        dirMap.put(Direction.DOWN, 0);
        GIMBAL.put(pos, dirMap);
    }

    private static int gimbalPower(double angle, double limitRad) {
        if (limitRad == 0) return 0;
        return Mth.clamp((int) (14.5 * angle / limitRad + 0.5), 0, 15);
    }

    public static int getGimbal(BlockPos pos, Direction dir) {
        Map<Direction, Integer> m = GIMBAL.get(pos);
        return m != null ? m.getOrDefault(dir, -1) : -1;
    }

    // ── Nav ──

    public static void setNavSignal(BlockPos pos, int signal) {
        Map<Direction, Integer> dirMap = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.values()) {
            dirMap.put(dir, signal);
        }
        NAV.put(pos, dirMap);
    }

    public static void setNavSignal(BlockPos pos, Direction dir, int signal) {
        NAV.computeIfAbsent(pos, k -> new EnumMap<>(Direction.class)).put(dir, signal);
    }

    public static int getNav(BlockPos pos, Direction dir) {
        Map<Direction, Integer> m = NAV.get(pos);
        return m != null ? m.getOrDefault(dir, -1) : -1;
    }
}
