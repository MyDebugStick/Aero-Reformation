package dev.simulated_team.aero_reformation.content.blocks.gravity_crystal;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Syncs gravity crystal settings from client GUI to server. */
public record GravityCrystalSettingsPacket(UUID subLevelId, float liftMul, float dragMul, float angularDragMul)
        implements CustomPacketPayload {

    public static final Type<GravityCrystalSettingsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "gravity_crystal_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GravityCrystalSettingsPacket> STREAM_CODEC =
            StreamCodec.of(GravityCrystalSettingsPacket::encode, GravityCrystalSettingsPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, GravityCrystalSettingsPacket p) {
        buf.writeUUID(p.subLevelId);
        buf.writeFloat(p.liftMul);
        buf.writeFloat(p.dragMul);
        buf.writeFloat(p.angularDragMul);
    }

    private static GravityCrystalSettingsPacket decode(RegistryFriendlyByteBuf buf) {
        return new GravityCrystalSettingsPacket(buf.readUUID(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            GravityCrystalSettings s = GravityCrystalSettings.get(subLevelId);
            s.liftMultiplier = Math.clamp(liftMul, 0f, 2f);
            s.dragMultiplier = Math.clamp(dragMul, 0f, 2f);
            s.angularDragMultiplier = Math.clamp(angularDragMul, 0f, 2f);
        });
    }
}
