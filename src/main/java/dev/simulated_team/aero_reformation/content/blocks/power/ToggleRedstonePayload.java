package dev.simulated_team.aero_reformation.content.blocks.power;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ToggleRedstonePayload(int entityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleRedstonePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "toggle_redstone"));

    public static final StreamCodec<FriendlyByteBuf, ToggleRedstonePayload> STREAM_CODEC =
            StreamCodec.of(ToggleRedstonePayload::write, ToggleRedstonePayload::read);

    private static void write(FriendlyByteBuf buf, ToggleRedstonePayload payload) {
        buf.writeVarInt(payload.entityId);
    }

    private static ToggleRedstonePayload read(FriendlyByteBuf buf) {
        return new ToggleRedstonePayload(buf.readVarInt());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleRedstonePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var level = ctx.player().level();
            var entity = level.getEntity(payload.entityId);
            if (entity instanceof SeatEntity seat) {
                seat.toggleRedstoneDisabled();
            }
        });
    }
}

