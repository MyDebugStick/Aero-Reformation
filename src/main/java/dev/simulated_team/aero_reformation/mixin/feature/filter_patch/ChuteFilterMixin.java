package dev.simulated_team.aero_reformation.mixin.feature.filter_patch;

import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import dev.simulated_team.aero_reformation.content.blocks.filter_patch.FilterPatchHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts Create chute item output to check for adjacent filter patches
 * on the target container below the chute.
 */
@Mixin(ChuteBlockEntity.class)
public class ChuteFilterMixin {

    @Shadow
    private ItemStack item;

    @Inject(method = "handleDownwardOutput", at = @At("HEAD"), cancellable = true, remap = false)
    private void aero$filterChuteDownwardOutput(boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        if (!dev.simulated_team.aero_reformation.config.AeroReformationConfig.filterPatchEnabled) return;
        ChuteBlockEntity self = (ChuteBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide) return;
        if (item.isEmpty()) return;

        BlockPos targetPos = self.getBlockPos().below();
        if (!FilterPatchHandler.canInsert(level, targetPos, item)) {
            cir.setReturnValue(false);
        }
    }
}
