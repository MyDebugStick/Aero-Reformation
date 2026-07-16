package dev.simulated_team.aero_reformation.ponder;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;

import dev.simulated_team.aero_reformation.AeroReformation;

/**
 * Aero Reformation 的 Ponder 场景注册入口。
 * 注册所有 Ponder 故事板到对应的物品。
 */
public class AeroPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return "aero_reformation";
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        // 红石扭簧
        helper.forComponents(ResourceLocation.parse("aero_reformation:redstone_spring"))
            .addStoryBoard(ResourceLocation.parse("aero_reformation:test1"),
                RedstoneSpringScenes::redstone_spring_p1)
            .addStoryBoard(ResourceLocation.parse("aero_reformation:test1"),
                RedstoneSpringScenes::redstone_spring_p2);

        // 方向同步器（母）
        helper.forComponents(ResourceLocation.parse("aero_reformation:directional_synchronizer_master"))
            .addStoryBoard(ResourceLocation.parse("aero_reformation:tbq3"),
                DirectionalSynchronizerMasterScenes::directional_synchronizer_master_p1)
            .addStoryBoard(ResourceLocation.parse("aero_reformation:tbq4"),
                DirectionalSynchronizerMasterScenes::directional_synchronizer_master_p2);

        // 方向同步器（子）
        helper.forComponents(ResourceLocation.parse("aero_reformation:directional_synchronizer_slave"))
            .addStoryBoard(ResourceLocation.parse("aero_reformation:tbq3"),
                DirectionalSynchronizerSlaveScenes::directional_synchronizer_slave_p1)
            .addStoryBoard(ResourceLocation.parse("aero_reformation:tbq4"),
                DirectionalSynchronizerSlaveScenes::directional_synchronizer_slave_p2);

        // 调试：打印已注册条目数
        AeroReformation.LOGGER.info("[Ponder] 注册完成, 全局共 {} 个场景条目",
            PonderIndex.getSceneAccess().getRegisteredEntries().size());

    }

    @Override
    public void registerSharedText(SharedTextRegistrationHelper helper) {
        // 如有共享文本，在此注册
    }
}
