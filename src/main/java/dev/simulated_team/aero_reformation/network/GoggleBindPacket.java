package dev.simulated_team.aero_reformation.network;

import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.content.items.goggle_monitor.GoggleMonitorData;
import dev.simulated_team.aero_reformation.content.items.goggle_monitor.SensorMonitorEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GoggleBindPacket(BlockPos pos, String sensorType, String name) implements CustomPacketPayload {

    public static final Type<GoggleBindPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "goggle_bind"));

    public static final StreamCodec<FriendlyByteBuf, GoggleBindPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeBlockPos(p.pos); buf.writeUtf(p.sensorType); buf.writeUtf(p.name); },
                    buf -> new GoggleBindPacket(buf.readBlockPos(), buf.readUtf(), buf.readUtf()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GoggleBindPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (GoggleMonitorData.findGoggles(sp).isEmpty()) return;
            boolean added = GoggleMonitorData.addEntry(sp,
                    new SensorMonitorEntry(packet.name, packet.sensorType, packet.pos));
            if (!added) {
                sp.displayClientMessage(Component.literal("§c该传感器已绑定"), true);
            } else {
                sp.displayClientMessage(Component.literal("§a已绑定: " + packet.name), true);
            }
        });
    }
}
