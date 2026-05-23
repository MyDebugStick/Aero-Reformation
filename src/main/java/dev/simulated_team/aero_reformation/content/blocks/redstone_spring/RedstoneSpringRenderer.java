package dev.simulated_team.aero_reformation.content.blocks.redstone_spring;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public class RedstoneSpringRenderer extends KineticBlockEntityRenderer<RedstoneSpringBlockEntity> {

    public RedstoneSpringRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(RedstoneSpringBlockEntity be, float partialTicks, PoseStack ms,
                               MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        Direction facing = be.getBlockState().getValue(RedstoneSpringBlock.FACING);

        // 输入传动杆 (底部) - 随动力网络旋转
        SuperByteBuffer shaftIn = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF,
                be.getBlockState(), facing.getOpposite());
        kineticRotationTransform(shaftIn, be, facing.getAxis(),
                getAngleForBe(be, be.getBlockPos(), facing.getAxis()), light);
        shaftIn.renderInto(ms, buffer.getBuffer(RenderType.solid()));

        // 弹簧圈
        SuperByteBuffer spring = CachedBuffers.partial(SimPartialModels.TORSION_SPRING, be.getBlockState());
        float angle = be.interpolatedSpring(partialTicks);
        kineticRotationTransform(spring, be, facing.getAxis(), Mth.DEG_TO_RAD * angle, light);
        if (facing.getAxis().isHorizontal()) {
            spring.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())), Direction.UP);
        }
        spring.rotateCentered(AngleHelper.rad(-90 - AngleHelper.verticalAngle(facing)), Direction.EAST);
        spring.renderInto(ms, buffer.getBuffer(RenderType.solid()));

        // 输出传动杆 (顶部)
        SuperByteBuffer shaftOut = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF,
                be.getBlockState(), facing);
        kineticRotationTransform(shaftOut, be, facing.getAxis(),
                getAngleForBe(be.getExtraKinetics(), be.getBlockPos(), facing.getAxis()), light);
        shaftOut.renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    @Override
    protected SuperByteBuffer getRotatedModel(RedstoneSpringBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state,
                state.getValue(BearingBlock.FACING).getOpposite());
    }
}
