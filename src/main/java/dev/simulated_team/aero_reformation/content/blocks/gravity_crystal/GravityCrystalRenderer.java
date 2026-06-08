package dev.simulated_team.aero_reformation.content.blocks.gravity_crystal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Renders gravity crystal with wool-color tint based on redstone signal. */
public class GravityCrystalRenderer implements BlockEntityRenderer<GravityCrystalBlockEntity> {

    /** Wool colors 0-15, brightened (1.12+ palette × 1.3). */
    private static final int[] WOOL_COLORS = {
            0xFEFEFE, // White   (brightened)
            0xFCAA3D, // Orange
            0xDC6AD0, // Magenta
            0x4DC8EC, // Light Blue
            0xFEE85A, // Yellow
            0x9CE030, // Lime
            0xF9A8C0, // Pink
            0x5D676B, // Gray
            0xB9B9B2, // Light Gray
            0x1EB8B8, // Cyan
            0xA64AD0, // Purple
            0x5058C8, // Blue
            0x9E6A43, // Brown
            0x78A020, // Green
            0xD64234, // Red
            0x2A2A30  // Black
    };

    private final BlockRenderDispatcher blockRenderer;

    public GravityCrystalRenderer(BlockEntityRendererProvider.Context ctx) {
        this.blockRenderer = Minecraft.getInstance().getBlockRenderer();
    }

    @Override
    public void render(GravityCrystalBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        if (level == null) return;

        int signal = level.getBestNeighborSignal(pos);
        if (signal == 0) signal = level.getDirectSignalTo(pos);
        int colorIndex = Mth.clamp(signal, 0, 15);
        int tint = WOOL_COLORS[colorIndex];

        float r = ((tint >> 16) & 0xFF) / 255f;
        float g = ((tint >> 8) & 0xFF) / 255f;
        float b = (tint & 0xFF) / 255f;
        float alpha = 0.65f;

        // Minimum brightness ≈ blockLight 8
        int light = Math.max(packedLight, LightTexture.pack(8, 8));

        BlockState state = be.getBlockState();
        BakedModel model = blockRenderer.getBlockModel(state);

        // Pass 1: base opaque model
        VertexConsumer solid = buffer.getBuffer(RenderType.solid());
        renderModelQuads(pose, solid, model, state, 1f, 1f, 1f, 1f, light);

        // Pass 2: colored translucent overlay
        VertexConsumer vc = buffer.getBuffer(RenderType.translucent());
        renderModelQuads(pose, vc, model, state, r, g, b, alpha, light);
    }

    private void renderModelQuads(PoseStack pose, VertexConsumer vc, BakedModel model,
                                   BlockState state, float r, float g, float b, float a, int light) {
        RandomSource rand = RandomSource.create();
        for (Direction dir : Direction.values()) {
            for (var quad : model.getQuads(state, dir, rand)) {
                renderQuad(pose, vc, quad, r, g, b, a, light);
            }
        }
        for (var quad : model.getQuads(state, null, rand)) {
            renderQuad(pose, vc, quad, r, g, b, a, light);
        }
    }

    private void renderQuad(PoseStack pose, VertexConsumer vc,
                             net.minecraft.client.renderer.block.model.BakedQuad quad,
                             float r, float g, float b, float a, int light) {
        vc.putBulkData(pose.last(), quad, r, g, b, a, light, OverlayTexture.NO_OVERLAY);
    }
}
