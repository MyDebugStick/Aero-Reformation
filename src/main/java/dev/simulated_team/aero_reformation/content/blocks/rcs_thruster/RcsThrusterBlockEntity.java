package dev.simulated_team.aero_reformation.content.blocks.rcs_thruster;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.aero_reformation.content.blocks.directional_synchronizer.DirectionalSynchronizerMasterBlockEntity;
import dev.simulated_team.aero_reformation.particles.RcsParticleData;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;
import java.util.List;

public class RcsThrusterBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor, IHaveGoggleInformation {

    // 5 nozzle exhaust positions in RCS block-local space (scaled 0-16)
    private static final Vector3d[] NOZZLE_POS = {
            new Vector3d(8, 8, 2),      // 0: FORWARD
            new Vector3d(13, 8, 8),     // 1: RIGHT-ANGLED
            new Vector3d(3, 8, 8),      // 2: LEFT-ANGLED
            new Vector3d(8, 13, 8),     // 3: UP-ANGLED
            new Vector3d(8, 3, 8),      // 4: DOWN-ANGLED
    };

    // Track nozzle activation for one-shot sounds
    private int prevActiveMask = 0;

    // 5 nozzle thrust directions in RCS block-local space (model faces north/-Z)
    private static final Vector3d[] NOZZLE_LOCAL = {
            new Vector3d(0, 0, 1),            // 0: FORWARD (push outward)
            new Vector3d(-0.707, 0, 0.707),   // 1: RIGHT-ANGLED
            new Vector3d(0.707, 0, 0.707),    // 2: LEFT-ANGLED
            new Vector3d(0, -0.707, 0.707),   // 3: UP-ANGLED
            new Vector3d(0, 0.707, 0.707),    // 4: DOWN-ANGLED
    };

    // Thrust levels in pN (Aeronautics force unit): 500~5000 step 500, 6000~20000 step 1000
    private static final int[] THRUST_OPTIONS = {
            500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000,
            6000, 7000, 8000, 9000, 10000, 11000, 12000, 13000, 14000, 15000,
            16000, 17000, 18000, 19000, 20000
    };
    private static final int DEFAULT_THRUST_IDX = 3; // 2000 pN

    private BlockPos boundSyncPos = null;
    private BlockPos boundWarheadPos = null;
    private boolean guidanceActive = false; // one-shot: true once PID starts
    public ScrollValueBehaviour thrustScroll;

    // PID state for guidance attitude control
    private double yawIntegral = 0;
    private double yawPrevError = 0;
    private double pitchIntegral = 0;
    private double pitchPrevError = 0;

    // PID gains (tunable)
    private static final double PID_KP = 0.8;
    private static final double PID_KI = 0.02;
    private static final double PID_KD = 0.15;
    private static final double PID_INTEGRAL_MAX = 2.0;

    // Accepted fuel fluid IDs
    private static final Set<String> ACCEPTED_FUELS = Set.of(
            "createdieselgenerators:gasoline",
            "createdieselgenerators:ethanol"
    );

    // Angled nozzle reduction mode: 0=100%, 1=50%, 2=25%, 3=10%
    public static final double[] ANGLED_REDUCTION = {1.0, 0.5, 0.25, 0.1, 0.05, 0.02};
    private int angledMode = 0;

    private double getFuelConsumption() {
        return dev.simulated_team.aero_reformation.config.AeroReformationConfig.rcsFuelConsumption;
    }
    private double getElectricEfficiency() {
        return dev.simulated_team.aero_reformation.config.AeroReformationConfig.rcsElectricEfficiency;
    }
    private boolean creativeMode = false;
    private boolean fuelAvailable = false; // synced for client VFX
    private boolean electricMode = false;  // true = using electricity, false = fluid fuel
    private Direction syncFacingCache = Direction.NORTH; // cached for client VFX
    private int activeNozzleMask = 0; // cached for client VFX
    private double currentThrustPN = 0; // synced for goggle HUD
    private boolean syncWasValid = false; // tracks if sync block existed on last tick

    // Fuel: configurable via AeroReformationConfig, default 5000pN/mB/tick
    private final Vector3d thrustWorld = new Vector3d();
    private final Vector3d blockCenter = new Vector3d();
    private final Vector3d force = new Vector3d();
    private final Vector3d totalForce = new Vector3d();
    private final Vector3d subLevelVelocity = new Vector3d(); // synced for client VFX


    public RcsThrusterBlockEntity(BlockPos pos, BlockState state) {
        super(AeroBlocks.RCS_THRUSTER_BE.get(), pos, state);
    }

    public void setBoundSync(BlockPos pos) {
        this.boundSyncPos = pos;
        this.syncWasValid = false;
        this.boundWarheadPos = null; // cannot bind both
        this.setChanged();
    }

