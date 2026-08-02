package dev.simulated_team.aero_reformation.network;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> server: save the player's current HUD (entries + placeholders) as
 * a preset bound to the exact helmet the player is wearing. The server writes
 * a unique UUID marker into that helmet's NBT and stores the preset under it,
 * so each individual helmet holds its own preset (1-to-1).
 */
public record HudPresetSavePacket(CompoundTag preset) implements CustomPacketPayload {

    public static final Type<HudPresetSavePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeroReformation.MODID, "hud_preset_save"));

    public static final StreamCodec<FriendlyByteBuf, HudPresetSavePacket> STREAM_CODEC =
            StreamCodec.of((buf, p) -> buf.writeNbt(p.preset),
                    buf -> new HudPresetSavePacket(buf.readNbt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(HudPresetSavePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                ItemStack helmet = sp.getInventory().getArmor(3);
                if (helmet.isEmpty()) return;
                UUID id = getOrCreatePresetUuid(helmet);
                HudPresetSavedData.get(sp.serverLevel().getServer()).set(id.toString(), packet.preset());
                // Sync the (now marked) helmet back to the client
                sp.inventoryMenu.broadcastChanges();
            }
        });
    }

    /** The helmet's preset marker UUID, creating and persisting one when missing. */
    public static UUID getOrCreatePresetUuid(ItemStack helmet) {
        CompoundTag tag = helmet.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.hasUUID(PRESET_KEY)) {
            return tag.getUUID(PRESET_KEY);
        }
        UUID id = UUID.randomUUID();
        tag.putUUID(PRESET_KEY, id);
        helmet.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return id;
    }

    /** The helmet's preset marker UUID, or null when the helmet has none. */
    public static UUID readPresetUuid(ItemStack helmet) {
        CompoundTag tag = helmet.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.hasUUID(PRESET_KEY)) {
            return tag.getUUID(PRESET_KEY);
        }
        return null;
    }

    public static final String PRESET_KEY = "aero_reformation_preset";
}
