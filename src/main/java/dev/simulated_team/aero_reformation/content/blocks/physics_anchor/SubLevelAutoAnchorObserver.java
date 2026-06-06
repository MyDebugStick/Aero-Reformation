package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.simulated_team.aero_reformation.AeroReformation;

import java.util.UUID;

/**
 * When a new SubLevel is created (e.g. via bearing splitting a multi-body structure),
 * auto-protect it if its parent container already has an anchored SubLevel.
 */
public class SubLevelAutoAnchorObserver implements SubLevelObserver {

    @Override
    public void onSubLevelAdded(SubLevel subLevel) {
        UUID id = subLevel.getUniqueId();
        // Skip if already protected (has anchor or already auto-protected)
        if (AnchorChunkLoader.hasAnchor(id)) return;
        // Check if any sibling SubLevel in this container is already anchored
        if (AnchorChunkLoader.hasAnchoredSibling(subLevel)) {
            AnchorChunkLoader.protectSubLevel(id);
            AeroReformation.LOGGER.info("[PhysicsAnchor] Auto-protected child sub={}", id);
        }
    }

    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        // Cleanup handled by AnchorChunkLoader tick
    }

    @Override
    public void tick(SubLevelContainer subLevels) {}
}
