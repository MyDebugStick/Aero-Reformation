package dev.simulated_team.aero_reformation.content.blocks.filter_patch;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Positions the filter slot at the center of the filter patch's outward face.
 * The patch is a 6×6 px panel — the entire surface is the filter interaction zone.
 */
public class FilterPatchSlotPositioning extends ValueBoxTransform {

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(DirectionalBlock.FACING);
        // Position at the outward face of the 6×6 panel (1 px = 0.0625 thick).
        // Panel model: NORTH z=0~1, SOUTH z=15~16, WEST x=0~1, EAST x=15~16,
        //              UP y=15~16, DOWN y=0~1
        return switch (facing) {
            case NORTH -> new Vec3(0.5, 0.6875, 0.04);
            case SOUTH -> new Vec3(0.5, 0.6875, 0.96);
            case WEST  -> new Vec3(0.04, 0.6875, 0.5);
            case EAST  -> new Vec3(0.96, 0.6875, 0.5);
            case UP    -> new Vec3(0.5, 0.96, 0.5);
            case DOWN  -> new Vec3(0.5, 0.04, 0.5);
        };
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        Direction facing = state.getValue(DirectionalBlock.FACING);
        float yRot = AngleHelper.horizontalAngle(facing);
        float xRot = facing == Direction.UP ? 270 : facing == Direction.DOWN ? 90 : 0;
        TransformStack.of(ms).rotateYDegrees(yRot).rotateXDegrees(xRot);
    }

    @Override
    public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
        return true; // The entire panel surface is the filter slot
    }

    @Override
    public float getScale() {
        return 0.375f; // 6/16 — matches the 6×6 px panel
    }
}
