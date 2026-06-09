package dev.simulated_team.aero_reformation.content.items.ethereal_key;

import dev.simulated_team.aero_reformation.content.blocks.physics_anchor.PhysicsAnchorBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.*;

public class EtherealKeyItem extends Item {

    private static final String TAG_BOUND = "aero_bound";
    private static final String TAG_HIDDEN = "aero_hidden";

    /** SubLevel UUIDs that are currently hidden (tracking range set to 1). */
    public static final Set<UUID> HIDDEN_SUBLEVELS = Collections.synchronizedSet(new HashSet<>());

    public EtherealKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        if (player == null || level.isClientSide()) return InteractionResult.SUCCESS;

        if (!(level.getBlockState(pos).getBlock() instanceof PhysicsAnchorBlock)) {
            return InteractionResult.PASS;
        }

        CompoundTag customTag = getOrCreateCustomTag(stack);

        if (player.isShiftKeyDown()) {
            // Clear binding
            customTag.remove(TAG_BOUND);
            customTag.remove(TAG_HIDDEN);
            saveCustomTag(stack, customTag);
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.5f);
            player.displayClientMessage(Component.translatable("item.aero_reformation.ethereal_key.unbind").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }

        // Bind
        GlobalPos gp = GlobalPos.of(level.dimension(), pos);
        GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, gp).result().ifPresent(t -> customTag.put(TAG_BOUND, t));
        saveCustomTag(stack, customTag);
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        player.displayClientMessage(Component.translatable("item.aero_reformation.ethereal_key.bind").withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);

        CompoundTag customTag = getCustomTag(stack);
        if (customTag == null || !customTag.contains(TAG_BOUND)) {
            player.displayClientMessage(Component.translatable("item.aero_reformation.ethereal_key.not_bound").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        boolean hidden = customTag.getBoolean(TAG_HIDDEN);

        GlobalPos.CODEC.parse(NbtOps.INSTANCE, customTag.get(TAG_BOUND)).result().ifPresent(gp -> {
            if (level instanceof ServerLevel sl) {
                UUID subId = getSubLevelId(sl, gp.pos());
                if (subId != null) {
                    if (hidden) {
                        HIDDEN_SUBLEVELS.remove(subId);
                        customTag.putBoolean(TAG_HIDDEN, false);
                        saveCustomTag(stack, customTag);
                        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.8f, 1.2f);
                        player.displayClientMessage(Component.translatable("item.aero_reformation.ethereal_key.show").withStyle(ChatFormatting.AQUA), true);
                    } else {
                        HIDDEN_SUBLEVELS.add(subId);
                        customTag.putBoolean(TAG_HIDDEN, true);
                        saveCustomTag(stack, customTag);
                        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_EYE_LAUNCH, SoundSource.PLAYERS, 0.8f, 0.8f);
                        player.displayClientMessage(Component.translatable("item.aero_reformation.ethereal_key.hide").withStyle(ChatFormatting.DARK_PURPLE), true);
                    }
                }
            }
        });

        return InteractionResultHolder.success(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        return tag != null && tag.contains(TAG_BOUND);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        // Always show description
        tooltip.add(Component.translatable("item.aero_reformation.ethereal_key.desc").withStyle(ChatFormatting.YELLOW));
        CompoundTag tag = getCustomTag(stack);
        if (tag != null && tag.contains(TAG_BOUND)) {
            GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_BOUND)).result().ifPresent(gp -> {
                tooltip.add(Component.translatable("item.aero_reformation.ethereal_key.bound_to",
                        gp.pos().toShortString()).withStyle(ChatFormatting.GRAY));
                if (tag.getBoolean(TAG_HIDDEN)) {
                    tooltip.add(Component.translatable("item.aero_reformation.ethereal_key.hidden").withStyle(ChatFormatting.DARK_PURPLE));
                }
            });
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        if (tag != null && tag.contains(TAG_BOUND)) {
            return Component.translatable("item.aero_reformation.ethereal_key.bound_name")
                    .withStyle(ChatFormatting.GREEN);
        }
        return super.getName(stack);
    }

    private static CompoundTag getCustomTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : null;
    }

    private static CompoundTag getOrCreateCustomTag(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.copyTag();
    }

    private static void saveCustomTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static UUID getSubLevelId(ServerLevel level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (be == null) return null;
        var sub = dev.ryanhcode.sable.Sable.HELPER.getContaining(be);
        return sub != null ? sub.getUniqueId() : null;
    }
}
