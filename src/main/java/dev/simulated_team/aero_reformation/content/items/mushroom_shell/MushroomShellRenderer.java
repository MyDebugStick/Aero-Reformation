package dev.simulated_team.aero_reformation.content.items.mushroom_shell;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import rbasamoyai.createbigcannons.index.CBCBlockPartials;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedProjectileBlock;

public class MushroomShellRenderer implements BlockEntityRenderer<FuzedBlockEntity> {

    public MushroomShellRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FuzedBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!blockEntity.hasFuze()) return;

        BlockState state = blockEntity.getBlockState();
        Direction facing = state.getValue(BlockStateProperties.FACING);
        if (state.getBlock() instanceof FuzedProjectileBlock<?, ?> fuzed && fuzed.isBaseFuze())
            facing = facing.getOpposite();

        SuperByteBuffer fuzeRender = CachedBuffers.partialFacing(CBCBlockPartials.FUZE, state, facing);
        fuzeRender.light(packedLight).renderInto(poseStack, buffer.getBuffer(RenderType.cutout()));
    }
}
