package dev.simulated_team.aero_reformation.registrate;

import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.particles.RcsParticleType;
import dev.simulated_team.aero_reformation.particles.RcsSmokeTransitionData;
import dev.simulated_team.aero_reformation.particles.RcsSmokeTransitionType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class AeroParticleTypes {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, AeroReformation.MODID);

    public static final Supplier<RcsParticleType> RCS_PLUME =
            PARTICLE_TYPES.register("rcs_plume", () -> new RcsParticleType(false));

    public static final Supplier<RcsParticleType> RCS_PLASMA =
            PARTICLE_TYPES.register("rcs_plasma", () -> new RcsParticleType(false));

    public static final Supplier<RcsSmokeTransitionType> RCS_SMOKE_TRANSITION =
            PARTICLE_TYPES.register("rcs_smoke_transition", () -> new RcsSmokeTransitionType());
}
