package dev.simulated_team.aero_reformation.content.blocks.electric_loadstone;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class ElectricLoadstoneRenderer implements BlockEntityRenderer<ElectricLoadstoneBlockEntity> {

    public ElectricLoadstoneRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ElectricLoadstoneBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ItemStack held = be.getHeldItem();
        if (held.isEmpty()) return;

        poseStack.pushPose();
        poseStack.translate(0.5, 1.2, 0.5);
        poseStack.scale(0.7f, 0.7f, 0.7f);
        poseStack.mulPose(Axis.YP.rotationDegrees((be.getLevel().getGameTime() + partialTick) * 2f));
        Minecraft.getInstance().getItemRenderer().renderStatic(held, ItemDisplayContext.GROUND,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, poseStack, buffer, be.getLevel(), 0);
        poseStack.popPose();
    }
}
