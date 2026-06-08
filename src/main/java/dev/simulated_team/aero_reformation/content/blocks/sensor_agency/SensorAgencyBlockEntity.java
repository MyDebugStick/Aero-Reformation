/*
 * Sensor computation logic in this file is derived from the Simulated mod.
 * MIT License - Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Source: https://github.com/TheSimulatedTeam/Simulated-Project
 */
package dev.simulated_team.aero_reformation.content.blocks.sensor_agency;

import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.annotation.Nullable;

public class SensorAgencyBlockEntity extends BlockEntity {

    private SensorBinding binding = SensorBinding.EMPTY;
    private Vec3 lastWorldPos = Vec3.ZERO;
    public final SensorAgencyConfig config = new SensorAgencyConfig();

    public SensorAgencyBlockEntity(BlockPos pos, BlockState state) {
        super(AeroBlocks.SENSOR_AGENCY_BE.get(), pos, state);
    }

    public void setBinding(SensorBinding binding) {
        this.binding = binding;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    public SensorBinding getBinding() {
        return binding;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SensorAgencyBlockEntity be) {
        if (level.isClientSide()) return;
        if (level.getGameTime() % 2 != 0) return;

        Vec3 agencyWorld = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(pos));
        SubLevel sub = Sable.HELPER.getContaining(be);

        // ═══ AltitudeSensor: linear mapping from agency config world-height range ═══
        float worldY = (float) agencyWorld.y;
        float minY = be.config.altitudeLowWorld;
        float maxY = be.config.altitudeHighWorld;
        float altRange = maxY - minY;
        float altValue = altRange > 0 ? Mth.clamp((worldY - minY) / altRange, 0f, 1f) : 0f;
        if (be.config.altitudeInverted) altValue = 1f - altValue;
        int aSignal = Math.round(altValue * 15f);

        for (BlockPos sensorPos : be.binding.altitude()) {
            SensorProxyData.setAltitude(sensorPos, Mth.clamp(aSignal, 0, 15));
            SensorProxyData.ALTITUDE_RAW.put(sensorPos, worldY);
            triggerUpdate(level, sensorPos);
        }

        // ═══ VelocitySensor: per-direction output (positive velocity → opposite face) ═══
        Vector3d globalVel = Vec3.ZERO.equals(be.lastWorldPos) ? new Vector3d() :
                new Vector3d(
                        (agencyWorld.x - be.lastWorldPos.x) * 10.0,
                        (agencyWorld.y - be.lastWorldPos.y) * 10.0,
                        (agencyWorld.z - be.lastWorldPos.z) * 10.0);
        be.lastWorldPos = agencyWorld;

        for (BlockPos sensorPos : be.binding.velocity()) {
            // Use the sensor's own AXIS (from AbstractDirectionalAxisBlock)
            BlockState sensorState = level.getBlockState(sensorPos);
            Direction.Axis axis = dev.simulated_team.simulated.content.blocks.util.AbstractDirectionalAxisBlock
                    .getAxis(sensorState);
            Direction axisDir = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);

            // Compute velocity along this axis
            double velOnAxis = globalVel.x() * axisDir.getStepX()
                    + globalVel.y() * axisDir.getStepY()
                    + globalVel.z() * axisDir.getStepZ();

            float maxSpd = be.config.velocityMaxSpeed > 0 ? be.config.velocityMaxSpeed : 10f;
            int signal = (int) Mth.clamp(Math.abs((float) velOnAxis) / maxSpd * 15f, 0, 15);

            // Determine output face: positive velocity → opposite face, negative → axis face
            Direction outputFace = velOnAxis > 0.05 ? axisDir.getOpposite()
                    : velOnAxis < -0.05 ? axisDir : null;

            for (Direction dir : Direction.values()) {
                SensorProxyData.setVelocity(sensorPos, dir, dir == outputFace ? signal : 0);
            }
            SensorProxyData.VELOCITY_RAW.put(sensorPos, (float) velOnAxis);
            triggerUpdate(level, sensorPos);
        }

