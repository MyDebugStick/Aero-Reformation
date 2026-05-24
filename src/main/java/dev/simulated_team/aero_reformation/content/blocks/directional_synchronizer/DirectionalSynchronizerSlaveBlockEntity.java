package dev.simulated_team.aero_reformation.content.blocks.directional_synchronizer;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;

import javax.annotation.Nullable;
import java.util.EnumMap;

public class DirectionalSynchronizerSlaveBlockEntity extends BlockEntity {

    @Nullable
    private BlockPos boundMasterPos;
    private int ticks = 0;
    private int ticksSinceChange = 0;
    private boolean active = true;
    private final EnumMap<Direction, Integer> redstoneMap = new EnumMap<>(Direction.class);

    public DirectionalSynchronizerSlaveBlockEntity(BlockPos pos, BlockState state) {
        super(dev.simulated_team.aero_reformation.registrate.AeroBlocks.DIRECTIONAL_SYNCHRONIZER_SLAVE_BE.get(), pos, state);
        for (Direction dir : Direction.values()) redstoneMap.put(dir, 0);
    }

    public int getPower(Direction dir) {
        return redstoneMap.getOrDefault(dir, 0);
    }

    public void bindTo(BlockPos masterPos) {
        this.boundMasterPos = masterPos;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
        }
    }

    @Nullable
    public BlockPos getBoundMasterPos() {
        return boundMasterPos;
    }

    @Nullable
    public Direction getLocalFacing(Level level) {
        if (boundMasterPos == null || !level.isLoaded(boundMasterPos)) return null;
        if (level.getBlockEntity(boundMasterPos) instanceof DirectionalSynchronizerMasterBlockEntity masterBE) {
            return Direction.getNearest(
                    masterBE.getWorldFacingVector().x,
                    masterBE.getWorldFacingVector().y,
                    masterBE.getWorldFacingVector().z);
        }
        BlockState masterState = level.getBlockState(boundMasterPos);
        if (masterState.getBlock() instanceof DirectionalSynchronizerMasterBlock) {
            return masterState.getValue(DirectionalBlock.FACING);
        }
        return null;
    }

    /** Returns the continuous local output vector (not quantized to 6 directions). */
    @Nullable
    public org.joml.Vector3d getOutputVector(Level level) {
        if (boundMasterPos == null || !level.isLoaded(boundMasterPos)) return null;
        org.joml.Vector3d worldVec;
        if (level.getBlockEntity(boundMasterPos) instanceof DirectionalSynchronizerMasterBlockEntity masterBE) {
            worldVec = masterBE.getWorldFacingVector();
        } else {
            BlockState masterState = level.getBlockState(boundMasterPos);
            if (!(masterState.getBlock() instanceof DirectionalSynchronizerMasterBlock)) return null;
            Direction local = masterState.getValue(DirectionalBlock.FACING);
            worldVec = new org.joml.Vector3d(local.getStepX(), local.getStepY(), local.getStepZ());
        }

        // If slave is on a physics body, convert world → local
        SubLevel slaveSub = Sable.HELPER.getContaining(this);
        if (slaveSub != null && worldVec != null) {
            Quaterniond inv = new Quaterniond(slaveSub.logicalPose().orientation()).invert();
            inv.transform(worldVec);
        }
        return worldVec;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (boundMasterPos != null) {
            CompoundTag bp = new CompoundTag();
            bp.putInt("X", boundMasterPos.getX());
            bp.putInt("Y", boundMasterPos.getY());
            bp.putInt("Z", boundMasterPos.getZ());
            tag.put("BoundMaster", bp);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("BoundMaster")) {
            CompoundTag bp = tag.getCompound("BoundMaster");
            this.boundMasterPos = new BlockPos(bp.getInt("X"), bp.getInt("Y"), bp.getInt("Z"));
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DirectionalSynchronizerSlaveBlockEntity be) {
        if (be.boundMasterPos == null || level.isClientSide()) return;
        be.ticks++;

        DirectionalSynchronizerMasterBlockEntity masterBE = null;
        if (level.getBlockEntity(be.boundMasterPos) instanceof DirectionalSynchronizerMasterBlockEntity mbe) {
            masterBE = mbe;
        }
        boolean isMirror = masterBE != null && masterBE.isMirrorMode();

        int interval = isMirror ? 2 : (be.active ? 10 : 60);
        if (be.ticks % interval != 0) return;

        boolean anyChanged = false;

        if (isMirror && masterBE != null) {
            SubLevel slaveSub = Sable.HELPER.getContaining(be);
            SubLevel masterSub = Sable.HELPER.getContaining(masterBE);
            for (Direction dir : Direction.values()) {
                org.joml.Vector3d v = new org.joml.Vector3d(dir.getStepX(), dir.getStepY(), dir.getStepZ());
                if (slaveSub != null) slaveSub.logicalPose().orientation().transform(v);
                if (masterSub != null) new Quaterniond(masterSub.logicalPose().orientation()).invert().transform(v);
                int signal = masterBE.getInputSignal(Direction.getNearest(v.x, v.y, v.z).getOpposite());
                if (be.redstoneMap.get(dir) != signal) {
                    be.redstoneMap.put(dir, signal);
                    anyChanged = true;
                }
            }
            // Mirror mode: stay active as long as signals are present; go inactive when all zero
            if (anyChanged) {
                be.ticksSinceChange = 0;
                be.active = true;
            } else if (be.redstoneMap.values().stream().allMatch(v -> v == 0)) {
                be.ticksSinceChange += interval;
                if (be.ticksSinceChange >= 60) be.active = false;
            }
        } else {
            org.joml.Vector3d outVec = be.getOutputVector(level);
            for (Direction dir : Direction.values()) {
                int signal;
                if (outVec == null) {
                    signal = 0;
                } else {
                    double dot = -(outVec.x * dir.getStepX() + outVec.y * dir.getStepY() + outVec.z * dir.getStepZ());
                    dot = Mth.clamp(dot, -1, 1);
                    double angleDeg = Math.toDegrees(Math.acos(dot));
                    signal = (int) Math.max(0, Math.round((1.0 - angleDeg / 90.0) * 15.0));
                }
                if (be.redstoneMap.get(dir) != signal) {
                    be.redstoneMap.put(dir, signal);
                    anyChanged = true;
                }
            }
        }

        boolean shouldPower = be.redstoneMap.values().stream().anyMatch(v -> v > 0);
        if (state.getValue(DirectionalSynchronizerSlaveBlock.POWERED) != shouldPower) {
            level.setBlock(pos, state.setValue(DirectionalSynchronizerSlaveBlock.POWERED, shouldPower), 3);
        }
        if (anyChanged) {
            level.updateNeighborsAt(pos, state.getBlock());
            be.ticksSinceChange = 0;
            be.active = true;
        } else if (!isMirror) {
            be.ticksSinceChange += interval;
            if (be.ticksSinceChange >= 60) {
                be.active = false;
            }
        }
    }
}
