package dev.simulated_team.aero_reformation.mixin.feature.physics_anchor;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.aero_reformation.content.blocks.physics_anchor.AnchorChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents Sable from marking a SubLevel as removed if we have an active anchor on it.
 * This is needed because markRemoved() is called AFTER destroyAllBlocks() in the
 * processSubLevelRemovals flow — cancelling destroyAllBlocks alone doesn't stop it.
 */
@Mixin(SubLevel.class)
public class SubLevelMarkRemovedMixin {

    @Inject(method = "markRemoved", at = @At("HEAD"), cancellable = true)
    private void preventMarkRemovedIfAnchored(CallbackInfo ci) {
        if (AnchorChunkLoader.hasAnchor(((SubLevel) (Object) this).getUniqueId())) {
            ci.cancel();
        }
    }
}
