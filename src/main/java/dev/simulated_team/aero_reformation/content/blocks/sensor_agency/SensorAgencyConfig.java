package dev.simulated_team.aero_reformation.content.blocks.sensor_agency;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** Configuration and inventory for Sensor Agency block entity. */
public class SensorAgencyConfig {
    public int gimbalPrimaryLimit = 45;
    public int gimbalSecondaryLimit = 45;
    public boolean gimbalInverted = false;

    /** Altitude signal range in world-height (default: -64 to 320) */
    public int altitudeLowWorld = -64;
    public int altitudeHighWorld = 320;
    public boolean altitudeInverted = false;

    public int velocityMaxSpeed = 10;

    public boolean navInverted = false;

    public final SimpleContainer compassSlot = new SimpleContainer(1) {
        @Override
        public void setChanged() {}
    };

    /** Convert world-height to normalized [0,1] for sensor computation. */
    public float toNormalizedLow() {
        return (float)(altitudeLowWorld + 64) / 384f;
    }

    /** Convert world-height to normalized [0,1] for sensor computation. */
    public float toNormalizedHigh() {
        return (float)(altitudeHighWorld + 64) / 384f;
    }

    public CompoundTag write(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("gimbalPrimary", gimbalPrimaryLimit);
        tag.putInt("gimbalSecondary", gimbalSecondaryLimit);
        tag.putBoolean("gimbalInverted", gimbalInverted);
        tag.putInt("altLow", altitudeLowWorld);
        tag.putInt("altHigh", altitudeHighWorld);
        tag.putBoolean("altInverted", altitudeInverted);
        tag.putInt("velMaxSpeed", velocityMaxSpeed);
        tag.putBoolean("navInverted", navInverted);
        if (!compassSlot.getItem(0).isEmpty()) {
            tag.put("compass", compassSlot.getItem(0).save(registries));
        }
        return tag;
    }

    public void read(CompoundTag tag, HolderLookup.Provider registries) {
        gimbalPrimaryLimit = tag.getInt("gimbalPrimary");
        gimbalSecondaryLimit = tag.getInt("gimbalSecondary");
        gimbalInverted = tag.getBoolean("gimbalInverted");
        altitudeLowWorld = tag.getInt("altLow");
        altitudeHighWorld = tag.getInt("altHigh");
        altitudeInverted = tag.getBoolean("altInverted");
        velocityMaxSpeed = tag.getInt("velMaxSpeed");
        navInverted = tag.getBoolean("navInverted");
        if (tag.contains("compass")) {
            compassSlot.setItem(0, ItemStack.parse(registries, tag.getCompound("compass")).orElse(ItemStack.EMPTY));
        }
    }
}
