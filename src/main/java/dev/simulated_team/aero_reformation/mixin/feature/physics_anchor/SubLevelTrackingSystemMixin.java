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
            if (subId != null && isInAnchoredChain(sp, subId)) {
                if (isInHiddenChain(sp, subId)) {
                    return 1.0;
                }
                return dev.simulated_team.aero_reformation.config.AeroReformationConfig.anchorTrackingRange;
            }
        }
        return SableConfig.SUB_LEVEL_TRACKING_RANGE.getAsDouble();
    }

    /** 检查该 SubLevel 或其约束连接链上的任意 SubLevel 是否有锚点 */
    private static boolean isInAnchoredChain(ServerPlayer sp, UUID subId) {
        if (AnchorChunkLoader.hasAnchor(subId)) return true;
        try {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(sp.serverLevel());
            if (container == null) return false;
            for (var sl : container.getAllSubLevels()) {
                if (sl instanceof ServerSubLevel ssl && ssl.getUniqueId().equals(subId)) {
                    var chain = dev.ryanhcode.sable.api.SubLevelHelper.getConnectedChain(ssl);
                    for (var cs : chain) {
                        if (cs instanceof ServerSubLevel cssl && AnchorChunkLoader.hasAnchor(cssl.getUniqueId())) {
                            return true;
                        }
                    }
                    break;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /** 检查该 SubLevel 或其约束连接链上的任意 SubLevel 是否被隐藏 */
    private static boolean isInHiddenChain(ServerPlayer sp, UUID subId) {
        var hidden = dev.simulated_team.aero_reformation.content.items.ethereal_key.EtherealKeyItem.HIDDEN_SUBLEVELS;
        if (hidden.contains(subId)) return true;
        try {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(sp.serverLevel());
            if (container == null) return false;
            for (var sl : container.getAllSubLevels()) {
                if (sl instanceof ServerSubLevel ssl && ssl.getUniqueId().equals(subId)) {
                    var chain = dev.ryanhcode.sable.api.SubLevelHelper.getConnectedChain(ssl);
                    for (var cs : chain) {
                        if (cs instanceof ServerSubLevel cssl && hidden.contains(cssl.getUniqueId())) {
                            return true;
                        }
                    }
                    break;
                }
            }
        } catch (Exception ignored) {}
        return false;
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
