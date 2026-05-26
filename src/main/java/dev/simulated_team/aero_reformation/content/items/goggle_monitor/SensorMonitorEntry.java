package dev.simulated_team.aero_reformation.content.items.goggle_monitor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

public record SensorMonitorEntry(String name, String sensorType, BlockPos pos) {

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("type", sensorType);
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    public static SensorMonitorEntry fromNBT(CompoundTag tag) {
        try {
            String name = tag.getString("name");
            String type = tag.getString("type");
            if (name.isEmpty() || type.isEmpty()) return null;
            BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            return new SensorMonitorEntry(name, type, pos);
        } catch (Exception e) {
            return null;
        }
    }
    public static final Codec<SensorMonitorEntry> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("name").forGetter(SensorMonitorEntry::name),
                    Codec.STRING.fieldOf("type").forGetter(SensorMonitorEntry::sensorType),
                    BlockPos.CODEC.fieldOf("pos").forGetter(SensorMonitorEntry::pos)
            ).apply(instance, SensorMonitorEntry::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SensorMonitorEntry> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SensorMonitorEntry::name,
                    ByteBufCodecs.STRING_UTF8, SensorMonitorEntry::sensorType,
                    BlockPos.STREAM_CODEC, SensorMonitorEntry::pos,
                    SensorMonitorEntry::new);

    public static final Codec<List<SensorMonitorEntry>> LIST_CODEC =
            CODEC.listOf().xmap(ArrayList::new, list -> list);

    public static final StreamCodec<RegistryFriendlyByteBuf, List<SensorMonitorEntry>> LIST_STREAM_CODEC =
            STREAM_CODEC.apply(ByteBufCodecs.collection(ArrayList::new));
}
