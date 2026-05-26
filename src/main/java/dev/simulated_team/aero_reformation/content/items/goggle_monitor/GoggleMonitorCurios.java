package dev.simulated_team.aero_reformation.content.items.goggle_monitor;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Optional Curios integration for goggle detection.
 * Only loaded when Curios is present (guarded by ModList check).
 */
public class GoggleMonitorCurios {

    @SuppressWarnings({"deprecation", "removal"})
    public static ItemStack findGogglesInCurios(Player player) {
        var result = CuriosApi.getCuriosHelper().findFirstCurio(player, GoggleMonitorData::isGoggles);
        return result.map(r -> r.stack()).orElse(ItemStack.EMPTY);
    }
}
