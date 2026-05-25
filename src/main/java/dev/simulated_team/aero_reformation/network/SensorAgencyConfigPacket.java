package dev.simulated_team.aero_reformation.network;

import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.content.blocks.sensor_agency.SensorAgencyBlockEntity;
import dev.simulated_team.aero_reformation.content.blocks.sensor_agency.SensorAgencyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SensorAgencyConfigPacket(BlockPos pos, int gimbalPrimary, int gimbalSecondary, boolean gimbalInverted,
                                        int altLow, int altHigh, int velMax, boolean navInverted)
        implements CustomPacketPayload {

    public static final Type<SensorAgencyConfigPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "sensor_agency_config"));

    public static final StreamCodec<FriendlyByteBuf, SensorAgencyConfigPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> packet.write(buf),
                    buf -> read(buf));

    /** Config synced from server, consumed by SensorAgencyScreen on init. */
    public static SensorAgencyConfigPacket CLIENT_SYNC = null;
    /** Last known agency pos for sync-back. */
    public static BlockPos LAST_POS = null;

    public static SensorAgencyConfigPacket fromConfig(BlockPos pos, SensorAgencyConfig config) {
        return new SensorAgencyConfigPacket(pos, config.gimbalPrimaryLimit, config.gimbalSecondaryLimit,
                config.gimbalInverted, config.altitudeLowWorld, config.altitudeHighWorld,
                config.velocityMaxSpeed, config.navInverted);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(gimbalPrimary);
        buf.writeVarInt(gimbalSecondary);
        buf.writeBoolean(gimbalInverted);
        buf.writeVarInt(altLow);
        buf.writeVarInt(altHigh);
        buf.writeVarInt(velMax);
        buf.writeBoolean(navInverted);
    }

    public static SensorAgencyConfigPacket read(FriendlyByteBuf buf) {
        return new SensorAgencyConfigPacket(
                buf.readBlockPos(),
                buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public void apply(SensorAgencyBlockEntity be) {
        be.config.gimbalPrimaryLimit = gimbalPrimary;
        be.config.gimbalSecondaryLimit = gimbalSecondary;
        be.config.gimbalInverted = gimbalInverted;
        be.config.altitudeLowWorld = altLow;
        be.config.altitudeHighWorld = altHigh;
        be.config.velocityMaxSpeed = velMax;
        be.config.navInverted = navInverted;
        be.saveConfig();
    }

    public static void handleBidirectional(SensorAgencyConfigPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            var level = player.level();
            if (level.getBlockEntity(packet.pos) instanceof SensorAgencyBlockEntity be) {
                packet.apply(be);
            }
            CLIENT_SYNC = packet;
            LAST_POS = packet.pos;
        });
    }
}
