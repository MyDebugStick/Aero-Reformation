package dev.simulated_team.aero_reformation.content.blocks.power;

import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PowerBlockEntity extends BlockEntity {

    private int yawMax = 90;
    private int pitchMax = 45;

    public PowerBlockEntity(BlockPos pos, BlockState state) {
        super(AeroBlocks.POWER_BE.get(), pos, state);
    }

    public int getYawMax() { return yawMax; }
    public int getPitchMax() { return pitchMax; }

    public void setYawMax(int v) {
        yawMax = Math.clamp(v, 1, 90);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    public void setPitchMax(int v) {
        pitchMax = Math.clamp(v, 1, 45);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("YawMax", yawMax);
        tag.putInt("PitchMax", pitchMax);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        yawMax = tag.getInt("YawMax");
        pitchMax = tag.getInt("PitchMax");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("YawMax", yawMax);
        tag.putInt("PitchMax", pitchMax);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        yawMax = tag.getInt("YawMax");
        pitchMax = tag.getInt("PitchMax");
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }
}


