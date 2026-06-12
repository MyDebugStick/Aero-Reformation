package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * No-op renderer for AnchorMarkerEntity — invisible in world,
 * but Xaero's minimap reads entity positions independently of rendering.
 */
public class AnchorMarkerRenderer extends EntityRenderer<AnchorMarkerEntity> {

    public AnchorMarkerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(AnchorMarkerEntity entity) {
        return null;
    }

    @Override
    public boolean shouldRender(AnchorMarkerEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return false;
    }
}
