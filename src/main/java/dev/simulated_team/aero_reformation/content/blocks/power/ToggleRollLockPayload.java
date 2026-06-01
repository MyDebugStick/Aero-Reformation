package dev.simulated_team.aero_reformation.content.blocks.power;

import dev.simulated_team.aero_reformation.AeroReformation;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ToggleRollLockPayload(int entityId) implements CustomPacketPayload {
    public static final Type<ToggleRollLockPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "toggle_roll_lock"));
    public static final StreamCodec<ByteBuf, ToggleRollLockPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ToggleRollLockPayload::entityId,
            ToggleRollLockPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ToggleRollLockPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Entity entity = ctx.player().level().getEntity(msg.entityId);
            if (entity instanceof SeatEntity seat) {
                seat.toggleRollLocked();
            }
        });
    }
}
