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
    public static boolean skipRollFrame = false;
    private static Quaterniond prevSubRot = null;
    /** Player's intended yaw/pitch (pre-compensation), for delta camera lock HUD/redstone */
    public static float intendedYaw, intendedPitch;
    /** Q-based worldRef yaw/pitch for roll lock HUD/snap/signal */
    public static float rollYawRef, rollPitchRef;

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getVehicle() == null) {
            needsSnap = true;
            middleWasDown = false;
            altWasDown = false;
            return;
        }
        Entity vehicle = mc.player.getVehicle();
        if (!(vehicle instanceof SeatEntity seat)) {
            needsSnap = true;
            middleWasDown = false;
            altWasDown = false;
            return;
        }

        boolean middleDown = GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        if (middleDown && !middleWasDown) {
            needsSnap = true;
        }
        middleWasDown = middleDown;

        // Alt cycles: Off → ViewSync → CameraLock → RollLock → Off
        boolean altDown = mc.screen == null && PowerKeyBindings.VIEW_SYNC.isDown();
        if (altDown && !altWasDown) {
            var conn = mc.player.connection;
            boolean wasDisabled = seat.isRedstoneDisabled();
            boolean wasLocked = seat.isCameraLocked();

            if (wasDisabled) {
                if (conn != null) conn.send(new ToggleRedstonePayload(seat.getId()));
                needsSnap = true;
                mc.player.displayClientMessage(Component.translatable("msg.aero_reformation.power.mode_view_sync"), true);
            } else if (!wasLocked) {
                if (conn != null) conn.send(new ToggleCameraLockPayload(seat.getId()));
                seat.pendingCameraLock = 1;
                needsSnap = true;
                mc.player.displayClientMessage(Component.translatable("msg.aero_reformation.power.mode_camera_lock"), true);
            } else if (!seat.isRollLocked()) {
                if (conn != null) conn.send(new ToggleRollLockPayload(seat.getId()));
                seat.toggleRollLocked();
                needsSnap = true;
                mc.player.displayClientMessage(Component.translatable("msg.aero_reformation.power.mode_roll_lock"), true);
            } else {
                if (conn != null) {
                    conn.send(new ToggleRollLockPayload(seat.getId()));
                    conn.send(new ToggleCameraLockPayload(seat.getId()));
                    conn.send(new ToggleRedstonePayload(seat.getId()));
                }
                seat.toggleRollLocked();
                seat.pendingCameraLock = 0;
                needsSnap = true;
                mc.player.displayClientMessage(Component.translatable("msg.aero_reformation.power.mode_off"), true);
            }
        }
        altWasDown = altDown;

        boolean locked = seat.pendingCameraLock >= 0 ? (seat.pendingCameraLock == 1) : seat.isCameraLocked();
        boolean rollLocked = locked && seat.isRollLocked();
        float seatYRot = seat.getBaseYaw();

        // Compute Q-based worldRef for roll lock mode (matches backup extractRollOnly reference)
        if (rollLocked) {
            var subLevel = Sable.HELPER.getContaining((Entity) seat);
            Quaterniondc liveQ = subLevel instanceof ClientSubLevel cs ? cs.renderPose().orientation()
                    : subLevel != null ? subLevel.logicalPose().orientation() : null;
            if (liveQ != null && (Math.abs(liveQ.w() - 1.0) > 1e-4 || Math.abs(liveQ.x()) > 1e-4
                    || Math.abs(liveQ.y()) > 1e-4 || Math.abs(liveQ.z()) > 1e-4)) {
                Quaterniond Q = new Quaterniond(liveQ);
                Vector3d worldRef = new Vector3d(0, 0, 1)
                        .rotateY(Math.toRadians(-seatYRot))
                        .rotate(Q, new Vector3d());
                rollYawRef = (float) Math.toDegrees(Math.atan2(-worldRef.x, worldRef.z));
                rollPitchRef = (float) Math.toDegrees(-Math.asin(worldRef.y));
            } else {
                rollYawRef = seatYRot;
                rollPitchRef = 0;
            }
            // No delta compensation for roll lock
        } else {
            rollYawRef = seatYRot;
            rollPitchRef = 0;
        }

        if (locked && !rollLocked) {
            // CameraLock mode: delta compensation (unchanged)
            var subLevel = Sable.HELPER.getContaining((Entity) seat);
            Quaterniondc liveQ = subLevel instanceof ClientSubLevel cs ? cs.renderPose().orientation()
                    : subLevel != null ? subLevel.logicalPose().orientation() : null;
            if (liveQ != null && (Math.abs(liveQ.w() - 1.0) > 1e-4 || Math.abs(liveQ.x()) > 1e-4
                    || Math.abs(liveQ.y()) > 1e-4 || Math.abs(liveQ.z()) > 1e-4)) {
                Quaterniond Q = new Quaterniond(liveQ);

                if (prevSubRot != null && !needsSnap) {
                    Quaterniond deltaWorld = new Quaterniond(Q).mul(new Quaterniond(prevSubRot).invert());
                    Quaterniond baseYawQ = new Quaterniond().rotationY(Math.toRadians(-seatYRot));
                    Quaterniond seatQ = new Quaterniond(Q).mul(baseYawQ);
                    Quaterniond deltaLocal = new Quaterniond(seatQ).invert().mul(deltaWorld).mul(seatQ);
                    Vector3d euler = deltaLocal.getEulerAnglesYXZ(new Vector3d());
                    float deltaYaw = (float) Math.toDegrees(euler.y);
                    float deltaPitch = (float) Math.toDegrees(euler.x);
                    intendedYaw = mc.player.getYRot();
                    intendedPitch = mc.player.getXRot();
                    mc.player.setYRot(Mth.wrapDegrees(mc.player.getYRot() + deltaYaw));
                    mc.player.setXRot(mc.player.getXRot() - deltaPitch);
                    event.setYaw(mc.player.getYRot());
                    event.setPitch(mc.player.getXRot());
                } else {
                    intendedYaw = mc.player.getYRot();
                    intendedPitch = mc.player.getXRot();
                }
                prevSubRot = Q;
            } else {
                prevSubRot = null;
                intendedYaw = mc.player.getYRot();
                intendedPitch = mc.player.getXRot();
            }

            // Signal for CameraLock mode
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
        } else if (rollLocked) {
            // RollLock: server computes signal (no client sync needed, prevents bounce)
            prevSubRot = null;
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
            float snapYaw = rollLocked ? rollYawRef : seatYRot;
            float snapPitch = rollLocked ? rollPitchRef : 0;
            mc.player.setYRot(snapYaw);
            mc.player.yRotO = snapYaw;
            mc.player.yHeadRot = snapYaw;
            mc.player.yHeadRotO = snapYaw;
            mc.player.setXRot(snapPitch);
            mc.player.xRotO = snapPitch;
            event.setYaw(snapYaw);
            event.setPitch(snapPitch);
            return;
        }

        if (seat.isRedstoneDisabled()) return;

        if (seat.pendingCameraLock >= 0 && (seat.pendingCameraLock == 1) == seat.isCameraLocked()) {
            seat.pendingCameraLock = -1;
        }
    }
}