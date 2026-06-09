package dev.simulated_team.aero_reformation.content.blocks.com_offset;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** Server→Client: syncs COM offset to client. */
public record ComSyncPayload(BlockPos pos, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<ComSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "com_sync"));
    public static final StreamCodec<FriendlyByteBuf, ComSyncPayload> STREAM_CODEC =
            StreamCodec.of(ComSyncPayload::encode, ComSyncPayload::decode);

    public static ComSyncPayload CLIENT_SYNC;

    private static void encode(FriendlyByteBuf buf, ComSyncPayload p) {
        buf.writeBlockPos(p.pos);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
    }

    private static ComSyncPayload decode(FriendlyByteBuf buf) {
        return new ComSyncPayload(buf.readBlockPos(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ComSyncPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> CLIENT_SYNC = p);
    }
}
