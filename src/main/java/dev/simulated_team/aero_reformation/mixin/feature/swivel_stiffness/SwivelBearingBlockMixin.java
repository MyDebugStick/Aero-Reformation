package dev.simulated_team.aero_reformation.mixin.feature.swivel_stiffness;

import dev.simulated_team.aero_reformation.feature.swivel_stiffness.ISwivelStiffnessAccessor;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
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

/**
 * Handles iron ingot right-click on SwivelBearingBlock to cycle stiffness levels.
 * Kept separate from the NavTable mixin for clean feature management.
 */
@Mixin(value = SwivelBearingBlock.class, remap = false, priority = 500)
public class SwivelBearingBlockMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, remap = false)
    private void aero_reformation$onUseItemOn(ItemStack itemStack, BlockState blockState, Level level,
                                               BlockPos blockPos, Player player, InteractionHand interactionHand,
                                               BlockHitResult blockHitResult,
                                               CallbackInfoReturnable<ItemInteractionResult> cir) {
        // Only intercept iron ingot right-click
        if (!itemStack.is(Items.IRON_INGOT)) {
            return;
        }

        if (level.isClientSide()) {
            cir.setReturnValue(ItemInteractionResult.SUCCESS);
            return;
        }

        if (level.getBlockEntity(blockPos) instanceof SwivelBearingBlockEntity be
                && be instanceof ISwivelStiffnessAccessor accessor) {

            accessor.aero_reformation$cycleStiffnessLevel();
            int stiffnessLevel = accessor.aero_reformation$getStiffnessLevel();

            // Visual & audio feedback
            float pitch = 0.5f + stiffnessLevel * 0.2f; // Higher pitch for higher levels
            level.playSound(null, blockPos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, pitch);

            // Chat message
            String langKey = "aero_reformation.swivel.stiffness_" + stiffnessLevel;
            player.displayClientMessage(Component.translatable(langKey), true);

            be.setChanged();
            be.sendData();
        }

        cir.setReturnValue(ItemInteractionResult.SUCCESS);
    }
}
