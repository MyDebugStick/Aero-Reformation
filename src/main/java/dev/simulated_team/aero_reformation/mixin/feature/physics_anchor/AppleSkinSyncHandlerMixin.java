package dev.simulated_team.aero_reformation.mixin.feature.physics_anchor;

import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import squeek.appleskin.network.SyncHandler;

@Mixin(value = SyncHandler.class, remap = false)
public class AppleSkinSyncHandlerMixin {

    @Inject(method = "onLivingTickEvent", at = @At("HEAD"), cancellable = true)
    public void skipFakePlayer(EntityTickEvent.Pre event, CallbackInfo ci) {
        if (event.getEntity() instanceof FakePlayer) {
            ci.cancel();
        }
    }
}