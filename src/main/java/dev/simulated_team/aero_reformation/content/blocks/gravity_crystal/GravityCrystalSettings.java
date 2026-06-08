package dev.simulated_team.aero_reformation.content.blocks.gravity_crystal;

import java.util.*;

/** Per-sublevel settings shared by all gravity crystals on the same physics body. */
public class GravityCrystalSettings {
    public float liftMultiplier = 1.0f;     // 0.0 ~ 2.0
    public float dragMultiplier = 1.0f;     // 0.0 ~ 2.0
    public float angularDragMultiplier = 1.0f; // 0.0 ~ 2.0
    public transient boolean active;        // set by Mixin when this sublevel has gravity crystals

    /** SubLevel UUIDs known to contain gravity crystals. */
    public static final Set<UUID> CRYSTAL_SUBLEVELS = new HashSet<>();

    private static final Map<UUID, GravityCrystalSettings> SETTINGS = new HashMap<>();

    public static GravityCrystalSettings get(UUID subLevelId) {
        return SETTINGS.computeIfAbsent(subLevelId, k -> new GravityCrystalSettings());
    }

    public static void remove(UUID subLevelId) {
        SETTINGS.remove(subLevelId);
    }
}
