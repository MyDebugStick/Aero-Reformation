package dev.simulated_team.aero_reformation.content.blocks.power;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record PowerConfigPayload(BlockPos pos, int yawMax, int pitchMax, double seatHeight) implements CustomPacketPayload {

    public static final Type<PowerConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "power_config"));

    public static final StreamCodec<FriendlyByteBuf, PowerConfigPayload> STREAM_CODEC =
            StreamCodec.of(PowerConfigPayload::write, PowerConfigPayload::read);

    private static void write(FriendlyByteBuf buf, PowerConfigPayload p) {
        buf.writeBlockPos(p.pos);
        buf.writeVarInt(p.yawMax);
        buf.writeVarInt(p.pitchMax);
        buf.writeDouble(p.seatHeight);
    }

    private static PowerConfigPayload read(FriendlyByteBuf buf) {
        return new PowerConfigPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readDouble());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PowerConfigPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var level = ctx.player().level();
            if (level.getBlockEntity(payload.pos) instanceof PowerBlockEntity be) {
                be.setYawMax(payload.yawMax);
                be.setPitchMax(payload.pitchMax);
                be.setSeatHeight(payload.seatHeight);
            }
        });
    }
}
