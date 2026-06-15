package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Persists anchor data across world reloads: SubLevel UUID + last known world position.
 * On far-distance rejoin, the FakePlayer is spawned at the saved position
 * WITHOUT force-loading the BE's chunk (avoids deadlock with Sable).
 */
public class AnchorSavedData extends SavedData {
    private static final String ID = "aero_reformation_anchors";

    public record StoredEntry(UUID subLevelId, double worldX, double worldY, double worldZ, String markerName, int radius) {}

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
        add(subLevelId, wx, wy, wz, null, 2);
    }

    public void add(UUID subLevelId, double wx, double wy, double wz, @org.jetbrains.annotations.Nullable String markerName, int radius) {
        StoredEntry old = entries.get(subLevelId);
        String name = markerName != null ? markerName : (old != null ? old.markerName : null);
        int r = old != null ? old.radius : radius;
        entries.put(subLevelId, new StoredEntry(subLevelId, wx, wy, wz, name, r));
        setDirty();
    }

    /** Update only the marker name (does not change position). */
    public void setMarkerName(UUID subLevelId, String name) {
        StoredEntry e = entries.get(subLevelId);
        if (e != null) {
            entries.put(subLevelId, new StoredEntry(subLevelId, e.worldX, e.worldY, e.worldZ, name, e.radius));
            setDirty();
        }
    }

    /** Update only the radius. */
    public void setRadius(UUID subLevelId, int radius) {
        StoredEntry e = entries.get(subLevelId);
        if (e != null) {
            entries.put(subLevelId, new StoredEntry(subLevelId, e.worldX, e.worldY, e.worldZ, e.markerName, radius));
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

    @org.jetbrains.annotations.Nullable
    public StoredEntry getEntry(UUID subLevelId) {
        return entries.get(subLevelId);
    }

    public int size() { return entries.size(); }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (StoredEntry e : entries.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("uuid", e.subLevelId);
            t.putDouble("x", e.worldX);
            t.putDouble("y", e.worldY);
            t.putDouble("z", e.worldZ);
            if (e.markerName != null) t.putString("name", e.markerName);
            t.putInt("radius", e.radius);
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
            data.entries.put(id, new StoredEntry(id,
                    t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), name, radius));
        }
        return data;
    }
}
