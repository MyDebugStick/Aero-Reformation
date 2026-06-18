package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PhysicsAnchorBlock extends Block implements IBE<PhysicsAnchorBlockEntity> {
    public static final MapCodec<PhysicsAnchorBlock> CODEC = simpleCodec(PhysicsAnchorBlock::new);

    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 16, 16);

    public PhysicsAnchorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        // Registration handled by BE.onLoad — BE isn't available yet during onPlace
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            AnchorChunkLoader.removeAnchor(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public Class<PhysicsAnchorBlockEntity> getBlockEntityClass() {
        return PhysicsAnchorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PhysicsAnchorBlockEntity> getBlockEntityType() {
        return dev.simulated_team.aero_reformation.registrate.AeroBlocks.PHYSICS_ANCHOR_BE.get();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            if (level.isClientSide()) {
                AnchorNamingGuiOpener.open(pos, level, player);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
