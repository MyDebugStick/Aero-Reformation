package dev.simulated_team.aero_reformation.network;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server -> client: live NBT values for the player's bound NBT paths.
 * Values are keyed by (block pos + nbt path) so multiple entries can bind the
 * same block with different paths.
 */
public record HudNbtSyncPacket(List<HudNbtSyncPacket.NbtValue> values) implements CustomPacketPayload {

    /** One live value: pos/path + value; entityUuid != null means a living entity value. */
    public record NbtValue(BlockPos pos, String path, String value, UUID entityUuid) {}

    /** Composite client-side cache key: pos + path (+ entityUuid for entities). */
    public record NbtKey(BlockPos pos, String path, UUID entityUuid) {}

    public static final Type<HudNbtSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "hud_nbt_sync"));

    public static final StreamCodec<FriendlyByteBuf, HudNbtSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.values.size());
                        for (NbtValue v : p.values) {
                            buf.writeBlockPos(v.pos());
                            buf.writeUtf(v.path());
                            buf.writeUtf(v.value());
                            buf.writeBoolean(v.entityUuid() != null);
                            if (v.entityUuid() != null) buf.writeUUID(v.entityUuid());
                        }
                    },
                    buf -> {
                        int n = buf.readVarInt();
                        List<NbtValue> list = new ArrayList<>();
                        for (int i = 0; i < n; i++) {
                            BlockPos pos = buf.readBlockPos();
                            String path = buf.readUtf();
                            String value = buf.readUtf();
                            UUID uuid = buf.readBoolean() ? buf.readUUID() : null;
                            list.add(new NbtValue(pos, path, value, uuid));
                        }
                        return new HudNbtSyncPacket(list);
                    });

    /** Client cache of the latest NBT values, keyed by (pos, path) or (entityUuid, path). */
    public static final Map<NbtKey, String> CLIENT_VALUES = new HashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @OnlyIn(Dist.CLIENT)
    public static void handle(HudNbtSyncPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            CLIENT_VALUES.clear();
            for (NbtValue v : packet.values()) {
                CLIENT_VALUES.put(new NbtKey(v.pos(), v.path(), v.entityUuid()), v.value());
            }
        });
    }
}
