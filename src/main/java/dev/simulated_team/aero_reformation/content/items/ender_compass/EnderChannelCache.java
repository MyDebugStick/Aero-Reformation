package dev.simulated_team.aero_reformation.content.items.ender_compass;

import net.minecraft.core.GlobalPos;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-wide channel position cache.
 * When any compass records a position, it's stored here so all compasses
 * (including those in nav tables) see the latest position.
 */
public class EnderChannelCache {
    private static final Map<String, GlobalPos> CHANNELS = new ConcurrentHashMap<>();

    public static void put(String channel, GlobalPos pos) {
        CHANNELS.put(channel, pos);
    }

    public static Optional<GlobalPos> get(String channel) {
        return Optional.ofNullable(CHANNELS.get(channel));
    }

    public static void remove(String channel) {
        CHANNELS.remove(channel);
    }
}
