package dev.simulated_team.aero_reformation.mixin.feature.physics_anchor;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.simulated_team.aero_reformation.content.blocks.physics_anchor.AnchorChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents Sable from destroying/marking SubLevels that have an active anchor.
 * Two injection points because both paths lead to SubLevel destruction.
 */
@Mixin(ServerLevelPlot.class)
public class ServerLevelPlotMixin {

    /** Block destroyAllBlocks when anchored */
    @Inject(method = "destroyAllBlocks", at = @At("HEAD"), cancellable = true)
    private void preventDestroy(CallbackInfo ci) {
        SubLevel sl = ((LevelPlot) (Object) this).getSubLevel();
        if (AnchorChunkLoader.hasAnchor(sl.getUniqueId())) {
            ci.cancel();
        }
    }
}
