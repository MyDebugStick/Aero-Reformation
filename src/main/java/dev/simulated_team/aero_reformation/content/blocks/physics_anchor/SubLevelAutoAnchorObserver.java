package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;

import java.util.UUID;

/**
 * Cleans up anchor data when a SubLevel is removed externally (e.g. via commands).
 */
public class SubLevelAutoAnchorObserver implements SubLevelObserver {

    @Override
    public void onSubLevelAdded(SubLevel subLevel) {}

    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        UUID id = subLevel.getUniqueId();
        if (!AnchorChunkLoader.hasAnchor(id)) return;

        AeroReformation.LOGGER.debug("[PhysicsAnchor] SubLevel removed externally sub={} reason={}, cleaning up", id, reason);

        // Clean up marker and tickets for all anchors in this dimension
        if (subLevel.getLevel() instanceof ServerLevel sl) {
            var dimMap = AnchorChunkLoader.anchorsFor(sl.dimension());
            if (dimMap != null) {
                var it = dimMap.entrySet().iterator();
                while (it.hasNext()) {
                    var e = it.next();
                    var d = e.getValue();
                    if (d == null || d.subLevel() == null || !d.subLevel().getUniqueId().equals(id)) continue;
                    if (d.marker() != null) d.marker().forceDiscard();
                    if (d.lastTicketChunk() != null)
                        sl.getChunkSource().removeRegionTicket(TicketType.PORTAL,
                                d.lastTicketChunk(), d.ticketRadius(), e.getKey());
                    it.remove();
                }
            }
            AnchorChunkLoader.removeAnchoredSubLevel(id);
            AnchorSavedData.get(sl).remove(id);
        }
    }

    @Override
    public void tick(SubLevelContainer subLevels) {}
}
