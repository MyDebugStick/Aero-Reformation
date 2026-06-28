package dev.simulated_team.aero_reformation.content.items.warhead_configurator;

import dev.simulated_team.aero_reformation.content.blocks.guidance_warhead.GuidanceWarheadBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WarheadConfiguratorItem extends Item {

    private static final String TAG_CONFIG = "WarheadConfig";

    public WarheadConfiguratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.aero_reformation.warhead_configurator.desc1")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.aero_reformation.warhead_configurator.desc2")
                .withStyle(ChatFormatting.AQUA));

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(TAG_CONFIG)) {
            tooltip.add(Component.translatable("tooltip.aero_reformation.warhead_configurator.has_config")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("tooltip.aero_reformation.warhead_configurator.empty")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        var be = level.getBlockEntity(context.getClickedPos());
        if (!(be instanceof GuidanceWarheadBlockEntity warhead)) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();

        if (player.isShiftKeyDown()) {
            // Copy: read all config from warhead into item
            if (level.isClientSide()) return InteractionResult.SUCCESS;

            CompoundTag cfg = new CompoundTag();
            cfg.putFloat("Kp", warhead.kp);
            cfg.putFloat("Ki", warhead.ki);
            cfg.putFloat("Kd", warhead.kd);
            cfg.putFloat("MaxSpeed", warhead.maxSpeed);
            cfg.putFloat("SidePower", warhead.sidePower);
            cfg.putFloat("MaxThrust", warhead.maxThrustPN);
            cfg.putFloat("CruiseAltitude", warhead.cruiseAltitude);
            cfg.putFloat("BrakeCoeff", warhead.brakeCoeff);
            cfg.putFloat("ProximityRange", warhead.proximityRange);
            cfg.putFloat("RedstoneRange", warhead.redstoneRange);
            cfg.putFloat("AltitudeOffset", warhead.altitudeOffset);
            cfg.putInt("SearchMode", warhead.searchMode);
            cfg.putFloat("MinSearchRange", warhead.minSearchRange);
            cfg.putFloat("MaxSearchRange", warhead.maxSearchRange);
            cfg.putDouble("ManualX", warhead.manualTargetX);
            cfg.putDouble("ManualY", warhead.manualTargetY);
            cfg.putDouble("ManualZ", warhead.manualTargetZ);
            if (warhead.boundMonitorPos != null) {
                cfg.putLong("BoundMonitor", warhead.boundMonitorPos.asLong());
            }

            CompoundTag outer = new CompoundTag();
            outer.put(TAG_CONFIG, cfg);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(outer));

            player.displayClientMessage(
                    Component.translatable("msg.aero_reformation.warhead_configurator.copied"), true);
            return InteractionResult.SUCCESS;
        } else {
            // Apply: write stored config to warhead
            if (level.isClientSide()) return InteractionResult.SUCCESS;

            CompoundTag outer = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (!outer.contains(TAG_CONFIG)) {
                player.displayClientMessage(
                        Component.translatable("msg.aero_reformation.warhead_configurator.no_config"), true);
                return InteractionResult.FAIL;
            }

            CompoundTag cfg = outer.getCompound(TAG_CONFIG);

            warhead.kp = cfg.getFloat("Kp");
            warhead.ki = cfg.getFloat("Ki");
            warhead.kd = cfg.getFloat("Kd");
            warhead.maxSpeed = cfg.getFloat("MaxSpeed");
            warhead.sidePower = cfg.getFloat("SidePower");
            warhead.maxThrustPN = cfg.getFloat("MaxThrust");
            warhead.cruiseAltitude = cfg.getFloat("CruiseAltitude");
            warhead.brakeCoeff = cfg.getFloat("BrakeCoeff");
            warhead.proximityRange = cfg.getFloat("ProximityRange");
            warhead.redstoneRange = cfg.getFloat("RedstoneRange");
            warhead.altitudeOffset = cfg.getFloat("AltitudeOffset");
            warhead.searchMode = cfg.getInt("SearchMode");
            warhead.minSearchRange = cfg.getFloat("MinSearchRange");
            warhead.maxSearchRange = cfg.getFloat("MaxSearchRange");
            warhead.manualTargetX = cfg.getDouble("ManualX");
            warhead.manualTargetY = cfg.getDouble("ManualY");
            warhead.manualTargetZ = cfg.getDouble("ManualZ");
            warhead.boundMonitorPos = cfg.contains("BoundMonitor")
                    ? BlockPos.of(cfg.getLong("BoundMonitor")) : null;
            warhead.unlockTarget();
            warhead.setChanged();

            player.displayClientMessage(
                    Component.translatable("msg.aero_reformation.warhead_configurator.applied"), true);
            return InteractionResult.SUCCESS;
        }
    }
}
