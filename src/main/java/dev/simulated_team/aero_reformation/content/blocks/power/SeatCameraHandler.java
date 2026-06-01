package dev.simulated_team.aero_reformation.content.blocks.power;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(Dist.CLIENT)
public class SeatCameraHandler {

    private static boolean needsSnap = false;
    private static boolean middleWasDown = false;
    private static boolean altWasDown = false;
    private static boolean ctrlWasDown = false;
    private static boolean cWasDown = false;
    public static boolean skipRollFrame = false;
    private static Quaterniond prevSubRot = null;
    /** Player's intended yaw/pitch (pre-compensation), for HUD/redstone */
    public static float intendedYaw, intendedPitch;

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
            altDown = PowerKeyBindings.VIEW_SYNC.isDown();
            ctrlDown = PowerKeyBindings.CAMERA_LOCK.isDown();
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

        // Roll lock toggle
        boolean cDown = mc.screen == null && PowerKeyBindings.ROLL_LOCK.isDown();
        if (cDown && !cWasDown) {
            var conn = mc.player.connection;
            if (conn != null) {
                conn.send(new ToggleRollLockPayload(seat.getId()));
            }
            seat.toggleRollLocked();
            mc.player.displayClientMessage(Component.translatable(
                    seat.isRollLocked() ? "msg.aero_reformation.power.axis3_on" : "msg.aero_reformation.power.axis3_off"), true);
        }
        cWasDown = cDown;

        boolean locked = seat.pendingCameraLock >= 0 ? (seat.pendingCameraLock == 1) : seat.isCameraLocked();

        float seatYRot = seat.getBaseYaw();

        if (locked) {
            var subLevel = Sable.HELPER.getContaining((Entity) seat);
            Quaterniondc liveQ = subLevel instanceof ClientSubLevel cs ? cs.renderPose().orientation()
                    : subLevel != null ? subLevel.logicalPose().orientation() : null;
            if (liveQ != null && (Math.abs(liveQ.w() - 1.0) > 1e-4 || Math.abs(liveQ.x()) > 1e-4
                    || Math.abs(liveQ.y()) > 1e-4 || Math.abs(liveQ.z()) > 1e-4)) {
                Quaterniond Q = new Quaterniond(liveQ);

                if (prevSubRot != null && !needsSnap) {
                    Quaterniond deltaWorld = new Quaterniond(Q).mul(new Quaterniond(prevSubRot).invert());
                    Quaterniond deltaLocal = new Quaterniond(Q).invert().mul(deltaWorld).mul(Q);
                    Vector3d euler = deltaLocal.getEulerAnglesYXZ(new Vector3d());
                    float deltaYaw = (float) Math.toDegrees(euler.y);
                    float deltaPitch = (float) Math.toDegrees(euler.x);
                    // Save intended direction BEFORE counter-rotating
                    intendedYaw = mc.player.getYRot();
                    intendedPitch = mc.player.getXRot();
                    mc.player.setYRot(Mth.wrapDegrees(mc.player.getYRot() + deltaYaw));
                    mc.player.setXRot(mc.player.getXRot() + deltaPitch);
                    event.setYaw(mc.player.getYRot());
                    event.setPitch(mc.player.getXRot());
                } else {
                    intendedYaw = mc.player.getYRot();
                    intendedPitch = mc.player.getXRot();
                }
                // Compute & sync redstone signals from intended direction
                float yawDev = Mth.wrapDegrees(intendedYaw - seatYRot);
                float pitchDev = intendedPitch;
                int yawMax = 90, pitchMax = 45;
                if (mc.level != null && mc.level.getBlockEntity(seat.blockPosition()) instanceof PowerBlockEntity be) {
                    yawMax = be.getYawMax(); pitchMax = be.getPitchMax();
                }
                int sigR = yawDev < 0 ? (int) Mth.clamp(Math.abs(yawDev) / yawMax * 15f, 0, 15) : 0;
                int sigL = yawDev > 0 ? (int) Mth.clamp(Math.abs(yawDev) / yawMax * 15f, 0, 15) : 0;
                int sigB = pitchDev > 0 ? (int) Mth.clamp(Math.abs(pitchDev) / pitchMax * 15f, 0, 15) : 0;
                int sigF = pitchDev < 0 ? (int) Mth.clamp(Math.abs(pitchDev) / pitchMax * 15f, 0, 15) : 0;
                var conn = mc.player.connection;
                if (conn != null) conn.send(new SyncSignalPayload(seat.getId(), sigR, sigL, sigB, sigF));

                prevSubRot = Q;
            } else {
                prevSubRot = null;
            }
        } else {
            prevSubRot = null;
            intendedYaw = mc.player.getYRot();
            intendedPitch = mc.player.getXRot();
        }

        if (needsSnap) {
            needsSnap = false;
            if (locked) {
                skipRollFrame = true;
            }
            mc.player.setYRot(seatYRot);
            mc.player.yRotO = seatYRot;
            mc.player.yHeadRot = seatYRot;
            mc.player.yHeadRotO = seatYRot;
            float snapPitch = 0;
            mc.player.setXRot(snapPitch);
            mc.player.xRotO = snapPitch;
            // Event already compensated above; don't overwrite
            return;
        }

        if (seat.isRedstoneDisabled()) return;

        if (seat.pendingCameraLock >= 0 && (seat.pendingCameraLock == 1) == seat.isCameraLocked()) {
            seat.pendingCameraLock = -1;
        }

        // Clamp disabled (conflicts with full Q)
    }
}