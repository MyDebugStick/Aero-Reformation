package dev.simulated_team.aero_reformation.network;

import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.content.blocks.electric_loadstone.ElectricLoadstoneBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Syncs the held item from server to client for ElectricLoadstone.
 */
public record LoadstoneSyncPacket(BlockPos pos, CompoundTag itemTag) implements CustomPacketPayload {

    public static final Type<LoadstoneSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "loadstone_sync"));

    public static final StreamCodec<FriendlyByteBuf, LoadstoneSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeBlockPos(packet.pos);
                        buf.writeNbt(packet.itemTag);
                    },
                    buf -> new LoadstoneSyncPacket(
                            buf.readBlockPos(),
                            buf.readNbt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(LoadstoneSyncPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var level = ctx.player().level();
            if (level.getBlockEntity(packet.pos) instanceof ElectricLoadstoneBlockEntity be) {
                ItemStack stack = packet.itemTag != null
                        ? ItemStack.parse(level.registryAccess(), packet.itemTag).orElse(ItemStack.EMPTY)
                        : ItemStack.EMPTY;
                be.clientSync(stack);
            }
        });
    }
}
