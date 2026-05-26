package dev.simulated_team.aero_reformation.event;

import dev.simulated_team.aero_reformation.content.blocks.redstone_spring.RedstoneSpringBlock;
import dev.simulated_team.aero_reformation.content.blocks.redstone_spring.RedstoneSpringBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import static dev.simulated_team.aero_reformation.AeroReformation.MODID;

@EventBusSubscriber(modid = MODID)
public class RedstoneSpringEventHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof RedstoneSpringBlock)) return;
        if (!event.getItemStack().is(Items.REDSTONE_TORCH)) return;
        if (event.getLevel().isClientSide()) return;

        if (event.getLevel().getBlockEntity(event.getPos()) instanceof RedstoneSpringBlockEntity be) {
            event.setCanceled(true);
            be.toggleBidirectional();
            boolean on = be.isBidirectional();
            event.getLevel().playSound(null, event.getPos(),
                    SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.8f, on ? 0.7f : 0.5f);
            event.getEntity().displayClientMessage(Component.translatable(
                    on ? "aero_reformation.redstone_spring.bidirectional_on"
                            : "aero_reformation.redstone_spring.bidirectional_off"), true);
            be.sendData();
            event.getLevel().updateNeighborsAt(event.getPos(),
                    event.getLevel().getBlockState(event.getPos()).getBlock());
        }
    }
}
