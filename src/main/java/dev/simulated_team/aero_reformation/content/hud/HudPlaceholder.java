package dev.simulated_team.aero_reformation.content.hud;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * A player-defined placeholder bound to a block's NBT value.
 * In entry text it is used as %name% and resolves to the live NBT value,
 * optionally transformed by a math expression (e.g. "+10").
 */
public class HudPlaceholder {
    /** Placeholder name WITHOUT the %...% wrappers, e.g. "alt" -> used as %alt%. */
    public String name = "";
    /** Binding target kind: "block" (block entity NBT) or "entity" (living entity NBT). */
    public String bindType = "block";
    /** Info source: "" (NBT value), "sensor" (live sensor data), "math" (placeholder
     *  arithmetic over %tokens%), "constant" (fixed value). */
    public String bindSource = "";
    /** Sensor kind when bindSource == "sensor" (e.g. "altitude_sensor"). */
    public String sensorType = "";
    /** Bound block position (block binds). */
    public BlockPos pos;
    /** Bound entity UUID (entity binds). */
    public UUID entityUuid;
    /** Dotted NBT path into the block entity / entity (e.g. "CustomData.Height"). */
    public String nbtPath = "";
    /** Math expression for bindSource == "math", or the fixed value for
     *  bindSource == "constant". */
    public String value = "";
    /** Optional math expression applied to the numeric value (x = the value), e.g. "+10". */
    public String math = "";
    /** Optional remark shown in the placeholder list. */
    public String desc = "";

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("bindType", bindType);
        tag.putString("bindSource", bindSource);
        tag.putString("sensorType", sensorType);
        if (pos != null) tag.putLong("pos", pos.asLong());
        if (entityUuid != null) tag.putString("entityUuid", entityUuid.toString());
        tag.putString("nbtPath", nbtPath);
        tag.putString("value", value);
        tag.putString("math", math);
        tag.putString("desc", desc);
        return tag;
    }

    public static HudPlaceholder fromNBT(CompoundTag tag) {
        try {
            HudPlaceholder p = new HudPlaceholder();
            p.name = tag.getString("name");
            p.bindType = tag.getString("bindType");
            if (p.bindType.isEmpty()) p.bindType = "block";
            p.bindSource = tag.getString("bindSource");
            p.sensorType = tag.getString("sensorType");
            if (tag.contains("pos")) p.pos = BlockPos.of(tag.getLong("pos"));
            p.entityUuid = tag.contains("entityUuid")
                    ? UUID.fromString(tag.getString("entityUuid")) : null;
            p.nbtPath = tag.getString("nbtPath");
            p.value = tag.getString("value");
            p.math = tag.getString("math");
            p.desc = tag.getString("desc");
            return p;
        } catch (Exception ex) {
            // Corrupt/foreign data must never crash login; skip the placeholder.
            return null;
        }
    }

    public HudPlaceholder copy() {
        HudPlaceholder p = new HudPlaceholder();
        p.name = name;
        p.bindType = bindType;
        p.bindSource = bindSource;
        p.sensorType = sensorType;
        p.pos = pos;
        p.entityUuid = entityUuid;
        p.nbtPath = nbtPath;
        p.value = value;
        p.math = math;
        p.desc = desc;
        return p;
    }
}
