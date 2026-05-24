package dev.simulated_team.aero_reformation.mixin.feature.analog_mode;

import dev.simulated_team.aero_reformation.feature.analog_mode.IAnalogModeAccessor;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionBlock;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AnalogTransmissionBlock.class, remap = false)
public class AnalogTransmissionBlockMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void aero_reformation$onUseItemOn(ItemStack stack, BlockState state, Level level,
                                               BlockPos pos, Player player, InteractionHand hand,
                                               BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (!stack.is(Items.REDSTONE_TORCH)) return;
        if (level.isClientSide()) {
            cir.setReturnValue(ItemInteractionResult.SUCCESS);
            return;
        }

        if (level.getBlockEntity(pos) instanceof AnalogTransmissionBlockEntity be
                && be instanceof IAnalogModeAccessor a) {
            a.aero_reformation$toggleLinearMode();
            boolean on = a.aero_reformation$isLinearMode();
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(),
                    SoundSource.BLOCKS, 0.8f, on ? 0.6f : 0.5f);
            player.displayClientMessage(Component.translatable(
                    on ? "aero_reformation.analog.linear_enabled"
                       : "aero_reformation.analog.linear_disabled"), true);
            be.setChanged();
            be.sendData();
        }
        cir.setReturnValue(ItemInteractionResult.SUCCESS);
    }
}
