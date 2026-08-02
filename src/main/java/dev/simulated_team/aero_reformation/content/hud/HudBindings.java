package dev.simulated_team.aero_reformation.content.hud;

import dev.simulated_team.aero_reformation.network.GoggleMonitorSyncPacket;
import dev.simulated_team.aero_reformation.network.HudNbtSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Detects what live info a bound block can provide (sensor data, block-state
 * properties, block name) and resolves the current display value for an entry.
 */
public final class HudBindings {

    /** A selectable info source for a bound block. */
    public record BindOption(String source, String key, String label) {}

    private static final Map<String, ResourceLocation> SENSOR_TYPES = Map.of(
            "altitude_sensor", ResourceLocation.parse("simulated:altitude_sensor"),
            "velocity_sensor", ResourceLocation.parse("simulated:velocity_sensor"),
            "gimbal_sensor", ResourceLocation.parse("simulated:gimbal_sensor"),
            "nav_table", ResourceLocation.parse("simulated:navigation_table"),
            "redstone_link", ResourceLocation.parse("create:redstone_link")
    );

    private HudBindings() {}

    private static final java.util.regex.Pattern TRAILING_ZEROS = java.util.regex.Pattern.compile("0+$");
    private static final java.util.regex.Pattern TRAILING_DOT = java.util.regex.Pattern.compile("\\.$");

    /** Format a double: integers as-is, otherwise up to 3 decimals with trailing zeros stripped. */
    public static String formatNumber(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return String.valueOf(v);
        if (v == Math.rint(v)) return String.valueOf((long) v);
        String s = String.format(java.util.Locale.ROOT, "%.3f", v);
        if (s.contains(".")) {
            s = TRAILING_ZEROS.matcher(s).replaceAll("");
            s = TRAILING_DOT.matcher(s).replaceAll("");
        }
        return s;
    }

    /** Parse and format a numeric string; returns null if it is not numeric. */
    public static String formatNumber(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return formatNumber(Double.parseDouble(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String sensorName(String type) {
        return switch (type) {
            case "altitude_sensor" -> "高度传感器";
            case "velocity_sensor" -> "速度传感器";
            case "gimbal_sensor" -> "姿态分析仪";
            case "nav_table" -> "导航台";
            case "redstone_link" -> "无线终端";
            default -> type;
        };
    }

    /** All info sources a block provides: sensor first, then state properties, then block name. */
    public static List<BindOption> detectOptions(Level level, BlockPos pos) {
        List<BindOption> list = new ArrayList<>();
        BlockState state = level.getBlockState(pos);
        String sensor = detectSensorType(state);
        if (!sensor.isEmpty()) {
            list.add(new BindOption("sensor", sensor, sensorName(sensor) + "（实时）"));
        }
        for (Map.Entry<net.minecraft.world.level.block.state.properties.Property<?>, Comparable<?>> e : state.getValues().entrySet()) {
            list.add(new BindOption("state", e.getKey().getName(), "状态 " + e.getKey().getName()));
        }
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        list.add(new BindOption("block", "id", "方块 " + key));
        return list;
    }

    /** Default option: the live sensor if present, else the first state property, else the block name. */
    public static BindOption defaultOption(List<BindOption> options) {
        for (BindOption o : options) {
            if (o.source().equals("sensor")) return o;
        }
        for (BindOption o : options) {
            if (o.source().equals("state")) return o;
        }
        return options.isEmpty() ? null : options.get(options.size() - 1);
    }

    /** Live display value for a bound entry. */
    public static String getDisplayValue(Level level, HudEntry e) {
        if (e.bindPos == null || e.bindSource.isEmpty()) return "";
        return switch (e.bindSource) {
            case "sensor" -> sensorValue(e.bindKey, e.bindPos);
            case "state" -> stateValue(level, e.bindPos, e.bindKey);
            case "block" -> blockName(level, e.bindPos);
            case "nbt" -> {
                UUID eid = e.bindEntityUuid;
                BlockPos p = eid != null ? BlockPos.ZERO : e.bindPos;
                String v = HudNbtSyncPacket.CLIENT_VALUES.get(
                        new HudNbtSyncPacket.NbtKey(p, e.bindKey, eid));
                if (v == null) yield "?";
                // Fine numeric values keep up to 3 decimals; ids/strings get localized
                String num = formatNumber(v);
                yield num != null ? num : localize(v);
            }
            default -> "";
        };
    }

    /** Sensor kind of the block at the given position, or "" if it is not a sensor. */
    public static String detectSensorType(Level level, BlockPos pos) {
        return detectSensorType(level.getBlockState(pos));
    }

    private static String detectSensorType(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (Map.Entry<String, ResourceLocation> e : SENSOR_TYPES.entrySet()) {
            if (key.equals(e.getValue())) return e.getKey();
        }
        return "";
    }

    private static String sensorValue(String type, BlockPos pos) {
        int[] data = GoggleMonitorSyncPacket.CLIENT_DATA.get(pos);
        if (data == null) return "--";
        return formatSensor(type, data);
    }

    /**
     * Format raw sensor data as pure numbers (no unit text) so the value can be
     * used directly in math expressions. The gimbal sensor exposes both axes
     * space-separated; math picks the first number.
     */
    public static String formatSensor(String type, int[] data) {
        return switch (type) {
            case "altitude_sensor" -> formatNumber(data[0] / 100f);
            case "velocity_sensor" -> formatNumber(data[1] / 100f);
            case "gimbal_sensor" -> formatNumber(data[2] / 100f) + " " + formatNumber(data[3] / 100f);
            case "nav_table" -> formatNumber(data[0] / 100f);
            case "redstone_link" -> String.valueOf(data[0]);
            default -> "--";
        };
    }

    private static String stateValue(Level level, BlockPos pos, String propertyName) {
        if (level == null) return "?";
        BlockState state = level.getBlockState(pos);
        for (Map.Entry<net.minecraft.world.level.block.state.properties.Property<?>, Comparable<?>> e : state.getValues().entrySet()) {
            if (e.getKey().getName().equals(propertyName)) {
                return friendly(e.getValue());
            }
        }
        return "?";
    }

    private static String blockName(Level level, BlockPos pos) {
        if (level == null) return "?";
        return level.getBlockState(pos).getBlock().getName().getString();
    }

    private static String friendly(Object v) {
        if (v instanceof Boolean b) return b ? "开" : "关";
        if (v instanceof Enum<?> en) return en.name().toLowerCase(Locale.ROOT);
        if (v instanceof Number n) return formatNumber(n.doubleValue());
        return String.valueOf(v);
    }

    /** If a value looks like a registry id (item/block/entity), return its localized name; else return as-is. */
    public static String localize(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) return raw;
        var item = BuiltInRegistries.ITEM.getOptional(id);
        if (item.isPresent()) return item.get().getDescription().getString();
        var block = BuiltInRegistries.BLOCK.getOptional(id);
        if (block.isPresent()) return block.get().getName().getString();
        var ent = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (ent.isPresent()) return ent.get().getDescription().getString();
        return raw;
    }
}
