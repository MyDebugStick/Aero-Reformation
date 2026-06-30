package dev.simulated_team.aero_reformation.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.joml.Vector3d;

/**
 * Particle data carrying velocity direction, speed multiplier, and type.
 */
public record RcsParticleData(
        boolean electric,
        double dirX, double dirY, double dirZ,
        float speed,
        float spread
) implements ParticleOptions {

    public static final MapCodec<RcsParticleData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.fieldOf("electric").forGetter(RcsParticleData::electric),
            Codec.DOUBLE.fieldOf("dx").forGetter(RcsParticleData::dirX),
            Codec.DOUBLE.fieldOf("dy").forGetter(RcsParticleData::dirY),
            Codec.DOUBLE.fieldOf("dz").forGetter(RcsParticleData::dirZ),
            Codec.FLOAT.fieldOf("speed").forGetter(RcsParticleData::speed),
            Codec.FLOAT.fieldOf("spread").forGetter(RcsParticleData::spread)
    ).apply(i, RcsParticleData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RcsParticleData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RcsParticleData::electric,
            ByteBufCodecs.DOUBLE, RcsParticleData::dirX,
            ByteBufCodecs.DOUBLE, RcsParticleData::dirY,
            ByteBufCodecs.DOUBLE, RcsParticleData::dirZ,
            ByteBufCodecs.FLOAT, RcsParticleData::speed,
            ByteBufCodecs.FLOAT, RcsParticleData::spread,
            RcsParticleData::new
    );

    @Override
    public net.minecraft.core.particles.ParticleType<?> getType() {
        return electric
                ? dev.simulated_team.aero_reformation.registrate.AeroParticleTypes.RCS_PLASMA.get()
                : dev.simulated_team.aero_reformation.registrate.AeroParticleTypes.RCS_PLUME.get();
    }
}