        // ═══ GimbalSensor: exact logic from GimbalSensorBlockEntity ═══
        for (BlockPos sensorPos : be.binding.gimbal()) {
            if (sub != null) {
                Vector3d ld = new Vector3d(0, -1, 0);
                sub.logicalPose().orientation().transformInverse(ld);
                double xAngle = (ld.y < 0 || ld.z * ld.z > 0.001) ? Math.atan2(ld.z, -ld.y) : 0;
                double zAngle = (ld.y < 0 || ld.x * ld.x > 0.001) ? Math.atan2(ld.x, -ld.y) : 0;

                double limit = Math.toRadians(be.config.gimbalPrimaryLimit > 0 ? be.config.gimbalPrimaryLimit : 45);
                SensorProxyData.setGimbalAngles(sensorPos, xAngle, zAngle, limit, be.config.gimbalInverted);
                SensorProxyData.GIMBAL_X_RAW.put(sensorPos, (float) Math.toDegrees(xAngle));
                SensorProxyData.GIMBAL_Z_RAW.put(sensorPos, (float) Math.toDegrees(zAngle));
            } else {
                SensorProxyData.setGimbalAngles(sensorPos, 0, 0, Math.toRadians(45), false);
                SensorProxyData.GIMBAL_X_RAW.put(sensorPos, 0f);
                SensorProxyData.GIMBAL_Z_RAW.put(sensorPos, 0f);
            }
            triggerUpdate(level, sensorPos);
        }

        // ═══ NavTable: per-direction signal from AGENCY's perspective ═══
        var compassNti = dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.NavigationTarget
                .ofStack(be.config.compassSlot.getItem(0));
        Vec3 target = null;
        var nti = compassNti;
        if (compassNti != null) {
            for (BlockPos sp : be.binding.nav()) {
                NavTableBlockEntity n = findBE(level, sp, NavTableBlockEntity.class);
                if (n != null) {
                    target = compassNti.getTarget(n, be.config.compassSlot.getItem(0));
                    break;
                }
            }
        }
        // Fallback: use first bound nav's own target
        if (target == null) {
            for (BlockPos sp : be.binding.nav()) {
                NavTableBlockEntity n = findBE(level, sp, NavTableBlockEntity.class);
                if (n != null) {
                    target = n.getTargetPosition(true);
                    nti = n.getNavTableItem();
                    break;
                }
            }
        }

