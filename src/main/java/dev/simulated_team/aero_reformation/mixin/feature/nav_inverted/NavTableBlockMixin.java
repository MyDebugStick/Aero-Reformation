package dev.simulated_team.aero_reformation.mixin.feature.nav_inverted;

import dev.simulated_team.aero_reformation.feature.nav_inverted.INavTableAccessor;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlock;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
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

@Mixin(value = NavTableBlock.class, remap = false)
public class NavTableBlockMixin {

    /**
     * Inject at the beginning of useItemOn to check for redstone torch right-click.
     * If the player is holding a redstone torch, toggle the inverted mode instead of
     * proceeding with normal item interaction.
     */
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void aero_reformation$onUseItemOn(ItemStack itemStack, BlockState blockState, Level level,
                                               BlockPos blockPos, Player player, InteractionHand interactionHand,
                                               BlockHitResult blockHitResult, CallbackInfoReturnable<ItemInteractionResult> cir) {
        // Check if player is holding a redstone torch
        if (itemStack.is(Items.REDSTONE_TORCH)) {
            if (!level.isClientSide()) {
                // Toggle inverted mode on the block entity
                if (level.getBlockEntity(blockPos) instanceof NavTableBlockEntity navBE
                        && navBE instanceof INavTableAccessor accessor) {
                    boolean currentState = accessor.aero_reformation$isInverted();
                    accessor.aero_reformation$toggleInverted();
                    boolean newState = accessor.aero_reformation$isInverted();

                    // Play feedback sound - higher pitch for enable, lower for disable
                    float pitch = newState ? 0.7f : 0.5f;
                    level.playSound(null, blockPos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.8f, pitch);

                    // Send chat message to the player
                    String key = newState ? "aero_reformation.nav_table.inverted_enable" : "aero_reformation.nav_table.inverted_disable";
                    player.displayClientMessage(Component.translatable(key), true);

                    // Send block entity update to sync redstone state with client
                    navBE.setChanged();
                    navBE.sendData();
                }
            }
            cir.setReturnValue(ItemInteractionResult.SUCCESS);
        }
    }
}
