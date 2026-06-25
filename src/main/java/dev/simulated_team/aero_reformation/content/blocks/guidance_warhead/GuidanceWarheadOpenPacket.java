package dev.simulated_team.aero_reformation.content.blocks.guidance_warhead;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** Server→Client: opens the guidance warhead settings screen with current values. */
public record GuidanceWarheadOpenPacket(BlockPos pos, float kp, float ki, float kd, float maxSpeed,
                                         float sidePower, float maxThrustPN,
                                         float brakeCoeff, float proximityRange,
                                         float cruiseAltitude, float redstoneRange,
                                         float altitudeOffset,
                                         int searchMode, float minSearchRange, float maxSearchRange,
                                         double manualX, double manualY, double manualZ)
        implements CustomPacketPayload {

    public static final Type<GuidanceWarheadOpenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "guidance_warhead_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GuidanceWarheadOpenPacket> STREAM_CODEC =
            StreamCodec.of(GuidanceWarheadOpenPacket::encode, GuidanceWarheadOpenPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, GuidanceWarheadOpenPacket p) {
        buf.writeBlockPos(p.pos);
        buf.writeFloat(p.kp);
        buf.writeFloat(p.ki);
        buf.writeFloat(p.kd);
        buf.writeFloat(p.maxSpeed);
        buf.writeFloat(p.sidePower);
        buf.writeFloat(p.maxThrustPN);
        buf.writeFloat(p.brakeCoeff);
        buf.writeFloat(p.proximityRange);
        buf.writeFloat(p.cruiseAltitude);
        buf.writeFloat(p.redstoneRange);
        buf.writeFloat(p.altitudeOffset);
        buf.writeInt(p.searchMode);
        buf.writeFloat(p.minSearchRange);
        buf.writeFloat(p.maxSearchRange);
        buf.writeDouble(p.manualX);
        buf.writeDouble(p.manualY);
        buf.writeDouble(p.manualZ);
    }

    private static GuidanceWarheadOpenPacket decode(RegistryFriendlyByteBuf buf) {
        return new GuidanceWarheadOpenPacket(buf.readBlockPos(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readInt(), buf.readFloat(), buf.readFloat(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> GuidanceWarheadScreenOpener.open(pos, kp, ki, kd, maxSpeed, sidePower, maxThrustPN,
                brakeCoeff, proximityRange, cruiseAltitude, redstoneRange, altitudeOffset,
                searchMode, minSearchRange, maxSearchRange, manualX, manualY, manualZ));
    }
}
