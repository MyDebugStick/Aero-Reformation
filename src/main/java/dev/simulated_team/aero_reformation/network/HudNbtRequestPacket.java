package dev.simulated_team.aero_reformation.network;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> server: request the full NBT of a block entity (or a living entity
 * when entityUuid != null) for the NBT browser.
 */
public record HudNbtRequestPacket(BlockPos pos, UUID entityUuid) implements CustomPacketPayload {

    public static final Type<HudNbtRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "hud_nbt_request"));

    public static final StreamCodec<FriendlyByteBuf, HudNbtRequestPacket> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {
                        buf.writeBlockPos(p.pos);
                        buf.writeBoolean(p.entityUuid != null);
                        if (p.entityUuid != null) buf.writeUUID(p.entityUuid);
                    },
                    buf -> {
                        BlockPos pos = buf.readBlockPos();
                        UUID uuid = buf.readBoolean() ? buf.readUUID() : null;
                        return new HudNbtRequestPacket(pos, uuid);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HudNbtRequestPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                var level = sp.serverLevel();
                if (packet.entityUuid() != null) {
                    Entity ent = level.getEntity(packet.entityUuid());
                    if (ent != null) {
                        CompoundTag tag = new CompoundTag();
                        ent.saveWithoutId(tag);
                        PacketDistributor.sendToPlayer(sp, new HudNbtResponsePacket(packet.pos(), packet.entityUuid(), tag));
                    }
                } else {
                    if (!level.isLoaded(packet.pos())) return;
                    BlockEntity be = level.getBlockEntity(packet.pos());
                    if (be != null) {
                        CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
                        PacketDistributor.sendToPlayer(sp, new HudNbtResponsePacket(packet.pos(), null, tag));
                    }
                }
            }
        });
    }
}
