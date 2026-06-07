package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;

public class SubLevelAutoAnchorObserver implements SubLevelObserver {

    @Override
    public void onSubLevelAdded(SubLevel subLevel) {}

    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {}

    @Override
    public void tick(SubLevelContainer subLevels) {}
}
