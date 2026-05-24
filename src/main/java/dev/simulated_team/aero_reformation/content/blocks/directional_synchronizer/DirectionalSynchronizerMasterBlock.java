package dev.simulated_team.aero_reformation.content.blocks.directional_synchronizer;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class DirectionalSynchronizerMasterBlock extends DirectionalBlock
        implements IBE<DirectionalSynchronizerMasterBlockEntity> {
    public static final MapCodec<DirectionalSynchronizerMasterBlock> CODEC = simpleCodec(DirectionalSynchronizerMasterBlock::new);

    private static final VoxelShape SHAPE = box(1, 1, 1, 15, 15, 15);

    public DirectionalSynchronizerMasterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Top face (special UV) faces the player
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(Items.REDSTONE_TORCH)) {
            if (!level.isClientSide()) {
                if (level.getBlockEntity(pos) instanceof DirectionalSynchronizerMasterBlockEntity be) {
                    be.toggleMirrorMode();
                    String key = be.isMirrorMode()
                            ? "aero_reformation.synchronizer.mirror_on"
                            : "aero_reformation.synchronizer.mirror_off";
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.8f,
                            be.isMirrorMode() ? 0.7f : 0.5f);
                    player.displayClientMessage(Component.translatable(key), true);
                    be.setChanged();
                }
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.is(AeroBlocks.DIRECTIONAL_SYNCHRONIZER_SLAVE_ITEM.get())) {
            if (!level.isClientSide()) {
                // Bind the slave item to this master position
                stack.set(AeroDataComponents.BOUND_MASTER.get(), pos);
                level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5f, 1.5f);
                player.displayClientMessage(
                        Component.translatable("aero_reformation.synchronizer.bound"), true);
            }
            return ItemInteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public Class<DirectionalSynchronizerMasterBlockEntity> getBlockEntityClass() {
        return DirectionalSynchronizerMasterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DirectionalSynchronizerMasterBlockEntity> getBlockEntityType() {
        return AeroBlocks.DIRECTIONAL_SYNCHRONIZER_MASTER_BE.get();
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type == AeroBlocks.DIRECTIONAL_SYNCHRONIZER_MASTER_BE.get()) {
            return (lvl, pos, st, be) -> DirectionalSynchronizerMasterBlockEntity.tick(lvl, pos, st,
                    (DirectionalSynchronizerMasterBlockEntity) be);
        }
        return null;
    }
}
