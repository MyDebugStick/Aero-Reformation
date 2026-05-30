package dev.simulated_team.aero_reformation.content.blocks.power;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(Dist.CLIENT)
public class SeatCameraHandler {

    private static boolean needsSnap = false;
    private static boolean middleWasDown = false;
    private static boolean altWasDown = false;

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getVehicle() == null) {
            needsSnap = true;
            middleWasDown = false;
            return;
        }
        Entity vehicle = mc.player.getVehicle();
        if (!(vehicle instanceof SeatEntity seat)) {
            needsSnap = true;
            middleWasDown = false;
            return;
        }

        boolean middleDown = GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        if (middleDown && !middleWasDown) {
            needsSnap = true;
        }
        middleWasDown = middleDown;

        // Alt to toggle redstone + clamp
        boolean altDown = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
        if (altDown && !altWasDown) {
            var conn = mc.player.connection;
            if (conn != null) {
                conn.send(new ToggleRedstonePayload(seat.getId()));
            }
            boolean wasDisabled = seat.isRedstoneDisabled();
            if (wasDisabled) {
                // Enabling: snap view to center
                needsSnap = true;
                mc.player.displayClientMessage(
                        Component.translatable("msg.aero_reformation.power.redstone_on"), true);
            } else {
                mc.player.displayClientMessage(
                        Component.translatable("msg.aero_reformation.power.redstone_off"), true);
            }
        }
        altWasDown = altDown;

        // Use baseYaw (sub-level local frame) - Sable applies Q during rendering
        float seatYRot = seat.getBaseYaw();

        if (needsSnap) {
            needsSnap = false;
            mc.player.setYRot(seatYRot);
            mc.player.yRotO = seatYRot;
            mc.player.yHeadRot = seatYRot;
            mc.player.yHeadRotO = seatYRot;
            mc.player.setXRot(0);
            mc.player.xRotO = 0;
            event.setYaw(seatYRot);
            event.setPitch(0);
            return;
        }

        // Skip clamping when redstone is disabled (free look)
        if (seat.isRedstoneDisabled()) return;

        float diff = Mth.wrapDegrees(mc.player.getYRot() - seatYRot);
        float clampedDiff = Mth.clamp(diff, -90, 90);
        float clampedYaw = seatYRot + clampedDiff;

        mc.player.setYRot(clampedYaw);
        mc.player.yRotO = clampedYaw;
        mc.player.yHeadRot = clampedYaw;
        mc.player.yHeadRotO = clampedYaw;
        event.setYaw(clampedYaw);
        event.setPitch(Mth.clamp(event.getPitch(), -45, 45));
    }
}
