package dev.simulated_team.aero_reformation.content.blocks.filter_patch;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Filter Patch block — a 6×6 flat panel that attaches to any face of a container block.
 * <p>
 * Interaction is handled entirely by Create's {@code FilteringBehaviour} (via
 * {@code ValueSettingsInputHandler}), matching the Brass Funnel pattern:
 * <ul>
 *   <li>Right-click with any item → set as filter</li>
 *   <li>Right-click with item while already filtered → swap filters</li>
 *   <li>Right-click with empty hand → retrieve the filter</li>
 * </ul>
 * Visual feedback (item rendering + selection outline) is handled by
 * {@code FilteringRenderer}.
 */
public class FilterPatchBlock extends DirectionalBlock implements IBE<FilterPatchBlockEntity> {
    public static final MapCodec<FilterPatchBlock> CODEC = simpleCodec(FilterPatchBlock::new);

    // Wall: 6×6 px, 1 px thick, centered vertically (y=8~14, 2px below upper-center).
    // Floor/ceiling: 6×6 px, 1 px thick, centered.
    private static final VoxelShape SHAPE_NORTH = box(5, 8, 0, 11, 14, 1.5);
    private static final VoxelShape SHAPE_SOUTH = box(5, 8, 14.5, 11, 14, 16);
    private static final VoxelShape SHAPE_WEST  = box(0, 8, 5, 1.5, 14, 11);
    private static final VoxelShape SHAPE_EAST  = box(14.5, 8, 5, 16, 14, 11);
    private static final VoxelShape SHAPE_UP    = box(5, 14.5, 5, 11, 16, 11);
    private static final VoxelShape SHAPE_DOWN  = box(5, 0, 5, 11, 1.5, 11);

    public FilterPatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace().getOpposite());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level.isClientSide) return;
        Direction facing = state.getValue(FACING);
        BlockPos containerPos = pos.relative(facing);
        BlockEntity be = level.getBlockEntity(containerPos);
        if (be instanceof Container) return;
        if (level.getCapability(Capabilities.ItemHandler.BLOCK, containerPos, facing.getOpposite()) != null) return;

        // Invalid container — drop and show message
        level.destroyBlock(pos, true);
        Player nearest = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, false);
        if (nearest != null) {
            nearest.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("block.aero_reformation.filter_patch.invalid_target"),
                    true);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case EAST  -> SHAPE_EAST;
            case WEST  -> SHAPE_WEST;
            case UP    -> SHAPE_UP;
            case DOWN  -> SHAPE_DOWN;
        };
    }

    @Override
    public Class<FilterPatchBlockEntity> getBlockEntityClass() {
        return FilterPatchBlockEntity.class;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                    BlockPos neighborPos, boolean movedByPiston) {
        Direction facing = state.getValue(FACING);
        BlockPos containerPos = pos.relative(facing);
        if (!neighborPos.equals(containerPos)) return;

        BlockEntity be = level.getBlockEntity(containerPos);
        if (be instanceof Container) return;
        if (level.getCapability(Capabilities.ItemHandler.BLOCK, containerPos, facing.getOpposite()) != null) return;

        level.destroyBlock(pos, true);
    }

    @Override
    public BlockEntityType<? extends FilterPatchBlockEntity> getBlockEntityType() {
        return dev.simulated_team.aero_reformation.registrate.AeroBlocks.FILTER_PATCH_BE.get();
    }
}
