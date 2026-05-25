package dev.simulated_team.aero_reformation.mixin.feature.sensor_agency;

import dev.simulated_team.aero_reformation.content.blocks.sensor_agency.SensorProxyData;
import dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GimbalSensorBlock.class, remap = false)
public class GimbalSensorProxyMixin {

    @Inject(method = "getSignal", at = @At("HEAD"), cancellable = true)
    private void aero$proxyGimbalSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir,
                                        CallbackInfoReturnable<Integer> cir) {
        int proxy = SensorProxyData.getGimbal(pos, dir);
        if (proxy >= 0) {
            cir.setReturnValue(proxy);
        }
    }
}
