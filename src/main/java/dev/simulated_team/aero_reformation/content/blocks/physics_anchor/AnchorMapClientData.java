package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import net.minecraft.core.BlockPos;

import java.util.*;

public class AnchorMapClientData {
    private static final Map<UUID, AnchorMapSyncPacket.MarkerEntry> MARKERS = new LinkedHashMap<>();
    private static final Map<BlockPos, Integer> RADIUS_MAP = new HashMap<>();

    public static void update(List<AnchorMapSyncPacket.MarkerEntry> entries) {
        MARKERS.clear();
        RADIUS_MAP.clear();
        for (var e : entries) {
            MARKERS.put(e.subLevelId(), e);
            RADIUS_MAP.put(new BlockPos((int) e.x(), (int) e.y(), (int) e.z()), e.radius());
        }
    }

    public static Collection<AnchorMapSyncPacket.MarkerEntry> getMarkers() {
        return Collections.unmodifiableCollection(MARKERS.values());
    }

    public static int getRadius(BlockPos pos) {
        return RADIUS_MAP.getOrDefault(pos, 2);
    }
}
