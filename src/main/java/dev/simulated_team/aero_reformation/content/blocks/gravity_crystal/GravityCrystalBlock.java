package dev.simulated_team.aero_reformation.content.blocks.gravity_crystal;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

public class GravityCrystalBlock extends Block implements IBE<GravityCrystalBlockEntity> {
    public static final MapCodec<GravityCrystalBlock> CODEC = simpleCodec(GravityCrystalBlock::new);
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final BooleanProperty TRANSITION = BooleanProperty.create("transition");
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 16, 16);

    public GravityCrystalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(POWERED, false)
                .setValue(TRANSITION, false));
    }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

    @Override public RenderShape getRenderShape(BlockState s) {
        return s.getValue(POWERED) || s.getValue(TRANSITION) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED, TRANSITION);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean moved) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, moved);
        if (!level.isClientSide()) {
            boolean powered = level.hasNeighborSignal(pos);
            if (powered != state.getValue(POWERED)) {
                if (powered) {
                    // Power restored: clear transition, mark powered
                    level.setBlock(pos, state.setValue(POWERED, true).setValue(TRANSITION, false), 3);
                } else {
                    // Power lost: set white transition flag + schedule clear
                    level.setBlock(pos, state.setValue(POWERED, false).setValue(TRANSITION, true), 3);
                    level.scheduleTick(pos, this, 2);
                }
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(TRANSITION)) {
            level.setBlock(pos, state.setValue(TRANSITION, false), 3);
        }
    }

    @Override public Class<GravityCrystalBlockEntity> getBlockEntityClass() { return GravityCrystalBlockEntity.class; }

    @Override public BlockEntityType<? extends GravityCrystalBlockEntity> getBlockEntityType() {
        return dev.simulated_team.aero_reformation.registrate.AeroBlocks.GRAVITY_CRYSTAL_BE.get();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (!level.isClientSide()) {
            boolean powered = level.hasNeighborSignal(pos);
            if (powered != state.getValue(POWERED)) {
                level.setBlock(pos, state.setValue(POWERED, powered).setValue(TRANSITION, false), 3);
            }
            SubLevel sl = Sable.HELPER.getContaining(level, pos);
            if (sl != null) {
                GravityCrystalSettings.CRYSTAL_SUBLEVELS.add(sl.getUniqueId());
            }
        }
    }

    // onRemove: don't touch CRYSTAL_SUBLEVELS — detectApplyLift/detectPhysTick
    // will notice the material is gone and clean up automatically on next tick.

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide) {
            SubLevel sl = Sable.HELPER.getContaining(level, pos);
            if (sl == null) return InteractionResult.PASS;
            PacketDistributor.sendToServer(new GravityCrystalOpenPacket(sl.getUniqueId()));
        }
        return InteractionResult.SUCCESS;
    }
}
