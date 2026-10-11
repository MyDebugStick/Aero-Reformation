package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Client-side mirror of the server's anchor bindings, filled by {@link AnchorMapSyncPacket}.
 *
 * <p>Needed because the binding tables in {@link AnchorChunkLoader} are server-side state:
 * on a dedicated server the client's copies are empty, which is why the goggle tooltip used
 * to claim "no marker" even on a perfectly healthy anchor (singleplayer only appeared to
 * work because the integrated server shares the same static maps).
 */
public class AnchorMapClientData {

    /** sub-level UUID -> entry */
    private static final Map<UUID, AnchorMapSyncPacket.MarkerEntry> MARKERS = new LinkedHashMap<>();
    /** anchor block position -> entry (this is what block-entity tooltips look up) */
    private static final Map<BlockPos, AnchorMapSyncPacket.MarkerEntry> BY_ANCHOR = new HashMap<>();

    public static void update(List<AnchorMapSyncPacket.MarkerEntry> entries) {
        MARKERS.clear();
        BY_ANCHOR.clear();
        for (var e : entries) {
            MARKERS.put(e.subLevelId(), e);
            if (e.anchorPos() != null) {
                BY_ANCHOR.put(e.anchorPos(), e);
            }
        }
    }

    public static Collection<AnchorMapSyncPacket.MarkerEntry> getMarkers() {
        return Collections.unmodifiableCollection(MARKERS.values());
    }

    /** Look up the binding owned by an anchor block. */
    @Nullable
    public static AnchorMapSyncPacket.MarkerEntry getEntry(BlockPos anchorPos) {
        return BY_ANCHOR.get(anchorPos);
    }

    public static int getRadius(BlockPos anchorPos) {
        var e = BY_ANCHOR.get(anchorPos);
        return e != null ? e.radius() : 2;
    }

    /** Called on disconnect/dimension change so stale entries cannot leak across worlds. */
    public static void clear() {
        MARKERS.clear();
        BY_ANCHOR.clear();
    }
}
