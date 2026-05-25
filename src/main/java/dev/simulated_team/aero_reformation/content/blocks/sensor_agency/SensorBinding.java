package dev.simulated_team.aero_reformation.content.blocks.sensor_agency;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record SensorBinding(
        List<BlockPos> altitude,
        List<BlockPos> velocity,
        List<BlockPos> gimbal,
        List<BlockPos> nav
) {
    public static final SensorBinding EMPTY = new SensorBinding(
            List.of(), List.of(), List.of(), List.of());

    public static final Codec<SensorBinding> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BlockPos.CODEC.listOf().optionalFieldOf("altitude", List.of()).forGetter(SensorBinding::altitude),
                    BlockPos.CODEC.listOf().optionalFieldOf("velocity", List.of()).forGetter(SensorBinding::velocity),
                    BlockPos.CODEC.listOf().optionalFieldOf("gimbal", List.of()).forGetter(SensorBinding::gimbal),
                    BlockPos.CODEC.listOf().optionalFieldOf("nav", List.of()).forGetter(SensorBinding::nav)
            ).apply(instance, SensorBinding::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SensorBinding> STREAM_CODEC =
            StreamCodec.of(
                    (buf, val) -> val.writeToBuf(buf),
                    SensorBinding::readFromBuf
            );

    public boolean isEmpty() {
        return altitude.isEmpty() && velocity.isEmpty() && gimbal.isEmpty() && nav.isEmpty();
    }

    public SensorBinding withAltitude(BlockPos pos) {
        if (altitude.contains(pos)) return this;
        List<BlockPos> list = new ArrayList<>(altitude);
        list.add(pos);
        return new SensorBinding(Collections.unmodifiableList(list), velocity, gimbal, nav);
    }

    public SensorBinding withVelocity(BlockPos pos) {
        if (velocity.contains(pos)) return this;
        List<BlockPos> list = new ArrayList<>(velocity);
        list.add(pos);
        return new SensorBinding(altitude, Collections.unmodifiableList(list), gimbal, nav);
    }

    public SensorBinding withGimbal(BlockPos pos) {
        if (gimbal.contains(pos)) return this;
        List<BlockPos> list = new ArrayList<>(gimbal);
        list.add(pos);
        return new SensorBinding(altitude, velocity, Collections.unmodifiableList(list), nav);
    }

    public SensorBinding withNav(BlockPos pos) {
        if (nav.contains(pos)) return this;
        List<BlockPos> list = new ArrayList<>(nav);
        list.add(pos);
        return new SensorBinding(altitude, velocity, gimbal, Collections.unmodifiableList(list));
    }

    private void writeToBuf(RegistryFriendlyByteBuf buf) {
        writeList(buf, altitude);
        writeList(buf, velocity);
        writeList(buf, gimbal);
        writeList(buf, nav);
    }

    private static void writeList(RegistryFriendlyByteBuf buf, List<BlockPos> list) {
        buf.writeVarInt(list.size());
        for (BlockPos pos : list) {
            buf.writeBlockPos(pos);
        }
    }

    private static SensorBinding readFromBuf(RegistryFriendlyByteBuf buf) {
        return new SensorBinding(
                readList(buf),
                readList(buf),
                readList(buf),
                readList(buf)
        );
    }

    private static List<BlockPos> readList(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<BlockPos> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buf.readBlockPos());
        }
        return Collections.unmodifiableList(list);
    }
}
