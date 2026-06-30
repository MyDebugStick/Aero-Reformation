package dev.simulated_team.aero_reformation.particles;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public class RcsSmokeTransitionType extends ParticleType<RcsSmokeTransitionData> {

    public RcsSmokeTransitionType() {
        super(false);
    }

    @Override
    public MapCodec<RcsSmokeTransitionData> codec() {
        return RcsSmokeTransitionData.CODEC;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, RcsSmokeTransitionData> streamCodec() {
        return RcsSmokeTransitionData.STREAM_CODEC;
    }
}
