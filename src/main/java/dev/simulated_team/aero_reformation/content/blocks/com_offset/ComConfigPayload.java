package dev.simulated_team.aero_reformation.content.blocks.com_offset;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** Client→Server: sends COM offset config to server. */
public record ComConfigPayload(BlockPos pos, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<ComConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "com_config"));
    public static final StreamCodec<FriendlyByteBuf, ComConfigPayload> STREAM_CODEC =
            StreamCodec.of(ComConfigPayload::encode, ComConfigPayload::decode);

    private static void encode(FriendlyByteBuf buf, ComConfigPayload p) {
        buf.writeBlockPos(p.pos);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
    }

    private static ComConfigPayload decode(FriendlyByteBuf buf) {
        return new ComConfigPayload(buf.readBlockPos(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ComConfigPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().level().getBlockEntity(p.pos) instanceof ComOffsetBlockEntity be) {
                be.setCom(p.x, p.y, p.z);
            }
        });
    }
}