    public BlockPos getBoundSync() {
        return boundSyncPos;
    }

    public void setBoundWarhead(BlockPos pos) {
        this.boundWarheadPos = pos;
        this.boundSyncPos = null; // cannot bind both
        this.setChanged();
    }

    public BlockPos getBoundWarhead() {
        return boundWarheadPos;
    }

    public boolean isGuidanceMode() {
        return boundWarheadPos != null;
    }

    /** Client VFX accessors */
    public int getActiveMask() { return activeNozzleMask; }
    public boolean isElectricMode() { return electricMode; }
    public Direction getRcsFacing() { return getBlockState().getValue(RcsThrusterBlock.FACING); }
    public float getThrustNorm() { return (float) Math.min(currentThrustPN / 20000.0, 1.0); }

    public double getConfiguredThrust() {
        if (thrustScroll == null) return THRUST_OPTIONS[DEFAULT_THRUST_IDX];
        return THRUST_OPTIONS[thrustScroll.getValue()];
    }

    public void cycleAngledMode() {
        angledMode = (angledMode + 1) % ANGLED_REDUCTION.length;
        setChanged();
    }

    public int getAngledMode() { return angledMode; }

    public void setCreativeMode(boolean v) { this.creativeMode = v; setChanged(); }
    public boolean isCreativeMode() { return creativeMode; }

