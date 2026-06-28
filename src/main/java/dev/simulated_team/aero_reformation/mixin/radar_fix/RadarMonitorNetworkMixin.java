package dev.simulated_team.aero_reformation.mixin.radar_fix;

import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.RadarTrackUtil;
import dev.simulated_team.aero_reformation.config.AeroReformationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles and caps Create Radar monitor track sync to reduce network bandwidth.
 * - Reduces sync frequency from 5 ticks to radarSyncInterval
 * - Caps synced tracks to radarMaxSyncedTracks sorted by priority+ID
 */
@Mixin(value = MonitorBlockEntity.class, remap = false)
public abstract class RadarMonitorNetworkMixin {

    private static final Field LEVEL_FIELD;
    private static final Field POS_FIELD;
    static {
        try {
            LEVEL_FIELD = BlockEntity.class.getDeclaredField("level");
            LEVEL_FIELD.setAccessible(true);
            POS_FIELD = BlockEntity.class.getDeclaredField("worldPosition");
            POS_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) { throw new RuntimeException("RadarFix: cannot access BlockEntity fields", e); }
    }

    private static final Map<BlockPos, Long> LAST_TICK = new ConcurrentHashMap<>();

    private static final Comparator<RadarTrack> PRIORITY_COMPARATOR = Comparator
            .comparingInt((RadarTrack t) -> switch (t.trackCategory()) {
                case PLAYER -> 0; case PROJECTILE -> 1; case SABLE -> 2; default -> 3;
            })
            .thenComparing(RadarTrack::id);

    // ─── Throttle ───────────────────────────────────────────────────────

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/happysg/radar/block/monitor/MonitorBlockEntity;sendData()V"),
            cancellable = true)
    private void throttleSendData(CallbackInfo ci) {
        if (!AeroReformationConfig.radarEnabled) return;
        try {
            Level lvl = (Level) LEVEL_FIELD.get(this);
            if (lvl == null || lvl.isClientSide) return;
            BlockPos pos = (BlockPos) POS_FIELD.get(this);
            long gt = lvl.getGameTime();
            Long last = LAST_TICK.get(pos);
            if (last != null && gt - last < AeroReformationConfig.radarSyncInterval) {
                ci.cancel();
                return;
            }
            LAST_TICK.put(pos, gt);
        } catch (Exception ignored) {}
    }

    // ─── Cap ────────────────────────────────────────────────────────────

    @Redirect(method = "write(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/happysg/radar/block/radar/track/RadarTrackUtil;serializeNBTList(Ljava/util/Collection;)Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag capSerializeNBTList(Collection<RadarTrack> tracks) {
        if (!AeroReformationConfig.radarEnabled || tracks.size() <= AeroReformationConfig.radarMaxSyncedTracks)
            return RadarTrackUtil.serializeNBTList(tracks);
        var sorted = new ArrayList<>(tracks);
        sorted.sort(PRIORITY_COMPARATOR);
        return RadarTrackUtil.serializeNBTList(sorted.subList(0, AeroReformationConfig.radarMaxSyncedTracks));
    }
}
