package dev.simulated_team.aero_reformation.content.blocks.gravity_crystal;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Server→Client: syncs current gravity crystal settings and opens GUI. */
public record GravityCrystalSyncPacket(UUID subLevelId, float liftMul, float dragMul, float angularDragMul)
        implements CustomPacketPayload {

    public static final Type<GravityCrystalSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "gravity_crystal_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GravityCrystalSyncPacket> STREAM_CODEC =
            StreamCodec.of(GravityCrystalSyncPacket::encode, GravityCrystalSyncPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, GravityCrystalSyncPacket p) {
        buf.writeUUID(p.subLevelId);
        buf.writeFloat(p.liftMul);
        buf.writeFloat(p.dragMul);
        buf.writeFloat(p.angularDragMul);
    }

    private static GravityCrystalSyncPacket decode(RegistryFriendlyByteBuf buf) {
        return new GravityCrystalSyncPacket(buf.readUUID(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            GravityCrystalSettings s = GravityCrystalSettings.get(subLevelId);
            s.liftMultiplier = liftMul;
            s.dragMultiplier = dragMul;
            s.angularDragMultiplier = angularDragMul;
            GravityCrystalScreenOpener.open(subLevelId, s);
        });
    }
}
