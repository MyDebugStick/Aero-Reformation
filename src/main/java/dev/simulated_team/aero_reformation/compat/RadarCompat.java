package dev.simulated_team.aero_reformation.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Safe reflection-based access to Create Radar classes.
 * All calls must be guarded by {@link #RADAR_LOADED}.
 */
public class RadarCompat {

    public static final boolean RADAR_LOADED;

    static {
        boolean loaded = false;
        try {
            loaded = ModList.get().isLoaded("create_radar");
        } catch (Exception ignored) {}
        RADAR_LOADED = loaded;
    }

    // ─── Reflection cache ───────────────────────────────────────────────

    private static Class<?> monitorClass;
    private static Method monitorGetController;
    private static Field activetrackField;
    private static Method trackPosition;
    private static Method trackGetId;

    private static boolean resolved = false;

    private static void resolve() {
        if (resolved || !RADAR_LOADED) return;
        resolved = true;
        try {
            monitorClass = Class.forName("com.happysg.radar.block.monitor.MonitorBlockEntity");
            monitorGetController = monitorClass.getMethod("getController");
            activetrackField = monitorClass.getField("activetrack");
            Class<?> trackClass = Class.forName("com.happysg.radar.block.radar.track.RadarTrack");
            trackPosition = trackClass.getMethod("position");
            trackGetId = trackClass.getMethod("getId");
        } catch (Exception e) {
            // Radar classes not available despite ModList check — silent
        }
    }

    // ─── Public API ─────────────────────────────────────────────────────

    /** Check if the given BE is a MonitorBlockEntity (via reflection). */
    public static boolean isMonitorBlockEntity(BlockEntity be) {
        if (!RADAR_LOADED) return false;
        resolve();
        return monitorClass != null && monitorClass.isInstance(be);
    }

    /**
     * Get the active RadarTrack from a MonitorBlockEntity (controller-aware).
     * @return RadarTrack object, or null if not available
     */
    @Nullable
    private static Object getActiveTrack(BlockEntity monitorBE) {
        if (!RADAR_LOADED) return null;
        resolve();
        try {
            Object ctrl = monitorGetController.invoke(monitorBE);
            return activetrackField.get(ctrl != null ? ctrl : monitorBE);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the active RadarTrack position from a MonitorBlockEntity.
     * @return position as Vec3, or null if not available
     */
    @Nullable
    public static Vec3 getActiveTrackPosition(BlockEntity monitorBE) {
        try {
            Object track = getActiveTrack(monitorBE);
            if (track == null) return null;
            return (Vec3) trackPosition.invoke(track);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the active RadarTrack's ID (UUID string) from a MonitorBlockEntity.
     * @return id string, or null if not available
     */
    @Nullable
    public static String getActiveTrackId(BlockEntity monitorBE) {
        try {
            Object track = getActiveTrack(monitorBE);
            if (track == null) return null;
            return (String) trackGetId.invoke(track);
        } catch (Exception e) {
            return null;
        }
    }
}
