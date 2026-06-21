package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class PhysicsAnchorBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public PhysicsAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(AeroBlocks.PHYSICS_ANCHOR_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            AnchorChunkLoader.addAnchor(level, worldPosition);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (level == null) return false;
        String indent = "\u00A0\u00A0\u00A0";

        AnchorMarkerEntity marker = AnchorChunkLoader.getMarker(level, worldPosition);
        if (marker != null && !marker.isRemoved()) {
            Component name = marker.hasCustomName() ? marker.getCustomName() : Component.translatable("aero_reformation.physics_anchor.goggle.unnamed");
            tooltip.add(Component.literal(indent).append(Component.translatable("aero_reformation.physics_anchor.goggle.name", name))
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.literal(indent).append(Component.translatable("aero_reformation.physics_anchor.goggle.no_marker"))
                    .withStyle(ChatFormatting.RED));
            tooltip.add(Component.literal(indent).append(Component.translatable("aero_reformation.physics_anchor.goggle.replace_hint"))
                    .withStyle(ChatFormatting.GRAY));
        }

        int radius = AnchorChunkLoader.getRadius(level, worldPosition);
        int side = 2 * radius + 1;
        tooltip.add(Component.literal(indent).append(Component.translatable("aero_reformation.physics_anchor.goggle.radius", radius, side, side))
                .withStyle(ChatFormatting.GREEN));
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
    }
}