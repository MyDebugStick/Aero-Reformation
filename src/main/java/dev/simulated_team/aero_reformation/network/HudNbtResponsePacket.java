package dev.simulated_team.aero_reformation.network;

import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.content.hud.HudNbtBrowserScreen;
import dev.simulated_team.aero_reformation.content.hud.HudNbtCache;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Server -> client: the requested block entity NBT (or entity NBT when
 * entityUuid != null), used by the NBT browser.
 */
public record HudNbtResponsePacket(BlockPos pos, UUID entityUuid, CompoundTag tag) implements CustomPacketPayload {

    public static final Type<HudNbtResponsePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "hud_nbt_response"));

    public static final StreamCodec<FriendlyByteBuf, HudNbtResponsePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBlockPos(p.pos);
                        buf.writeBoolean(p.entityUuid != null);
                        if (p.entityUuid != null) buf.writeUUID(p.entityUuid);
                        buf.writeNbt(p.tag);
                    },
                    buf -> {
                        BlockPos pos = buf.readBlockPos();
                        UUID uuid = buf.readBoolean() ? buf.readUUID() : null;
                        return new HudNbtResponsePacket(pos, uuid, buf.readNbt());
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @OnlyIn(Dist.CLIENT)
    public static void handle(HudNbtResponsePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (packet.tag() == null) return;
            if (packet.entityUuid() != null) {
                HudNbtCache.ENTITY_SNAPSHOTS.put(packet.entityUuid(), packet.tag());
                var pendingEntry = HudNbtCache.takePendingEntity(packet.entityUuid());
                if (pendingEntry != null) {
                    // HudEntry entity browsing auto-opens
                    Minecraft.getInstance().setScreen(new HudNbtBrowserScreen(pendingEntry));
                    return;
                }
                // Placeholder entity snapshots are browsed manually via the "Pick NBT" button
                HudNbtCache.takePendingPlaceholderEntity(packet.entityUuid());
                return;
            }
            HudNbtCache.SNAPSHOTS.put(packet.pos(), packet.tag());
            var pending = HudNbtCache.takePending(packet.pos());
            if (pending != null) {
                // HudEntry NBT browsing still auto-opens
                Minecraft.getInstance().setScreen(new HudNbtBrowserScreen(pending));
                return;
            }
            // Placeholder block snapshots are browsed manually
            HudNbtCache.takePendingPlaceholder(packet.pos());
        });
    }
}
