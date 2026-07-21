package dev.simulated_team.aero_reformation.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.simulated_team.aero_reformation.content.blocks.physics_anchor.AnchorChunkLoader;
import dev.simulated_team.aero_reformation.content.items.ethereal_key.EtherealKeyItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.lang.reflect.Method;
import java.util.List;

public class RefCommands {

    private static Method RECOVER_METHOD;
    private static boolean RECOVER_RESOLVED = false;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ref")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("removebc")
                        .executes(RefCommands::removeAnchor)
                        .then(Commands.literal("all")
                                .executes(RefCommands::removeAllAnchors)
                        )
                )
                .then(Commands.literal("removeallmark")
                        .executes(RefCommands::removeAllMarkers)
                )
                .then(Commands.literal("hiddingremoveall")
                        .executes(RefCommands::hiddingRemoveAll)
                )
                .then(Commands.literal("recoverallsublevel")
                        .executes(RefCommands::recoverAllSubLevels)
                )
        );
    }

    private static int removeAnchor(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        var hit = player.pick(20, 0, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Look at a physics anchor block."));
            return 0;
        }

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        var level = player.level();
        level.destroyBlock(pos, true);
        source.sendSuccess(() -> Component.literal("Anchor destroyed at " + pos.toShortString()), true);
        return 1;
    }

    private static int removeAllAnchors(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var level = source.getLevel();
        int count = 0;
        for (var pos : List.copyOf(AnchorChunkLoader.getAllAnchorPositions(level.dimension()))) {
            level.destroyBlock(pos, true);
            count++;
        }
        int finalCount = count;
        source.sendSuccess(() -> Component.literal("Destroyed " + finalCount + " anchor(s)."), true);
        return 1;
    }

    private static int removeAllMarkers(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        int count = 0;
        for (var level : source.getServer().getAllLevels()) {
            count += AnchorChunkLoader.discardAllMarkers(level);
        }
        int finalCount = count;
        source.sendSuccess(() -> Component.literal("Removed " + finalCount + " marker entity(s). Re-place anchors to restore."), true);
        return 1;
    }

    private static int hiddingRemoveAll(CommandContext<CommandSourceStack> ctx) {
        int count = EtherealKeyItem.HIDDEN_SUBLEVELS.size();
        EtherealKeyItem.HIDDEN_SUBLEVELS.clear();
        final int finalCount = count;
        ctx.getSource().sendSuccess(() -> Component.literal("Unhid " + finalCount + " sublevel(s)."), true);
        return 1;
    }

    private static int recoverAllSubLevels(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        int total = 0;
        try {
            if (!RECOVER_RESOLVED) {
                RECOVER_RESOLVED = true;
                RECOVER_METHOD = Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem")
                        .getMethod("recoverSubLevel",
                                Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel"));
            }
            if (RECOVER_METHOD == null) {
                source.sendFailure(Component.literal("Sable recoverSubLevel API not available."));
                return 0;
            }

            for (ServerLevel sl : source.getServer().getAllLevels()) {
                Object container = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer")
                        .getMethod("getContainer", Level.class).invoke(null, sl);
                if (container == null) continue;
                Object physicsSystem = container.getClass().getMethod("physicsSystem").invoke(container);
                if (physicsSystem == null) continue;

                for (Object sub : (Iterable<?>) container.getClass().getMethod("getAllSubLevels").invoke(container)) {
                    if (sub.getClass().getName().equals("dev.ryanhcode.sable.sublevel.ServerSubLevel")) {
                        RECOVER_METHOD.invoke(physicsSystem, sub);
                        // Clear any stale COM shift cache for this sublevel
                        Object uuid = sub.getClass().getMethod("getUniqueId").invoke(sub);
                        dev.simulated_team.aero_reformation.feature.com_controller.ComShiftHelper.clearOffsetCache((java.util.UUID) uuid);
                        total++;
                    }
                }
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error recovering sublevels: " + e.getMessage()));
            return 0;
        }

        int finalTotal = total;
        source.sendSuccess(() -> Component.literal("Recovered " + finalTotal + " sublevel(s)."), true);
        return 1;
    }
}
