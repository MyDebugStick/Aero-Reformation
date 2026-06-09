package dev.simulated_team.aero_reformation.content.blocks.com_offset;

import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ComOffsetBlockEntity extends BlockEntity {
    private static final String KEY_X = "comX";
    private static final String KEY_Y = "comY";
    private static final String KEY_Z = "comZ";

    private double comX, comY, comZ;

    public ComOffsetBlockEntity(BlockPos pos, BlockState state) {
        super(AeroBlocks.COM_OFFSET_BE.get(), pos, state);
    }

    public double getComX() { return comX; }
    public double getComY() { return comY; }
    public double getComZ() { return comZ; }

    public void setCom(double x, double y, double z) {
        this.comX = Math.clamp(x, -100, 100);
        this.comY = Math.clamp(y, -100, 100);
        this.comZ = Math.clamp(z, -100, 100);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putDouble(KEY_X, comX);
        tag.putDouble(KEY_Y, comY);
        tag.putDouble(KEY_Z, comZ);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        comX = tag.getDouble(KEY_X);
        comY = tag.getDouble(KEY_Y);
        comZ = tag.getDouble(KEY_Z);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        saveAdditional(tag, provider);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
