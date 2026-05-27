package dev.simulated_team.aero_reformation.mixin.feature.nav_table_cache;

import dev.simulated_team.aero_reformation.content.items.ender_compass.EnderChannelCache;
import dev.simulated_team.aero_reformation.content.items.ender_compass.EnderCompassData;
import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Makes NavTable read EnderChannelCache as a fallback when the compass
 * component's target is not set. This enables auto-sync from loadstones
 * and arrows to propagate to NavTable outputs.
 */
@Mixin(value = NavTableBlockEntity.class, remap = false)
public class NavTableChannelCacheMixin {

    @Inject(method = "getTargetPosition", at = @At("TAIL"), cancellable = true)
    private void useChannelCache(boolean refresh, CallbackInfoReturnable<Vec3> cir) {
        NavTableBlockEntity self = (NavTableBlockEntity) (Object) this;
        ItemStack held = self.getHeldItem();
        if (held.isEmpty()) return;

        EnderCompassData data = held.getOrDefault(AeroDataComponents.ENDER_COMPASS.get(), EnderCompassData.EMPTY);
        if (!data.hasChannel()) return;

        Optional<GlobalPos> cached = EnderChannelCache.get(data.channel());
        if (cached.isPresent()) {
            Vec3 existing = cir.getReturnValue();
            if (existing == null || refresh) {
                GlobalPos gp = cached.get();
                cir.setReturnValue(new Vec3(gp.pos().getX() + 0.5, gp.pos().getY() + 0.5, gp.pos().getZ() + 0.5));
            }
        }
    }
}
