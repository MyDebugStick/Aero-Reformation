/*
 * Copyright (c) 2024-2025 Aeronautics Team (dev.eriksonn and contributors)
 * Sable physics engine: dev.ryanhcode.sable
 *
 * Gravity Crystal — physics handled by Sable FloatingBlockController via JSON.
 * Mixin removes restoring torque → force effectively at COM.
 */

package dev.simulated_team.aero_reformation.content.blocks.gravity_crystal;

import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class GravityCrystalBlockEntity extends BlockEntity {
    private static final String LIFT_KEY = "gc_lift";
    private static final String DRAG_KEY = "gc_drag";
    private static final String ANG_KEY = "gc_ang";

    // Cached copy of settings for loading before sublevel is available
    private float pendingLift = Float.NaN;
    private float pendingDrag = Float.NaN;
    private float pendingAng = Float.NaN;

    public GravityCrystalBlockEntity(BlockPos pos, BlockState state) {
        super(AeroBlocks.GRAVITY_CRYSTAL_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            var sl = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, worldPosition);
            if (sl != null) {
                GravityCrystalSettings.CRYSTAL_SUBLEVELS.add(sl.getUniqueId());
                // Apply pending settings loaded from NBT before sublevel was available
                if (!Float.isNaN(pendingLift)) {
                    GravityCrystalSettings s = GravityCrystalSettings.get(sl.getUniqueId());
                    s.liftMultiplier = pendingLift;
                    s.dragMultiplier = pendingDrag;
                    s.angularDragMultiplier = pendingAng;
                    pendingLift = Float.NaN;
                    setChanged();
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        GravityCrystalSettings s = settings();
        if (s == null) return;
        tag.putFloat(LIFT_KEY, s.liftMultiplier);
        tag.putFloat(DRAG_KEY, s.dragMultiplier);
        tag.putFloat(ANG_KEY, s.angularDragMultiplier);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains(LIFT_KEY)) {
            float lift = tag.getFloat(LIFT_KEY);
            float drag = tag.getFloat(DRAG_KEY);
            float ang = tag.getFloat(ANG_KEY);
            GravityCrystalSettings s = settings();
            if (s != null && level != null) {
                // Sublevel available — apply directly
                s.liftMultiplier = lift;
                s.dragMultiplier = drag;
                s.angularDragMultiplier = ang;
            } else {
                // Sublevel not yet available — cache for onLoad
                pendingLift = lift;
                pendingDrag = drag;
                pendingAng = ang;
            }
        }
    }

    private GravityCrystalSettings settings() {
        if (level == null) return null;
        var sl = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, worldPosition);
        if (sl == null) return null;
        return GravityCrystalSettings.get(sl.getUniqueId());
    }
}
