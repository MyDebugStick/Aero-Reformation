package dev.simulated_team.aero_reformation.content.hud;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * A single HUD entry: position / transform + content + style.
 * Pure client-side data, serialized into player persistent data.
 */
public class HudEntry {

    public enum Type { TEXT, BAR, LINE, SHAPE }

    public ResourceLocation id;
    public float x, y;           // top-left in GUI-scaled coordinates
    public float scale = 1.0F;
    public float rotation = 0.0F; // degrees
    public Type type = Type.TEXT;
    public String text = "新条目";
    public String suffix = ""; // extra content appended after the bound info (or after text when unbound)
    public int color = 0xFFFFFFFF;
    public int barWidth = 100;
    public int barHeight = 8;
    public float barMax = 100.0F; // BAR: fill ratio = resolved value / barMax
    public float endX, endY;   // LINE/SHAPE: endpoint (stored normalized, >= x/y)
    public int lineWidth = 2;
    public String shape = "rect";  // SHAPE: "rect" / "circle" / "triangle" / "diamond"
    public boolean filled = false; // SHAPE: hollow or solid
    public BlockPos bindPos;       // bound block position (null = not bound)
    public UUID bindEntityUuid;    // bound entity UUID (null = block bind)
    public String bindSource = ""; // "sensor" / "state" / "block" / "nbt"
    public String bindKey = "";    // sensor type or block-state property name
    public int bindColor = 0xFF55FF55; // color for the bound info part
    public boolean rainbow = false;    // color cycles over time
    public int alpha = 255;            // opacity 0-255
    public String anchor = "screen";   // "screen" / "horizon" / "world"
    public double worldX, worldY, worldZ; // world anchor (world mode), or offset to the physics body
    public double horizonDist = 24.0;  // horizon mode: distance in front of the player
    public String physBodyId = "";    // bound sable sublevel UUID (world mode)

    public HudEntry() {
        this.id = ResourceLocation.fromNamespaceAndPath(
                AeroReformation.MODID, UUID.randomUUID().toString().substring(0, 8));
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id.toString());
        tag.putFloat("x", x);
        tag.putFloat("y", y);
        tag.putFloat("scale", scale);
        tag.putFloat("rotation", rotation);
        tag.putString("type", type.name());
        tag.putString("text", text);
        tag.putString("suffix", suffix);
        tag.putInt("color", color);
        tag.putInt("barWidth", barWidth);
        tag.putInt("barHeight", barHeight);
        tag.putFloat("barMax", barMax);
        tag.putFloat("endX", endX);
        tag.putFloat("endY", endY);
        tag.putInt("lineWidth", lineWidth);
        tag.putString("shape", shape);
        tag.putBoolean("filled", filled);
        if (bindEntityUuid != null) {
            tag.putString("bindEntityUuid", bindEntityUuid.toString());
        }
        if (bindPos != null) {
            tag.putLong("bindPos", bindPos.asLong());
            tag.putString("bindSource", bindSource);
            tag.putString("bindKey", bindKey);
            tag.putInt("bindColor", bindColor);
        }
        tag.putBoolean("rainbow", rainbow);
        tag.putInt("alpha", alpha);
        tag.putString("anchor", anchor);
        tag.putDouble("worldX", worldX);
        tag.putDouble("worldY", worldY);
        tag.putDouble("worldZ", worldZ);
        tag.putDouble("horizonDist", horizonDist);
        tag.putString("physBodyId", physBodyId);
        return tag;
    }

    public static HudEntry fromNBT(CompoundTag tag) {
        try {
            HudEntry e = new HudEntry();
            e.id = ResourceLocation.parse(tag.getString("id"));
            e.x = tag.getFloat("x");
            e.y = tag.getFloat("y");
            e.scale = tag.getFloat("scale");
            e.rotation = tag.getFloat("rotation");
            e.type = Type.valueOf(tag.getString("type"));
            e.text = tag.getString("text");
            e.suffix = tag.getString("suffix");
            e.color = tag.getInt("color");
            e.barWidth = tag.getInt("barWidth");
            e.barHeight = tag.getInt("barHeight");
            e.barMax = tag.contains("barMax") ? tag.getFloat("barMax") : 100.0F;
            e.endX = tag.getFloat("endX");
            e.endY = tag.getFloat("endY");
            e.lineWidth = tag.getInt("lineWidth");
            e.shape = tag.getString("shape");
            if (e.shape.isEmpty()) e.shape = "rect";
            e.filled = tag.getBoolean("filled");
            e.bindEntityUuid = tag.contains("bindEntityUuid")
                    ? UUID.fromString(tag.getString("bindEntityUuid")) : null;
            if (tag.contains("bindPos")) {
                e.bindPos = BlockPos.of(tag.getLong("bindPos"));
                e.bindSource = tag.getString("bindSource");
                e.bindKey = tag.getString("bindKey");
                e.bindColor = tag.getInt("bindColor");
            }
            e.rainbow = tag.getBoolean("rainbow");
            e.alpha = tag.contains("alpha") ? tag.getInt("alpha") : 255;
            e.anchor = tag.getString("anchor");
            if (e.anchor.isEmpty()) e.anchor = "screen";
            e.worldX = tag.getDouble("worldX");
            e.worldY = tag.getDouble("worldY");
            e.worldZ = tag.getDouble("worldZ");
            e.horizonDist = tag.contains("horizonDist") ? tag.getDouble("horizonDist") : 24.0;
            e.physBodyId = tag.getString("physBodyId");
            return e;
        } catch (Exception ex) {
            return null;
        }
    }

    public HudEntry copy() {
        HudEntry e = new HudEntry();
        e.id = id;
        e.x = x;
        e.y = y;
        e.scale = scale;
        e.rotation = rotation;
        e.type = type;
        e.text = text;
        e.suffix = suffix;
        e.color = color;
        e.barWidth = barWidth;
        e.barHeight = barHeight;
        e.barMax = barMax;
        e.endX = endX;
        e.endY = endY;
        e.lineWidth = lineWidth;
        e.shape = shape;
        e.filled = filled;
        e.bindPos = bindPos;
        e.bindEntityUuid = bindEntityUuid;
        e.bindSource = bindSource;
        e.bindKey = bindKey;
        e.bindColor = bindColor;
        e.rainbow = rainbow;
        e.alpha = alpha;
        e.anchor = anchor;
        e.worldX = worldX;
        e.worldY = worldY;
        e.worldZ = worldZ;
        e.horizonDist = horizonDist;
        e.physBodyId = physBodyId;
        return e;
    }
}
