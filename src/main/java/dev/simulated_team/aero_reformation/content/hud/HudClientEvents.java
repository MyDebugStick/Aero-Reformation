package dev.simulated_team.aero_reformation.content.hud;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Client-side events: open the HUD config screen when the configured key
 * (default Ctrl+H) is pressed; load persisted entries on login.
 */
@EventBusSubscriber(modid = AeroReformation.MODID, value = Dist.CLIENT)
public class HudClientEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        while (HudKeyBindings.HUD_CONFIG_KEY.consumeClick()) {
            mc.setScreen(new HudConfigScreen());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        HudBoard.loadFromPlayer(event.getEntity());
        HudPlaceholderBoard.loadFromPlayer(event.getEntity());
    }
}
