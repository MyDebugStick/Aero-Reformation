package dev.simulated_team.aero_reformation.content.hud;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Middle-click block picking for the "bind to block" flow: the edit screen
 * closes, the player middle-clicks a block, its block state + NBT are captured,
 * then the edit screen reopens showing "[text]: [state/NBT]".
 */
@EventBusSubscriber(modid = AeroReformation.MODID, value = Dist.CLIENT)
public class HudPickHandler {

    private static HudEntry pendingEntry;
    private static HudPlaceholder pendingPlaceholder;
    private static boolean awaiting;
    private static boolean worldAnchorMode; // pick a world anchor instead of binding a block
    private static boolean placeholderMode; // pick a block + NBT key for a custom placeholder

    public static void beginPick(HudEntry entry) {
        pendingEntry = entry;
        awaiting = true;
        worldAnchorMode = false;
        placeholderMode = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("hud.aero_reformation.bind_hint"), true);
        }
    }

    /**
     * Enter world-anchor picking: middle-click a block to place the entry there.
     * If the block sits on a sable physics body, the entry is attached to that
     * body (offset from its position) and follows it automatically.
     */
    public static void beginWorldAnchorPick(HudEntry entry) {
        pendingEntry = entry;
        awaiting = true;
        worldAnchorMode = true;
        placeholderMode = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("hud.aero_reformation.pick_world_hint"), true);
        }
    }

    /** Enter custom-placeholder picking: middle-click a block, then pick an NBT key. */
    public static void beginPlaceholderPick() {
        beginPlaceholderPick(null);
    }

    /**
     * Enter custom-placeholder picking for an existing placeholder (rebind). The
     * object is reused so its name/math survive the middle-click flow.
     */
    public static void beginPlaceholderPick(HudPlaceholder existing) {
        pendingPlaceholder = existing != null ? existing : new HudPlaceholder();
        awaiting = true;
        worldAnchorMode = false;
        placeholderMode = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("hud.aero_reformation.ph_pick_hint"), true);
        }
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Post event) {
        if (!awaiting || (pendingEntry == null && pendingPlaceholder == null)) return;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE || event.getAction() != GLFW.GLFW_PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        HitResult hit = mc.hitResult;
        if (worldAnchorMode) {
            if (!(hit instanceof BlockHitResult bhr)) {
                mc.player.displayClientMessage(Component.translatable("hud.aero_reformation.bind_miss"), true);
                return;
            }
            setWorldAnchor(mc, bhr.getBlockPos());
            HudBoard.saveToPlayer(mc.player);
            awaiting = false;
            HudEntry entry = pendingEntry;
            pendingEntry = null;
            mc.setScreen(new HudEntryEditScreen(entry));
        } else if (placeholderMode) {
            HudPlaceholder ph = pendingPlaceholder;
            if (hit instanceof net.minecraft.world.phys.EntityHitResult ehr) {
                ph.bindType = "entity";
                ph.entityUuid = ehr.getEntity().getUUID();
                ph.pos = ehr.getEntity().blockPosition();
                ph.bindSource = "";
                HudNbtCache.requestPlaceholderNbt(ph, ph.pos, ph.entityUuid);
            } else if (hit instanceof BlockHitResult bhr) {
                ph.bindType = "block";
                ph.entityUuid = null;
                ph.pos = bhr.getBlockPos();
                String sensor = HudBindings.detectSensorType(mc.level, ph.pos);
                if (!sensor.isEmpty()) {
                    // Sensor blocks provide live data (not NBT): bind directly to the sensor
                    ph.bindSource = "sensor";
                    ph.sensorType = sensor;
                } else {
                    ph.bindSource = "";
                    HudNbtCache.requestPlaceholderNbt(ph, ph.pos, null);
                }
            } else {
                mc.player.displayClientMessage(Component.translatable("hud.aero_reformation.bind_miss"), true);
                return;
            }
            // Never auto-open the NBT browser; the edit screen has a "Pick NBT" button
            awaiting = false;
            HudPlaceholder ph2 = pendingPlaceholder;
            pendingPlaceholder = null;
            mc.setScreen(new HudPlaceholderEditScreen(ph2));
        } else {
            if (hit instanceof net.minecraft.world.phys.EntityHitResult ehr) {
                // Entity bind: NBT path is picked later via the "NBT Browse" button
                pendingEntry.bindPos = ehr.getEntity().blockPosition();
                pendingEntry.bindEntityUuid = ehr.getEntity().getUUID();
                pendingEntry.bindSource = "nbt";
                pendingEntry.bindKey = "";
            } else if (hit instanceof BlockHitResult bhr) {
                BlockPos pos = bhr.getBlockPos();
                var options = HudBindings.detectOptions(mc.level, pos);
                HudBindings.BindOption def = HudBindings.defaultOption(options);
                pendingEntry.bindPos = pos;
                pendingEntry.bindEntityUuid = null;
                pendingEntry.bindSource = def != null ? def.source() : "";
                pendingEntry.bindKey = def != null ? def.key() : "";
            } else {
                mc.player.displayClientMessage(Component.translatable("hud.aero_reformation.bind_miss"), true);
                return;
            }
            HudBoard.saveToPlayer(mc.player);
            awaiting = false;
            HudEntry entry = pendingEntry;
            pendingEntry = null;
            mc.setScreen(new HudEntryEditScreen(entry));
        }
    }

    /**
     * Anchor the pending entry to the picked block. If the block lies inside a
     * physics body's bounds, attach to that body using a local offset so the
     * entry moves with it; otherwise use the fixed world coordinates.
     */
    private static void setWorldAnchor(Minecraft mc, BlockPos pos) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        var sub = HudRenderers.findPhysBodyAt(mc, x, y, z);
        // The picked block's absolute coordinates are already body-local when it
        // sits on a body; the renderer projects them with Sable.HELPER.
        pendingEntry.worldX = x;
        pendingEntry.worldY = y;
        pendingEntry.worldZ = z;
        if (sub != null) {
            pendingEntry.physBodyId = sub.getUniqueId().toString();
            mc.player.displayClientMessage(
                    Component.translatable("hud.aero_reformation.phys_attached", sub.getName()), true);
        } else {
            pendingEntry.physBodyId = "";
            mc.player.displayClientMessage(Component.translatable("hud.aero_reformation.world_set"), true);
        }
    }
}
