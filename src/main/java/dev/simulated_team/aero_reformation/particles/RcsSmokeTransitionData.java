package dev.simulated_team.aero_reformation.particles;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Minimal particle data for red-to-gray smoke transition particles.
 */
public class RcsSmokeTransitionData implements ParticleOptions {

    public static final MapCodec<RcsSmokeTransitionData> CODEC = MapCodec.unit(new RcsSmokeTransitionData());
    public static final StreamCodec<? super RegistryFriendlyByteBuf, RcsSmokeTransitionData> STREAM_CODEC =
            StreamCodec.unit(new RcsSmokeTransitionData());

    @Override
    public ParticleType<?> getType() {
        return dev.simulated_team.aero_reformation.registrate.AeroParticleTypes.RCS_SMOKE_TRANSITION.get();
    }
}
