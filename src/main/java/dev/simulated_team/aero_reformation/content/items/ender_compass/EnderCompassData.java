package dev.simulated_team.aero_reformation.content.items.ender_compass;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

public record EnderCompassData(String channel, Optional<GlobalPos> target) {

    public static final Codec<EnderCompassData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("channel").forGetter(EnderCompassData::channel),
                    GlobalPos.CODEC.optionalFieldOf("target").forGetter(EnderCompassData::target)
            ).apply(instance, EnderCompassData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EnderCompassData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, EnderCompassData::channel,
                    ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), EnderCompassData::target,
                    EnderCompassData::new);

    public static final EnderCompassData EMPTY = new EnderCompassData("", Optional.empty());

    public boolean hasChannel() { return channel != null && !channel.isEmpty(); }
}
