package dev.simulated_team.aero_reformation.network;

import dev.simulated_team.aero_reformation.AeroReformation;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record GoggleMonitorSyncPacket(Map<BlockPos, int[]> data) implements CustomPacketPayload {

    public static final Type<GoggleMonitorSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "goggle_monitor_sync"));

    public static final StreamCodec<FriendlyByteBuf, GoggleMonitorSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> packet.write(buf),
                    buf -> read(buf));

    // Cached per-player data, updated by server packet
    public static final Map<BlockPos, int[]> CLIENT_DATA = new HashMap<>();

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(data.size());
        for (var e : data.entrySet()) {
            buf.writeBlockPos(e.getKey());
            int[] v = e.getValue();
            buf.writeVarInt(v.length);
            for (int i : v) buf.writeVarInt(i);
        }
    }

    public static GoggleMonitorSyncPacket read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Map<BlockPos, int[]> map = new HashMap<>();
        for (int i = 0; i < count; i++) {
            BlockPos pos = buf.readBlockPos();
            int len = buf.readVarInt();
            int[] vals = new int[len];
            for (int j = 0; j < len; j++) vals[j] = buf.readVarInt();
            map.put(pos, vals);
        }
        return new GoggleMonitorSyncPacket(map);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** Read sensor values directly from sensor BEs. Format: [alt*100, vel*100, gimX*100, gimZ*100] */
    public static GoggleMonitorSyncPacket fromPositions(Collection<BlockPos> positions, ServerLevel level) {
        Map<BlockPos, int[]> map = new HashMap<>();
        for (BlockPos pos : positions) {
            if (!level.isLoaded(pos)) continue;
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) continue;

            if (be instanceof RedstoneLinkBlockEntity link) {
                int signal = link.getReceivedSignal();
                map.put(pos, new int[]{signal, 0, 0, 0});
            } else if (be instanceof NavTableBlockEntity nav) {
                float dist = (float) nav.distanceToTarget();
                map.put(pos, new int[]{Math.round(dist * 100f), 0, 0, 0});
            } else if (be instanceof dev.simulated_team.simulated.content.blocks.altitude_sensor.AltitudeSensorBlockEntity a) {
                float alt = a.getWorldHeight();
                map.put(pos, new int[]{Math.round(alt * 100f), 0, 0, 0});
            } else if (be instanceof dev.simulated_team.simulated.content.blocks.velocity_sensor.VelocitySensorBlockEntity v) {
                float vel = Math.abs(v.getAdjustedVelocity());
                map.put(pos, new int[]{0, Math.round(vel * 100f), 0, 0});
            } else if (be instanceof dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlockEntity g) {
                float gx = (float) Math.toDegrees(g.getXAngle());
                float gz = (float) Math.toDegrees(g.getZAngle());
                map.put(pos, new int[]{0, 0, Math.round(gx * 100f), Math.round(gz * 100f)});
            }
        }
        return new GoggleMonitorSyncPacket(map);
    }

    public static void handle(GoggleMonitorSyncPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            CLIENT_DATA.clear();
            CLIENT_DATA.putAll(packet.data);
            AeroReformation.LOGGER.warn("AeroDebug GOGGLE: client received {} entries", packet.data.size());
        });
    }
}
