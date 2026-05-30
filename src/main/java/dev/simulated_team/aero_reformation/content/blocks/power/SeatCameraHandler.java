package dev.simulated_team.aero_reformation.content.blocks.power;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(Dist.CLIENT)
public class SeatCameraHandler {

    private static boolean needsSnap = false;
    private static boolean middleWasDown = false;
    private static boolean altWasDown = false;
    private static boolean ctrlWasDown = false;

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getVehicle() == null) {
            needsSnap = true;
            middleWasDown = false;
            altWasDown = false;
            ctrlWasDown = false;
            return;
        }
        Entity vehicle = mc.player.getVehicle();
        if (!(vehicle instanceof SeatEntity seat)) {
            needsSnap = true;
            middleWasDown = false;
            altWasDown = false;
            ctrlWasDown = false;
            return;
        }

        boolean middleDown = GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        if (middleDown && !middleWasDown) {
            needsSnap = true;
        }
        middleWasDown = middleDown;

        // Alt to toggle redstone + clamp (only when no GUI is open)
        boolean altDown = false, ctrlDown = false;
        if (mc.screen == null) {
            altDown = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
            ctrlDown = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        }
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

        // Ctrl to toggle camera rotation lock
        if (ctrlDown && !ctrlWasDown) {
            var conn = mc.player.connection;
            if (conn != null) {
                conn.send(new ToggleCameraLockPayload(seat.getId()));
            }
            boolean willLock = !seat.isCameraLocked();
            seat.pendingCameraLock = willLock ? 1 : 0;
            needsSnap = true;
            mc.player.displayClientMessage(
                    Component.translatable(willLock ? "msg.aero_reformation.power.camera_lock_on" : "msg.aero_reformation.power.camera_lock_off"), true);
        }
        ctrlWasDown = ctrlDown;

        // Use pending state during toggle transition, otherwise current state
        boolean locked = seat.pendingCameraLock >= 0 ? (seat.pendingCameraLock == 1) : seat.isCameraLocked();

        float seatYRot;
        if (locked) {
            float qx = seat.getSubRotX(), qy = seat.getSubRotY(), qz = seat.getSubRotZ(), qw = seat.getSubRotW();
            boolean hasRot = Math.abs(qw - 1.0) > 0.0001 || Math.abs(qx) > 0.0001
                    || Math.abs(qy) > 0.0001 || Math.abs(qz) > 0.0001;
            if (hasRot) {
                Quaterniond Q = new Quaterniond(qx, qy, qz, qw);
                Vector3d localFwd = new Vector3d(0, 0, 1)
                        .rotateY(Math.toRadians(-seat.getBaseYaw()));
                Vector3d worldFwd = localFwd.rotate(Q, new Vector3d());
                seatYRot = (float) Math.toDegrees(Math.atan2(-worldFwd.x, worldFwd.z));
            } else {
                seatYRot = seat.getBaseYaw();
            }
        } else {
            seatYRot = seat.getBaseYaw();
        }

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

        // Clear pending camera lock once synced
        if (seat.pendingCameraLock >= 0 && (seat.pendingCameraLock == 1) == seat.isCameraLocked()) {
            seat.pendingCameraLock = -1;
        }

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
