package dev.simulated_team.aero_reformation.content.items.ender_compass;

import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.NavigationTarget;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public enum EnderCompassNavigationTarget implements NavigationTarget {
    INSTANCE;

    @Override
    @Nullable
    public Vec3 getTarget(NavTableBlockEntity navBE, ItemStack self) {
        EnderCompassData data = self.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
        if (!data.hasChannel()) return null;

        // Prefer channel cache (real-time sync) over ItemStack data
        GlobalPos pos = EnderChannelCache.get(data.channel())
                .orElse(data.target().orElse(null));
        if (pos == null) return null;

        return new Vec3(pos.pos().getX() + 0.5, pos.pos().getY(), pos.pos().getZ() + 0.5);
    }

    @Override
    public float getMaxRange() { return 0; }

    @Override
    public float getModulatingRange() { return 0; }
}
