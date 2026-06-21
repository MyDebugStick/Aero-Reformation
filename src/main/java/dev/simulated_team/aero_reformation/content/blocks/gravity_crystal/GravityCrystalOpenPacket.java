package dev.simulated_team.aero_reformation.content.blocks.gravity_crystal;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Client→Server: requests current gravity crystal settings to open GUI. */
public record GravityCrystalOpenPacket(UUID subLevelId) implements CustomPacketPayload {

    public static final Type<GravityCrystalOpenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "gravity_crystal_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GravityCrystalOpenPacket> STREAM_CODEC =
            StreamCodec.of(GravityCrystalOpenPacket::encode, GravityCrystalOpenPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, GravityCrystalOpenPacket p) {
        buf.writeUUID(p.subLevelId);
    }

    private static GravityCrystalOpenPacket decode(RegistryFriendlyByteBuf buf) {
        return new GravityCrystalOpenPacket(buf.readUUID());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            GravityCrystalSettings s = GravityCrystalSettings.get(subLevelId);
            s.active = true;
            GravityCrystalSettings.CRYSTAL_SUBLEVELS.add(subLevelId);
            PacketDistributor.sendToPlayer((ServerPlayer) ctx.player(),
                    new GravityCrystalSyncPacket(subLevelId, s.liftMultiplier, s.dragMultiplier, s.angularDragMultiplier, s.crystalCount));
        });
    }
}
