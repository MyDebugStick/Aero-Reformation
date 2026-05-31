package dev.simulated_team.aero_reformation.content.blocks.power;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Quaterniond;
import org.joml.Vector3d;

@EventBusSubscriber(Dist.CLIENT)
public class PowerHudOverlay {

    private static final int BAR_W = 100;
    private static final int BAR_H = 6;
    private static final int BG = 0x30000000;
    private static final int FILL = 0x6044FF88;
    private static final int TICK = 0x8044FF88;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.CROSSHAIR.equals(event.getName())) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !(player.getVehicle() instanceof SeatEntity seat)) return;
        if (seat.isRedstoneDisabled()) return;

        float yawDiff;
        float pitchDiff;

        if (seat.isCameraLocked()) {
            float qx = seat.getSubRotX(), qy = seat.getSubRotY(), qz = seat.getSubRotZ(), qw = seat.getSubRotW();
            boolean hasRot = Math.abs(qw - 1.0) > 0.0001 || Math.abs(qx) > 0.0001
                    || Math.abs(qy) > 0.0001 || Math.abs(qz) > 0.0001;
            if (hasRot) {
                Quaterniond Q = new Quaterniond(qx, qy, qz, qw);
                Vector3d fwd = new Vector3d(0, 0, 1)
                        .rotateY(Math.toRadians(-seat.getBaseYaw()))
                        .rotate(Q, new Vector3d());
                float worldRef = (float) Math.toDegrees(Math.atan2(-fwd.x, fwd.z));
                float seatWorldPitch = (float) Math.toDegrees(Math.asin(-fwd.y));
                yawDiff = Mth.wrapDegrees(player.getYRot() - worldRef);
                pitchDiff = Mth.clamp(player.getXRot() - seatWorldPitch, -45, 45);
            } else {
                yawDiff = Mth.wrapDegrees(player.getYRot() - seat.getBaseYaw());
                pitchDiff = Mth.clamp(player.getXRot(), -45, 45);
            }
        } else {
            yawDiff = Mth.wrapDegrees(player.getYRot() - seat.getBaseYaw());
            pitchDiff = Mth.clamp(player.getXRot(), -45, 45);
        }

        GuiGraphics gfx = event.getGuiGraphics();
        PoseStack pose = gfx.pose();
        pose.pushPose();

        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int cy = mc.getWindow().getGuiScaledHeight() / 2;

        // --- Yaw bar (horizontal, below crosshair) ---
        int barY = cy + 20;
        int barX = cx - BAR_W / 2;
        gfx.fill(barX, barY, barX + BAR_W, barY + BAR_H, BG);

        float yawFrac = Mth.clamp(yawDiff / 90f, -1f, 1f);
        int px = cx + (int)(yawFrac * BAR_W / 2);
        gfx.fill(px - 1, barY - 2, px + 1, barY + BAR_H + 2, TICK);

        if (yawDiff < 0) {
            int end = cx + (int)(yawDiff / 90f * BAR_W / 2);
            gfx.fill(end, barY + 1, cx, barY + BAR_H - 1, FILL);
        }
        if (yawDiff > 0) {
            int end = cx + (int)(yawDiff / 90f * BAR_W / 2);
            gfx.fill(cx, barY + 1, end, barY + BAR_H - 1, FILL);
        }
        gfx.fill(cx - 1, barY - 1, cx + 1, barY + BAR_H + 1, TICK);

        // --- Pitch bar (vertical, right of crosshair) ---
        int pBarX = cx + 20;
        int pBarY = cy - BAR_W / 2;
        gfx.fill(pBarX, pBarY, pBarX + BAR_H, pBarY + BAR_W, BG);

        // Invert: looking up (negative pitch) → bar above center; looking down → below
        float pFrac = Mth.clamp(pitchDiff / 45f, -1f, 1f);
        int py = cy + (int)(pFrac * BAR_W / 2);
        gfx.fill(pBarX - 2, py - 1, pBarX + BAR_H + 2, py + 1, TICK);

        if (pitchDiff < 0) {
            int end = cy + (int)(pitchDiff / 45f * BAR_W / 2);
            gfx.fill(pBarX + 1, end, pBarX + BAR_H - 1, cy, FILL);
        }
        if (pitchDiff > 0) {
            int end = cy + (int)(pitchDiff / 45f * BAR_W / 2);
            gfx.fill(pBarX + 1, cy, pBarX + BAR_H - 1, end, FILL);
        }
        gfx.fill(pBarX - 1, cy - 1, pBarX + BAR_H + 1, cy + 1, TICK);

        pose.popPose();
    }
}
