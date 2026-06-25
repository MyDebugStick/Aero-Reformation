package dev.simulated_team.aero_reformation.content.blocks.guidance_warhead;

import com.mojang.serialization.MapCodec;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;

public class GuidanceWarheadBlock extends DirectionalBlock implements EntityBlock {
    public static final DirectionProperty FACING = DirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final MapCodec<GuidanceWarheadBlock> CODEC = simpleCodec(GuidanceWarheadBlock::new);

    // Warhead shape: 4-element stepped cone, base→tip along +Y (facing UP)
    // Main body 6×6×6 at Y=0~6, Ridge 4×4×1 at Y=6~7,
    // Tip 2×2×2 at Y=7~9, Antenna 1×1×1 at Y=9~10
    private static final VoxelShape SHAPE_UP = Shapes.or(
            box(5, 0, 5, 11, 6, 11),       // Main body
            box(6, 6, 6, 10, 7, 10),       // Ridge
            box(7, 7, 7, 9, 9, 9),         // Tip
            box(7.5, 9, 7.5, 8.5, 10, 8.5) // Antenna
    );

    private static final VoxelShape SHAPE_DOWN = Shapes.or(
            box(5, 10, 5, 11, 16, 11),
            box(6, 9, 6, 10, 10, 10),
            box(7, 7, 7, 9, 9, 9),
            box(7.5, 6, 7.5, 8.5, 7, 8.5)
    );

    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            box(5, 5, 10, 11, 11, 16),     // Main body (model Y→world -Z)
            box(6, 6, 9, 10, 10, 10),
            box(7, 7, 7, 9, 9, 9),
            box(7.5, 7.5, 6, 8.5, 8.5, 7)
    );

    private static final VoxelShape SHAPE_SOUTH = Shapes.or(
            box(5, 5, 0, 11, 11, 6),
            box(6, 6, 6, 10, 10, 7),
            box(7, 7, 7, 9, 9, 9),
            box(7.5, 7.5, 9, 8.5, 8.5, 10)
    );

    private static final VoxelShape SHAPE_EAST = Shapes.or(
            box(0, 5, 5, 6, 11, 11),
            box(6, 6, 6, 7, 10, 10),
            box(7, 7, 7, 9, 9, 9),
            box(9, 7.5, 7.5, 10, 8.5, 8.5)
    );

    private static final VoxelShape SHAPE_WEST = Shapes.or(
            box(10, 5, 5, 16, 11, 11),
            box(9, 6, 6, 10, 10, 10),
            box(7, 7, 7, 9, 9, 9),
            box(6, 7.5, 7.5, 7, 8.5, 8.5)
    );

    public GuidanceWarheadBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GuidanceWarheadBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case UP -> SHAPE_UP;
            case DOWN -> SHAPE_DOWN;
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
        };
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        // RCS Thruster item: bind this warhead to the RCS
        if (stack.is(AeroBlocks.RCS_THRUSTER_ITEM.get())) {
            // Cannot bind if already bound to a synchronizer
            if (stack.has(AeroDataComponents.BOUND_MASTER.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!level.isClientSide()) {
                // Clear any existing warhead binding first
                stack.remove(AeroDataComponents.BOUND_WARHEAD.get());
                stack.set(AeroDataComponents.BOUND_WARHEAD.get(), pos);
                level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5f, 1.5f);
                player.displayClientMessage(
                        Component.translatable("aero_reformation.rcs_thruster.bound_warhead"), true);
            }
            return ItemInteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof GuidanceWarheadBlockEntity warhead) {
            PacketDistributor.sendToPlayer((net.minecraft.server.level.ServerPlayer) player,
                    new GuidanceWarheadOpenPacket(pos, warhead.kp, warhead.ki, warhead.kd,
                            warhead.maxSpeed, warhead.sidePower, warhead.maxThrustPN,
                            warhead.brakeCoeff, warhead.proximityRange,
                            warhead.cruiseAltitude, warhead.redstoneRange, warhead.altitudeOffset,
                            warhead.searchMode, warhead.minSearchRange, warhead.maxSearchRange,
                            warhead.manualTargetX, warhead.manualTargetY, warhead.manualTargetZ));
        }
        return InteractionResult.SUCCESS;
    }
}
