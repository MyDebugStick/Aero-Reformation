package dev.simulated_team.aero_reformation.content.blocks.power;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ToggleCameraLockPayload(int entityId) implements CustomPacketPayload {

    public static final Type<ToggleCameraLockPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "toggle_camera_lock"));

    public static final StreamCodec<FriendlyByteBuf, ToggleCameraLockPayload> STREAM_CODEC =
            StreamCodec.of(ToggleCameraLockPayload::write, ToggleCameraLockPayload::read);

    private static void write(FriendlyByteBuf buf, ToggleCameraLockPayload p) { buf.writeVarInt(p.entityId); }
    private static ToggleCameraLockPayload read(FriendlyByteBuf buf) { return new ToggleCameraLockPayload(buf.readVarInt()); }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ToggleCameraLockPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var entity = ctx.player().level().getEntity(payload.entityId);
            if (entity instanceof SeatEntity seat) {
                seat.toggleCameraLocked();
            }
        });
    }
}
