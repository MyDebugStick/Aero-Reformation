package dev.simulated_team.aero_reformation.content.blocks.guidance_warhead;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** Syncs guidance warhead settings from client GUI to server. */
public record GuidanceWarheadSettingsPacket(BlockPos pos, float kp, float ki, float kd, float maxSpeed,
                                             float sidePower, float maxThrustPN,
                                             float brakeCoeff, float proximityRange,
                                             float cruiseAltitude, float redstoneRange,
                                             float altitudeOffset,
                                             int searchMode, float minSearchRange, float maxSearchRange,
                                             double manualX, double manualY, double manualZ)
        implements CustomPacketPayload {

    public static final Type<GuidanceWarheadSettingsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "guidance_warhead_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GuidanceWarheadSettingsPacket> STREAM_CODEC =
            StreamCodec.of(GuidanceWarheadSettingsPacket::encode, GuidanceWarheadSettingsPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, GuidanceWarheadSettingsPacket p) {
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

    private static GuidanceWarheadSettingsPacket decode(RegistryFriendlyByteBuf buf) {
        return new GuidanceWarheadSettingsPacket(buf.readBlockPos(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readInt(), buf.readFloat(), buf.readFloat(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var level = ctx.player().level();
            var be = level.getBlockEntity(pos);
            if (be instanceof GuidanceWarheadBlockEntity warhead) {
                warhead.kp = Math.clamp(kp, 0.1f, 5.0f);
                warhead.ki = Math.clamp(ki, 0.0f, 1.0f);
                warhead.kd = Math.clamp(kd, 0.0f, 2.0f);
                warhead.maxSpeed = Math.clamp(maxSpeed, 1.0f, 100.0f);
                warhead.sidePower = Math.clamp(sidePower, 0.01f, 0.5f);
                warhead.maxThrustPN = Math.clamp(maxThrustPN, 100.0f, 20000.0f);
                warhead.brakeCoeff = Math.clamp(brakeCoeff, 0.0f, 1.0f);
                warhead.proximityRange = Math.clamp(proximityRange, 0.0f, 200.0f);
                warhead.cruiseAltitude = Math.clamp(cruiseAltitude, 0.0f, 500.0f);
                warhead.redstoneRange = Math.clamp(redstoneRange, 0.0f, 200.0f);
                warhead.altitudeOffset = Math.clamp(altitudeOffset, 0.0f, 100.0f);
                warhead.searchMode = Math.clamp(searchMode, 0, 2);
                warhead.minSearchRange = Math.clamp(minSearchRange, 0.0f, 4000.0f);
                warhead.maxSearchRange = Math.clamp(maxSearchRange, 0.0f, 4000.0f);
                warhead.manualTargetX = manualX;
                warhead.manualTargetY = manualY;
                warhead.manualTargetZ = manualZ;
                // Unlock target so new settings take effect on next scan
                warhead.unlockTarget();
                warhead.setChanged();
            }
        });
    }
}
