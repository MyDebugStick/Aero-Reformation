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

    public record StoredEntry(UUID subLevelId, double worldX, double worldY, double worldZ,
                              @org.jetbrains.annotations.Nullable String markerName, int radius,
                              int anchorX, int anchorY, int anchorZ) {
        public BlockPos anchorPos() {
            return new BlockPos(anchorX, anchorY, anchorZ);
        }
        public boolean hasAnchorPos() {
            return anchorX != Integer.MAX_VALUE;
        }
    }

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
        add(subLevelId, wx, wy, wz, null, 2, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public void add(UUID subLevelId, double wx, double wy, double wz, @org.jetbrains.annotations.Nullable String markerName, int radius) {
        add(subLevelId, wx, wy, wz, markerName, radius, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public void add(UUID subLevelId, double wx, double wy, double wz, @org.jetbrains.annotations.Nullable String markerName, int radius,
                    int ax, int ay, int az) {
        StoredEntry old = entries.get(subLevelId);
        String name = markerName != null ? markerName : (old != null ? old.markerName : null);
        int r = old != null ? old.radius : radius;
        // Preserve old anchor pos if not provided and old entry had one
        int savedAx = (ax != Integer.MAX_VALUE || old == null) ? ax : old.anchorX;
        int savedAy = (ay != Integer.MAX_VALUE || old == null) ? ay : old.anchorY;
        int savedAz = (az != Integer.MAX_VALUE || old == null) ? az : old.anchorZ;
        entries.put(subLevelId, new StoredEntry(subLevelId, wx, wy, wz, name, r, savedAx, savedAy, savedAz));
        setDirty();
    }

    /** Update only the marker name (does not change position). */
    public void setMarkerName(UUID subLevelId, String name) {
        StoredEntry e = entries.get(subLevelId);
        if (e != null) {
            entries.put(subLevelId, new StoredEntry(subLevelId, e.worldX, e.worldY, e.worldZ, name, e.radius,
                    e.anchorX, e.anchorY, e.anchorZ));
            setDirty();
        }
    }

    /** Update only the radius. */
    public void setRadius(UUID subLevelId, int radius) {
        StoredEntry e = entries.get(subLevelId);
        if (e != null) {
            entries.put(subLevelId, new StoredEntry(subLevelId, e.worldX, e.worldY, e.worldZ, e.markerName, radius,
                    e.anchorX, e.anchorY, e.anchorZ));
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
            if (e.hasAnchorPos()) {
                t.putInt("ax", e.anchorX);
                t.putInt("ay", e.anchorY);
                t.putInt("az", e.anchorZ);
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
            int ax = t.contains("ax") ? t.getInt("ax") : Integer.MAX_VALUE;
            int ay = t.contains("ay") ? t.getInt("ay") : Integer.MAX_VALUE;
            int az = t.contains("az") ? t.getInt("az") : Integer.MAX_VALUE;
            data.entries.put(id, new StoredEntry(id,
                    t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), name, radius, ax, ay, az));
        }
        return data;
    }
}
