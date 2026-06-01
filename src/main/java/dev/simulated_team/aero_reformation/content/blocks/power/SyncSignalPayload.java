package dev.simulated_team.aero_reformation.content.blocks.power;

import dev.simulated_team.aero_reformation.AeroReformation;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncSignalPayload(int entityId, int yawRight, int yawLeft, int pitchBack, int pitchFwd) implements CustomPacketPayload {
    public static final Type<SyncSignalPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "sync_signal"));
    public static final StreamCodec<ByteBuf, SyncSignalPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncSignalPayload::entityId,
            ByteBufCodecs.VAR_INT, SyncSignalPayload::yawRight,
            ByteBufCodecs.VAR_INT, SyncSignalPayload::yawLeft,
            ByteBufCodecs.VAR_INT, SyncSignalPayload::pitchBack,
            ByteBufCodecs.VAR_INT, SyncSignalPayload::pitchFwd,
            SyncSignalPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncSignalPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().level().getEntity(msg.entityId) instanceof SeatEntity seat) {
                seat.sigRight = msg.yawRight;
                seat.sigLeft = msg.yawLeft;
                seat.sigBack = msg.pitchBack;
                seat.sigFwd = msg.pitchFwd;
            }
        });
    }
}
