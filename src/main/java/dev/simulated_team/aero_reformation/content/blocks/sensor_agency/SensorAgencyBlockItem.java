package dev.simulated_team.aero_reformation.content.blocks.sensor_agency;

import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class SensorAgencyBlockItem extends BlockItem {
    public SensorAgencyBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        SensorBinding binding = stack.getOrDefault(AeroDataComponents.SENSOR_BINDING.get(), SensorBinding.EMPTY);
        return !binding.isEmpty();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        Block clickedBlock = level.getBlockState(pos).getBlock();
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(clickedBlock);

        SensorBinding binding = stack.getOrDefault(AeroDataComponents.SENSOR_BINDING.get(), SensorBinding.EMPTY);
        SensorBinding newBinding = null;

        if (id.equals(ResourceLocation.parse("simulated:altitude_sensor"))) {
            newBinding = binding.withAltitude(pos);
        } else if (id.equals(ResourceLocation.parse("simulated:velocity_sensor"))) {
            newBinding = binding.withVelocity(pos);
        } else if (id.equals(ResourceLocation.parse("simulated:gimbal_sensor"))) {
            newBinding = binding.withGimbal(pos);
        } else if (id.equals(ResourceLocation.parse("simulated:navigation_table"))) {
            newBinding = binding.withNav(pos);
        }

        if (newBinding != null) {
            if (!level.isClientSide()) {
                // Check if sensor already bound to another agency
                if (SensorProxyData.isSensorBound(pos)) {
                    player.displayClientMessage(
                            Component.translatable("aero_reformation.sensor_agency.already_bound"), true);
                    return InteractionResult.FAIL;
                }
                stack.set(AeroDataComponents.SENSOR_BINDING.get(), newBinding);
                player.setItemInHand(context.getHand(), stack);
                level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.5f);
                player.displayClientMessage(
                        Component.translatable("aero_reformation.sensor_agency.bind",
                                Component.translatable(clickedBlock.getDescriptionId())), true);
            }
            return InteractionResult.SUCCESS;
        }

        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            SensorBinding binding = stack.getOrDefault(AeroDataComponents.SENSOR_BINDING.get(), SensorBinding.EMPTY);
            if (!binding.isEmpty()) {
                if (!level.isClientSide()) {
                    stack.remove(AeroDataComponents.SENSOR_BINDING.get());
                    level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.0f);
                    player.displayClientMessage(
                            Component.translatable("aero_reformation.sensor_agency.clear"), true);
                }
                return InteractionResultHolder.success(stack);
            }
        }

        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("aero_reformation.tooltip.sensor_agency"));
        SensorBinding binding = stack.getOrDefault(AeroDataComponents.SENSOR_BINDING.get(), SensorBinding.EMPTY);
        if (binding.isEmpty()) {
            tooltip.add(Component.translatable("aero_reformation.sensor_agency.no_bindings"));
        } else {
            for (BlockPos pos : binding.altitude()) {
                tooltip.add(Component.translatable("aero_reformation.sensor_agency.bound_altitude",
                        pos.getX(), pos.getY(), pos.getZ()));
            }
            for (BlockPos pos : binding.velocity()) {
                tooltip.add(Component.translatable("aero_reformation.sensor_agency.bound_velocity",
                        pos.getX(), pos.getY(), pos.getZ()));
            }
            for (BlockPos pos : binding.gimbal()) {
                tooltip.add(Component.translatable("aero_reformation.sensor_agency.bound_gimbal",
                        pos.getX(), pos.getY(), pos.getZ()));
            }
            for (BlockPos pos : binding.nav()) {
                tooltip.add(Component.translatable("aero_reformation.sensor_agency.bound_nav",
                        pos.getX(), pos.getY(), pos.getZ()));
            }
        }
    }
}
