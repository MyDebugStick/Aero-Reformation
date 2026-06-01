package dev.simulated_team.aero_reformation.content.blocks.power;

import dev.simulated_team.aero_reformation.AeroReformation;
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
    public static boolean skipRollFrame = false;

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
                needsSnap = true;
                mc.player.displayClientMessage(
                        Component.translatable("msg.aero_reformation.power.redstone_on"), true);
            } else {
                mc.player.displayClientMessage(
                        Component.translatable("msg.aero_reformation.power.redstone_off"), true);
            }
        }
        altWasDown = altDown;

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

        boolean locked = seat.pendingCameraLock >= 0 ? (seat.pendingCameraLock == 1) : seat.isCameraLocked();

        float seatYRot;
        if (locked) {
            float qx = seat.getSubRotX(), qy = seat.getSubRotY(), qz = seat.getSubRotZ(), qw = seat.getSubRotW();
            Quaterniond Q = new Quaterniond(qx, qy, qz, qw);
            Vector3d localFwd = new Vector3d(0, 0, 1).rotateY(Math.toRadians(-seat.getBaseYaw()));
            Vector3d worldFwd = localFwd.rotate(Q, new Vector3d());
            seatYRot = (float) Math.toDegrees(Math.atan2(-worldFwd.x, worldFwd.z));
        } else {
            seatYRot = seat.getBaseYaw();
        }

        if (needsSnap) {
            needsSnap = false;
            if (locked) {
                skipRollFrame = true;
                seatYRot = seat.getBaseYaw();
            }
            mc.player.setYRot(seatYRot);
            mc.player.yRotO = seatYRot;
            mc.player.yHeadRot = seatYRot;
            mc.player.yHeadRotO = seatYRot;
            float snapPitch = 0;
            mc.player.setXRot(snapPitch);
            mc.player.xRotO = snapPitch;
            event.setYaw(seatYRot);
            event.setPitch(snapPitch);
            return;
        }

        if (seat.isRedstoneDisabled()) return;

        if (seat.pendingCameraLock >= 0 && (seat.pendingCameraLock == 1) == seat.isCameraLocked()) {
            seat.pendingCameraLock = -1;
        }

        // Clamp disabled (conflicts with full Q)
    }
}