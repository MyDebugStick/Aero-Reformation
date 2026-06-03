package dev.simulated_team.aero_reformation.content.blocks.power;

import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.mojang.serialization.MapCodec;

public class EndRodSeatBlock extends PowerBlock {

    public static final MapCodec<EndRodSeatBlock> END_ROD_CODEC = simpleCodec(EndRodSeatBlock::new);

    private static final VoxelShape END_ROD_SHAPE = Shapes.or(
            box(7, 0, 7, 9, 16, 9),
            box(6, 0, 6, 10, 2, 10)
    );

    public EndRodSeatBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() { return END_ROD_CODEC; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, net.minecraft.core.BlockPos pos, CollisionContext context) {
        return END_ROD_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, net.minecraft.core.BlockPos pos, CollisionContext context) {
        return END_ROD_SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, net.minecraft.core.BlockPos pos,
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
            seat.moveTo(pos.getX() + 0.5, pos.getY() + 16.0 / 16.0 + hOffset, pos.getZ() + 0.5, localYaw, 0);
            level.addFreshEntity(seat);
            seat.attachToSubLevel(level, pos);
            player.moveTo(seat.getX(), seat.getY() + seat.getPassengersRidingOffset(), seat.getZ(), localYaw, 0);
            player.startRiding(seat);
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
