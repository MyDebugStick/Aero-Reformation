package dev.simulated_team.aero_reformation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class AeroReformationConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LEVITITE_GOLD_PICKAXE;
    public static final ModConfigSpec.DoubleValue REDSTONE_SPRING_STRESS_CAPACITY;

    static {
        BUILDER.push("Levitite Mining");
        LEVITITE_GOLD_PICKAXE = BUILDER
                .comment("If true, levitite and pearlescent_levitite can only be mined with a golden pickaxe.",
                        "Mining speed equals diamond pickaxe on obsidian. Efficiency enchantment has no effect.")
                .define("levititeGoldPickaxeOnly", true);
        BUILDER.pop();

        BUILDER.push("Redstone Spring");
        REDSTONE_SPRING_STRESS_CAPACITY = BUILDER
                .comment("Stress capacity (SU) of the Redstone Spring output. Default 8.0 (same as torsion spring).")
                .defineInRange("redstoneSpringStressCapacity", 8.0, 0.0, 1024.0);
        BUILDER.pop();

        CONFIG_SPEC = BUILDER.build();
    }

    public static final ModConfigSpec CONFIG_SPEC;

    // Cached values
    public static boolean levititeGoldPickaxeOnly = true;
    public static double redstoneSpringStressCapacity = 8.0;

    public static void refresh() {
        levititeGoldPickaxeOnly = LEVITITE_GOLD_PICKAXE.get();
        redstoneSpringStressCapacity = REDSTONE_SPRING_STRESS_CAPACITY.get();
    }
}
