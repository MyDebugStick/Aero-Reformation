package dev.simulated_team.aero_reformation.content.blocks.power;

import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Map;

import com.simibubi.create.foundation.block.IBE;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class PowerBlock extends Block implements IBE<PowerBlockEntity> {

    public static final MapCodec<PowerBlock> CODEC = simpleCodec(PowerBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Shapes.or(
            box(1, 0, 1, 3, 10, 3),
            box(2, 0, 12, 4, 10, 14),
            box(13, 0, 1, 15, 10, 3),
            box(12, 0, 12, 14, 10, 14),
            box(1, 10, 1, 15, 11, 14),
            box(1, 10, 2, 2, 14, 3),
            box(14, 10, 2, 15, 14, 3),
            box(1, 14, 3, 2, 15, 7),
            box(14, 14, 3, 15, 15, 7),
            box(1, 15, 10, 2, 20, 11),
            box(14, 15, 10, 15, 20, 11),
            box(1, 15, 7, 2, 16, 9),
            box(14, 15, 7, 15, 16, 9),
            box(0, 20, 12, 1, 21, 14),
            box(5, 21, 14, 11, 23, 15)
    );

    // Per-facing outlines (rotated to match model)
    private static final VoxelShape OUTLINE_NORTH = box(1, 0, 1, 15, 22, 14);
    private static final VoxelShape OUTLINE_EAST  = box(2, 0, 1, 15, 22, 15);
    private static final VoxelShape OUTLINE_SOUTH = box(1, 0, 2, 15, 22, 15);
    private static final VoxelShape OUTLINE_WEST  = box(1, 0, 1, 14, 22, 15);
    private static final Map<Direction, VoxelShape> OUTLINES = Map.of(
            Direction.NORTH, OUTLINE_NORTH,
            Direction.EAST, OUTLINE_EAST,
            Direction.SOUTH, OUTLINE_SOUTH,
            Direction.WEST, OUTLINE_WEST
    );

    public PowerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINES.getOrDefault(state.getValue(FACING), OUTLINE_NORTH);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            for (SeatEntity seat : level.getEntitiesOfClass(SeatEntity.class, new AABB(pos).inflate(1))) {
                seat.discard();
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (level instanceof Level lvl) {
            // Search wider area - after physics assembly the entity may be offset from the block
            var seats = lvl.getEntitiesOfClass(SeatEntity.class, new AABB(pos).inflate(4));
            for (SeatEntity seat : seats) {
                if (!seat.isVehicle() || !(seat.getPassengers().get(0) instanceof Player player)) continue;
                if (seat.distanceToSqr(pos.getCenter()) > 4) continue;

                if (seat.isRedstoneDisabled()) return 0;

                if (seat.isCameraLocked() && seat.isRollLocked()) {
                    // RollLock: server-side Q-based signal (matches backup, prevents bounce)
                    float baseYaw = seat.getBaseYaw();
                    float yawDiff;
                    float pitchDiff;

                    var subLevel = Sable.HELPER.getContaining(lvl, pos);
                    if (subLevel != null) {
                        Quaterniond subRot = subLevel.logicalPose().orientation();
                        Vector3d worldRef = new Vector3d(0, 0, 1)
                                .rotateY(Math.toRadians(-baseYaw))
                                .rotate(subRot);
                        float yawRef = (float) Math.toDegrees(Math.atan2(-worldRef.x, worldRef.z));
                        float pitchRef = (float) Math.toDegrees(-Math.asin(worldRef.y));
                        yawDiff = Mth.wrapDegrees(player.getYRot() - yawRef);
                        pitchDiff = player.getXRot() - pitchRef;
                    } else {
                        yawDiff = Mth.wrapDegrees(player.getYRot() - baseYaw);
                        pitchDiff = player.getXRot();
                    }

                    float yawSignal, pitchSignal;
                    if (lvl.getBlockEntity(pos) instanceof PowerBlockEntity be) {
                        yawSignal = Mth.clamp(Math.abs(yawDiff) / be.getYawMax() * 15f, 0, 15);
                        pitchSignal = Mth.clamp(Math.abs(pitchDiff) / be.getPitchMax() * 15f, 0, 15);
                    } else {
                        yawSignal = Mth.clamp(Math.abs(yawDiff) / 90f * 15f, 0, 15);
                        pitchSignal = Mth.clamp(Math.abs(pitchDiff) / 45f * 15f, 0, 15);
                    }

                    Direction right = state.getValue(FACING).getClockWise();
                    Direction left = right.getOpposite();
                    if (direction == right && yawDiff < 0) return (int) yawSignal;
                    if (direction == left && yawDiff > 0) return (int) yawSignal;
                    if (direction == state.getValue(FACING).getOpposite() && pitchDiff > 0) return (int) pitchSignal;
                    if (direction == state.getValue(FACING) && pitchDiff < 0) return (int) pitchSignal;
                    return 0;
                }

                if (seat.isCameraLocked()) {
                    Direction right = state.getValue(FACING).getClockWise();
                    Direction left = right.getOpposite();
                    Direction back = state.getValue(FACING).getOpposite();
                    Direction fwd = state.getValue(FACING);
                    if (direction == right) return seat.sigRight;
                    if (direction == left) return seat.sigLeft;
                    if (direction == back) return seat.sigBack;
                    if (direction == fwd) return seat.sigFwd;
                    return 0;
                }

                float baseYaw = seat.getBaseYaw();
                float yawDiff = Mth.wrapDegrees(player.getYRot() - baseYaw);
                float pitchDiff = player.getXRot();

                float yawSignal;
                float pitchSignal;
                if (lvl.getBlockEntity(pos) instanceof PowerBlockEntity be) {
                    yawSignal = Mth.clamp(Math.abs(yawDiff) / be.getYawMax() * 15f, 0, 15);
                    pitchSignal = Mth.clamp(Math.abs(pitchDiff) / be.getPitchMax() * 15f, 0, 15);
                } else {
                    yawSignal = Mth.clamp(Math.abs(yawDiff) / 90f * 15f, 0, 15);
                    pitchSignal = Mth.clamp(Math.abs(pitchDiff) / 45f * 15f, 0, 15);
                }

                Direction right = state.getValue(FACING).getClockWise();
                Direction left = right.getOpposite();

                if (direction == right && yawDiff < 0) return (int) yawSignal;
                if (direction == left && yawDiff > 0) return (int) yawSignal;
                if (direction == state.getValue(FACING).getOpposite() && pitchDiff > 0) return (int) pitchSignal;
                if (direction == state.getValue(FACING) && pitchDiff < 0) return (int) pitchSignal;
                return 0;
            }
        }
        return 0;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            if (player.isShiftKeyDown()) {
                PowerGuiOpener.open(pos, level);
                return ItemInteractionResult.SUCCESS;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (player.isShiftKeyDown()) return ItemInteractionResult.SUCCESS;

        if (player.isPassenger()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (!level.getEntitiesOfClass(SeatEntity.class,
                new AABB(pos.getCenter().subtract(0.5, 0.5, 0.5), pos.getCenter().add(0.5, 0.5, 0.5))).isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // Remove any stale seat entities nearby (e.g. after physics assembly)
        for (SeatEntity old : level.getEntitiesOfClass(SeatEntity.class, new AABB(pos).inflate(4))) {
            old.discard();
        }

        SeatEntity seat = AeroBlocks.SEAT_ENTITY_TYPE.get().create(level);
        if (seat != null) {
            Direction facing = state.getValue(FACING);
            float localYaw = facing.toYRot();
            seat.setBlockPos(pos);
            seat.setBaseYaw(localYaw);

            double hOffset = level.getBlockEntity(pos) instanceof PowerBlockEntity be ? be.getSeatHeight() : 0.0;
            seat.moveTo(pos.getX() + 0.5, pos.getY() + 11.0 / 16.0 + hOffset, pos.getZ() + 0.5, localYaw, 0);
            level.addFreshEntity(seat);
            // Move player to riding position before mounting to avoid arm shake
            player.moveTo(seat.getX(), seat.getY() + seat.getPassengersRidingOffset(), seat.getZ(), localYaw, 0);
            player.startRiding(seat);
            player.setYRot(localYaw);
            player.yRotO = localYaw;
            player.setYHeadRot(localYaw);
            player.yHeadRotO = localYaw;
            seat.attachToSubLevel(level, pos);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public Class<PowerBlockEntity> getBlockEntityClass() {
        return PowerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PowerBlockEntity> getBlockEntityType() {
        return AeroBlocks.POWER_BE.get();
    }
}
