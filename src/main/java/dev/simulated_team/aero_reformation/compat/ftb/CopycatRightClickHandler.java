package dev.simulated_team.aero_reformation.compat.ftb;

import dev.ftb.mods.ftbultimine.api.rightclick.RightClickHandler;
import dev.ftb.mods.ftbultimine.api.shape.ShapeContext;
import dev.simulated_team.aero_reformation.compat.CopycatCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/**
 * Applies copycat right-click actions (set/remove material) to all matching blocks.
 * Registered via FTB Ultimine's RightClickHandler API.
 */
public class CopycatRightClickHandler implements RightClickHandler {

    @Override
    public int handleRightClickBlock(ShapeContext context, InteractionHand hand, Collection<BlockPos> positions) {
        if (!dev.simulated_team.aero_reformation.config.AeroReformationConfig.copycatUltimineEnabled)
            return -1;
        var player = context.player();
        if (!(player instanceof ServerPlayer sp)) return -1;

        Level level = sp.level();
        ItemStack stack = sp.getItemInHand(hand);

        int handled = 0;
        for (BlockPos pos : positions) {
            if (!CopycatCompat.isCopycatBlock(level, pos)) continue;

            BlockState state = level.getBlockState(pos);
            Vec3 center = Vec3.atCenterOf(pos);
            BlockHitResult hit = new BlockHitResult(center, context.face(), pos, false);

            if (stack.getItem() instanceof BlockItem && stack.getCount() > 0) {
                // BlockItem: chain set material
                if (state.useItemOn(stack, level, sp, hand, hit).consumesAction()) handled++;
            } else if (state.getBlock() instanceof com.simibubi.create.content.equipment.wrench.IWrenchable w) {
                // Wrench: chain operation (material removal for copycat blocks)
                var ctx = new net.minecraft.world.item.context.UseOnContext(level, sp, hand, stack, hit);
                w.onWrenched(state, ctx);
                handled++;
            }
        }
        return handled;
    }

    @Override
    public boolean hurtItemAndCheckIfBroken(ServerPlayer player, InteractionHand hand) {
        return false; // We handle item damage manually
    }
}
