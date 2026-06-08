package dev.simulated_team.aero_reformation.content.blocks.rcs_thruster;

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
import org.joml.Vector3d;

import java.util.Collections;
import java.util.List;

public class RcsThrusterBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor {

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
    public ScrollValueBehaviour thrustScroll;

    // Gasoline fluid ID
    private static final String GASOLINE_FLUID = "createdieselgenerators:gasoline";

    // Angled nozzle reduction mode: 0=100%, 1=50%, 2=25%, 3=10%
    private static final double[] ANGLED_REDUCTION = {1.0, 0.5, 0.25, 0.1, 0.05, 0.02};
    private int angledMode = 0;

    // Fuel: configurable via AeroReformationConfig, default 5000pN/mB/tick
    private double getFuelConsumption() {
        return dev.simulated_team.aero_reformation.config.AeroReformationConfig.rcsFuelConsumption;
    }
    private boolean creativeMode = false;
    private boolean fuelAvailable = false; // synced for client VFX


    // Working vectors (reused)
    private final Vector3d thrustWorld = new Vector3d();
    private final Vector3d blockCenter = new Vector3d();
    private final Vector3d force = new Vector3d();
    private final Vector3d totalForce = new Vector3d();

    public RcsThrusterBlockEntity(BlockPos pos, BlockState state) {
        super(AeroBlocks.RCS_THRUSTER_BE.get(), pos, state);
    }

    public void setBoundSync(BlockPos pos) {
        this.boundSyncPos = pos;
        this.setChanged();
    }

    public BlockPos getBoundSync() {
        return boundSyncPos;
    }

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

