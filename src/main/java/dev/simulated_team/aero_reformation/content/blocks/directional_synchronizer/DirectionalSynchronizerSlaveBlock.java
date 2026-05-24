package dev.simulated_team.aero_reformation.content.blocks.directional_synchronizer;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class DirectionalSynchronizerSlaveBlock extends Block implements IBE<DirectionalSynchronizerSlaveBlockEntity> {
    public static final MapCodec<DirectionalSynchronizerSlaveBlock> CODEC = simpleCodec(DirectionalSynchronizerSlaveBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape SHAPE = box(1, 1, 1, 15, 15, 15);

    public DirectionalSynchronizerSlaveBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockPos bound = stack.get(AeroDataComponents.BOUND_MASTER.get());
        if (bound != null && level.getBlockEntity(pos) instanceof DirectionalSynchronizerSlaveBlockEntity be) {
            be.bindTo(bound);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public boolean isSignalSource(BlockState state) { return true; }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!(level instanceof Level l)) return 0;
        if (!(l.getBlockEntity(pos) instanceof DirectionalSynchronizerSlaveBlockEntity be)) return 0;
        return be.getPower(direction);
    }

    @Override
    public Class<DirectionalSynchronizerSlaveBlockEntity> getBlockEntityClass() {
        return DirectionalSynchronizerSlaveBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DirectionalSynchronizerSlaveBlockEntity> getBlockEntityType() {
        return AeroBlocks.DIRECTIONAL_SYNCHRONIZER_SLAVE_BE.get();
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type == AeroBlocks.DIRECTIONAL_SYNCHRONIZER_SLAVE_BE.get()) {
            return (lvl, pos, st, be) -> DirectionalSynchronizerSlaveBlockEntity.tick(lvl, pos, st,
                    (DirectionalSynchronizerSlaveBlockEntity) be);
        }
        return null;
    }
}
