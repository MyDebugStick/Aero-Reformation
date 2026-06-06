package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AnchorRenamePacket(BlockPos pos, String name, int radius) implements CustomPacketPayload {

    public static final Type<AnchorRenamePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "anchor_rename"));

    public static final StreamCodec<FriendlyByteBuf, AnchorRenamePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeBlockPos(p.pos); buf.writeUtf(p.name); buf.writeVarInt(p.radius); },
                    buf -> new AnchorRenamePacket(buf.readBlockPos(), buf.readUtf(), buf.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AnchorRenamePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            AnchorChunkLoader.renameAnchor(sp.level(), packet.pos, packet.name, packet.radius);
        });
    }
}
