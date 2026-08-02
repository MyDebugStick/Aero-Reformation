package dev.simulated_team.aero_reformation.network;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-side shared storage of HUD presets keyed by the helmet item id. Any
 * player can save a preset under a helmet id and any other player wearing the
 * same helmet can load and apply it. Persisted into the overworld's saved data.
 */
public class HudPresetSavedData extends SavedData {

    private static final String DATA_NAME = "aero_reformation_hud_presets";

    private final Map<String, CompoundTag> presets = new HashMap<>();

    public static HudPresetSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(HudPresetSavedData::new, HudPresetSavedData::load), DATA_NAME);
    }

    public CompoundTag get(String helmetId) {
        return presets.get(helmetId);
    }

    public void set(String helmetId, CompoundTag preset) {
        presets.put(helmetId, preset);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<String, CompoundTag> e : presets.entrySet()) {
            all.put(e.getKey(), e.getValue());
        }
        tag.put("presets", all);
        return tag;
    }

    public static HudPresetSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        HudPresetSavedData data = new HudPresetSavedData();
        CompoundTag all = tag.getCompound("presets");
        for (String key : all.getAllKeys()) {
            data.presets.put(key, all.getCompound(key));
        }
        return data;
    }
}
