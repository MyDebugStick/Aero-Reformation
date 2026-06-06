package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Client-only helper. Called from PhysicsAnchorBlock when sneaking + right-click.
 */
public class AnchorNamingGuiOpener {

    public static void open(BlockPos pos, Level level, Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        AnchorMarkerEntity marker = AnchorChunkLoader.getMarker(level, pos);
        String currentName = (marker != null && marker.hasCustomName())
                ? marker.getCustomName().getString()
                : "";
        mc.setScreen(new AnchorNamingScreen(pos, currentName));
    }
}
