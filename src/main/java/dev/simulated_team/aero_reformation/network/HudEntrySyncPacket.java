package dev.simulated_team.aero_reformation.network;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client -> server: the block binds of the player's HUD entries, split into
 * sensor positions (for GoggleMonitorSyncPacket live data) and NBT paths
 * (for HudNbtSyncPacket live values).
 */
public record HudEntrySyncPacket(List<BlockPos> sensorBinds, List<NbtBind> nbtBinds) implements CustomPacketPayload {

    /** A bound NBT path: pos + dotted path (+ entityUuid for a living entity). */
    public record NbtBind(BlockPos pos, String path, UUID entityUuid) {}

    public static final String KEY = "aero_reformation_hud_binds";
    public static final String NBT_KEY = "aero_reformation_hud_nbt_binds";

    public static final Type<HudEntrySyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "hud_entry_sync"));

    public static final StreamCodec<FriendlyByteBuf, HudEntrySyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.sensorBinds.size());
                        for (BlockPos pos : p.sensorBinds) buf.writeBlockPos(pos);
                        buf.writeVarInt(p.nbtBinds.size());
                        for (NbtBind b : p.nbtBinds) {
                            buf.writeBlockPos(b.pos());
                            buf.writeUtf(b.path());
                            buf.writeBoolean(b.entityUuid() != null);
                            if (b.entityUuid() != null) buf.writeUUID(b.entityUuid());
                        }
                    },
                    buf -> {
                        int ns = buf.readVarInt();
                        List<BlockPos> sensors = new ArrayList<>();
                        for (int i = 0; i < ns; i++) sensors.add(buf.readBlockPos());
                        int nn = buf.readVarInt();
                        List<NbtBind> nbt = new ArrayList<>();
                        for (int i = 0; i < nn; i++) {
                            BlockPos pos = buf.readBlockPos();
                            String path = buf.readUtf();
                            UUID uuid = buf.readBoolean() ? buf.readUUID() : null;
                            nbt.add(new NbtBind(pos, path, uuid));
                        }
                        return new HudEntrySyncPacket(sensors, nbt);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HudEntrySyncPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            CompoundTag persistent = sp.getPersistentData();

            // sensor binds (long array)
            long[] arr = new long[packet.sensorBinds.size()];
            for (int i = 0; i < packet.sensorBinds.size(); i++) {
                arr[i] = packet.sensorBinds.get(i).asLong();
            }
            if (arr.length == 0) {
                persistent.remove(KEY);
            } else {
                persistent.putLongArray(KEY, arr);
            }

            // nbt binds (list of {pos, path, entityUuid})
            if (packet.nbtBinds.isEmpty()) {
                persistent.remove(NBT_KEY);
            } else {
                ListTag list = new ListTag();
                for (NbtBind b : packet.nbtBinds) {
                    CompoundTag t = new CompoundTag();
                    t.putLong("pos", b.pos().asLong());
                    t.putString("path", b.path());
                    if (b.entityUuid() != null) {
                        t.putString("entityUuid", b.entityUuid().toString());
                    }
                    list.add(t);
                }
                persistent.put(NBT_KEY, list);
            }
        });
    }
}
