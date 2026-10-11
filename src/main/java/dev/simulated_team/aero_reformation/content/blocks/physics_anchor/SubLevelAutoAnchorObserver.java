package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Bridges Sable's SubLevel lifecycle callbacks into {@link AnchorChunkLoader}.
 *
 * <p>This is the piece that makes anchor bindings survive a sub-level going away and coming
 * back. Sable distinguishes two very different events and we must too:
 *
 * <ul>
 *   <li>{@link SubLevelRemovalReason#UNLOADED} — the sub-level left memory but still exists.
 *       The binding is <b>parked</b>: the marker entity stays in the world at its last known
 *       position, and a persistent force-load ticket is filed against the UUID so Sable
 *       brings it back. Binding is restored on {@code onSubLevelAdded}.</li>
 *   <li>{@link SubLevelRemovalReason#REMOVED} — the sub-level is gone for good. Everything
 *       is torn down.</li>
 * </ul>
 */
public class SubLevelAutoAnchorObserver implements SubLevelObserver {

    @Override
    public void onSubLevelAdded(SubLevel subLevel) {
        if (!(subLevel.getLevel() instanceof ServerLevel sl)) return;
        try {
            AnchorChunkLoader.onSubLevelLoaded(sl, subLevel);
        } catch (Throwable t) {
            AeroReformation.LOGGER.error("[PhysicsAnchor] onSubLevelAdded hook failed for sub={}",
                    subLevel.getUniqueId(), t);
        }
    }

    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        if (!(subLevel.getLevel() instanceof ServerLevel sl)) return;

        final UUID id = subLevel.getUniqueId();
        if (!AnchorChunkLoader.hasAnchor(id)) return;

        try {
            if (reason == SubLevelRemovalReason.UNLOADED) {
                AeroReformation.LOGGER.debug(
                        "[PhysicsAnchor] SubLevel {} unloaded — parking anchor binding", id);
                AnchorChunkLoader.onSubLevelUnloaded(sl, subLevel);
            } else {
                AeroReformation.LOGGER.debug(
                        "[PhysicsAnchor] SubLevel {} removed ({}) — tearing down binding", id, reason);
                AnchorChunkLoader.onSubLevelRemoved(sl, subLevel);
            }
        } catch (Throwable t) {
            AeroReformation.LOGGER.error("[PhysicsAnchor] onSubLevelRemoved hook failed for sub={}", id, t);
        }
    }

    @Override
    public void tick(SubLevelContainer subLevels) {}
}
