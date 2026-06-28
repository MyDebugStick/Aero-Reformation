package dev.simulated_team.aero_reformation.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Safe access to copycat block materials from Create and Copycats+.
 */
public class CopycatCompat {

    public static final boolean COPYCATS_LOADED;

    static {
        boolean loaded = false;
        try {
            loaded = ModList.get().isLoaded("copycats");
        } catch (Exception ignored) {}
        COPYCATS_LOADED = loaded;
    }

    private static Class<?> ccICopycatBlock;
    private static Class<?> ccICopycatBE;
    private static Method ccGetMaterial;

    private static boolean resolvedCC = false;

    private static void resolveCC() {
        if (resolvedCC || !COPYCATS_LOADED) return;
        resolvedCC = true;
        try {
            ccICopycatBlock = Class.forName("com.copycatsplus.copycats.foundation.copycat.ICopycatBlock");
            ccICopycatBE = Class.forName("com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity");
            ccGetMaterial = ccICopycatBlock.getMethod("getMaterial", BlockGetter.class, BlockPos.class);
        } catch (Exception ignored) {}
    }

    /** Check if the block at pos is a copycat block (Create or Copycats+). */
    public static boolean isCopycatBlock(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof com.simibubi.create.content.decoration.copycat.CopycatBlock) return true;
        if (COPYCATS_LOADED) {
            resolveCC();
            try {
                BlockEntity be = level.getBlockEntity(pos);
                return be != null && ccICopycatBE.isInstance(be);
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * Get the material (mimicked block state) of a copycat block.
     * Supports both Create and Copycats+.
     * @return the material BlockState, or null if not a copycat block
     */
    public static BlockState getMaterial(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        // Try Create's CopycatBlock first (required dep)
        if (state.getBlock() instanceof com.simibubi.create.content.decoration.copycat.CopycatBlock) {
            return com.simibubi.create.content.decoration.copycat.CopycatBlock.getMaterial(level, pos);
        }

        // Try Copycats+ via reflection (optional)
        if (COPYCATS_LOADED) {
            resolveCC();
            try {
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null && ccICopycatBE.isInstance(be)) {
                    Object result = ccGetMaterial.invoke(null, level, pos);
                    if (result instanceof BlockState mat) return mat;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }
}
