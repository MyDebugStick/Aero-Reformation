package dev.simulated_team.aero_reformation.network;

import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.content.hud.HudPresetStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> client: the preset bound to the worn helmet (may be null when the
 * helmet has no marker or no stored preset). The client applies it to the HUD.
 */
public record HudPresetResponsePacket(CompoundTag preset) implements CustomPacketPayload {

    public static final Type<HudPresetResponsePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "hud_preset_response"));

    public static final StreamCodec<FriendlyByteBuf, HudPresetResponsePacket> STREAM_CODEC =
            StreamCodec.of((buf, p) -> buf.writeNbt(p.preset),
                    buf -> new HudPresetResponsePacket(buf.readNbt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    @OnlyIn(Dist.CLIENT)
    public static void handle(HudPresetResponsePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> HudPresetStore.onLoadResponse(packet.preset()));
    }
}
