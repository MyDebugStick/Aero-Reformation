package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Persists anchor data across world reloads.
 *
 * <p>Holds, per anchored SubLevel: the SubLevel UUID, the last known world position of
 * the marker, and — critically — the <b>anchor block position</b> that owns the binding.
 *
 * <p>The block position is what makes re-attachment possible: the in-memory {@code ANCHORS}
 * map is keyed by anchor block position, while everything Sable persists is keyed by
 * SubLevel UUID. Without the position recorded here, a marker rebuilt from this file
 * (the WARMUP state) can never be promoted back onto a live anchor, because nothing
 * remembers where that anchor was.
 */
public class AnchorSavedData extends SavedData {
    private static final String ID = "aero_reformation_anchors";

    public record StoredEntry(UUID subLevelId, double worldX, double worldY, double worldZ,
                              @Nullable String markerName, int radius, @Nullable BlockPos anchorPos) {}

    private final Map<UUID, StoredEntry> entries = new LinkedHashMap<>();

    public static AnchorSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public static final Factory<AnchorSavedData> FACTORY = new Factory<>(
            AnchorSavedData::new,
            AnchorSavedData::load,
            null
    );

    public void add(UUID subLevelId, double wx, double wy, double wz) {
        add(subLevelId, wx, wy, wz, null, 2, null);
    }

    public void add(UUID subLevelId, double wx, double wy, double wz,
                    @Nullable String markerName, int radius) {
        add(subLevelId, wx, wy, wz, markerName, radius, null);
    }

    /**
     * Full update. {@code anchorPos} is only overwritten when a non-null value is supplied,
     * so the periodic position-only refresh from {@code tick()} cannot wipe it.
     */
    public void add(UUID subLevelId, double wx, double wy, double wz,
                    @Nullable String markerName, int radius, @Nullable BlockPos anchorPos) {
        StoredEntry old = entries.get(subLevelId);
        String name = markerName != null ? markerName : (old != null ? old.markerName() : null);
        int r = old != null ? old.radius() : radius;
        BlockPos pos = anchorPos != null ? anchorPos : (old != null ? old.anchorPos() : null);
        entries.put(subLevelId, new StoredEntry(subLevelId, wx, wy, wz, name, r, pos));
        setDirty();
    }

    /** Update only the marker name (does not change position). */
    public void setMarkerName(UUID subLevelId, String name) {
        StoredEntry e = entries.get(subLevelId);
        if (e != null) {
            entries.put(subLevelId, new StoredEntry(subLevelId, e.worldX(), e.worldY(), e.worldZ(),
                    name, e.radius(), e.anchorPos()));
            setDirty();
        }
    }

    /** Update only the radius. */
    public void setRadius(UUID subLevelId, int radius) {
        StoredEntry e = entries.get(subLevelId);
        if (e != null) {
            entries.put(subLevelId, new StoredEntry(subLevelId, e.worldX(), e.worldY(), e.worldZ(),
                    e.markerName(), radius, e.anchorPos()));
            setDirty();
        }
    }

    /** Remember which anchor block owns this SubLevel binding. */
    public void setAnchorPos(UUID subLevelId, BlockPos anchorPos) {
        StoredEntry e = entries.get(subLevelId);
        if (e != null) {
            entries.put(subLevelId, new StoredEntry(subLevelId, e.worldX(), e.worldY(), e.worldZ(),
                    e.markerName(), e.radius(), anchorPos));
            setDirty();
        }
    }

    public void remove(UUID subLevelId) {
        entries.remove(subLevelId);
        setDirty();
    }

    public void clearAll() {
        entries.clear();
        setDirty();
    }

    public Collection<StoredEntry> getEntries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    @Nullable
    public StoredEntry getEntry(UUID subLevelId) {
        return entries.get(subLevelId);
    }

    public int size() { return entries.size(); }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (StoredEntry e : entries.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("uuid", e.subLevelId());
            t.putDouble("x", e.worldX());
            t.putDouble("y", e.worldY());
            t.putDouble("z", e.worldZ());
            if (e.markerName() != null) t.putString("name", e.markerName());
            t.putInt("radius", e.radius());
            if (e.anchorPos() != null) {
                t.putInt("ax", e.anchorPos().getX());
                t.putInt("ay", e.anchorPos().getY());
                t.putInt("az", e.anchorPos().getZ());
            }
            list.add(t);
        }
        tag.put("anchors", list);
        return tag;
    }

    private static AnchorSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        AnchorSavedData data = new AnchorSavedData();
        ListTag list = tag.getList("anchors", ListTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            UUID id = t.getUUID("uuid");
            String name = t.contains("name") ? t.getString("name") : null;
            int radius = t.contains("radius") ? t.getInt("radius") : 2;
            // Older saves have no anchor position; a null here simply means the entry
            // cannot be auto-promoted and must wait for its block entity to load.
            BlockPos anchorPos = t.contains("ax")
                    ? new BlockPos(t.getInt("ax"), t.getInt("ay"), t.getInt("az"))
                    : null;
            data.entries.put(id, new StoredEntry(id,
                    t.getDouble("x"), t.getDouble("y"), t.getDouble("z"),
                    name, radius, anchorPos));
        }
        return data;
    }
}
