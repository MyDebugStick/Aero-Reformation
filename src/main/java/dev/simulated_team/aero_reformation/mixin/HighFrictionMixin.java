package dev.simulated_team.aero_reformation.mixin;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockCluster;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockController;
import dev.ryanhcode.sable.physics.floating_block.FloatingClusterContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;

import java.util.*;

/**
 * 高摩擦方块混合——缩放同一维度中所有含高摩擦方块 SubLevel 的总 Drag，
 * 确保轴承连接的多物理体（如轮胎+车身）按整体质量计算摩擦。
 *
 * 质量因子 = clamp(totalMass / 1000, 1.0, 10.0)
 */
@Mixin(value = FloatingBlockController.class, remap = false)
public abstract class HighFrictionMixin {

    @Shadow private ServerSubLevel subLevel;

    /** 缓存：当前维度的总高摩擦质量，每 20 tick 刷新 */
    @Unique private static final Map<ServerLevel, Double> cachedTotalMass = new WeakHashMap<>();
    @Unique private static final Map<ServerLevel, Integer> cacheRefreshTick = new WeakHashMap<>();

    @Invoker("applyFriction")
    abstract void callApplyFriction(FloatingClusterContainer container, FloatingBlockCluster cluster,
                                    Vector3dc localGravity, Vector3dc linearVelocity, Vector3dc angularVelocity,
                                    Vector3d frictionForce, Vector3d frictionTorque);

    @Redirect(method = "physicsTick",
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/physics/floating_block/FloatingBlockController;"
                           + "applyFriction(Ldev/ryanhcode/sable/physics/floating_block/FloatingClusterContainer;"
                           + "Ldev/ryanhcode/sable/physics/floating_block/FloatingBlockCluster;"
                           + "Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;"
                           + "Lorg/joml/Vector3d;Lorg/joml/Vector3d;)V"),
            require = 0,
            remap = false)
    private void redirectApplyFriction(FloatingBlockController self,
                                       FloatingClusterContainer container, FloatingBlockCluster cluster,
                                       Vector3dc localGravity, Vector3dc linearVelocity, Vector3dc angularVelocity,
                                       Vector3d frictionForce, Vector3d frictionTorque) {
        callApplyFriction(container, cluster, localGravity, linearVelocity, angularVelocity,
                frictionForce, frictionTorque);

        if (isHighFriction(cluster)) {
            double totalMass = getTotalHighFrictionMass();
            double massFactor = clamp(totalMass / 1000.0, 1.0, 10.0);
            frictionForce.mul(massFactor);
            frictionTorque.mul(massFactor);
        }
    }

    /**
     * 获取当前 SubLevel 所在物理体的总质量（遍历约束连接的所有 SubLevel）。
     */
    @Unique
    private double getTotalHighFrictionMass() {
        ServerLevel level = (ServerLevel) subLevel.getLevel();
        Integer lastRefresh = cacheRefreshTick.get(level);
        int currentTick = level.getServer().getTickCount();
        if (lastRefresh != null && currentTick - lastRefresh < 20) {
            Double cached = cachedTotalMass.get(level);
            if (cached != null) return cached;
        }

        double total = subLevel.getMassTracker().getMass(); // fallback
        try {
            // 找到有高摩擦方块的 SubLevel，再获取其完整连接链
            ServerSubLevel seed = null;
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container instanceof ServerSubLevelContainer svrContainer) {
                for (ServerSubLevel sl : svrContainer.getAllSubLevels()) {
                    if (hasHighFrictionMaterial(sl)) {
                        seed = sl;
                        break;
                    }
                }
            }

            if (seed != null) {
                // 获取所有约束连接的 SubLevel（含无高摩擦方块的主体）
                var chain = dev.ryanhcode.sable.api.SubLevelHelper.getConnectedChain(seed);
                total = 0;
                for (var sl : chain) {
                    if (sl instanceof ServerSubLevel ssl) {
                        total += ssl.getMassTracker().getMass();
                    }
                }
            }
        } catch (Exception ignored) {}

        cachedTotalMass.put(level, Math.max(total, 1));
        cacheRefreshTick.put(level, currentTick);
        return Math.max(total, 1);
    }

    /** 检查 SubLevel 中是否有 liftStrength==-1 的 cluster */
    @Unique
    private static boolean hasHighFrictionMaterial(ServerSubLevel sl) {
        try {
            var controller = sl.getFloatingBlockController();
            // Access the private 'containers' field via reflection (same field we shadow)
            var containersField = FloatingBlockController.class.getDeclaredField("containers");
            containersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var containers = (List<FloatingClusterContainer>) containersField.get(controller);
            if (containers != null) {
                for (var c : containers) {
                    for (var cl : c.clusters) {
                        if (cl.getMaterial().liftStrength() == -1
                                && cl.getMaterial().transitionSpeed() == -1) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    @Unique
    private static boolean isHighFriction(FloatingBlockCluster cluster) {
        return cluster.getMaterial().liftStrength() == -1
                && cluster.getMaterial().transitionSpeed() == -1;
    }

    @Unique
    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }
}
