package dev.simulated_team.aero_reformation.content.blocks.electric_loadstone;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.content.items.ender_compass.EnderCompassData;
import dev.simulated_team.aero_reformation.event.EnderArrowHandler;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class ElectricLoadstoneBlock extends Block implements IBE<ElectricLoadstoneBlockEntity> {

    public static final MapCodec<ElectricLoadstoneBlock> CODEC = simpleCodec(ElectricLoadstoneBlock::new);

    public ElectricLoadstoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public Class<ElectricLoadstoneBlockEntity> getBlockEntityClass() {
        return ElectricLoadstoneBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ElectricLoadstoneBlockEntity> getBlockEntityType() {
        return AeroBlocks.ELECTRIC_LOADSTONE_BE.get();
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                    BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == AeroBlocks.ELECTRIC_LOADSTONE_BE.get()
                ? (l, p, s, be) -> ElectricLoadstoneBlockEntity.tick(l, p, s, (ElectricLoadstoneBlockEntity) be)
                : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                               BlockPos pos, Player player, InteractionHand hand,
                                               BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ElectricLoadstoneBlockEntity loadstone))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        ItemStack held = loadstone.getHeldItem();

        // Show outline when holding a valid compass — respond SUCCESS on both sides
        if (stack.getItem() instanceof dev.simulated_team.aero_reformation.content.items.ender_compass.EnderCompassItem) {
            EnderCompassData data = stack.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
            if (!data.hasChannel()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!held.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (level.isClientSide()) return ItemInteractionResult.SUCCESS;

            ItemStack toPlace = stack.split(1);
            loadstone.setHeldItem(toPlace);
            return ItemInteractionResult.SUCCESS;
        }

        // Empty hand: take the compass off (show outline when there's something to take)
        if (stack.isEmpty()) {
            if (held.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (level.isClientSide()) return ItemInteractionResult.SUCCESS;

            ItemStack toGive = held.copy();
            EnderArrowHandler.refreshCompassPublic(toGive);
            player.setItemInHand(hand, toGive);
            loadstone.setHeldItem(ItemStack.EMPTY);
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
