package dev.simulated_team.aero_reformation.content.blocks.rcs_thruster;

import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class RcsThrusterBlockItem extends BlockItem {

    public RcsThrusterBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.has(AeroDataComponents.BOUND_MASTER.get())) {
            if (!level.isClientSide()) {
                stack.remove(AeroDataComponents.BOUND_MASTER.get());
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.3f, 0.5f);
                player.displayClientMessage(
                        Component.translatable("aero_reformation.rcs_thruster.cleared"), true);
            }
            return InteractionResultHolder.success(stack);
        }
        return super.use(level, player, hand);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(AeroDataComponents.BOUND_MASTER.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("aero_reformation.rcs_thruster.desc1")
                .withStyle(net.minecraft.ChatFormatting.AQUA));
        tooltip.add(Component.translatable("aero_reformation.rcs_thruster.desc2")
                .withStyle(net.minecraft.ChatFormatting.AQUA));
        tooltip.add(Component.translatable("aero_reformation.rcs_thruster.desc3",
                String.format("%.0f", dev.simulated_team.aero_reformation.config.AeroReformationConfig.rcsFuelConsumption))
                .withStyle(net.minecraft.ChatFormatting.GREEN));
        BlockPos boundSync = stack.get(AeroDataComponents.BOUND_MASTER.get());
        if (boundSync != null) {
            tooltip.add(Component.translatable("aero_reformation.rcs_thruster.bound_pos",
                    boundSync.getX(), boundSync.getY(), boundSync.getZ())
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
    }

    /**
     * Transfer bound sync position from item data component to block entity on placement.
     */
    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, net.minecraft.world.level.Level level,
                                                  @javax.annotation.Nullable net.minecraft.world.entity.player.Player player,
                                                  ItemStack stack, net.minecraft.world.level.block.state.BlockState state) {
        BlockPos boundSync = stack.get(AeroDataComponents.BOUND_MASTER.get());
        if (boundSync != null) {
            if (level.getBlockEntity(pos) instanceof RcsThrusterBlockEntity rcs) {
                rcs.setBoundSync(boundSync);
            }
        }
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }
}
