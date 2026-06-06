package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record AnchorMapSyncPacket(List<MarkerEntry> markers) implements CustomPacketPayload {

    public record MarkerEntry(UUID subLevelId, double x, double y, double z, String name, int radius) {}

    public static final Type<AnchorMapSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "anchor_map_sync"));

    public static final StreamCodec<FriendlyByteBuf, AnchorMapSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.markers.size());
                        for (var m : p.markers) {
                            buf.writeUUID(m.subLevelId);
                            buf.writeDouble(m.x); buf.writeDouble(m.y); buf.writeDouble(m.z);
                            buf.writeBoolean(m.name != null);
                            if (m.name != null) buf.writeUtf(m.name);
                            buf.writeVarInt(m.radius);
                        }
                    },
                    buf -> {
                        int count = buf.readVarInt();
                        List<MarkerEntry> list = new ArrayList<>();
                        for (int i = 0; i < count; i++) {
                            UUID id = buf.readUUID();
                            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
                            String name = buf.readBoolean() ? buf.readUtf() : null;
                            int radius = buf.readVarInt();
                            list.add(new MarkerEntry(id, x, y, z, name, radius));
                        }
                        return new AnchorMapSyncPacket(list);
                    });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AnchorMapSyncPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> AnchorMapClientData.update(packet.markers));
    }
}
