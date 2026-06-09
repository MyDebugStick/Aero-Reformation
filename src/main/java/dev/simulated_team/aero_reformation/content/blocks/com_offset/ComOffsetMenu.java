package dev.simulated_team.aero_reformation.content.blocks.com_offset;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

public class ComOffsetMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final ComOffsetBlockEntity blockEntity;

    public ComOffsetMenu(int id, Inventory inv, BlockPos pos, ComOffsetBlockEntity blockEntity) {
        super(MenuType.GENERIC_9x1, id);
        this.pos = pos;
        this.blockEntity = blockEntity;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    public void updateCom(double x, double y, double z) {
        blockEntity.setCom(x, y, z);
        PacketDistributor.sendToServer(new ComConfigPayload(pos, x, y, z));
    }

    public ComOffsetBlockEntity getBlockEntity() { return blockEntity; }
}
