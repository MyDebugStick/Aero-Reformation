package dev.simulated_team.aero_reformation.compat.ftb;

import dev.ftb.mods.ftbultimine.api.blockselection.BlockSelectionHandler;
import dev.simulated_team.aero_reformation.compat.CopycatCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Matches copycat blocks by their material (mimicked block), not by block type.
 * Registered via FTB Ultimine's BlockSelectionHandler API.
 */
public class CopycatSelectionHandler implements BlockSelectionHandler {

    @Override
    public Result customSelectionCheck(Player player, BlockPos originalPos, BlockPos currentPos,
                                        BlockState originalState, BlockState currentState) {
        if (!dev.simulated_team.aero_reformation.config.AeroReformationConfig.copycatUltimineEnabled)
            return Result.PASS;
        BlockState matOriginal = CopycatCompat.getMaterial(player.level(), originalPos);
        BlockState matCurrent = CopycatCompat.getMaterial(player.level(), currentPos);

        // Not a copycat block — let FTB Ultimine handle with default matcher
        if (matOriginal == null) return Result.PASS;
        if (matCurrent == null) return Result.FALSE;

        // Must be same copycat shape (block type) AND same material
        if (originalState.getBlock() != currentState.getBlock()) return Result.FALSE;
        return matOriginal.getBlock() == matCurrent.getBlock() ? Result.TRUE : Result.FALSE;
    }
}
