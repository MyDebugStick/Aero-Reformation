package dev.simulated_team.aero_reformation.content.blocks.filter_patch;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Minimal BER that delegates to Create's {@link FilteringRenderer} for filter item
 * rendering and selection outlines. All actual rendering logic is in Create's
 * {@code FilteringRenderer.renderOnBlockEntity()} and {@code FilteringRenderer.tick()}.
 */
public class FilterPatchRenderer implements BlockEntityRenderer<FilterPatchBlockEntity> {

    public FilterPatchRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(FilterPatchBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        FilteringRenderer.renderOnBlockEntity(be, partialTick, poseStack, buffer, packedLight, packedOverlay);
    }
}
