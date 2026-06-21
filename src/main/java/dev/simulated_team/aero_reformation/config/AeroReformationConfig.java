package dev.simulated_team.aero_reformation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class AeroReformationConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LEVITITE_GOLD_PICKAXE;
    public static final ModConfigSpec.DoubleValue REDSTONE_SPRING_STRESS_CAPACITY;

    public static final ModConfigSpec.DoubleValue RCS_FUEL_CONSUMPTION;
    public static final ModConfigSpec.DoubleValue RCS_ELECTRIC_EFFICIENCY;
    public static final ModConfigSpec.IntValue MAX_ANCHOR_RADIUS;
    public static final ModConfigSpec.BooleanValue FILTER_PATCH_ENABLED;
    public static final ModConfigSpec.DoubleValue ANCHOR_TRACKING_RANGE;

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

        BUILDER.push("RCS Thruster");
        RCS_FUEL_CONSUMPTION = BUILDER
                .comment("Fuel consumption of RCS Thruster in pN per mB per tick. Higher = more efficient. Default 5000.")
                .defineInRange("rcsFuelConsumption", 5000.0, 1.0, 1000000.0);
        RCS_ELECTRIC_EFFICIENCY = BUILDER
                .comment("Electric efficiency of RCS Thruster in pN per FE per tick. Higher = more efficient. Default 25.")
                .defineInRange("rcsElectricEfficiency", 25.0, 1.0, 1000000.0);
        BUILDER.pop();

        BUILDER.push("Filter Patch");
        FILTER_PATCH_ENABLED = BUILDER
                .comment("If false, all filter patch mixins are disabled for compatibility.")
                .define("filterPatchEnabled", true);
        BUILDER.pop();

        BUILDER.push("Physics Anchor");
        MAX_ANCHOR_RADIUS = BUILDER
                .comment("Maximum chunk loading radius for the Physics Anchor (2 to this value). Default 5.")
                .defineInRange("maxAnchorRadius", 5, 4, 64);
        ANCHOR_TRACKING_RANGE = BUILDER
                .comment("Tracking range in blocks for anchored SubLevels. Higher values prevent SubLevels from being removed by Sable when far away. Default 1024.")
                .defineInRange("anchorTrackingRange", 1024.0, 64.0, Double.MAX_VALUE);
        BUILDER.pop();

        CONFIG_SPEC = BUILDER.build();
    }

    public static final ModConfigSpec CONFIG_SPEC;

    // Cached values
    public static boolean levititeGoldPickaxeOnly = true;
    public static double redstoneSpringStressCapacity = 8.0;
    public static double rcsFuelConsumption = 5000.0;
    public static double rcsElectricEfficiency = 25.0;
    public static boolean filterPatchEnabled = true;
    public static int maxAnchorRadius = 5;
    public static double anchorTrackingRange = 1024.0;

    public static void refresh() {
        levititeGoldPickaxeOnly = LEVITITE_GOLD_PICKAXE.get();
        redstoneSpringStressCapacity = REDSTONE_SPRING_STRESS_CAPACITY.get();
        rcsFuelConsumption = RCS_FUEL_CONSUMPTION.get();
        rcsElectricEfficiency = RCS_ELECTRIC_EFFICIENCY.get();
        filterPatchEnabled = FILTER_PATCH_ENABLED.get();
        maxAnchorRadius = MAX_ANCHOR_RADIUS.get();
        anchorTrackingRange = ANCHOR_TRACKING_RANGE.get();
    }
}
