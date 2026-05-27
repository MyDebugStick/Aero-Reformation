package dev.simulated_team.aero_reformation.content.blocks.electric_loadstone;

import dev.simulated_team.aero_reformation.content.items.ender_compass.EnderArrowTracker;
import dev.simulated_team.aero_reformation.content.items.ender_compass.EnderChannelCache;
import dev.simulated_team.aero_reformation.content.items.ender_compass.EnderCompassData;
import dev.simulated_team.aero_reformation.event.EnderArrowHandler;
import dev.simulated_team.aero_reformation.network.LoadstoneSyncPacket;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public class ElectricLoadstoneBlockEntity extends BlockEntity {

    private ItemStack heldItem = ItemStack.EMPTY;
    private boolean wasPowered = false;

    public ElectricLoadstoneBlockEntity(BlockPos pos, BlockState state) {
        super(AeroBlocks.ELECTRIC_LOADSTONE_BE.get(), pos, state);
    }

    public ItemStack getHeldItem() {
        return heldItem;
    }

    public void setHeldItem(ItemStack stack) {
        this.heldItem = stack;
        setChanged();
        if (level instanceof ServerLevel sl) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            var tag = stack.isEmpty() ? null : (net.minecraft.nbt.CompoundTag) stack.save(sl.registryAccess());
            PacketDistributor.sendToPlayersTrackingChunk(sl,
                    new net.minecraft.world.level.ChunkPos(worldPosition),
                    new LoadstoneSyncPacket(worldPosition, tag));
        }
    }

    /** Called on client from sync packet */
    public void clientSync(ItemStack stack) {
        this.heldItem = stack;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ElectricLoadstoneBlockEntity be) {
        if (level.isClientSide()) return;

        boolean powered = level.getBestNeighborSignal(pos) > 0;

        // Only sync on rising edge (unpowered → powered)
        if (powered && !be.wasPowered) {
            ItemStack held = be.getHeldItem();
            if (!held.isEmpty()) {
                EnderCompassData data = held.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
                if (data.hasChannel()) {
                    Vec3 worldPos = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(pos));
                    BlockPos targetPos = BlockPos.containing(worldPos);

                    // Sync globally to all compasses on this channel
                    EnderChannelCache.put(data.channel(),
                            GlobalPos.of(((ServerLevel) level).dimension(), targetPos));
                    EnderArrowTracker.updatePos(data.channel(),
                            GlobalPos.of(((ServerLevel) level).dimension(), targetPos));
                    EnderArrowHandler.markChannelDirty(data.channel());
                    syncAllCompasses((ServerLevel) level, data.channel(), targetPos);

                    // Also update the compass physically on the loadstone
                    EnderCompassData newData = new EnderCompassData(data.channel(),
                            java.util.Optional.of(GlobalPos.of(((ServerLevel) level).dimension(), targetPos)));
                    held.set(AeroDataComponents.ENDER_COMPASS.get(), newData);
                    be.setHeldItem(held);
                }
            }
        }

        be.wasPowered = powered;
    }

    private static void syncAllCompasses(ServerLevel level, String channel, BlockPos pos) {
        EnderCompassData newData = new EnderCompassData(channel,
                java.util.Optional.of(GlobalPos.of(level.dimension(), pos)));
        for (var player : level.getServer().getPlayerList().getPlayers()) {
            // Skip players without any compass
            boolean hasCompass = false;
            for (ItemStack s : player.getInventory().items) {
                if (s.is(AeroBlocks.ENDER_COMPASS.get())) { hasCompass = true; break; }
            }
            if (!hasCompass && !player.getOffhandItem().is(AeroBlocks.ENDER_COMPASS.get())) continue;
            boolean changed = false;
            for (ItemStack s : player.getInventory().items) {
                if (!s.is(AeroBlocks.ENDER_COMPASS.get())) continue;
                EnderCompassData existing = s.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
                if (channel.equals(existing.channel())) {
                    s.set(AeroDataComponents.ENDER_COMPASS.get(), newData);
                    changed = true;
                }
            }
            ItemStack offhand = player.getOffhandItem();
            if (offhand.is(AeroBlocks.ENDER_COMPASS.get())) {
                EnderCompassData existing = offhand.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
                if (channel.equals(existing.channel())) {
                    offhand.set(AeroDataComponents.ENDER_COMPASS.get(), newData);
                    changed = true;
                }
            }
            if (changed) player.getInventory().setChanged();
            // Notification
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("aero_reformation.loadstone.sync",
                            pos.getX(), pos.getY(), pos.getZ(), channel), true);
        }
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!heldItem.isEmpty()) {
            tag.put("HeldItem", heldItem.save(registries));
        }
        tag.putBoolean("WasPowered", wasPowered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        heldItem = tag.contains("HeldItem")
                ? ItemStack.parse(registries, tag.getCompound("HeldItem")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        wasPowered = tag.getBoolean("WasPowered");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
