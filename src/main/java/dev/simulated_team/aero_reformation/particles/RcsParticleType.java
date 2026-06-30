package dev.simulated_team.aero_reformation.particles;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Particle type for RCS thruster plume (fuel) or plasma (electric).
 * Carries velocity multiplier and spread parameters.
 */
public class RcsParticleType extends ParticleType<RcsParticleData> {

    public RcsParticleType(boolean alwaysShow) {
        super(alwaysShow);
    }

    @Override
    public MapCodec<RcsParticleData> codec() {
        return RcsParticleData.CODEC;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, RcsParticleData> streamCodec() {
        return RcsParticleData.STREAM_CODEC;
    }
}
