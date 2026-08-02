package dev.simulated_team.aero_reformation.network;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> server: request the preset bound to the exact helmet the player is
 * wearing (by its per-helmet UUID marker). The server replies with a
 * HudPresetResponsePacket (preset may be null).
 */
public record HudPresetLoadPacket() implements CustomPacketPayload {

    public static final Type<HudPresetLoadPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "hud_preset_load"));

    public static final StreamCodec<FriendlyByteBuf, HudPresetLoadPacket> STREAM_CODEC =
            StreamCodec.unit(new HudPresetLoadPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HudPresetLoadPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                ItemStack helmet = sp.getInventory().getArmor(3);
                CompoundTag preset = null;
                if (!helmet.isEmpty()) {
                    UUID id = HudPresetSavePacket.readPresetUuid(helmet);
                    if (id != null) {
                        preset = HudPresetSavedData.get(sp.serverLevel().getServer()).get(id.toString());
                    }
                }
                PacketDistributor.sendToPlayer(sp, new HudPresetResponsePacket(preset));
            }
        });
    }
}
