package dev.simulated_team.aero_reformation.feature.torsion_redstone;

import dev.simulated_team.simulated.content.blocks.torsion_spring.TorsionSpringBlock;
import dev.simulated_team.simulated.content.blocks.torsion_spring.TorsionSpringBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import static dev.simulated_team.aero_reformation.AeroReformation.MODID;

@EventBusSubscriber(modid = MODID)
public class TorsionRedstoneHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof TorsionSpringBlock)) return;
        if (!event.getItemStack().is(Items.REDSTONE_TORCH)) return;
        if (event.getLevel().isClientSide()) return;

        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (!(be instanceof TorsionSpringBlockEntity tsbe)) return;

        if (!(tsbe.getExtraKinetics() instanceof ITorsionRedstoneAccessor accessor)) return;

        event.setCanceled(true);
        accessor.aero_reformation$toggleRedstoneMode();
        boolean on = accessor.aero_reformation$isRedstoneMode();

        event.getLevel().playSound(null, event.getPos(),
                SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.8f, on ? 0.7f : 0.5f);
        event.getEntity().displayClientMessage(Component.translatable(
                on ? "aero_reformation.torsion.redstone_enable" : "aero_reformation.torsion.redstone_disable"), true);

        tsbe.setChanged();
        tsbe.sendData();
        event.getLevel().updateNeighborsAt(event.getPos(),
                event.getLevel().getBlockState(event.getPos()).getBlock());
    }
}
