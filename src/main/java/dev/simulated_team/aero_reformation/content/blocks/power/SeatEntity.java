package dev.simulated_team.aero_reformation.content.blocks.power;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SeatEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_BASE_YAW =
            SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_REDSTONE_DISABLED =
            SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CAMERA_LOCKED =
            SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_SUB_ROT_X =
            SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SUB_ROT_Y =
            SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SUB_ROT_Z =
            SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SUB_ROT_W =
            SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.FLOAT);

    private BlockPos blockPos = BlockPos.ZERO;

    public SeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void setBlockPos(BlockPos pos) {
        this.blockPos = pos;
    }

    public void setBaseYaw(float yaw) {
        this.entityData.set(DATA_BASE_YAW, yaw);
    }

    public float getBaseYaw() {
        return this.entityData.get(DATA_BASE_YAW);
    }

    public boolean isRedstoneDisabled() {
        return this.entityData.get(DATA_REDSTONE_DISABLED);
    }

    public boolean isCameraLocked() {
        return this.entityData.get(DATA_CAMERA_LOCKED);
    }

    public float getSubRotX() { return this.entityData.get(DATA_SUB_ROT_X); }
    public float getSubRotY() { return this.entityData.get(DATA_SUB_ROT_Y); }
    public float getSubRotZ() { return this.entityData.get(DATA_SUB_ROT_Z); }
    public float getSubRotW() { return this.entityData.get(DATA_SUB_ROT_W); }

    public void toggleRedstoneDisabled() {
        this.entityData.set(DATA_REDSTONE_DISABLED, !this.entityData.get(DATA_REDSTONE_DISABLED));
    }

    public void toggleCameraLocked() {
        this.entityData.set(DATA_CAMERA_LOCKED, !this.entityData.get(DATA_CAMERA_LOCKED));
    }

    public void attachToSubLevel(Level level, BlockPos pos) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel != null) {
            Vec3 localPos = subLevel.logicalPose().transformPositionInverse(this.position());
            this.setPos(localPos.x, localPos.y, localPos.z);
            ((EntityMovementExtension) this).sable$setTrackingSubLevel(subLevel);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.getPassengers().isEmpty()) {
            notifyRedstone();
            this.discard();
            return;
        }
        this.setDeltaMovement(Vec3.ZERO);

        // Sync sub-level rotation quaternion to client (for camera lock mode)
        if (!this.level().isClientSide) {
            SubLevel subLevel = ((EntityMovementExtension) this).sable$getTrackingSubLevel();
            if (subLevel == null && this.blockPos != null) {
                subLevel = Sable.HELPER.getContaining(this.level(), this.blockPos);
            }
            if (subLevel != null) {
                var rot = subLevel.logicalPose().orientation();
                this.entityData.set(DATA_SUB_ROT_X, (float) rot.x());
                this.entityData.set(DATA_SUB_ROT_Y, (float) rot.y());
                this.entityData.set(DATA_SUB_ROT_Z, (float) rot.z());
                this.entityData.set(DATA_SUB_ROT_W, (float) rot.w());
            } else {
                this.entityData.set(DATA_SUB_ROT_X, 0f);
                this.entityData.set(DATA_SUB_ROT_Y, 0f);
                this.entityData.set(DATA_SUB_ROT_Z, 0f);
                this.entityData.set(DATA_SUB_ROT_W, 1f);
            }
        }

        if (!this.level().isClientSide && this.isVehicle()) {
            notifyRedstone();
        }
    }

    private void notifyRedstone() {
        if (this.blockPos != null && this.level().isLoaded(this.blockPos)) {
            this.level().updateNeighborsAt(this.blockPos,
                    this.level().getBlockState(this.blockPos).getBlock());
        }
    }

    @Override
    public void move(MoverType type, Vec3 movement) {}

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BASE_YAW, 0f);
        builder.define(DATA_REDSTONE_DISABLED, true);
        builder.define(DATA_CAMERA_LOCKED, false);
        builder.define(DATA_SUB_ROT_X, 0f);
        builder.define(DATA_SUB_ROT_Y, 0f);
        builder.define(DATA_SUB_ROT_Z, 0f);
        builder.define(DATA_SUB_ROT_W, 1f);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("BlockPos")) {
            this.blockPos = BlockPos.of(tag.getLong("BlockPos"));
        }
        if (tag.contains("BaseYaw")) {
            this.entityData.set(DATA_BASE_YAW, tag.getFloat("BaseYaw"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("BlockPos", this.blockPos.asLong());
        tag.putFloat("BaseYaw", this.entityData.get(DATA_BASE_YAW));
    }

    public double getPassengersRidingOffset() {
        return 0.375;
    }
}
