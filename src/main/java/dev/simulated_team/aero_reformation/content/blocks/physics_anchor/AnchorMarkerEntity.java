package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Invisible entity that follows a SubLevel's world position.
 * Each instance is bound to exactly one SubLevel via subLevelUUID.
 */
public class AnchorMarkerEntity extends Entity {

    private static final String TAG_SUBLEVEL_UUID = "aero_sublevel_id";
    private UUID subLevelUUID;

    public AnchorMarkerEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public void setSubLevelUUID(UUID id) { this.subLevelUUID = id; }
    public UUID getSubLevelUUID() { return subLevelUUID; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID(TAG_SUBLEVEL_UUID))
            subLevelUUID = tag.getUUID(TAG_SUBLEVEL_UUID);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (subLevelUUID != null)
            tag.putUUID(TAG_SUBLEVEL_UUID, subLevelUUID);
    }

    @Override
    public void tick() {
        // Position is set externally by AnchorChunkLoader
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return false;
    }

    @Override
    public int getTeamColor() {
        return 0x64B4FF; // light blue dot
    }
}
