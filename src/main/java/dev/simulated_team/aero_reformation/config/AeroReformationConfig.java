package dev.simulated_team.aero_reformation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class AeroReformationConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LEVITITE_GOLD_PICKAXE;
    public static final ModConfigSpec.DoubleValue REDSTONE_SPRING_STRESS_CAPACITY;

    public static final ModConfigSpec.DoubleValue RCS_FUEL_CONSUMPTION;
    public static final ModConfigSpec.DoubleValue RCS_ELECTRIC_EFFICIENCY;
    public static final ModConfigSpec.DoubleValue GUIDANCE_MAX_SPEED;
    public static final ModConfigSpec.IntValue MAX_ANCHOR_RADIUS;
    public static final ModConfigSpec.BooleanValue FILTER_PATCH_ENABLED;
    public static final ModConfigSpec.DoubleValue ANCHOR_TRACKING_RANGE;
    public static final ModConfigSpec.BooleanValue RUNAWAY_PROTECTION_ENABLED;
    public static final ModConfigSpec.DoubleValue RUNAWAY_MAX_SPEED;
    public static final ModConfigSpec.IntValue RUNAWAY_STRIKES_TO_KILL;
    public static final ModConfigSpec.IntValue RUNAWAY_STRIKE_WINDOW_TICKS;
    public static final ModConfigSpec.BooleanValue RADAR_ENABLED;
    public static final ModConfigSpec.IntValue RADAR_SCAN_SPLIT_SIZE;
    public static final ModConfigSpec.IntValue RADAR_SYNC_INTERVAL;
    public static final ModConfigSpec.IntValue RADAR_MAX_SYNCED_TRACKS;
    public static final ModConfigSpec.BooleanValue COPYCAT_ULTIMINE_ENABLED;

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
        GUIDANCE_MAX_SPEED = BUILDER
                .comment("Maximum speed (m/s) for guidance warhead. RCS will throttle forward thrust above this speed. Default 20.")
                .defineInRange("guidanceMaxSpeed", 20.0, 1.0, 1000.0);
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
        RUNAWAY_PROTECTION_ENABLED = BUILDER
                .comment("Master switch for the runaway self-destruct guard on anchored SubLevels.",
                        "When a craft starts moving absurd distances per tick the guard tears its anchors down",
                        "to stop it from stalling the server. Default true.")
                .define("runawayProtectionEnabled", true);
        RUNAWAY_MAX_SPEED = BUILDER
                .comment("Blocks per tick above which a move counts as one runaway strike. Default 500.",
                        "A single strike is never fatal — the guard requires several inside a short window,",
                        "so an ordinary teleport will not destroy a healthy craft.")
                .defineInRange("runawayMaxSpeed", 500.0, 16.0, Double.MAX_VALUE);
        RUNAWAY_STRIKES_TO_KILL = BUILDER
                .comment("Strikes needed inside the window before the craft self-destructs. Default 3.")
                .defineInRange("runawayStrikesToKill", 3, 2, 100);
        RUNAWAY_STRIKE_WINDOW_TICKS = BUILDER
                .comment("Length in ticks of the rolling window that strikes are counted over. Default 20.")
                .defineInRange("runawayStrikeWindowTicks", 20, 2, 200);
        BUILDER.pop();

        BUILDER.push("Create Radar Fix");
        RADAR_ENABLED = BUILDER
                .comment("Master switch for Create Radar performance optimizations (default false).",
                        "When enabled: improves AABB scan splitting, throttles monitor sync, caps track count.")
                .define("radarFixEnabled", false);
        RADAR_SCAN_SPLIT_SIZE = BUILDER
                .comment("Sub-AABB chunk size for radar entity scanning. Larger = fewer queries. Default 512 (range 256-1024).")
                .defineInRange("radarScanSplitSize", 512, 256, 1024);
        RADAR_SYNC_INTERVAL = BUILDER
                .comment("Ticks between radar monitor syncs to clients. Default 10 (original 5).")
                .defineInRange("radarSyncInterval", 10, 5, 40);
        RADAR_MAX_SYNCED_TRACKS = BUILDER
                .comment("Maximum number of radar tracks synced per monitor per update. Default 50.")
                .defineInRange("radarMaxSyncedTracks", 50, 10, 500);
        BUILDER.pop();

        BUILDER.push("Copycat Ultimine");
        COPYCAT_ULTIMINE_ENABLED = BUILDER
                .comment("Master switch for FTB Ultimine copycat block chain right-click compatibility.",
                        "When enabled: right-clicking a copycat block with a BlockItem or Wrench while holding",
                        "the Ultimine key will apply the action to all adjacent copycat blocks with the same material.")
                .define("copycatUltimineEnabled", true);
        BUILDER.pop();

        CONFIG_SPEC = BUILDER.build();
    }

    public static final ModConfigSpec CONFIG_SPEC;

    // Cached values
    public static boolean levititeGoldPickaxeOnly = true;
    public static double redstoneSpringStressCapacity = 8.0;
    public static double rcsFuelConsumption = 5000.0;
    public static double rcsElectricEfficiency = 25.0;
    public static double guidanceMaxSpeed = 20.0;
    public static boolean filterPatchEnabled = true;
    public static int maxAnchorRadius = 5;
    public static double anchorTrackingRange = 1024.0;
    public static boolean runawayProtectionEnabled = true;
    public static double runawayMaxSpeed = 500.0;
    public static int runawayStrikesToKill = 3;
    public static int runawayStrikeWindowTicks = 20;
    public static boolean radarEnabled = false;
    public static int radarScanSplitSize = 512;
    public static int radarSyncInterval = 10;
    public static int radarMaxSyncedTracks = 50;
    public static boolean copycatUltimineEnabled = true;

    public static void refresh() {
        levititeGoldPickaxeOnly = LEVITITE_GOLD_PICKAXE.get();
        redstoneSpringStressCapacity = REDSTONE_SPRING_STRESS_CAPACITY.get();
        rcsFuelConsumption = RCS_FUEL_CONSUMPTION.get();
        rcsElectricEfficiency = RCS_ELECTRIC_EFFICIENCY.get();
        guidanceMaxSpeed = GUIDANCE_MAX_SPEED.get();
        filterPatchEnabled = FILTER_PATCH_ENABLED.get();
        maxAnchorRadius = MAX_ANCHOR_RADIUS.get();
        anchorTrackingRange = ANCHOR_TRACKING_RANGE.get();
        runawayProtectionEnabled = RUNAWAY_PROTECTION_ENABLED.get();
        runawayMaxSpeed = RUNAWAY_MAX_SPEED.get();
        runawayStrikesToKill = RUNAWAY_STRIKES_TO_KILL.get();
        runawayStrikeWindowTicks = RUNAWAY_STRIKE_WINDOW_TICKS.get();
        radarEnabled = RADAR_ENABLED.get();
        radarScanSplitSize = RADAR_SCAN_SPLIT_SIZE.get();
        radarSyncInterval = RADAR_SYNC_INTERVAL.get();
        radarMaxSyncedTracks = RADAR_MAX_SYNCED_TRACKS.get();
        copycatUltimineEnabled = COPYCAT_ULTIMINE_ENABLED.get();
    }
}
