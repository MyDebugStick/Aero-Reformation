package dev.simulated_team.aero_reformation.mixin.feature.physics_anchor;

import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem;
import dev.simulated_team.aero_reformation.content.blocks.physics_anchor.AnchorChunkLoader;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/**
 * For anchored SubLevels: replace the fixed tracking range with sim distance + 64.
 * This keeps them tracked well beyond normal range, preventing Sable from removing them.
 */
@Mixin(value = SubLevelTrackingSystem.class, remap = false)
public abstract class SubLevelTrackingSystemMixin {

    @Redirect(method = "shouldLoad",
            at = @At(value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/common/ModConfigSpec$DoubleValue;getAsDouble()D"),
            remap = false)
    private double replaceTrackingRange(net.neoforged.neoforge.common.ModConfigSpec.DoubleValue configValue,
                                         net.minecraft.world.entity.player.Player player,
                                         org.joml.Vector3dc entityPos) {
        if (player instanceof ServerPlayer sp) {
            UUID subId = getSubLevelIdAt(sp, entityPos);
            if (subId != null && AnchorChunkLoader.hasAnchor(subId)) {
                // Hidden SubLevels: tiny tracking range so they disappear
                if (dev.simulated_team.aero_reformation.content.items.ethereal_key.EtherealKeyItem.HIDDEN_SUBLEVELS.contains(subId)) {
                    return 1.0;
                }
                return dev.simulated_team.aero_reformation.config.AeroReformationConfig.anchorTrackingRange;
            }
        }
        return SableConfig.SUB_LEVEL_TRACKING_RANGE.getAsDouble();
    }

    private static UUID getSubLevelIdAt(ServerPlayer sp, org.joml.Vector3dc entityPos) {
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(sp.serverLevel());
        if (container == null) return null;
        final double eps = 0.5;
        for (SubLevel sub : container.getAllSubLevels()) {
            if (sub.isRemoved() || !(sub instanceof ServerSubLevel ssl)) continue;
            var pos = sub.logicalPose().position();
            if (Math.abs(pos.x() - entityPos.x()) < eps
                    && Math.abs(pos.y() - entityPos.y()) < eps
                    && Math.abs(pos.z() - entityPos.z()) < eps) {
                return ssl.getUniqueId();
            }
        }
        return null;
    }
}
