package dev.simulated_team.aero_reformation.registrate;

import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.content.items.ender_compass.EnderCompassData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class AeroDataComponents {

    public static final DeferredRegister<DataComponentType<?>> REGISTER =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, AeroReformation.MODID);

    public static final Supplier<DataComponentType<EnderCompassData>> ENDER_COMPASS =
            REGISTER.register("ender_compass", () ->
                    DataComponentType.<EnderCompassData>builder()
                            .persistent(EnderCompassData.CODEC)
                            .networkSynchronized(EnderCompassData.STREAM_CODEC)
                            .build());

    public static final Supplier<DataComponentType<BlockPos>> BOUND_MASTER =
            REGISTER.register("bound_master", () ->
                    DataComponentType.<BlockPos>builder()
                            .persistent(BlockPos.CODEC)
                            .networkSynchronized(BlockPos.STREAM_CODEC)
                            .build());
}
