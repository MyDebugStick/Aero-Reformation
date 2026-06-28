package dev.simulated_team.aero_reformation.content.blocks.guidance_warhead;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import dev.simulated_team.aero_reformation.compat.RadarCompat;

import java.util.List;

public class GuidanceWarheadBlockItem extends BlockItem {

    private static final String TAG_MONITOR = "boundMonitorPos";

    public GuidanceWarheadBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.aero_reformation.guidance_warhead.desc1")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.aero_reformation.guidance_warhead.desc2")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.aero_reformation.guidance_warhead.desc3")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.aero_reformation.guidance_warhead.desc4")
                .withStyle(ChatFormatting.GRAY));
        BlockPos mon = readBoundMonitor(stack);
        if (mon != null) {
            tooltip.add(Component.translatable("tooltip.aero_reformation.guidance_warhead.bound", mon.toShortString())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(ChatFormatting.AQUA);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, net.minecraft.world.entity.player.Player player, ItemStack stack, BlockState state) {
        BlockPos mon = readBoundMonitor(stack);
        if (mon != null && level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            // Use reflection to set boundMonitorPos on GuidanceWarheadBlockEntity
            try {
                java.lang.reflect.Field f = be.getClass().getField("boundMonitorPos");
                f.set(be, mon);
                be.setChanged();
            } catch (Exception ignored) {}
        }
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var be = level.getBlockEntity(context.getClickedPos());
        if (RadarCompat.isMonitorBlockEntity(be)) {
            if (!level.isClientSide()) {
                CompoundTag tag = context.getItemInHand()
                        .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                tag.putLong(TAG_MONITOR, context.getClickedPos().asLong());
                context.getItemInHand().set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                context.getPlayer().displayClientMessage(
                        Component.translatable("aero_reformation.guidance_warhead.bound_monitor",
                                context.getClickedPos().toShortString()), true);
            }
            return InteractionResult.SUCCESS;
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (readBoundMonitor(stack) != null) {
            if (!level.isClientSide()) {
                CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                tag.remove(TAG_MONITOR);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                player.displayClientMessage(
                        Component.translatable("aero_reformation.guidance_warhead.unbound_monitor"), true);
            }
            return InteractionResultHolder.success(stack);
        }
        return super.use(level, player, hand);
    }

    /** Read bound monitor pos from item CustomData. */
    public static BlockPos readBoundMonitor(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(TAG_MONITOR)) {
            return BlockPos.of(tag.getLong(TAG_MONITOR));
        }
        return null;
    }
}
