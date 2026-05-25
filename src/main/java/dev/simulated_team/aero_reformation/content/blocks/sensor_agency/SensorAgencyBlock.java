package dev.simulated_team.aero_reformation.content.blocks.sensor_agency;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

public class SensorAgencyBlock extends HorizontalDirectionalBlock implements IBE<SensorAgencyBlockEntity> {
    public static final MapCodec<SensorAgencyBlock> CODEC = simpleCodec(SensorAgencyBlock::new);

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // Base is centered/symmetric, poles on the back side
        VoxelShape base = box(2, 0, 2, 14, 4, 14);

        SHAPES.put(Direction.NORTH, Shapes.or(base,
                box(5, 0, 14, 6, 10, 15),
                box(11, 0, 14, 12, 11, 15)));

        SHAPES.put(Direction.EAST, Shapes.or(base,
                box(1, 0, 5, 2, 10, 6),
                box(1, 0, 11, 2, 11, 12)));

        SHAPES.put(Direction.SOUTH, Shapes.or(base,
                box(10, 0, 1, 11, 10, 2),
                box(4, 0, 1, 5, 11, 2)));

        SHAPES.put(Direction.WEST, Shapes.or(base,
                box(14, 0, 10, 15, 10, 11),
                box(14, 0, 4, 15, 11, 5)));
    }

    public SensorAgencyBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.NORTH));
    }

    // ─── BlockEntity ───

    @Override
    public Class<SensorAgencyBlockEntity> getBlockEntityClass() {
        return SensorAgencyBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SensorAgencyBlockEntity> getBlockEntityType() {
        return dev.simulated_team.aero_reformation.registrate.AeroBlocks.SENSOR_AGENCY_BE.get();
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type == getBlockEntityType()) {
            @SuppressWarnings("unchecked")
            BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<SensorAgencyBlockEntity>)
                    (l, p, s, be) -> SensorAgencyBlockEntity.tick(l, p, s, be);
            return ticker;
        }
        return null;
    }

    // ─── Pass binding from item to BE on place ───

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof SensorAgencyBlockEntity be) {
            SensorBinding binding = stack.getOrDefault(AeroDataComponents.SENSOR_BINDING.get(), SensorBinding.EMPTY);
            be.setBinding(binding);
            // Claim all bound sensors
            if (!level.isClientSide()) {
                for (BlockPos sp : binding.altitude()) SensorProxyData.claimSensor(sp, pos);
                for (BlockPos sp : binding.velocity()) SensorProxyData.claimSensor(sp, pos);
                for (BlockPos sp : binding.gimbal()) SensorProxyData.claimSensor(sp, pos);
                for (BlockPos sp : binding.nav()) SensorProxyData.claimSensor(sp, pos);
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof SensorAgencyBlockEntity be) {
                for (BlockPos sp : be.getBinding().altitude()) { SensorProxyData.releaseSensor(sp); SensorAgencyBlockEntity.triggerUpdate(level, sp); }
                for (BlockPos sp : be.getBinding().velocity()) { SensorProxyData.releaseSensor(sp); SensorAgencyBlockEntity.triggerUpdate(level, sp); }
                for (BlockPos sp : be.getBinding().gimbal()) { SensorProxyData.releaseSensor(sp); SensorAgencyBlockEntity.triggerUpdate(level, sp); }
                for (BlockPos sp : be.getBinding().nav()) { SensorProxyData.releaseSensor(sp); SensorAgencyBlockEntity.triggerUpdate(level, sp); }
                // Drop compass
                ItemStack compass = be.config.compassSlot.getItem(0);
                if (!compass.isEmpty()) {
                    net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), compass);
                    be.config.compassSlot.setItem(0, ItemStack.EMPTY);
                }
            }
            SensorProxyData.releaseAgency(pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof SensorAgencyBlockEntity be) {
            var nti = dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.NavigationTarget
                    .ofStack(stack);
            if (nti != null) {
                if (!level.isClientSide()) {
                    ItemStack existing = be.config.compassSlot.getItem(0);
                    if (!existing.isEmpty()) {
                        if (!player.getInventory().add(existing)) {
                            player.drop(existing, false);
                        }
                    }
                    be.config.compassSlot.setItem(0, stack.copyWithCount(1));
                    stack.shrink(1);
                    be.saveConfig();
                    level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.BLOCKS, 0.5f, 1.0f);
                }
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SensorAgencyBlockEntity be) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer((ServerPlayer) player,
                    dev.simulated_team.aero_reformation.network.SensorAgencyConfigPacket.fromConfig(pos, be.config));
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new SensorAgencyMenu(id, inv, be, be.config),
                    Component.translatable("block.aero_reformation.sensor_agency")),
                    buf -> { buf.writeBlockPos(pos); });
        }
        return InteractionResult.SUCCESS;
    }
}
