package dev.simulated_team.aero_reformation.content.blocks.rcs_thruster;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class RcsThrusterBlock extends Block implements IBE<RcsThrusterBlockEntity> {

    public static final DirectionProperty FACING = DirectionProperty.create("facing");
    public static final MapCodec<RcsThrusterBlock> CODEC = simpleCodec(RcsThrusterBlock::new);

    private static final double[] ANGLED_MODES = {1.0, 0.5, 0.25, 0.1, 0.05, 0.02};

    public RcsThrusterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.block();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(Items.REDSTONE_TORCH)) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RcsThrusterBlockEntity rcs) {
                rcs.cycleAngledMode();
                double val = ANGLED_MODES[rcs.getAngledMode() % ANGLED_MODES.length];
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.6f,
                        (float)(0.5 + val * 0.5));
                player.displayClientMessage(
                        Component.translatable("aero_reformation.rcs_thruster.angled_mode", String.format("%.0f", val * 100) + "%"),
                        true);
            }
            return ItemInteractionResult.SUCCESS;
        }

        // Creative Blaze Cake: enable infinite fuel mode
        if (stack.getItem() == net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(net.minecraft.resources.ResourceLocation.parse("create:creative_blaze_cake"))) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RcsThrusterBlockEntity rcs) {
                if (!rcs.isCreativeMode()) {
                    rcs.setCreativeMode(true);
                    stack.shrink(1);
                    level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.3f, 1.5f);
                    player.displayClientMessage(
                            Component.translatable("aero_reformation.rcs_thruster.creative_mode"), true);
                }
            }
            return ItemInteractionResult.SUCCESS;
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    // === IBE ===

    @Override
    public Class<RcsThrusterBlockEntity> getBlockEntityClass() {
        return RcsThrusterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RcsThrusterBlockEntity> getBlockEntityType() {
        return AeroBlocks.RCS_THRUSTER_BE.get();
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                    BlockEntityType<T> type) {
        return type == AeroBlocks.RCS_THRUSTER_BE.get()
                ? (l, p, s, be) -> ((RcsThrusterBlockEntity) be).tick()
                : null;
    }
}
