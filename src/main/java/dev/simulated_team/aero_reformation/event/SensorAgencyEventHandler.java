package dev.simulated_team.aero_reformation.event;

import dev.simulated_team.aero_reformation.content.blocks.sensor_agency.SensorBinding;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import static dev.simulated_team.aero_reformation.AeroReformation.MODID;

@EventBusSubscriber(modid = MODID)
public class SensorAgencyEventHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(AeroBlocks.SENSOR_AGENCY_ITEM.get())) return;

        Block clickedBlock = event.getLevel().getBlockState(event.getPos()).getBlock();
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(clickedBlock);

        SensorBinding binding = stack.getOrDefault(AeroDataComponents.SENSOR_BINDING.get(), SensorBinding.EMPTY);
        SensorBinding newBinding = null;

        if (id.equals(ResourceLocation.parse("simulated:altitude_sensor"))) {
            newBinding = binding.withAltitude(event.getPos());
        } else if (id.equals(ResourceLocation.parse("simulated:velocity_sensor"))) {
            newBinding = binding.withVelocity(event.getPos());
        } else if (id.equals(ResourceLocation.parse("simulated:gimbal_sensor"))) {
            newBinding = binding.withGimbal(event.getPos());
        } else if (id.equals(ResourceLocation.parse("simulated:navigation_table"))) {
            newBinding = binding.withNav(event.getPos());
        }

        if (newBinding != null) {
            if (!event.getLevel().isClientSide()) {
                stack.set(AeroDataComponents.SENSOR_BINDING.get(), newBinding);
                event.getEntity().setItemInHand(event.getHand(), stack);
                event.getLevel().playSound(null, event.getPos(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.5f);
                event.getEntity().displayClientMessage(
                        Component.translatable("aero_reformation.sensor_agency.bind",
                                Component.translatable(clickedBlock.getDescriptionId())), true);
            }
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
