package dev.simulated_team.aero_reformation.content.blocks.directional_synchronizer;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;

public class DirectionalSynchronizerMasterBlockEntity extends BlockEntity {

    private Direction worldFacing = Direction.NORTH;
    private final org.joml.Vector3d worldFacingVector = new org.joml.Vector3d(0, 0, -1);
    private int ticks = 0;

    private boolean mirrorMode = false;
    private final int[] inputSignals = new int[6];
    private final int[] delayedSignals = new int[6];
    private final int[] lastSignals = new int[6];  // last sent to slaves
    private int delayTick = 0;

    public DirectionalSynchronizerMasterBlockEntity(BlockPos pos, BlockState state) {
        super(dev.simulated_team.aero_reformation.registrate.AeroBlocks.DIRECTIONAL_SYNCHRONIZER_MASTER_BE.get(), pos, state);
    }

    public Direction getWorldFacing() { return worldFacing; }
    public org.joml.Vector3d getWorldFacingVector() { return worldFacingVector; }
    public boolean isMirrorMode() { return mirrorMode; }
    public int getInputSignal(Direction dir) { return delayedSignals[dir.ordinal()]; }

    public boolean hasInputChanged() {
        for (int i = 0; i < 6; i++) {
            if (delayedSignals[i] != lastSignals[i]) return true;
        }
        return false;
    }

    public void markInputConsumed() {
        System.arraycopy(delayedSignals, 0, lastSignals, 0, 6);
    }

    public void toggleMirrorMode() {
        this.mirrorMode = !this.mirrorMode;
        this.setChanged();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DirectionalSynchronizerMasterBlockEntity be) {
        if (level.isClientSide()) return;
        be.ticks++;

        if (!be.mirrorMode && be.ticks % 10 != 0) return;

        if (!be.mirrorMode) {
            // Default mode: compute world facing
            Direction local = state.getValue(DirectionalBlock.FACING);
            be.worldFacingVector.set(local.getStepX(), local.getStepY(), local.getStepZ());
            SubLevel subLevel = Sable.HELPER.getContaining(be);
            if (subLevel != null) {
                Quaterniond rot = subLevel.logicalPose().orientation();
                rot.transform(be.worldFacingVector);
            }
            be.worldFacing = Direction.getNearest(
                    be.worldFacingVector.x, be.worldFacingVector.y, be.worldFacingVector.z);
        } else {
            // Mirror mode: read input signals, output after 2-tick delay
            be.delayTick++;
            if (be.delayTick >= 3) {
                be.delayTick = 0;
                // Shift: delayed <- current, then read new current
                System.arraycopy(be.inputSignals, 0, be.delayedSignals, 0, 6);
            }
            // Read inputs every tick
            for (Direction dir : Direction.values()) {
                be.inputSignals[dir.ordinal()] = level.getSignal(pos.relative(dir), dir.getOpposite());
            }
            be.setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("WorldFacing", worldFacing.ordinal());
        tag.putBoolean("MirrorMode", mirrorMode);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("WorldFacing")) worldFacing = Direction.values()[tag.getInt("WorldFacing")];
        mirrorMode = tag.getBoolean("MirrorMode");
    }
}
