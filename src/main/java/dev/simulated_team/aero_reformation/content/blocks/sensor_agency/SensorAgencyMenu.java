package dev.simulated_team.aero_reformation.content.blocks.sensor_agency;

import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SensorAgencyMenu extends AbstractContainerMenu {
    public static final int COMPASS_SLOT = 0;
    public static final int PLAYER_INV_START = 1;

    public final SensorAgencyBlockEntity be;
    public final SensorAgencyConfig config;

    // Client constructor — config synced via Screen.init() from CLIENT_SYNC
    public SensorAgencyMenu(int id, Inventory playerInv, FriendlyByteBuf extraData) {
        this(id, playerInv,
                (SensorAgencyBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SensorAgencyConfig());
    }

    // Server constructor
    public SensorAgencyMenu(int id, Inventory playerInv, SensorAgencyBlockEntity be, SensorAgencyConfig config) {
        super(AeroBlocks.SENSOR_AGENCY_MENU.get(), id);
        this.be = be;
        this.config = config;

        // Compass slot only (no player inventory)
        addSlot(new Slot(config.compassSlot, 0, 92, 22) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.NavigationTarget
                        .ofStack(stack) != null;
            }
        });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index == COMPASS_SLOT) {
            Slot slot = slots.get(index);
            if (slot.hasItem()) {
                ItemStack stack = slot.getItem().copy();
                slot.set(ItemStack.EMPTY);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                if (be != null) be.saveConfig();
                return ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return be != null && !be.isRemoved();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (be != null && !player.level().isClientSide()) {
            be.saveConfig();
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (be == null || player.level().isClientSide()) return true;
        switch (buttonId) {
            case 0: config.gimbalPrimaryLimit = Math.clamp(config.gimbalPrimaryLimit - 5, 1, 90); break;
            case 1: config.gimbalPrimaryLimit = Math.clamp(config.gimbalPrimaryLimit + 5, 1, 90); break;
            case 2: config.gimbalSecondaryLimit = Math.clamp(config.gimbalSecondaryLimit - 5, 1, 90); break;
            case 3: config.gimbalSecondaryLimit = Math.clamp(config.gimbalSecondaryLimit + 5, 1, 90); break;
            case 4: config.altitudeLowWorld = Math.clamp(config.altitudeLowWorld - 1, -64, 320); break;
            case 5: config.altitudeLowWorld = Math.clamp(config.altitudeLowWorld + 1, -64, 320); break;
            case 6: config.altitudeHighWorld = Math.clamp(config.altitudeHighWorld - 1, -64, 320); break;
            case 7: config.altitudeHighWorld = Math.clamp(config.altitudeHighWorld + 1, -64, 320); break;
            case 8: config.velocityMaxSpeed = Math.clamp(config.velocityMaxSpeed - 1, 1, 50); break;
            case 9: config.velocityMaxSpeed = Math.clamp(config.velocityMaxSpeed + 1, 1, 50); break;
            case 10: config.gimbalInverted = !config.gimbalInverted; break;
            case 11: config.navInverted = !config.navInverted; break;
        }
        be.saveConfig();
        return true;
    }
}
