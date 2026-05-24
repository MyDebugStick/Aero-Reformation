package dev.simulated_team.aero_reformation.content.items.ender_compass;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.core.GlobalPos;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EnderArrowTracker {
    private static final Map<Integer, String> ARROWS = new ConcurrentHashMap<>();
    private static final Map<String, GlobalPos> CHANNEL_POS = new ConcurrentHashMap<>();
    private static final Map<Integer, AbstractArrow> ARROW_REFS = new ConcurrentHashMap<>();

    public static void register(AbstractArrow arrow, String channel, GlobalPos target) {
        ARROWS.put(arrow.getId(), channel);
        ARROW_REFS.put(arrow.getId(), arrow);
        if (target != null) CHANNEL_POS.put(channel, target);
    }

    public static void remove(AbstractArrow arrow) {
        ARROWS.remove(arrow.getId());
        ARROW_REFS.remove(arrow.getId());
    }

    public static Optional<String> getChannel(AbstractArrow arrow) {
        return Optional.ofNullable(ARROWS.get(arrow.getId()));
    }

    public static void updatePos(String channel, GlobalPos pos) {
        CHANNEL_POS.put(channel, pos);
    }

    public static Optional<GlobalPos> getPos(String channel) {
        return Optional.ofNullable(CHANNEL_POS.get(channel));
    }

    public static Set<Integer> getActiveArrowIds() {
        return ARROWS.keySet();
    }

    public static AbstractArrow getArrowRef(int id) {
        return ARROW_REFS.get(id);
    }

    public static String getChannelById(int id) {
        return ARROWS.get(id);
    }

    /**
     * Check if the given arrow is the youngest (most recently spawned) among all
     * arrows registered with the same channel. Only the youngest arrow should sync.
     */
    public static boolean isYoungestArrow(String channel, int arrowId) {
        return ARROWS.entrySet().stream()
                .filter(e -> channel.equals(e.getValue()))
                .mapToInt(Map.Entry::getKey)
                .max()
                .orElse(-1) == arrowId;
    }
}
