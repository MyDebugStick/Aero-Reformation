package dev.simulated_team.aero_reformation.event;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.simulated_team.aero_reformation.content.items.ender_compass.EnderArrowTracker;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;

import static dev.simulated_team.aero_reformation.AeroReformation.MODID;

/**
 * Renders a compass item in place of tracked compass arrows.
 * Replaces ArrowRendererMixin.
 */
@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class EnderArrowRenderHandler {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        PoseStack stack = event.getPoseStack();
        MultiBufferSource buffer = mc.renderBuffers().bufferSource();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        float partialTicks = event.getPartialTick().getGameTimeDeltaTicks();

        boolean hasArrow = false;
        for (int id : EnderArrowTracker.getActiveArrowIds()) {
            AbstractArrow arrow = EnderArrowTracker.getArrowRef(id);
            if (arrow == null) continue;
            String channel = EnderArrowTracker.getChannelById(id);
            if (channel == null) continue;
            hasArrow = true;

            // Interpolate position
            Vec3 pos = arrow.getPosition(partialTicks);
            Vec3 camPos = camera.getPosition();

            stack.pushPose();
            stack.translate(pos.x - camPos.x, pos.y - camPos.y + 0.25, pos.z - camPos.z);
            stack.scale(0.6f, 0.6f, 0.6f);
            stack.mulPose(Axis.YP.rotationDegrees(arrow.getYRot() + 90f));
            stack.mulPose(Axis.ZP.rotationDegrees(-90f));

            // Disable depth test so compass always renders on top of the arrow
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            ItemStack compass = new ItemStack(AeroBlocks.ENDER_COMPASS.get());
            itemRenderer.renderStatic(compass, ItemDisplayContext.FIXED,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    stack, buffer, arrow.level(), id);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);

            stack.popPose();
        }

        if (hasArrow) {
            ((net.minecraft.client.renderer.RenderBuffers) mc.renderBuffers()).bufferSource().endBatch();
        }
    }
}
