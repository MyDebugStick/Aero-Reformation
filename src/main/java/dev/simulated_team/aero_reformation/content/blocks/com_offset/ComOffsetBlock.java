package dev.simulated_team.aero_reformation.content.blocks.com_offset;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.loading.FMLEnvironment;

public class ComOffsetBlock extends Block implements IBE<ComOffsetBlockEntity> {
    public static final MapCodec<ComOffsetBlock> CODEC = simpleCodec(ComOffsetBlock::new);

    public ComOffsetBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override public Class<ComOffsetBlockEntity> getBlockEntityClass() { return ComOffsetBlockEntity.class; }

    @Override public BlockEntityType<? extends ComOffsetBlockEntity> getBlockEntityType() {
        return dev.simulated_team.aero_reformation.registrate.AeroBlocks.COM_OFFSET_BE.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                               BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ComOffsetBlockEntity coe)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (level.isClientSide) {
            try {
                Class<?> screenClass = Class.forName("dev.simulated_team.aero_reformation.content.blocks.com_offset.ComOffsetScreen");
                var ctor = screenClass.getDeclaredConstructor(BlockPos.class, double.class, double.class, double.class);
                Object screen = ctor.newInstance(pos, coe.getComX(), coe.getComY(), coe.getComZ());
                net.minecraft.client.Minecraft.getInstance().setScreen((net.minecraft.client.gui.screens.Screen) screen);
            } catch (Exception ignored) {}
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
