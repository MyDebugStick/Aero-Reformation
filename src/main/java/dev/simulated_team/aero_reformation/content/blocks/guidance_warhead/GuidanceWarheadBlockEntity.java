package dev.simulated_team.aero_reformation.content.blocks.guidance_warhead;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public class GuidanceWarheadBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {

    // Search modes
    public static final int SEARCH_MASS = 0;
    public static final int SEARCH_NEAREST = 1;
    public static final int SEARCH_MANUAL = 2;

    @Nullable
    private Vector3d targetPos = null;
    private int cooldown = 0;
    int lockedTargetId = -1; // runtime ID of locked SubLevel, -1 = not locked
    boolean guidanceFrameActive = false; // set by RCS each tick guidance is running

    // Tunable PID / guidance settings (synced from GUI)
    public float kp = 0.8f;
    public float ki = 0.02f;
    public float kd = 0.15f;
    public float maxSpeed = 20.0f;
    public float sidePower = 0.04f;
    public float maxThrustPN = 2000.0f;
    public float cruiseAltitude = 10.0f;
    public float brakeCoeff = 0.15f;
    public float proximityRange = 50.0f;
    public float redstoneRange = 10.0f;
    public float altitudeOffset = 0.0f;
    public int searchMode = SEARCH_MASS;
    public float minSearchRange = 0.0f;
    public float maxSearchRange = 1000.0f;
    public double manualTargetX = 0.0;
    public double manualTargetY = 64.0;
    public double manualTargetZ = 0.0;

    public GuidanceWarheadBlockEntity(BlockPos pos, BlockState state) {
        super(AeroBlocks.GUIDANCE_WARHEAD_BE.get(), pos, state);
    }

    @Nullable
    public Vector3d getTargetPos() {
        return targetPos;
    }

    /** Called by RCS thruster each physics tick when guidance is active (redstone ON). */
    public void markGuidanceActive() {
        this.guidanceFrameActive = true;
    }

    /** Called by RCS thruster when redstone turns OFF. */
    public void markGuidanceInactive() {
        this.guidanceFrameActive = false;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle bodyHandle, double dt) {
        if (level == null || level.isClientSide()) return;

        // Only active when RCS has redstone signal
        if (!guidanceFrameActive) return;

        // Apply linear drag every tick when guidance is active (once per body, no RCS compounding)
        if (lockedTargetId >= 0) {
            var vel = new Vector3d();
            bodyHandle.getLinearVelocity(vel);
            if (vel.length() > 0.01) {
                double dragCoeff = maxThrustPN / (40.0 * maxSpeed * 20.0);
                vel.mul(-dragCoeff);
                bodyHandle.addLinearAndAngularVelocity(vel, new Vector3d());
            }
        }

        if (--cooldown > 0) return;
        cooldown = 20; // scan every 20 ticks

        if (!(level instanceof ServerLevel serverLevel)) return;
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(serverLevel);
        if (container == null) return;

        var currentPos = subLevel.logicalPose().position();

        // Check if locked target still exists
        if (lockedTargetId >= 0) {
            SubLevel locked = findSubLevelById(container, lockedTargetId);
            if (locked != null && !locked.isRemoved()) {
                var lp = locked.logicalPose().position();
                targetPos = new Vector3d(lp);
                updateRedstone(currentPos, targetPos);
                return;
            }
            // Locked target gone, unlock
            lockedTargetId = -1;
            targetPos = null;
        }

        // Manual coordinate mode: use fixed coordinates
        if (searchMode == SEARCH_MANUAL) {
            targetPos = new Vector3d(manualTargetX, manualTargetY, manualTargetZ);
            lockedTargetId = -1; // no physics body to track
            updateRedstone(currentPos, targetPos);
            return;
        }

        // Scan for targets
        Vector3d bestPos = null;
        double bestScore = (searchMode == SEARCH_NEAREST) ? Double.MAX_VALUE : Double.MIN_VALUE;

        for (SubLevel other : container.getAllSubLevels()) {
            if (other.isRemoved() || other == subLevel) continue;
            var otherPos = other.logicalPose().position();
            double dist = currentPos.distance(otherPos);

            // Range filter
            if (minSearchRange > 0 && dist < minSearchRange) continue;
            if (maxSearchRange > 0 && dist > maxSearchRange) continue;

            if (searchMode == SEARCH_NEAREST) {
                if (dist < bestScore) {
                    bestScore = dist;
                    bestPos = new Vector3d(otherPos);
                }
            } else {
                // Mass priority: use total mass as score
                double mass = 1.0;
                if (other instanceof PhysicsPipelineBody pipeBody) {
                    var massData = pipeBody.getMassTracker();
                    if (massData != null && !massData.isInvalid()) {
                        mass = massData.getMass();
                    }
                }
                if (mass > bestScore) {
                    bestScore = mass;
                    bestPos = new Vector3d(otherPos);
                }
            }
        }

        if (bestPos != null) {
            targetPos = bestPos;
            // Lock onto this target: find its runtime ID
            lockedTargetId = findRuntimeId(container, bestPos);
        } else {
            targetPos = null;
        }

        updateRedstone(currentPos, targetPos);
    }

    /** Find SubLevel by runtime ID. */
    @Nullable
    private static SubLevel findSubLevelById(dev.ryanhcode.sable.api.sublevel.SubLevelContainer container, int id) {
        for (SubLevel sl : container.getAllSubLevels()) {
            if (sl instanceof PhysicsPipelineBody body && body.getRuntimeId() == id && !sl.isRemoved()) {
                return sl;
            }
        }
        return null;
    }

    /** Find the runtime ID of the SubLevel at the given position. */
    private static int findRuntimeId(dev.ryanhcode.sable.api.sublevel.SubLevelContainer container, Vector3d pos) {
        for (SubLevel sl : container.getAllSubLevels()) {
            if (sl instanceof PhysicsPipelineBody body && !sl.isRemoved()) {
                var p = sl.logicalPose().position();
                if (Math.abs(p.x() - pos.x) < 0.01 && Math.abs(p.y() - pos.y) < 0.01 && Math.abs(p.z() - pos.z) < 0.01) {
                    return body.getRuntimeId();
                }
            }
        }
        return -1;
    }

    /** Update POWERED block state based on redstone proximity. */
    private void updateRedstone(org.joml.Vector3dc currentPos, @Nullable Vector3d target) {
        boolean near = false;
        if (redstoneRange > 0 && target != null) {
            near = currentPos.distance(target) < redstoneRange;
        }
        var state = getBlockState();
        if (state.hasProperty(GuidanceWarheadBlock.POWERED) && state.getValue(GuidanceWarheadBlock.POWERED) != near) {
            level.setBlock(worldPosition, state.setValue(GuidanceWarheadBlock.POWERED, near), 3);
        }
    }

    /** Unlock target so next scan can acquire a new one. Called when GUI settings change. */
    public void unlockTarget() {
        this.lockedTargetId = -1;
        this.targetPos = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("Kp", kp);
        tag.putFloat("Ki", ki);
        tag.putFloat("Kd", kd);
        tag.putFloat("MaxSpeed", maxSpeed);
        tag.putFloat("SidePower", sidePower);
        tag.putFloat("MaxThrust", maxThrustPN);
        tag.putFloat("CruiseAltitude", cruiseAltitude);
        tag.putFloat("BrakeCoeff", brakeCoeff);
        tag.putFloat("ProximityRange", proximityRange);
        tag.putFloat("RedstoneRange", redstoneRange);
        tag.putFloat("AltitudeOffset", altitudeOffset);
        tag.putInt("SearchMode", searchMode);
        tag.putFloat("MinSearchRange", minSearchRange);
        tag.putFloat("MaxSearchRange", maxSearchRange);
        tag.putDouble("ManualX", manualTargetX);
        tag.putDouble("ManualY", manualTargetY);
        tag.putDouble("ManualZ", manualTargetZ);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Kp")) kp = tag.getFloat("Kp");
        if (tag.contains("Ki")) ki = tag.getFloat("Ki");
        if (tag.contains("Kd")) kd = tag.getFloat("Kd");
        if (tag.contains("MaxSpeed")) maxSpeed = tag.getFloat("MaxSpeed");
        if (tag.contains("SidePower")) sidePower = tag.getFloat("SidePower");
        if (tag.contains("MaxThrust")) maxThrustPN = tag.getFloat("MaxThrust");
        if (tag.contains("CruiseAltitude")) cruiseAltitude = tag.getFloat("CruiseAltitude");
        if (tag.contains("BrakeCoeff")) brakeCoeff = tag.getFloat("BrakeCoeff");
        if (tag.contains("ProximityRange")) proximityRange = tag.getFloat("ProximityRange");
        if (tag.contains("RedstoneRange")) redstoneRange = tag.getFloat("RedstoneRange");
        if (tag.contains("AltitudeOffset")) altitudeOffset = tag.getFloat("AltitudeOffset");
        if (tag.contains("SearchMode")) searchMode = tag.getInt("SearchMode");
        if (tag.contains("MinSearchRange")) minSearchRange = tag.getFloat("MinSearchRange");
        if (tag.contains("MaxSearchRange")) maxSearchRange = tag.getFloat("MaxSearchRange");
        if (tag.contains("ManualX")) manualTargetX = tag.getDouble("ManualX");
        if (tag.contains("ManualY")) manualTargetY = tag.getDouble("ManualY");
        if (tag.contains("ManualZ")) manualTargetZ = tag.getDouble("ManualZ");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }
}
