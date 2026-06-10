package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import dev.simulated_team.aero_reformation.content.items.ethereal_key.EtherealKeyItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Invisible entity that follows a SubLevel's world position.
 * Immune to damage, /kill, and entity clearing — only removable via our own {@link #forceDiscard()}.
 */
public class AnchorMarkerEntity extends Entity {

    private static final String TAG_SUBLEVEL_UUID = "aero_sublevel_id";
    private static final String TAG_HIDDEN = "aero_hidden";
    private static final EntityDataAccessor<Boolean> DATA_HIDDEN =
            SynchedEntityData.defineId(AnchorMarkerEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID subLevelUUID;
    private boolean forceRemoval;

    public AnchorMarkerEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public void forceDiscard() {
        this.forceRemoval = true;
        // Clear hidden state when marker is discarded
        EtherealKeyItem.HIDDEN_SUBLEVELS.remove(subLevelUUID);
        discard();
    }

    public void setHidden(boolean hidden) {
        this.entityData.set(DATA_HIDDEN, hidden);
        if (hidden && subLevelUUID != null) {
            EtherealKeyItem.HIDDEN_SUBLEVELS.add(subLevelUUID);
        } else if (!hidden && subLevelUUID != null) {
            EtherealKeyItem.HIDDEN_SUBLEVELS.remove(subLevelUUID);
        }
    }

    public boolean isHidden() {
        return this.entityData.get(DATA_HIDDEN);
    }

    @Override
    public boolean isInvisible() {
        return isHidden();
    }

    @Override
    public boolean isInvisibleTo(Player player) {
        return isHidden();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!forceRemoval && reason != RemovalReason.CHANGED_DIMENSION) return;
        forceRemoval = false;
        super.remove(reason);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    public void setSubLevelUUID(UUID id) { this.subLevelUUID = id; }
    public UUID getSubLevelUUID() { return subLevelUUID; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HIDDEN, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID(TAG_SUBLEVEL_UUID))
            subLevelUUID = tag.getUUID(TAG_SUBLEVEL_UUID);
        if (tag.contains(TAG_HIDDEN))
            setHidden(tag.getBoolean(TAG_HIDDEN));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (subLevelUUID != null)
            tag.putUUID(TAG_SUBLEVEL_UUID, subLevelUUID);
        tag.putBoolean(TAG_HIDDEN, isHidden());
    }

    @Override
    public void tick() {}

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public int getTeamColor() {
        return 0x64B4FF;
    }
}
