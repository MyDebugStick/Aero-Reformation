package dev.simulated_team.aero_reformation.ponder;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * 由 Ponderer 场景自动生成的 PonderPlugin
 * 源场景: ponderer:directional_synchronizer_master
 */
public class DirectionalSynchronizerMasterPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return "aero_reformation";
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(ResourceLocation.parse("aero_reformation:directional_synchronizer_master"))
            .addStoryBoard("ponderer:tbq3", DirectionalSynchronizerMasterScenes::directional_synchronizer_master_p1);
        helper.forComponents(ResourceLocation.parse("aero_reformation:directional_synchronizer_master"))
            .addStoryBoard("ponderer:tbq4", DirectionalSynchronizerMasterScenes::directional_synchronizer_master_p2);
    }

    @Override
    public void registerSharedText(SharedTextRegistrationHelper helper) {
        // TODO: 如有 shared_text 步骤，在此注册共享文本
    }
}
