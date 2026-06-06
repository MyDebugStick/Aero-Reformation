package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;

/**
 * Custom ticket type for physics anchor chunk loading.
 * Uses timeout=0 (persistent, like /forceload) and radius=1.
 */
public class TicketManager {
    /** Ticket level 31 = full player-equivalent loading */
    public static final TicketType<ChunkPos> PHYSICS_ANCHOR =
            TicketType.create("aero_reformation:physics_anchor",
                    Comparator.comparingLong(ChunkPos::toLong), 0);
}
