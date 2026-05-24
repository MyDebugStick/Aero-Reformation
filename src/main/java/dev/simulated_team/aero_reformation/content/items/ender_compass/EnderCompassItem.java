package dev.simulated_team.aero_reformation.content.items.ender_compass;

import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Optional;

public class EnderCompassItem extends Item {

    /** Arrow launch speed (same as fully charged bow) */
    private static final float LAUNCH_SPEED = 3.0f;
    /** Minimum charge ticks before firing */
    private static final int MIN_CHARGE = 15;

    public EnderCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        EnderCompassData data = stack.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
        return data.hasChannel();
    }

    // ─── Charging ───

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    // ─── Right-click (air): start charging ───

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        EnderCompassData data = stack.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
        if (!data.hasChannel()) return InteractionResultHolder.pass(stack);
        if (player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    // ─── Block click: start charging (or record position on shift) ───

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        EnderCompassData data = stack.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
        if (!data.hasChannel()) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            Level level = context.getLevel();
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            BlockPos playerPos = player.blockPosition();
            GlobalPos target = GlobalPos.of(level.dimension(), playerPos);
            stack.set(AeroDataComponents.ENDER_COMPASS,
                    new EnderCompassData(data.channel(), Optional.of(target)));
            EnderChannelCache.put(data.channel(), target);
            level.playSound(null, playerPos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.2f);
            player.displayClientMessage(
                    Component.translatable("aero_reformation.ender_compass.set",
                            playerPos.getX(), playerPos.getY(), playerPos.getZ()), true);
            // Sync all same-channel compasses in player inventory
            syncPlayerCompasses(player, data.channel(), target);
            return InteractionResult.SUCCESS;
        }

        player.startUsingItem(context.getHand());
        return InteractionResult.CONSUME;
    }

    // ─── Every tick while charging: auto-fire when ready ───

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (level.isClientSide()) return;
        if (!(entity instanceof Player player)) return;

        int usedTicks = getUseDuration(stack, entity) - remainingTicks;
        if (usedTicks < MIN_CHARGE) return;

        EnderCompassData data = stack.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
        if (!data.hasChannel()) return;

        // Consume one arrow + one compass
        if (!player.getAbilities().instabuild) {
            if (!consumeArrow(player)) return;
            stack.shrink(1);
        }

        // Spawn arrow with compass as pickup item
        ItemStack pickup = new ItemStack(AeroBlocks.ENDER_COMPASS.get());
        pickup.set(AeroDataComponents.ENDER_COMPASS, new EnderCompassData(data.channel(), data.target()));
        Arrow arrow = new Arrow(level, player.getX(), player.getEyeY() - 0.1, player.getZ(),
                pickup, new ItemStack(Items.BOW));
        arrow.setOwner(player);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, LAUNCH_SPEED, 1.0f);

        // Set lifespan to 3 minutes
        try {
            java.lang.reflect.Field lifeField = net.minecraft.world.entity.projectile.AbstractArrow.class.getDeclaredField("life");
            lifeField.setAccessible(true);
            lifeField.setInt(arrow, -2400);
        } catch (Exception ignored) {}

        EnderArrowTracker.register(arrow, data.channel(), data.target().orElse(null));
        level.addFreshEntity(arrow);
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f);

        player.stopUsingItem();
    }

    private static boolean consumeArrow(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.ARROW)) {
                stack.shrink(1);
                return true;
            }
        }
        // Also check offhand
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(Items.ARROW)) {
            offhand.shrink(1);
            return true;
        }
        return false;
    }

    // ─── Release (early): do nothing, let onUseTick handle it ───

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
    }

    // ─── Helpers ───

    private static void syncPlayerCompasses(Player player, String channel, GlobalPos target) {
        EnderCompassData newData = new EnderCompassData(channel, Optional.of(target));
        for (ItemStack s : player.getInventory().items) {
            if (!s.is(AeroBlocks.ENDER_COMPASS.get())) continue;
            EnderCompassData existing = s.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
            if (channel.equals(existing.channel())) {
                s.set(AeroDataComponents.ENDER_COMPASS, newData);
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (!s.is(AeroBlocks.ENDER_COMPASS.get())) continue;
            EnderCompassData existing = s.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
            if (channel.equals(existing.channel())) {
                s.set(AeroDataComponents.ENDER_COMPASS, newData);
            }
        }
    }
}
