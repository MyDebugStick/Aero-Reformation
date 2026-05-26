package dev.simulated_team.aero_reformation.event;

import dev.simulated_team.aero_reformation.content.items.ender_compass.EnderArrowTracker;
import dev.simulated_team.aero_reformation.content.items.ender_compass.EnderChannelCache;
import dev.simulated_team.aero_reformation.content.items.ender_compass.EnderCompassData;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static dev.simulated_team.aero_reformation.AeroReformation.MODID;

@EventBusSubscriber(modid = MODID)
public class EnderArrowHandler {

    private static final Set<String> DIRTY_CHANNELS = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, GlobalPos> LAST_SYNC_POS = Collections.synchronizedMap(new HashMap<>());

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        if (!(arrow.level() instanceof ServerLevel)) return;

        Optional<String> channel = EnderArrowTracker.getChannel(arrow);
        if (channel.isEmpty()) return;

        GlobalPos pos = getWorldPos(arrow);
        aero$sync(arrow, channel.get(), pos, true);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) return;

        if (event.getLevel().getGameTime() % 60 == 0) {
            for (int id : EnderArrowTracker.getActiveArrowIds()) {
                AbstractArrow arrow = EnderArrowTracker.getArrowRef(id);
                String channel = EnderArrowTracker.getChannelById(id);
                if (arrow == null || channel == null) continue;

                if (!EnderArrowTracker.isYoungestArrow(channel, id)) continue;

                if (arrow.isAlive()) {
                    GlobalPos pos = getWorldPos(arrow);
                    aero$sync(arrow, channel, pos, false);
                } else {
                    GlobalPos pos = getWorldPos(arrow);
                    aero$sync(arrow, channel, pos, true);
                    EnderArrowTracker.remove(arrow);
                }
            }
        }

        // 2. Broadcast dirty channels every 5 seconds
        if (event.getLevel().getGameTime() % 100 == 0) {
            if (!DIRTY_CHANNELS.isEmpty() && event.getLevel() instanceof ServerLevel serverLevel) {
                Set<String> dirty = new HashSet<>(DIRTY_CHANNELS);
                DIRTY_CHANNELS.clear();

                for (ServerPlayer player : serverLevel.players()) {
                    if (!hasAnyCompass(player)) continue;
                    updatePlayerCompassesForChannels(player, dirty);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        ItemStack stack = event.getItemEntity().getItem();
        if (stack.is(AeroBlocks.ENDER_COMPASS.get())) {
            refreshCompass(stack);
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack stack = event.getCrafting();
        if (stack.is(AeroBlocks.ENDER_COMPASS.get())) {
            refreshCompass(stack);
        }
    }

    /**
     * Get the real-world position of an arrow, handling sub-level (contraption) transforms.
     */
    private static GlobalPos getWorldPos(AbstractArrow arrow) {
        Vec3 projected = Sable.HELPER.projectOutOfSubLevel(arrow.level(), arrow.position());
        BlockPos pos = BlockPos.containing(projected);
        return GlobalPos.of(arrow.level().dimension(), pos);
    }

    private static void refreshCompass(ItemStack stack) {
        EnderCompassData data = stack.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
        if (!data.hasChannel()) return;
        String channel = data.channel();

        Optional<GlobalPos> latest = EnderArrowTracker.getPos(channel);
        if (latest.isEmpty()) latest = EnderChannelCache.get(channel);
        if (latest.isEmpty()) return;

        stack.set(AeroDataComponents.ENDER_COMPASS,
                new EnderCompassData(channel, latest));
    }

    private static boolean hasAnyCompass(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(AeroBlocks.ENDER_COMPASS.get())) return true;
        }
        return player.getOffhandItem().is(AeroBlocks.ENDER_COMPASS.get());
    }

    private static void updatePlayerCompassesForChannels(ServerPlayer player, Set<String> channels) {
        Inventory inv = player.getInventory();
        boolean changed = false;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.is(AeroBlocks.ENDER_COMPASS.get())) continue;
            EnderCompassData data = stack.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
            if (!data.hasChannel() || !channels.contains(data.channel())) continue;

            Optional<GlobalPos> latest = EnderArrowTracker.getPos(data.channel());
            if (latest.isEmpty()) latest = EnderChannelCache.get(data.channel());
            if (latest.isEmpty()) continue;

            GlobalPos current = data.target().orElse(null);
            if (!latest.get().equals(current)) {
                stack.set(AeroDataComponents.ENDER_COMPASS,
                        new EnderCompassData(data.channel(), latest));
                changed = true;
            }
        }

        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(AeroBlocks.ENDER_COMPASS.get())) {
            EnderCompassData data = offhand.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
            if (data.hasChannel() && channels.contains(data.channel())) {
                Optional<GlobalPos> latest = EnderArrowTracker.getPos(data.channel());
                if (latest.isEmpty()) latest = EnderChannelCache.get(data.channel());
                if (latest.isPresent()) {
                    GlobalPos current = data.target().orElse(null);
                    if (!latest.get().equals(current)) {
                        offhand.set(AeroDataComponents.ENDER_COMPASS,
                                new EnderCompassData(data.channel(), latest));
                        changed = true;
                    }
                }
            }
        }

        if (changed) {
            player.getInventory().setChanged();
        }
    }

    private static void aero$sync(AbstractArrow arrow, String channel, GlobalPos pos, boolean isFinal) {
        EnderArrowTracker.updatePos(channel, pos);
        EnderChannelCache.put(channel, pos);
        DIRTY_CHANNELS.add(channel);

        // Only notify if position changed (or is final)
        GlobalPos last = LAST_SYNC_POS.put(channel, pos);
        boolean changed = last == null || !last.equals(pos);

        Entity owner = arrow.getOwner();
        if (owner instanceof ServerPlayer player && (isFinal || changed)) {
            Component msg = Component.translatable(
                    isFinal ? "message.aero_reformation.ender_arrow.landed"
                            : "message.aero_reformation.ender_arrow.sync",
                    channel, pos.pos().getX(), pos.pos().getY(), pos.pos().getZ()
            );
            player.displayClientMessage(msg, true);
        }
    }
}