        if (target != null && !be.binding.nav().isEmpty()) {
            float maxRange = Float.MAX_VALUE; // ignore nav distance limit
            float deadzone = (nti != null && nti.getDeadzone() > 0) ? nti.getDeadzone() : 2;

            // Use AGENCY's position, project onto XZ plane (horizontal)
            Quaterniond agencySubRot = sub != null ? sub.logicalPose().orientation() : new Quaterniond();
            Vec3 diff = target.subtract(agencyWorld);
            diff = dev.simulated_team.simulated.util.SimMathUtils.rotateQuat(diff, agencySubRot);
            // Project onto horizontal plane so both X and Z components are preserved
            Vec3 planeProj = dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.NavigationTarget
                    .getPlaneProjectedPos(diff, Direction.UP.getNormal());
            double dist = planeProj.length();

            if ((maxRange > 0 && dist > maxRange - 0.0001) || dist < deadzone - 0.0001) {
                for (BlockPos sensorPos : be.binding.nav()) {
                    for (Direction dir : Direction.values()) {
                        SensorProxyData.setNavSignal(sensorPos, dir, 0);
                    }
                    triggerUpdate(level, sensorPos);
                }
            } else {
                // Replicate NavigationTarget.calculateSideStrength: -(planeProj·dirNormal) / dist
                double px = planeProj.x, pz = planeProj.z;
                int nSig = computeDirSignal(pz, dist, be.config.navInverted);  // NORTH: -(0,0,-1)·proj = pz
                int sSig = computeDirSignal(-pz, dist, be.config.navInverted); // SOUTH: -(0,0,1)·proj = -pz
                int wSig = computeDirSignal(px, dist, be.config.navInverted);  // WEST:  -(-1,0,0)·proj = px
                int eSig = computeDirSignal(-px, dist, be.config.navInverted); // EAST:  -(1,0,0)·proj = -px

                for (BlockPos sensorPos : be.binding.nav()) {
                    SensorProxyData.setNavSignal(sensorPos, Direction.NORTH, nSig);
                    SensorProxyData.setNavSignal(sensorPos, Direction.SOUTH, sSig);
                    SensorProxyData.setNavSignal(sensorPos, Direction.WEST, wSig);
                    SensorProxyData.setNavSignal(sensorPos, Direction.EAST, eSig);
                    triggerUpdate(level, sensorPos);
                }
            }
        }
    }

    /** Compute per-direction nav signal using asin formula from NavigationTarget.calculateSideStrength. */
    private static int computeDirSignal(double dot, double dist, boolean inverted) {
        double normalizedDot = dot / dist;
        double angle = Math.asin(normalizedDot);
        int signal = (int) (angle / Math.PI * 30 + 0.5);
        signal = Mth.clamp(signal, 0, 15);
        if (inverted) signal = signal > 0 ? 0 : 15;
        return signal;
    }

    /** Trigger a redstone neighbor update on the sensor's block position. */
    public static void triggerUpdate(Level agencyLevel, BlockPos sensorPos) {
        // Try the agency's level first
        if (agencyLevel.isLoaded(sensorPos)) {
            BlockState st = agencyLevel.getBlockState(sensorPos);
            if (!st.isAir()) {
                agencyLevel.updateNeighbourForOutputSignal(sensorPos, st.getBlock());
                agencyLevel.updateNeighborsAt(sensorPos, st.getBlock());
            }
        }
        // Also try all server levels (for sub-level ↔ world mismatch)
        if (agencyLevel.getServer() != null) {
            for (ServerLevel sl : agencyLevel.getServer().getAllLevels()) {
                if (sl != agencyLevel && sl.isLoaded(sensorPos)) {
                    BlockState st = sl.getBlockState(sensorPos);
                    if (!st.isAir()) {
                        sl.updateNeighbourForOutputSignal(sensorPos, st.getBlock());
                        sl.updateNeighborsAt(sensorPos, st.getBlock());
                    }
                }
            }
        }
    }

    /** Find a BE by pos, checking both the current level and the server's world levels. */
    @Nullable
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> T findBE(Level level, BlockPos pos, Class<T> type) {
        if (level.isLoaded(pos)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (type.isInstance(be)) return (T) be;
        }
        // Fallback: check all server levels (for sub-level ↔ world mismatch)
        if (level.getServer() != null) {
            for (ServerLevel sl : level.getServer().getAllLevels()) {
                if (sl != level && sl.isLoaded(pos)) {
                    BlockEntity be = sl.getBlockEntity(pos);
                    if (type.isInstance(be)) return (T) be;
                }
            }
        }
        return null;
    }

    // ─── NBT persistence ───

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!binding.isEmpty()) {
            tag.put("sensor_binding", SensorBinding.CODEC.encodeStart(
                    net.minecraft.nbt.NbtOps.INSTANCE, binding).result().orElse(new CompoundTag()));
        }
        tag.put("agency_config", config.write(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("sensor_binding")) {
            binding = SensorBinding.CODEC.parse(
                    net.minecraft.nbt.NbtOps.INSTANCE, tag.get("sensor_binding")).result()
                    .orElse(SensorBinding.EMPTY);
        }
        if (tag.contains("agency_config")) {
            config.read(tag.getCompound("agency_config"), registries);
        }
    }

    public void saveConfig() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public CompoundTag getConfigTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("agency_config", config.write(
                level != null ? level.registryAccess() : net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                        net.minecraft.core.registries.BuiltInRegistries.REGISTRY)));
        return tag;
    }

    // ─── Network sync ───

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
