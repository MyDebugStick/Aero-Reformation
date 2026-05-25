package dev.simulated_team.aero_reformation.mixin.feature.sensor_agency;

import dev.simulated_team.aero_reformation.content.blocks.sensor_agency.SensorProxyData;
import dev.simulated_team.simulated.content.blocks.velocity_sensor.VelocitySensorBlockEntity;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VelocitySensorBlockEntity.class, remap = false)
public class VelocitySensorBEMixin {

    @Inject(method = "getRedstoneStrength", at = @At("HEAD"), cancellable = true)
    private void aero$proxyVelocityBESignal(CallbackInfoReturnable<Integer> cir) {
        var self = (VelocitySensorBlockEntity) (Object) this;
        // Return the max signal from any direction for model feedback
        int best = 0;
        for (Direction dir : Direction.values()) {
            int proxy = SensorProxyData.getVelocity(self.getBlockPos(), dir);
            if (proxy > best) best = proxy;
        }
        if (best > 0) {
            cir.setReturnValue(best);
        }
    }
}
