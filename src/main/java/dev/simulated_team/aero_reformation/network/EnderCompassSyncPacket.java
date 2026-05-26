package dev.simulated_team.aero_reformation.network;

import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.content.items.ender_compass.EnderChannelCache;
import dev.simulated_team.aero_reformation.content.items.ender_compass.EnderCompassData;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

public record EnderCompassSyncPacket(BlockPos pos, String channel) implements CustomPacketPayload {

    public static final Type<EnderCompassSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "ender_compass_sync"));

    public static final StreamCodec<FriendlyByteBuf, EnderCompassSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> { buf.writeBlockPos(packet.pos); buf.writeUtf(packet.channel); },
                    buf -> new EnderCompassSyncPacket(buf.readBlockPos(), buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EnderCompassSyncPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            ServerLevel level = sp.serverLevel();
            GlobalPos target = GlobalPos.of(level.dimension(), packet.pos);
            String channel = packet.channel;

            // Update channel cache first (nav tables read from this)
            EnderChannelCache.put(channel, target);

            // Sync all compasses in all players' inventories
            EnderCompassData newData = new EnderCompassData(channel, Optional.of(target));
            for (ServerPlayer other : level.getServer().getPlayerList().getPlayers()) {
                for (ItemStack s : other.getInventory().items) {
                    if (!s.is(AeroBlocks.ENDER_COMPASS.get())) continue;
                    EnderCompassData existing = s.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
                    if (channel.equals(existing.channel())) {
                        s.set(AeroDataComponents.ENDER_COMPASS, newData);
                    }
                }
                ItemStack offhand = other.getOffhandItem();
                if (offhand.is(AeroBlocks.ENDER_COMPASS.get())) {
                    EnderCompassData existing = offhand.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
                    if (channel.equals(existing.channel())) {
                        offhand.set(AeroDataComponents.ENDER_COMPASS, newData);
                    }
                }
            }

            level.playSound(null, sp.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.2f);
            sp.displayClientMessage(
                    Component.translatable("aero_reformation.ender_compass.set",
                            packet.pos.getX(), packet.pos.getY(), packet.pos.getZ()), true);
        });
    }
}