    /** Find and return a gasoline IFluidHandler from the back face (non-nozzle side) */
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
        // Check if the fluid is gasoline
        for (int i = 0; i < source.getTanks(); i++) {
            var stack = source.getFluidInTank(i);
            if (!stack.isEmpty()) {
                var key = net.minecraft.core.registries.BuiltInRegistries.FLUID
                        .getKey(stack.getFluid());
                if (key != null && key.toString().equals(GASOLINE_FLUID)) {
                    return stack.getAmount();
                }
            }
        }
        return 0;
    }

    /** Drain up to 'amount' mB of gasoline from the connected container. */
    private int drainFuel(int amount) {
        IFluidHandler source = getFuelSource();
        if (source == null) return 0;
        for (int i = 0; i < source.getTanks(); i++) {
            var stack = source.getFluidInTank(i);
            if (!stack.isEmpty()) {
                var key = net.minecraft.core.registries.BuiltInRegistries.FLUID
                        .getKey(stack.getFluid());
                if (key != null && key.toString().equals(GASOLINE_FLUID)) {
                    return source.drain(amount, IFluidHandler.FluidAction.EXECUTE).getAmount();
                }
            }
        }
        return 0;
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
        if (boundSyncPos == null || level == null) return;
        if (!creativeMode && !fuelAvailable) {
            prevActiveMask = 0;
            return;
        }
        BlockEntity be = level.getBlockEntity(boundSyncPos);
        if (!(be instanceof DirectionalSynchronizerMasterBlockEntity sync)) return;

        Direction syncFacing = sync.getBlockState().getValue(DirectionalBlock.FACING);
        Direction rcsFacing = getBlockState().getValue(RcsThrusterBlock.FACING);
        RandomSource rand = level.random;
        int newMask = 0;

        for (Direction inputFace : Direction.values()) {
            if (inputFace == syncFacing) continue;
            int signal = level.getSignal(boundSyncPos.relative(inputFace), inputFace.getOpposite());
            if (signal == 0) continue;

            int nozzleIdx = getNozzleForSyncFace(syncFacing, inputFace);
            newMask |= (1 << nozzleIdx);

            // Nozzle position for sound
            Vector3d nPos = new Vector3d(NOZZLE_POS[nozzleIdx]);
            transformByFacing(nPos, rcsFacing, nPos);
            Vector3d nozzleWorld = new Vector3d(
                    worldPosition.getX() + nPos.x / 16.0,
                    worldPosition.getY() + nPos.y / 16.0,
                    worldPosition.getZ() + nPos.z / 16.0);
            dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, nozzleWorld);

            // Activation sound
            if ((prevActiveMask & (1 << nozzleIdx)) == 0) {
                level.playLocalSound(nozzleWorld.x, nozzleWorld.y, nozzleWorld.z,
                        SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS,
                        0.3f, 0.8f + rand.nextFloat() * 0.4f, false);
            }
            // Continuous crackle
            if (level.getGameTime() % 3 == nozzleIdx % 3) {
                level.playLocalSound(nozzleWorld.x, nozzleWorld.y, nozzleWorld.z,
                        SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                        0.50f, 0.9f + rand.nextFloat() * 0.3f, false);
            }
        }
        prevActiveMask = newMask;

        // Particles: forward nozzle only, exhaust opposite to facing (goes out the front)
        if ((newMask & 1) == 0) return;

        // Exhaust direction = facing direction (comes out the nozzle front)
        Vector3d particleDir = new Vector3d(
                rcsFacing.getStepX(), rcsFacing.getStepY(), rcsFacing.getStepZ());

        // Rotate direction by sublevel's world orientation
        var sl = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, worldPosition);
        if (sl != null) {
            sl.logicalPose().orientation().transform(particleDir);
        }

        // Position: block center + 0.4 toward front (facing direction)
        Vector3d particleWorld = new Vector3d(
                worldPosition.getX() + 0.5 + rcsFacing.getStepX() * 0.4,
                worldPosition.getY() + 0.5 + rcsFacing.getStepY() * 0.4,
                worldPosition.getZ() + 0.5 + rcsFacing.getStepZ() * 0.4);
        dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, particleWorld);

        for (int i = 0; i < 3; i++) {
            double ox = particleDir.x * 0.2 + (rand.nextDouble() - 0.5) * 0.15;
            double oy = particleDir.y * 0.2 + (rand.nextDouble() - 0.5) * 0.15;
            double oz = particleDir.z * 0.2 + (rand.nextDouble() - 0.5) * 0.15;
            level.addParticle(ParticleTypes.FLAME,
                    particleWorld.x + ox, particleWorld.y + oy, particleWorld.z + oz,
                    particleDir.x * 0.1, particleDir.y * 0.1, particleDir.z * 0.1);
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle bodyHandle, double dt) {
        if (boundSyncPos == null) return;
        if (level == null) return;

        BlockEntity be = level.getBlockEntity(boundSyncPos);
        if (!(be instanceof DirectionalSynchronizerMasterBlockEntity sync)) return;

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
        QueuedForceGroup liftGroup = subLevel.getOrCreateQueuedForceGroup(ForceGroups.LIFT.get());

        for (Direction inputFace : Direction.values()) {
            if (inputFace == syncFacing) continue;

            int signal = level.getSignal(boundSyncPos.relative(inputFace), inputFace.getOpposite());
            if (signal == 0) continue;

            int nozzleIdx = getNozzleForSyncFace(syncFacing, inputFace);
            Vector3d localDir = NOZZLE_LOCAL[nozzleIdx];

            transformByFacing(localDir, rcsFacing, thrustWorld);

            double scale = signal / 15.0;
            double mult = (nozzleIdx == 0) ? 1.0 : ANGLED_REDUCTION[angledMode];
            double thrustPN = maxThrust * scale * mult;
            totalThrustPN += thrustPN;
            totalForce.add(thrustWorld.mul(thrustPN / 40.0, force));
        }

        // Fuel consumption
        int fuelNeeded = creativeMode ? 0 : (int) Math.ceil(totalThrustPN / getFuelConsumption());
        boolean prevFuel = fuelAvailable;
        if (!creativeMode && fuelNeeded > 0) {
            int drained = drainFuel(fuelNeeded);
            fuelAvailable = drained > 0;
            if (drained < fuelNeeded) {
                double ratio = fuelNeeded > 0 ? (double) drained / fuelNeeded : 0;
                totalForce.mul(ratio);
            }
        } else {
            fuelAvailable = creativeMode;
        }
        if (fuelAvailable != prevFuel) {
            sendData();
            setChanged();
        }

        // Apply force directly (burst output)
        if (totalForce.lengthSquared() > 1e-6) {
            liftGroup.applyAndRecordPointForce(blockCenter, totalForce);
        }
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

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (boundSyncPos != null) {
            tag.putInt("SyncX", boundSyncPos.getX());
            tag.putInt("SyncY", boundSyncPos.getY());
            tag.putInt("SyncZ", boundSyncPos.getZ());
        }
        tag.putInt("AngledMode", angledMode);
        tag.putBoolean("CreativeMode", creativeMode);
        tag.putBoolean("FuelAvailable", fuelAvailable);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("SyncX")) {
            boundSyncPos = new BlockPos(tag.getInt("SyncX"), tag.getInt("SyncY"), tag.getInt("SyncZ"));
        }
        angledMode = tag.getInt("AngledMode");
        creativeMode = tag.getBoolean("CreativeMode");
        fuelAvailable = tag.getBoolean("FuelAvailable");
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
