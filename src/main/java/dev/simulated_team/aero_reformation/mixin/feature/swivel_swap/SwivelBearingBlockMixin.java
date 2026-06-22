package dev.simulated_team.aero_reformation.mixin.feature.swivel_swap;

import dev.simulated_team.aero_reformation.feature.swivel_swap.ISwivelSwapAccessor;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SwivelBearingBlock.class, remap = false, priority = 500)
public class SwivelBearingBlockMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, remap = false)
    private void aero_reformation$onUseItemOn(ItemStack stack, BlockState state, Level level,
                                               BlockPos pos, Player player, InteractionHand hand,
                                               BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (!stack.is(Items.REDSTONE_TORCH)) return;
        if (level.isClientSide()) { cir.setReturnValue(ItemInteractionResult.SUCCESS); return; }

        if (level.getBlockEntity(pos) instanceof SwivelBearingBlockEntity be && be instanceof ISwivelSwapAccessor a) {
            a.aero_reformation$toggleSwapped();
            boolean on = a.aero_reformation$isSwapped();
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.8f, on ? 0.7f : 0.5f);
            player.displayClientMessage(Component.translatable(
                on ? "aero_reformation.swivel.swap_enabled" : "aero_reformation.swivel.swap_disabled"), true);
            be.setChanged();
            be.sendData();
        }
        cir.setReturnValue(ItemInteractionResult.SUCCESS);
    }

    /** When dual-drive is on, also allow shaft output on the facing direction. */
    @Inject(method = "hasShaftTowards", at = @At("RETURN"), cancellable = true)
    private void aero_reformation$dualDriveShaft(LevelReader world, BlockPos pos, BlockState state,
                                                  Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return; // Already true, skip
        if (world.getBlockEntity(pos) instanceof SwivelBearingBlockEntity be
                && be instanceof ISwivelSwapAccessor a && a.aero_reformation$isSwapped()) {
            Direction facing = state.getValue(SwivelBearingBlock.FACING);
            if (face == facing) cir.setReturnValue(true);
        }
    }
}