    @Nullable
    private IEnergyStorage getEnergySource() {
        if (level == null) return null;
        Direction back = getBlockState().getValue(RcsThrusterBlock.FACING).getOpposite();
        BlockPos backPos = worldPosition.relative(back);
        // Query the face of the energy source that touches the thruster (opposite of "back")
        Direction sourceFace = back.getOpposite();
        // Try standard NeoForge FE capability first
        IEnergyStorage storage = level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                backPos, sourceFace);
        if (storage != null) return storage;
        // Mekanism compatibility: try its own energy handler via capability
        return getMekanismEnergy(backPos, sourceFace);
    }

    /** Try Mekanism's energy API via its own BlockCapability (side-aware, no compile-time dependency). */
    @Nullable
    private IEnergyStorage getMekanismEnergy(BlockPos pos, Direction side) {
        try {
            // Mekanism registers its capability as:
            //   BlockCapability<IStrictEnergyHandler, @Nullable Direction>
            // with capability token at mekanism.common.capabilities.Capabilities.ENERGY
            Class<?> capClass = Class.forName("mekanism.common.capabilities.Capabilities");
            Object capToken = capClass.getField("ENERGY").get(null);
            // capToken is a BlockCapability<IStrictEnergyHandler, @Nullable Direction>
            // Query it: level.getCapability(capToken, pos, side)
            Object handler = net.minecraft.world.level.Level.class
                    .getMethod("getCapability",
                            Class.forName("net.neoforged.neoforge.capabilities.BlockCapability"),
                            BlockPos.class, Object.class)
                    .invoke(level, capToken, pos, side);
            if (handler == null) return null;

            // handler implements IStrictEnergyHandler, wrap as IEnergyStorage
            return new IEnergyStorage() {
                public int receiveEnergy(int maxReceive, boolean simulate) { return 0; }
                public int extractEnergy(int maxExtract, boolean simulate) {
                    try {
                        Class<?> actionClass = Class.forName("mekanism.api.Action");
                        Object action = simulate ?
                                actionClass.getField("SIMULATE").get(null) :
                                actionClass.getField("EXECUTE").get(null);
                        long extracted = (long) handler.getClass()
                                .getMethod("extractEnergy", long.class, actionClass)
                                .invoke(handler, (long) maxExtract, action);
                        return (int) Math.min(extracted, Integer.MAX_VALUE);
                    } catch (Exception e) { return 0; }
                }
                public int getEnergyStored() { return 0; }
                public int getMaxEnergyStored() { return 0; }
                public boolean canExtract() { return true; }
                public boolean canReceive() { return false; }
            };
        } catch (Exception ignored) { return null; }
    }

    private IFluidHandler getFuelSource() {
        if (level == null) return null;
        Direction back = getBlockState().getValue(RcsThrusterBlock.FACING).getOpposite();
        BlockPos backPos = worldPosition.relative(back);
        return level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                backPos, back);
    }

    /** Returns available gasoline in mB from connected container */
    public int getFuelAmount() {
        IFluidHandler source = getFuelSource();
        if (source == null) return 0;
        int total = 0;
        for (int i = 0; i < source.getTanks(); i++) {
            var stack = source.getFluidInTank(i);
            if (!stack.isEmpty()) {
                var key = net.minecraft.core.registries.BuiltInRegistries.FLUID
                        .getKey(stack.getFluid());
                if (key != null && ACCEPTED_FUELS.contains(key.toString())) {
                    total += stack.getAmount();
                }
            }
        }
        return total;
    }

    /** Drain up to 'amount' mB of accepted fuel from the connected container. */
    private int drainFuel(int amount) {
        IFluidHandler source = getFuelSource();
        if (source == null) return 0;
        for (int i = 0; i < source.getTanks(); i++) {
            var stack = source.getFluidInTank(i);
            if (!stack.isEmpty()) {
                var key = net.minecraft.core.registries.BuiltInRegistries.FLUID
                        .getKey(stack.getFluid());
                if (key != null && ACCEPTED_FUELS.contains(key.toString())) {
                    return source.drain(amount, IFluidHandler.FluidAction.EXECUTE).getAmount();
                }
            }
        }
        return 0;
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        tag.putBoolean("FuelAvailable", fuelAvailable);
        tag.putBoolean("ElectricMode", electricMode);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        thrustScroll = new ThrustScrollBehaviour(this);
        thrustScroll.setValue(DEFAULT_THRUST_IDX);
        thrustScroll.withCallback(v -> setChanged());
        behaviours.add(thrustScroll);
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide) {
            spawnNozzleParticles();
        }
    }

    private void spawnNozzleParticles() {
        if (level == null) return;
        // Need either sync or warhead binding for active VFX
        if (boundSyncPos == null && boundWarheadPos == null) return;
        if (!creativeMode && !fuelAvailable) {
            prevActiveMask = 0;
            return;
        }
        if (activeNozzleMask == 0) {
            prevActiveMask = 0;
            return;
        }

        Direction syncFacing = syncFacingCache;
        Direction rcsFacing = getBlockState().getValue(RcsThrusterBlock.FACING);
        RandomSource rand = level.random;

        // Use synced electric mode (getEnergySource() may not work client-side when chunks unloaded)
        boolean hasElectric = electricMode;
        var subLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, worldPosition);

        // Continuous sound: plays at block center as long as any nozzle is active
        if (activeNozzleMask != 0 && level.getGameTime() % 10 == 0) {
            level.playLocalSound(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                    hasElectric ? SoundEvents.BEACON_AMBIENT : SoundEvents.CAMPFIRE_CRACKLE,
                    SoundSource.BLOCKS,
                    1.00f,
                    hasElectric ? 1.8f + rand.nextFloat() * 0.4f : 0.9f + rand.nextFloat() * 0.3f, false);
        }

        for (int nozzleIdx = 0; nozzleIdx < 5; nozzleIdx++) {
            if ((activeNozzleMask & (1 << nozzleIdx)) == 0) continue;

            // Particles: block center + nozzle surface offset + forward along exhaust
            Vector3d localDir = new Vector3d(NOZZLE_LOCAL[nozzleIdx]);
            transformByFacing(localDir, rcsFacing, localDir);
            // Exhaust world direction (negate thrust, then rotate by sublevel)
            Vector3d worldDir = new Vector3d(-localDir.x, -localDir.y, -localDir.z);
            if (subLevel != null) subLevel.logicalPose().orientation().transform(worldDir);

            // Nozzle offset from block center (in 0-16 local space), transformed by facing
            Vector3d centerOffset = new Vector3d(
                    (NOZZLE_POS[nozzleIdx].x - 8) / 16.0,
                    (NOZZLE_POS[nozzleIdx].y - 8) / 16.0,
                    (NOZZLE_POS[nozzleIdx].z - 8) / 16.0);
            transformByFacing(centerOffset, rcsFacing, centerOffset);

            // Block center + nozzle offset + forward along exhaust
            // Forward offset pushes spawn point outward along exhaust direction
            // Must exceed block half-size (0.5) minus centerOffset magnitude to reach outside
            double fwd = nozzleIdx == 0 ? -0.35 : -0.5;
            Vector3d localPos = new Vector3d(
                    worldPosition.getX() + 0.5 + centerOffset.x + localDir.x * fwd,
                    worldPosition.getY() + 0.5 + centerOffset.y + localDir.y * fwd,
                    worldPosition.getZ() + 0.5 + centerOffset.z + localDir.z * fwd);
            dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, localPos);
            Vec3 particleWorld = new Vec3(localPos.x, localPos.y, localPos.z);

            // Read redstone signal for throttle (available client-side via boundSyncPos)
            float throttle = 1.0f;
            if (boundSyncPos != null) {
                Direction inputFace = getSyncFaceForNozzle(syncFacing, nozzleIdx);
                if (inputFace != null) {
                    int signal = level.getSignal(boundSyncPos.relative(inputFace), inputFace.getOpposite());
                    throttle = Math.max(0.1f, signal / 15.0f);
                }
            }
            // In guidance mode, throttle is determined server-side; use full for VFX
            float t = 0.5f + throttle * 0.5f;

            // Spacing-based particle count: more particles at higher speed for consistent visual density
            double targetSpacing = 0.3;

            if (hasElectric) {
                // Electric: plasma beam
                float spd = (nozzleIdx == 0 ? 0.15f : 0.10f) * t;
                float spr = (nozzleIdx == 0 ? 0.04f : 0.03f) * t;
                int count = Math.max(1, Math.min(12, (int)Math.ceil(Math.abs(spd) / targetSpacing)));
                for (int i = 0; i < count; i++) {
                    double frac = count <= 1 ? 0.0 : (double) i / count;
                    double ox = (rand.nextDouble() - 0.5) * spr;
                    double oy = (rand.nextDouble() - 0.5) * spr;
                    double oz = (rand.nextDouble() - 0.5) * spr;
                    level.addParticle(new RcsParticleData(true,
                                    worldDir.x, worldDir.y, worldDir.z, spd, spr),
                            particleWorld.x + worldDir.x * spd * frac + ox,
                            particleWorld.y + worldDir.y * spd * frac + oy,
                            particleWorld.z + worldDir.z * spd * frac + oz,
                            worldDir.x * spd,
                            worldDir.y * spd,
                            worldDir.z * spd);
                }
            } else {
                // Fuel: plume
                float spd = (nozzleIdx == 0 ? 0.18f : 0.12f) * t;
                float spr = (nozzleIdx == 0 ? 0.03f : 0.02f) * t;
                int count = Math.max(1, Math.min(12, (int)Math.ceil(Math.abs(spd) / targetSpacing)));
                for (int i = 0; i < count; i++) {
                    double frac = count <= 1 ? 0.0 : (double) i / count;
                    double ox = (rand.nextDouble() - 0.5) * spr;
                    double oy = (rand.nextDouble() - 0.5) * spr;
                    double oz = (rand.nextDouble() - 0.5) * spr;
                    level.addParticle(new RcsParticleData(false,
                                    worldDir.x, worldDir.y, worldDir.z, spd, spr),
                            particleWorld.x + worldDir.x * spd * frac + ox,
                            particleWorld.y + worldDir.y * spd * frac + oy,
                            particleWorld.z + worldDir.z * spd * frac + oz,
                            worldDir.x * spd,
                            worldDir.y * spd,
                            worldDir.z * spd);
                }
            }
        }
        // Activation sound: only when all nozzles were off and at least one starts
        if (prevActiveMask == 0 && activeNozzleMask != 0) {
            level.playLocalSound(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                    hasElectric ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.FIRECHARGE_USE,
                    SoundSource.BLOCKS,
                    0.6f, hasElectric ? 1.5f : 0.8f + rand.nextFloat() * 0.4f, false);
        }
        prevActiveMask = activeNozzleMask;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle bodyHandle, double dt) {
        if (level == null) return;

        // Guidance mode: bound to warhead, no sync
        if (boundWarheadPos != null) {
            sable$guidanceTick(subLevel, bodyHandle, dt);
            return;
        }

        // Sync mode: bound to DirectionalSynchronizer
        if (boundSyncPos == null) return;

        BlockEntity be = level.getBlockEntity(boundSyncPos);
        if (!(be instanceof DirectionalSynchronizerMasterBlockEntity sync)) {
            if (syncWasValid) {
                // Sync was valid before but now gone → destroyed, shut down
                syncWasValid = false;
                if (activeNozzleMask != 0) {
                    activeNozzleMask = 0; currentThrustPN = 0;
                    fuelAvailable = false; electricMode = false;
                    sendData(); setChanged();
                }
            }
            return;
        }
        syncWasValid = true;

        BlockState syncState = sync.getBlockState();
        Direction syncFacing = syncState.getValue(DirectionalBlock.FACING);

        BlockState rcsState = getBlockState();
        Direction rcsFacing = rcsState.getValue(RcsThrusterBlock.FACING);

        double maxThrust = getConfiguredThrust();

        // Block center in world space
        blockCenter.set(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);

        // Accumulate active thrust (in pN, before /40) and fuel cost
        totalForce.set(0, 0, 0);
        double totalThrustPN = 0;
        int physMask = 0;
        QueuedForceGroup liftGroup = subLevel.getOrCreateQueuedForceGroup(ForceGroups.LIFT.get());

        for (Direction inputFace : Direction.values()) {
            if (inputFace == syncFacing) continue;

            int signal = level.getSignal(boundSyncPos.relative(inputFace), inputFace.getOpposite());
            if (signal == 0) continue;

            int nozzleIdx = getNozzleForSyncFace(syncFacing, inputFace);
            physMask |= (1 << nozzleIdx);
            Vector3d localDir = NOZZLE_LOCAL[nozzleIdx];

            transformByFacing(localDir, rcsFacing, thrustWorld);

            double scale = signal / 15.0;
            double mult = (nozzleIdx == 0) ? 1.0 : ANGLED_REDUCTION[angledMode];
            double thrustPN = maxThrust * scale * mult;
            totalThrustPN += thrustPN;
            totalForce.add(thrustWorld.mul(thrustPN / 40.0, force));
        }

        // Fuel / Energy consumption (electricity first, then fluid fuel)
        applyFuelAndEnergy(totalThrustPN);

        // Cache sync facing and active nozzle mask for client VFX
        syncFacingCache = syncFacing;
        if (activeNozzleMask != physMask || Math.abs(currentThrustPN - totalThrustPN) > 0.5) {
            activeNozzleMask = physMask;
            currentThrustPN = totalThrustPN;
            sendData();
            setChanged();
        }

        // Apply force directly (burst output)
        if (totalForce.lengthSquared() > 1e-6) {
            liftGroup.applyAndRecordPointForce(blockCenter, totalForce);
        }

        // Cache sub-level linear velocity for client VFX
        cacheSubLevelVelocity(bodyHandle);
    }

    /** Guidance mode: seek nearest physics body, orient and thrust toward it. */
    private void sable$guidanceTick(ServerSubLevel subLevel, RigidBodyHandle bodyHandle, double dt) {
        BlockEntity warheadBe = level.getBlockEntity(boundWarheadPos);
        if (!(warheadBe instanceof dev.simulated_team.aero_reformation.content.blocks.guidance_warhead.GuidanceWarheadBlockEntity warhead)) {
            // Warhead removed — shut down nozzles and reset state
            if (activeNozzleMask != 0) {
                activeNozzleMask = 0; currentThrustPN = 0;
                fuelAvailable = false; electricMode = false;
                sendData(); setChanged();
            }
            return;
        }
        var mySub = dev.ryanhcode.sable.Sable.HELPER.getContaining(this);
        var warheadSub = dev.ryanhcode.sable.Sable.HELPER.getContaining(warheadBe);
        if (mySub == null || warheadSub == null || mySub != warheadSub) return;

        Direction facing = getBlockState().getValue(RcsThrusterBlock.FACING);
        BlockPos backPos = worldPosition.relative(facing.getOpposite());
        if (!level.hasNeighborSignal(backPos)) {
            yawIntegral = 0; yawPrevError = 0;
            pitchIntegral = 0; pitchPrevError = 0;
            guidanceActive = false;
            if (activeNozzleMask != 0) {
                activeNozzleMask = 0; currentThrustPN = 0;
                fuelAvailable = false; electricMode = false;
                sendData(); setChanged();
            }
            warhead.markGuidanceInactive();
            return;
        }

        // Mark warhead that guidance is active (for drag application once per body)
        warhead.markGuidanceActive();

        Direction rcsFacing = getBlockState().getValue(RcsThrusterBlock.FACING);
        double maxThrust = warhead.maxThrustPN;
        totalForce.set(0, 0, 0);
        double totalThrustPN = 0;
        int physMask = 0;

        var target = warhead.getTargetPos();
        if (target == null) return;

        var currentPos = subLevel.logicalPose().position();
        double dist = currentPos.distance(target); // original distance for redstone
        if (dist < 0.5) return;

        // Apply altitude offset: aim above target until within proximity range
        boolean useOffset = warhead.proximityRange <= 0 || dist > warhead.proximityRange;
        var guidanceTarget = useOffset ? new Vector3d(target).add(0, warhead.altitudeOffset, 0) : new Vector3d(target);
        var toTarget = new Vector3d(guidanceTarget).sub(currentPos);
        toTarget.div(toTarget.length());

        var warheadState = warhead.getBlockState();
        var warheadFacing = warheadState.getValue(dev.simulated_team.aero_reformation.content.blocks.guidance_warhead.GuidanceWarheadBlock.FACING);
        var warheadWorldDir = new Vector3d(warheadFacing.getStepX(), warheadFacing.getStepY(), warheadFacing.getStepZ());
        subLevel.logicalPose().orientation().transform(warheadWorldDir);

        // ===== Phase 1: Climb to guidance altitude (one-time) =====
        double currentAlt = currentPos.y();
        if (!guidanceActive && warhead.cruiseAltitude > 0 && currentAlt < warhead.cruiseAltitude) {
            physMask |= 1;
            Vector3d localDir = NOZZLE_LOCAL[0];
            transformByFacing(localDir, rcsFacing, thrustWorld);
            double thrustPN = maxThrust;
            totalThrustPN += thrustPN;
            totalForce.add(thrustWorld.mul(thrustPN / 40.0, force));
            finishGuidanceFrame(subLevel, bodyHandle, totalThrustPN, physMask, totalForce);
            return;
        }
        guidanceActive = true;

        // ===== Phase 2: PID guidance =====

        // RCS-local axes in world space
        var rcsUpLocal = new Vector3d(0, 1, 0);
        transformByFacing(rcsUpLocal, rcsFacing, rcsUpLocal);
        subLevel.logicalPose().orientation().transform(rcsUpLocal);
        var rcsRightLocal = new Vector3d(1, 0, 0);
        transformByFacing(rcsRightLocal, rcsFacing, rcsRightLocal);
        subLevel.logicalPose().orientation().transform(rcsRightLocal);

        // PID errors from cross product (warphead direction vs target)
        var cross = new Vector3d(warheadWorldDir).cross(toTarget);
        double yawError = cross.dot(rcsUpLocal);
        double pitchError = cross.dot(rcsRightLocal);

        // Forward alignment
        double forwardAlign = warheadWorldDir.dot(toTarget);
        double forwardScale = 0.1 + 0.9 * Math.pow(Math.max(0.0, forwardAlign), 2.34);

        // Angular velocity
        var angVel = new Vector3d();
        bodyHandle.getAngularVelocity(angVel);
        double yawAngVel = angVel.dot(rcsUpLocal);
        double pitchAngVel = angVel.dot(rcsRightLocal);

        // PID
        double yawOutput = pidStep(yawError, yawPrevError, yawIntegral, warhead.kp, warhead.ki, warhead.kd);
        yawPrevError = yawError;
        yawIntegral = clamp(yawIntegral + yawError, -PID_INTEGRAL_MAX, PID_INTEGRAL_MAX);
        if (yawError * yawPrevError < 0) yawIntegral *= 0.5;
        yawOutput -= yawAngVel * warhead.brakeCoeff;

        double pitchOutput = pidStep(pitchError, pitchPrevError, pitchIntegral, warhead.kp, warhead.ki, warhead.kd);
        pitchPrevError = pitchError;
        pitchIntegral = clamp(pitchIntegral + pitchError, -PID_INTEGRAL_MAX, PID_INTEGRAL_MAX);
        if (pitchError * pitchPrevError < 0) pitchIntegral *= 0.5;
        pitchOutput -= pitchAngVel * warhead.brakeCoeff;

        // Forward thrust
        physMask |= 1;
        Vector3d localDir = NOZZLE_LOCAL[0];
        transformByFacing(localDir, rcsFacing, thrustWorld);
        double thrustPN = maxThrust * forwardScale;
        totalThrustPN += thrustPN;
        totalForce.add(thrustWorld.mul(thrustPN / 40.0, force));

        // Side nozzles (PID-driven)
        double sidePower = maxThrust * warhead.sidePower;
        if (Math.abs(yawOutput) > 0.005) {
            int idx = yawOutput > 0 ? 1 : 2;
            physMask |= (1 << idx);
            var nozzleDir = new Vector3d(NOZZLE_LOCAL[idx]);
            transformByFacing(nozzleDir, rcsFacing, nozzleDir);
            double pn = sidePower * Math.abs(yawOutput);
            totalThrustPN += pn;
            totalForce.add(nozzleDir.mul(pn / 40.0, force));
        }
        if (Math.abs(pitchOutput) > 0.005) {
            int idx = pitchOutput > 0 ? 4 : 3;
            physMask |= (1 << idx);
            var nozzleDir = new Vector3d(NOZZLE_LOCAL[idx]);
            transformByFacing(nozzleDir, rcsFacing, nozzleDir);
            double pn = sidePower * Math.abs(pitchOutput);
            totalThrustPN += pn;
            totalForce.add(nozzleDir.mul(pn / 40.0, force));
        }

        finishGuidanceFrame(subLevel, bodyHandle, totalThrustPN, physMask, totalForce);
    }

    /** Apply fuel, sync state, apply forces, and update velocity cache. */
    private void finishGuidanceFrame(ServerSubLevel subLevel, RigidBodyHandle bodyHandle,
                                      double totalThrustPN, int physMask, Vector3d totalForce) {
        applyFuelAndEnergy(totalThrustPN);

        if (activeNozzleMask != physMask || Math.abs(currentThrustPN - totalThrustPN) > 0.5) {
            activeNozzleMask = physMask;
            currentThrustPN = totalThrustPN;
            sendData();
            setChanged();
        }

        if (totalForce.lengthSquared() > 1e-6) {
            blockCenter.set(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
            QueuedForceGroup liftGroup = subLevel.getOrCreateQueuedForceGroup(ForceGroups.LIFT.get());
            liftGroup.applyAndRecordPointForce(blockCenter, totalForce);
        }

        cacheSubLevelVelocity(bodyHandle);
    }

    /** Consume fuel/energy for accumulated thrust. Returns true if enough fuel available. */
    private void applyFuelAndEnergy(double totalThrustPN) {
        boolean prevFuel = fuelAvailable;
        if (creativeMode) {
            fuelAvailable = true;
        } else if (totalThrustPN > 0) {
            var energySource = getEnergySource();
            double eff = getElectricEfficiency();
            int energyNeeded = (int) Math.ceil(totalThrustPN / eff);
            int energyDrained = energySource != null ? energySource.extractEnergy(energyNeeded, false) : 0;
            double energyCovered = energyDrained * eff;
            if (energyCovered >= totalThrustPN) {
                fuelAvailable = true;
                electricMode = true;
            } else {
                electricMode = false;
                double remainingPN = totalThrustPN - energyCovered;
                int fuelNeeded = (int) Math.ceil(remainingPN / getFuelConsumption());
                int drained = drainFuel(fuelNeeded);
                fuelAvailable = drained > 0;
                if (energyDrained > 0 || drained < fuelNeeded) {
                    double ratio = (energyCovered + drained * getFuelConsumption()) / totalThrustPN;
                    totalForce.mul(ratio);
                }
            }
        } else {
            fuelAvailable = false;
            electricMode = false;
        }
        if (fuelAvailable != prevFuel) {
            sendData();
            setChanged();
        }
    }

    private void cacheSubLevelVelocity(RigidBodyHandle bodyHandle) {
        bodyHandle.getLinearVelocity(subLevelVelocity);
        subLevelVelocity.mul(1.0 / 20.0);
        if (fuelAvailable && level != null && level.getGameTime() % 5 == 0) {
            sendData();
            setChanged();
        }
    }

    // ==================== Goggle HUD ====================

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        String indent = "\u00A0\u00A0\u00A0"; // non-breaking spaces to offset past goggle icon
        if (creativeMode) {
            tooltip.add(Component.literal(indent).append(Component.translatable("aero_reformation.rcs_thruster.creative_mode"))
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
            return true;
        }
        if (fuelAvailable) {
            if (electricMode) {
                tooltip.add(Component.literal(indent + "⚡ ").append(Component.translatable("aero_reformation.rcs_thruster.goggle.electric"))
                        .withStyle(net.minecraft.ChatFormatting.AQUA));
            } else {
                tooltip.add(Component.literal(indent + "🛢 ").append(Component.translatable("aero_reformation.rcs_thruster.goggle.fuel"))
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
                if (level != null) {
                    int fuelMb = getFuelAmount();
                    tooltip.add(Component.literal(indent + "  ").append(Component.translatable("aero_reformation.rcs_thruster.goggle.fuel_stored", fuelMb))
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            }
        } else if (activeNozzleMask != 0) {
            tooltip.add(Component.literal(indent).append(Component.translatable("aero_reformation.rcs_thruster.goggle.no_fuel"))
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
        if (currentThrustPN > 0) {
            tooltip.add(Component.literal(indent + "→ ").append(Component.translatable("aero_reformation.rcs_thruster.goggle.thrust",
                    String.format("%.0f", currentThrustPN)))
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
            if (electricMode) {
                int fePerTick = (int) Math.ceil(currentThrustPN / getElectricEfficiency());
                tooltip.add(Component.literal(indent + "  ").append(Component.translatable("aero_reformation.rcs_thruster.goggle.fe_usage", fePerTick))
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            } else {
                int mbPerTick = (int) Math.ceil(currentThrustPN / getFuelConsumption());
                tooltip.add(Component.literal(indent + "  ").append(Component.translatable("aero_reformation.rcs_thruster.goggle.fuel_usage", mbPerTick))
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
        return true;
    }


    private static int getNozzleForSyncFace(Direction syncFacing, Direction inputFace) {
        if (inputFace == syncFacing.getOpposite()) return 0; // FORWARD

        Direction up = getRelativeUp(syncFacing);
        Direction down = up.getOpposite();
        Direction left = getRelativeLeft(syncFacing, up);
        Direction right = left.getOpposite();

        if (inputFace == up) return 3;    // UP-ANGLED
        if (inputFace == down) return 4;  // DOWN-ANGLED
        if (inputFace == left) return 1;  // RIGHT-ANGLED
        if (inputFace == right) return 2; // LEFT-ANGLED

        return 0;
    }

    /** Reverse of getNozzleForSyncFace: returns the sync input face for a given nozzle index. */
    @javax.annotation.Nullable
    private static Direction getSyncFaceForNozzle(Direction syncFacing, int nozzleIdx) {
        return switch (nozzleIdx) {
            case 0 -> syncFacing.getOpposite();
            case 1 -> getRelativeLeft(syncFacing, getRelativeUp(syncFacing));
            case 2 -> getRelativeLeft(syncFacing, getRelativeUp(syncFacing)).getOpposite();
            case 3 -> getRelativeUp(syncFacing);
            case 4 -> getRelativeUp(syncFacing).getOpposite();
            default -> null;
        };
    }

    private static Direction getRelativeUp(Direction facing) {
        return switch (facing) {
            case UP -> Direction.SOUTH;
            case DOWN -> Direction.NORTH;
            default -> Direction.UP;
        };
    }

    private static Direction getRelativeLeft(Direction facing, Direction up) {
        if (facing.getAxis() == Direction.Axis.Y) {
            return Direction.EAST;
        }
        return facing.getClockWise();
    }

    private static void transformByFacing(Vector3d local, Direction facing, Vector3d out) {
        out.set(local);
        switch (facing) {
            case NORTH -> {}
            case SOUTH -> rotateY(out, Math.PI);
            case EAST -> rotateY(out, Math.PI / 2);
            case WEST -> rotateY(out, -Math.PI / 2);
            case UP -> rotateX(out, Math.PI / 2);
            case DOWN -> rotateX(out, -Math.PI / 2);
        }
    }

    private static void rotateY(Vector3d v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = v.x * cos - v.z * sin;
        double z = v.x * sin + v.z * cos;
        v.x = x;
        v.z = z;
    }

    private static void rotateX(Vector3d v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double y = v.y * cos - v.z * sin;
        double z = v.y * sin + v.z * cos;
        v.y = y;
        v.z = z;
    }

    private static double pidStep(double error, double prevError, double integral,
                                   double kp, double ki, double kd) {
        return kp * error + ki * integral + kd * (error - prevError);
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (boundSyncPos != null) {
            tag.putInt("SyncX", boundSyncPos.getX());
            tag.putInt("SyncY", boundSyncPos.getY());
            tag.putInt("SyncZ", boundSyncPos.getZ());
        }
        if (boundWarheadPos != null) {
            tag.putInt("WarheadX", boundWarheadPos.getX());
            tag.putInt("WarheadY", boundWarheadPos.getY());
            tag.putInt("WarheadZ", boundWarheadPos.getZ());
        }
        tag.putInt("AngledMode", angledMode);
        tag.putBoolean("CreativeMode", creativeMode);
        tag.putBoolean("FuelAvailable", fuelAvailable);
        tag.putBoolean("ElectricMode", electricMode);
        tag.putInt("SyncFacing", syncFacingCache.get3DDataValue());
        tag.putInt("ActiveNozzleMask", activeNozzleMask);
        tag.putDouble("CurrentThrustPN", currentThrustPN);
        tag.putDouble("SubVelX", subLevelVelocity.x);
        tag.putDouble("SubVelY", subLevelVelocity.y);
        tag.putDouble("SubVelZ", subLevelVelocity.z);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("SyncX")) {
            boundSyncPos = new BlockPos(tag.getInt("SyncX"), tag.getInt("SyncY"), tag.getInt("SyncZ"));
        } else {
            boundSyncPos = null;
        }
        if (tag.contains("WarheadX")) {
            boundWarheadPos = new BlockPos(tag.getInt("WarheadX"), tag.getInt("WarheadY"), tag.getInt("WarheadZ"));
        } else {
            boundWarheadPos = null;
        }
        angledMode = tag.getInt("AngledMode");
        creativeMode = tag.getBoolean("CreativeMode");
        fuelAvailable = tag.getBoolean("FuelAvailable");
        electricMode = tag.getBoolean("ElectricMode");
        syncFacingCache = Direction.from3DDataValue(tag.getInt("SyncFacing"));
        activeNozzleMask = tag.getInt("ActiveNozzleMask");
        currentThrustPN = tag.getDouble("CurrentThrustPN");
        subLevelVelocity.set(tag.getDouble("SubVelX"), tag.getDouble("SubVelY"), tag.getDouble("SubVelZ"));
    }

    // ==================== Thrust Scroll Behaviour ====================

    public static class ThrustScrollBehaviour extends ScrollValueBehaviour {
        public ThrustScrollBehaviour(SmartBlockEntity be) {
            super(Component.translatable("aero_reformation.rcs_thruster.thrust"), be, new ThrustValueBox());
            this.between(0, THRUST_OPTIONS.length - 1);
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(this.label, THRUST_OPTIONS.length - 1, 1,
                    Collections.emptyList(),
                    new ValueSettingsFormatter(settings -> {
                        int idx = Math.max(0, Math.min(settings.value(), THRUST_OPTIONS.length - 1));
                        return Component.literal(THRUST_OPTIONS[idx] + "pN");
                    }));
        }

        @Override
        public String formatValue() {
            int idx = Math.max(0, Math.min(value, THRUST_OPTIONS.length - 1));
            return THRUST_OPTIONS[idx] + "pN";
        }
    }

    public static class ThrustValueBox extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return new Vec3(8, 8, 15.5).scale(1.0 / 16.0);
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            return super.getLocalOffset(level, pos, state);
        }
    }
}
