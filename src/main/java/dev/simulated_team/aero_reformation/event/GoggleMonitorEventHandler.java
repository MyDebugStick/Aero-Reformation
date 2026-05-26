package dev.simulated_team.aero_reformation.event;

import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.content.items.goggle_monitor.GoggleMonitorData;
import dev.simulated_team.aero_reformation.content.items.goggle_monitor.SensorMonitorEntry;
import dev.simulated_team.aero_reformation.network.GoggleBindPacket;
import dev.simulated_team.aero_reformation.network.GoggleMonitorSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

import static dev.simulated_team.aero_reformation.AeroReformation.MODID;

@EventBusSubscriber(modid = MODID)
public class GoggleMonitorEventHandler {

    /** Load persisted bindings when player logs in */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        GoggleMonitorData.loadFromPlayer(event.getEntity());
    }

    private static final Map<String, ResourceLocation> SENSOR_IDS = Map.of(
            "altitude_sensor", ResourceLocation.parse("simulated:altitude_sensor"),
            "velocity_sensor", ResourceLocation.parse("simulated:velocity_sensor"),
            "gimbal_sensor", ResourceLocation.parse("simulated:gimbal_sensor"),
            "nav_table", ResourceLocation.parse("simulated:navigation_table"),
            "redstone_link", ResourceLocation.parse("create:redstone_link")
    );

    // ─── Client: open naming GUI ───

    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class Client {
        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            var player = event.getEntity();
            ItemStack goggles = GoggleMonitorData.findGoggles(player);
            if (goggles.isEmpty()) return;
            if (!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) return;
            if (player.isShiftKeyDown()) return; // Don't bind when sneaking (preserve vanilla interactions)
            if (Minecraft.getInstance().level == null) return;

            var id = event.getLevel().getBlockState(event.getPos()).getBlock();
            var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(id);
            String sensorType = null;
            for (var e : SENSOR_IDS.entrySet()) {
                if (key.equals(e.getValue())) { sensorType = e.getKey(); break; }
            }
            if (sensorType == null) return;

            event.setCanceled(true);
            // Send bind to server immediately with default name
            String name = GoggleMonitorData.sensorDefaultName(sensorType);
            var mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                PacketDistributor.sendToServer(new GoggleBindPacket(event.getPos(), sensorType, name));
            }
        }
    }

    // ─── Server: sync + clear on remove ───
    private static int serverTickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        serverTickCounter++;
        var server = event.getServer();
        if (server == null) return;

        for (var player : server.getPlayerList().getPlayers()) {
            ItemStack goggles = GoggleMonitorData.findGoggles(player);

            // Clear bindings when goggles removed
            if (goggles.isEmpty()) {
                GoggleMonitorData.clearEntries(player);
                continue;
            }

            // Sync data every 2 ticks
            if (serverTickCounter % 2 != 0) continue;
            var entries = GoggleMonitorData.getEntries(player);
            if (entries.isEmpty()) continue;
            List<BlockPos> positions = entries.stream().map(SensorMonitorEntry::pos).toList();
            AeroReformation.LOGGER.warn("AeroDebug GOGGLE: sending sync to {} with {} positions",
                    player.getName().getString(), positions.size());
                PacketDistributor.sendToPlayer(player, GoggleMonitorSyncPacket.fromPositions(positions, player.serverLevel()));
        }
    }
}
