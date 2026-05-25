package dev.simulated_team.aero_reformation.feature.gimbal_inverted;

import dev.simulated_team.aero_reformation.content.blocks.sensor_agency.SensorProxyData;
import dev.simulated_team.aero_reformation.feature.gimbal_inverted.IGimbalInvertedAccessor;
import dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlock;
import dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import static dev.simulated_team.aero_reformation.AeroReformation.MODID;

@EventBusSubscriber(modid = MODID)
public class GimbalSensorEventHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof GimbalSensorBlock)) return;
        if (!event.getItemStack().is(Items.REDSTONE_TORCH)) return;
        if (event.getLevel().isClientSide()) return;

        if (event.getLevel().getBlockEntity(event.getPos()) instanceof GimbalSensorBlockEntity be
                && be instanceof IGimbalInvertedAccessor a) {
            // Block toggle on sensors bound to an agency
            if (SensorProxyData.isSensorBound(event.getPos())) {
                event.setCanceled(true);
                return;
            }
            event.setCanceled(true);
            a.aero_reformation$toggleInverted();
            boolean on = a.aero_reformation$isInverted();
            event.getLevel().playSound(null, event.getPos(),
                    SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.8f, on ? 0.7f : 0.5f);
            event.getEntity().displayClientMessage(Component.translatable(
                    on ? "aero_reformation.gimbal.inverted_enable" : "aero_reformation.gimbal.inverted_disable"), true);
            be.setChanged();
            be.sendData();
            event.getLevel().updateNeighborsAt(event.getPos(),
                    event.getLevel().getBlockState(event.getPos()).getBlock());
        }
    }
}
