package dev.simulated_team.aero_reformation.content.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Shared drawing logic used by both the in-game overlay and the config screen.
 * Content is transformed (scaled / rotated) around its own center.
 */
public final class HudRenderers {

    /** Axis-aligned bounds in screen coordinates (rotation ignored for hit-testing). */
    public record Bounds(float x, float y, float w, float h) {
        public boolean contains(double px, double py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    /** A point on screen, used for selection handles. */
    public record Handle(float x, float y) {}

    private static final int HANDLE_SIZE = 7; // draw size for handles

    /** Cap for scanline fills so huge shapes never tank the frame rate. */
    private static final int MAX_FILL_ROWS = 400;

    private HudRenderers() {}

    /** Resolve the entry color; rainbow cycles the hue, and the entry alpha applies. */
    private static int resolveColor(HudEntry e, int baseColor) {
        int color = baseColor;
        if (e.rainbow) {
            float hue = ((System.currentTimeMillis() % 2400) / 2400f);
            color = 0xFF000000 | Mth.hsvToRgb(hue, 1.0f, 1.0f);
        }
        int a = Math.max(0, Math.min(255, e.alpha));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    // ── geometry helpers ──

    public static int contentWidth(HudEntry e, Minecraft mc) {
        return switch (e.type) {
            case TEXT -> {
                String resolved = HudPlaceholders.resolve(e.text, mc);
                int w = mc.font.width(resolved);
                String info = HudBindings.getDisplayValue(mc.level, e);
                if (!info.isEmpty()) w += mc.font.width(": " + info);
                String suffix = HudPlaceholders.resolve(e.suffix, mc);
                if (!suffix.isEmpty()) w += mc.font.width((info.isEmpty() ? "" : " ") + suffix);
                yield w;
            }
            case BAR -> e.barWidth;
            case LINE, SHAPE -> Math.max(1, (int) Math.abs(e.endX - e.x));
        };
    }

    public static int contentHeight(HudEntry e) {
        return switch (e.type) {
            case TEXT -> 8;
            case BAR -> e.barHeight;
            case LINE, SHAPE -> Math.max(1, (int) Math.abs(e.endY - e.y));
        };
    }

    public static float centerX(HudEntry e, Minecraft mc) {
        if (e.type == HudEntry.Type.LINE) return (e.x + e.endX) / 2f;
        return e.x + contentWidth(e, mc) * e.scale / 2f;
    }

    public static float centerY(HudEntry e, Minecraft mc) {
        if (e.type == HudEntry.Type.LINE) return (e.y + e.endY) / 2f;
        return e.y + contentHeight(e) * e.scale / 2f;
    }

    public static Bounds getBounds(HudEntry e, Minecraft mc) {
        if (e.type == HudEntry.Type.LINE) {
            float minX = Math.min(e.x, e.endX);
            float minY = Math.min(e.y, e.endY);
            return new Bounds(minX, minY,
                    Math.abs(e.endX - e.x) * e.scale, Math.abs(e.endY - e.y) * e.scale);
        }
        float w = contentWidth(e, mc) * e.scale;
        float h = contentHeight(e) * e.scale;
        return new Bounds(e.x, e.y, w, h);
    }

    private static Handle rotatePoint(float px, float py, float cx, float cy, float deg) {
        float rad = (float) Math.toRadians(deg);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        float dx = px - cx, dy = py - cy;
        return new Handle(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos);
    }

    /** Rotation handle: above the frame's top-center. */
    public static Handle getRotateHandle(HudEntry e, Minecraft mc) {
        float cx = centerX(e, mc), cy = centerY(e, mc);
        float hw = contentWidth(e, mc) * e.scale / 2f;
        float hh = contentHeight(e) * e.scale / 2f;
        return rotatePoint(cx, cy - hh - 8, cx, cy, e.rotation);
    }

    /** Eight resize handles on the rotated frame (corners + edge midpoints). */
    public static Handle[] getResizeHandles(HudEntry e, Minecraft mc) {
        float cx = centerX(e, mc), cy = centerY(e, mc);
        float hw = contentWidth(e, mc) * e.scale / 2f;
        float hh = contentHeight(e) * e.scale / 2f;
        float[][] pts = {
                {-hw, -hh}, {0, -hh}, {hw, -hh},
                {-hw, 0}, {hw, 0},
                {-hw, hh}, {0, hh}, {hw, hh}
        };
        Handle[] out = new Handle[pts.length];
        for (int i = 0; i < pts.length; i++) {
            out[i] = rotatePoint(cx + pts[i][0], cy + pts[i][1], cx, cy, e.rotation);
        }
        return out;
    }

    // ── world-anchored entries ──

    /** Find a bound sable physics body (ClientSubLevel) by its UUID. */
    public static ClientSubLevel findPhysBody(Minecraft mc, String uuid) {
        if (uuid == null || uuid.isEmpty() || mc.level == null) return null;
        ClientSubLevelContainer c = ClientSubLevelContainer.getContainer(mc.level);
        if (c == null) return null;
        for (ClientSubLevel s : c.getAllSubLevels()) {
            if (s.getUniqueId().toString().equals(uuid)) return s;
        }
        return null;
    }

    /** Find the physics body owning the chunk of the given point (via sable). */
    public static ClientSubLevel findPhysBodyAt(Minecraft mc, double x, double y, double z) {
        if (mc.level == null) return null;
        SubLevel sub = Sable.HELPER.getContaining(mc.level,
                new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        return sub instanceof ClientSubLevel cs ? cs : null;
    }

    /** Horizon anchor: a fixed distance straight ahead of the player, at eye height.
     *  Uses the interpolated view vector so the anchor moves smoothly between ticks
     *  when riding a physics body (the player's yaw/pitch are tick-updated there). */
    public static Vec3 horizonAnchor(HudEntry e, Minecraft mc) {
        if (mc.player == null) return new Vec3(e.worldX, e.worldY, e.worldZ);
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 look = mc.player.getViewVector(pt);
        Vec3 horiz = new Vec3(look.x, 0, look.z);
        if (horiz.lengthSqr() < 1e-8) horiz = new Vec3(0, 0, -1);
        horiz = horiz.normalize();
        Vec3 eye = mc.player.getEyePosition(pt);
        return eye.add(horiz.scale(e.horizonDist));
    }

    /**
     * World anchor: fixed world coordinates, or the picked block's body-local
     * position projected into the main world via Sable.HELPER so it follows the
     * body's full transform (position AND rotation). worldX/Y/Z hold the
     * body-local position when attached, absolute world coordinates otherwise.
     */
    public static Vec3 worldAnchor(HudEntry e, Minecraft mc) {
        if (mc.level == null) return new Vec3(e.worldX, e.worldY, e.worldZ);
        if (e.physBodyId.isEmpty()) return new Vec3(e.worldX, e.worldY, e.worldZ);
        return Sable.HELPER.projectOutOfSubLevel(mc.level, new Vec3(e.worldX, e.worldY, e.worldZ));
    }

    /**
     * Screen rotation (degrees) applied to a world-anchored entry.
     * - world mode: follows the bound physics body, so the entry stays parallel
     *   to the body's own horizontal plane (rotates with the body).
     * - horizon mode: keeps level with the ground using the world Y axis.
     *
     * The angle is atan2(up · camLeft, up · camUp): pitch rotates around
     * camLeft, so it contributes nothing (dot with camLeft stays 0); only
     * camera roll changes the ratio, so the entry stays level whether or not
     * the player is riding a physics body.
     */
    public static float entryWorldAngle(HudEntry e, Minecraft mc) {
        Vector3f up = new Vector3f(0, 1, 0);
        if ("world".equals(e.anchor)) {
            ClientSubLevel sub = findPhysBody(mc, e.physBodyId);
            if (sub != null) {
                Pose3dc pose = sub.renderPose();
                if (pose != null) {
                    Vector3d upD = pose.orientation().transform(new Vector3d(0, 1, 0));
                    up = new Vector3f((float) upD.x, (float) upD.y, (float) upD.z);
                }
            }
        }
        Camera cam = mc.gameRenderer.getMainCamera();
        float dotL = up.dot(cam.getLeftVector());
        float dotU = up.dot(cam.getUpVector());
        return (float) -Math.toDegrees(Math.atan2(dotL, dotU));
    }

    /**
     * Project a world position to GUI-scaled screen coordinates using the camera.
     * Returns null when the point is behind the camera.
     */
    public static Vector2f projectToScreen(Vec3 worldPos, Minecraft mc) {
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();
        Quaternionf camOri = cam.rotation().conjugate();
        Vector3f v = new Vector3f((float) (worldPos.x - camPos.x),
                (float) (worldPos.y - camPos.y), (float) (worldPos.z - camPos.z));
        v.rotate(camOri);
        if (v.z() >= 0) return null; // behind the camera
        double fov = Math.toRadians(mc.options.fov().get());
        double halfH = Math.tan(fov / 2);
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        double halfW = halfH * (sw / (double) sh);
        float sx = (float) (sw / 2.0 + (v.x() / (-v.z() * halfW)) * sw / 2.0);
        float sy = (float) (sh / 2.0 - (v.y() / (-v.z() * halfH)) * sh / 2.0);
        return new Vector2f(sx, sy);
    }

    /**
     * Screen position of an entry according to its anchor mode.
     * screen -> its own x/y; horizon/world -> projected anchor plus the entry's
     * x/y screen offset (so dragging in the editor positions it relative to the
     * anchor); null = not visible.
     */
    public static Vector2f resolveScreenPos(HudEntry e, Minecraft mc) {
        return switch (e.anchor) {
            case "horizon" -> projectWithOffset(projectToScreen(horizonAnchor(e, mc), mc), e);
            case "world" -> projectWithOffset(projectToScreen(worldAnchor(e, mc), mc), e);
            default -> new Vector2f(e.x, e.y);
        };
    }

    /** Anchor projection plus the entry's own screen offset (x/y). */
    private static Vector2f projectWithOffset(Vector2f p, HudEntry e) {
        if (p == null) return null;
        return new Vector2f(p.x + e.x, p.y + e.y);
    }

    /**
     * Render an entry at its resolved screen position (projecting world-anchored
     * entries). Temporarily offsets the local geometry so hit-testing helpers
     * keep working unchanged.
     */
    /** On-screen width of an entry (unscaled content size). */
    public static float entryWidth(HudEntry e, Minecraft mc) {
        return switch (e.type) {
            case LINE, SHAPE -> Math.max(1, Math.abs(e.endX - e.x));
            case BAR -> e.barWidth;
            case TEXT -> contentWidth(e, mc);
        };
    }

    /** On-screen height of an entry (unscaled content size). */
    public static float entryHeight(HudEntry e) {
        return switch (e.type) {
            case LINE, SHAPE -> Math.max(1, Math.abs(e.endY - e.y));
            case BAR -> e.barHeight;
            case TEXT -> contentHeight(e);
        };
    }

    // ── shared view snapshot (render all horizon entries against one capture) ──

    /**
     * One camera/view snapshot shared by ALL horizon/world entries in a single
     * render pass. Capturing it once up-front guarantees every entry is
     * projected with the exact same eye / look / camera / roll data, so there
     * is no entry-to-entry timing discrepancy within a frame.
     */
    public record ViewSnapshot(Vec3 playerEye, Vec3 horizLook, Vec3 camPos,
                               Quaternionf camRot, float rollAngle) {
        public static ViewSnapshot capture(Minecraft mc) {
            if (mc.player == null) return null;
            Camera cam = mc.gameRenderer.getMainCamera();
            // Interpolated partial-tick values so the whole set moves smoothly.
            float pt = mc.getTimer().getGameTimeDeltaPartialTick(true);
            Vec3 look = mc.player.getViewVector(pt);
            Vec3 horiz = new Vec3(look.x, 0, look.z);
            if (horiz.lengthSqr() < 1e-8) horiz = new Vec3(0, 0, -1);
            horiz = horiz.normalize();
            // Roll compensation for horizon entries: -atan2(up·camLeft, up·camUp), up = (0,1,0)
            float dotL = cam.getLeftVector().y();
            float dotU = cam.getUpVector().y();
            float roll = (float) -Math.toDegrees(Math.atan2(dotL, dotU));
            return new ViewSnapshot(
                    mc.player.getEyePosition(pt),
                    horiz,
                    cam.getPosition(),
                    new Quaternionf(cam.rotation()).conjugate(),
                    roll);
        }
    }

    private static ViewSnapshot stableSnap = null;
    private static net.minecraft.client.multiplayer.ClientLevel lastSnapLevel = null;

    /**
     * Capture one view snapshot shared by the whole HUD pass. Every frame uses
     * the current camera pose so horizon/world entries rotate and move with the
     * player. The only guard is that a snapshot from another world/dimension is
     * never reused - otherwise switching between a server and a single-player
     * world (or respawning/teleporting far away) would freeze the HUD against a
     * stale pose, and horizon entries would stop rotating/moving entirely.
     */
    public static ViewSnapshot captureStable(Minecraft mc) {
        ViewSnapshot snap = ViewSnapshot.capture(mc);
        if (snap == null) return stableSnap;
        // New world/dimension: never reuse a pose captured elsewhere.
        net.minecraft.client.multiplayer.ClientLevel level = mc.level;
        if (level != lastSnapLevel) {
            lastSnapLevel = level;
            stableSnap = snap;
            return snap;
        }
        stableSnap = snap;
        return snap;
    }

    /** Horizon anchor from a shared view snapshot. */
    public static Vec3 horizonAnchor(HudEntry e, ViewSnapshot snap) {
        return snap.playerEye().add(snap.horizLook().scale(e.horizonDist));
    }

    /** Project a world position with a shared view snapshot. Returns null when behind the camera. */
    public static Vector2f projectToScreen(Vec3 worldPos, ViewSnapshot snap, Minecraft mc) {
        Vector3f v = new Vector3f((float) (worldPos.x - snap.camPos().x),
                (float) (worldPos.y - snap.camPos().y), (float) (worldPos.z - snap.camPos().z));
        v.rotate(snap.camRot());
        if (v.z() >= 0) return null; // behind the camera
        double fov = Math.toRadians(mc.options.fov().get());
        double halfH = Math.tan(fov / 2);
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        double halfW = halfH * (sw / (double) sh);
        float sx = (float) (sw / 2.0 + (v.x() / (-v.z() * halfW)) * sw / 2.0);
        float sy = (float) (sh / 2.0 - (v.y() / (-v.z() * halfH)) * sh / 2.0);
        return new Vector2f(sx, sy);
    }

    /** Screen position from a shared view snapshot (world mode still resolves live). */
    public static Vector2f resolveScreenPos(HudEntry e, Minecraft mc, ViewSnapshot snap) {
        return switch (e.anchor) {
            case "horizon" -> projectWithOffset(projectToScreen(horizonAnchor(e, snap), snap, mc), e);
            case "world" -> projectWithOffset(projectToScreen(worldAnchor(e, mc), snap, mc), e);
            default -> new Vector2f(e.x, e.y);
        };
    }

    /** Roll-compensated screen rotation for a shared pass (world keeps live body-up). */
    public static float screenRotation(HudEntry e, Minecraft mc, ViewSnapshot snap) {
        // World entries follow the anchor's position only; they no longer rotate
        // with the physics body and stay upright (always facing the player).
        return "world".equals(e.anchor) ? 0 : snap.rollAngle();
    }

    public static void renderProjected(GuiGraphics g, HudEntry e, Minecraft mc, boolean editor, ViewSnapshot snap) {
        if (snap == null) {
            renderProjected(g, e, mc, editor);
            return;
        }
        if ("screen".equals(e.anchor)) {
            renderEntry(g, e, mc, editor);
            return;
        }
        // World anchors follow the physics body's logical pose (20 tps), so their
        // screen projection is smoothed across render frames; horizon anchors use
        // the interpolated view and need no extra smoothing.
        Vector2f anchor = "world".equals(e.anchor)
                ? smoothAnchorScreen(e, projectToScreen(worldAnchor(e, mc), snap, mc))
                : projectToScreen(horizonAnchor(e, snap), snap, mc);
        if (anchor == null) return; // behind camera
        Vector2f sp = projectWithOffset(anchor, e);
        // Anchor-centered rotation: the whole set rotates rigidly around the
        // anchor's screen point (endpoints of parallel lines stay aligned).
        float rot = screenRotation(e, mc, snap);
        // Top-left model: sp is the entry's top-left, endpoint follows
        HudEntry tmp = e.copy();
        tmp.x = sp.x;
        tmp.y = sp.y;
        tmp.endX = e.endX + (sp.x - e.x);
        tmp.endY = e.endY + (sp.y - e.y);
        tmp.rotation = e.rotation; // roll/angle applied by the outer transform
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(anchor.x(), anchor.y(), 0);
        pose.mulPose(Axis.ZP.rotationDegrees(rot));
        pose.translate(-anchor.x(), -anchor.y(), 0);
        renderEntry(g, tmp, mc, editor);
        pose.popPose();
    }

    public static void renderProjected(GuiGraphics g, HudEntry e, Minecraft mc, boolean editor) {
        if ("screen".equals(e.anchor)) {
            renderEntry(g, e, mc, editor);
            return;
        }
        Vector2f anchor = "world".equals(e.anchor)
                ? smoothAnchorScreen(e, projectToScreen(worldAnchor(e, mc), mc))
                : projectToScreen(horizonAnchor(e, mc), mc);
        if (anchor == null) return; // behind camera
        Vector2f sp = projectWithOffset(anchor, e);
        // World entries stay upright (rot = 0); horizon entries keep level with the ground.
        float rot = "world".equals(e.anchor) ? 0 : entryWorldAngle(e, mc);
        // Top-left model: sp (anchor projection + e.x/e.y) is the entry's top-left
        // and the endpoint follows, so shapes/lines keep their drawn size.
        HudEntry tmp = e.copy();
        tmp.x = sp.x;
        tmp.y = sp.y;
        tmp.endX = e.endX + (sp.x - e.x);
        tmp.endY = e.endY + (sp.y - e.y);
        tmp.rotation = e.rotation;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(anchor.x(), anchor.y(), 0);
        pose.mulPose(Axis.ZP.rotationDegrees(rot));
        pose.translate(-anchor.x(), -anchor.y(), 0);
        renderEntry(g, tmp, mc, editor);
        pose.popPose();
    }

    /** The world anchor point of a horizon/world entry (for anchor-centered rotation). */
    private static Vec3 anchorPoint(HudEntry e, Minecraft mc, ViewSnapshot snap) {
        return "world".equals(e.anchor) ? worldAnchor(e, mc) : horizonAnchor(e, snap);
    }

    /** The world anchor point of a horizon/world entry (no shared snapshot). */
    private static Vec3 anchorPoint(HudEntry e, Minecraft mc) {
        return "world".equals(e.anchor) ? worldAnchor(e, mc) : horizonAnchor(e, mc);
    }

    // ── world-anchor frame smoothing ──

    /** Last screen projection of each world entry's anchor, smoothed across render
     *  frames (the physics body's logical pose updates at 20 tps). WeakHashMap keys
     *  on the entry object identity, so entries are cleaned up when removed. */
    private static final Map<HudEntry, Vector2f> SMOOTH_ANCHOR = new WeakHashMap<>();
    private static final float SMOOTH_ALPHA = 0.4f;
    private static final float SMOOTH_SNAP_DIST = 120.0f; // px: larger jump = snap (teleport)

    private static Vector2f smoothAnchorScreen(HudEntry e, Vector2f target) {
        if (target == null) return null;
        Vector2f prev = SMOOTH_ANCHOR.get(e);
        if (prev == null) {
            SMOOTH_ANCHOR.put(e, new Vector2f(target));
            return new Vector2f(target);
        }
        float dx = target.x - prev.x;
        float dy = target.y - prev.y;
        float alpha = (dx * dx + dy * dy) > SMOOTH_SNAP_DIST * SMOOTH_SNAP_DIST ? 1.0f : SMOOTH_ALPHA;
        Vector2f s = new Vector2f(prev.x + dx * alpha, prev.y + dy * alpha);
        SMOOTH_ANCHOR.put(e, s);
        return new Vector2f(s);
    }

    // ── rendering ──

    public static void renderEntry(GuiGraphics g, HudEntry e, Minecraft mc) {
        renderEntry(g, e, mc, false);
    }

    public static void renderEntry(GuiGraphics g, HudEntry e, Minecraft mc, boolean editor) {
        // editor backplate keeps the entry readable over the dimmed background
        if (editor) {
            Bounds b = getBounds(e, mc);
            g.fill((int) b.x() - 1, (int) b.y() - 1, (int) (b.x() + b.w()) + 1, (int) (b.y() + b.h()) + 1, 0x80000000);
        }

        PoseStack pose = g.pose();
        pose.pushPose();

        if (e.type == HudEntry.Type.LINE) {
            // Lines keep their drawn direction: rotate/scale around the segment center
            float x1 = e.x, y1 = e.y, x2 = e.endX, y2 = e.endY;
            pose.translate((x1 + x2) / 2f, (y1 + y2) / 2f, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(e.rotation));
            pose.scale(e.scale, e.scale, 1.0F);
            drawLineFast(g, (x1 - x2) / 2f, (y1 - y2) / 2f, (x2 - x1) / 2f, (y2 - y1) / 2f,
                    Math.max(1, e.lineWidth), resolveColor(e, e.color));
        } else {
            // Resolve TEXT content once so width measurement and drawing share the
            // same strings (avoids re-parsing placeholders / re-reading binds).
            String resolved = null, suffix = null, info = null;
            float cw;
            if (e.type == HudEntry.Type.TEXT) {
                resolved = HudPlaceholders.resolve(e.text, mc);
                suffix = HudPlaceholders.resolve(e.suffix, mc);
                info = HudBindings.getDisplayValue(mc.level, e);
                cw = mc.font.width(resolved);
                if (!info.isEmpty()) cw += mc.font.width(": " + info);
                if (!suffix.isEmpty()) cw += mc.font.width((info.isEmpty() ? "" : " ") + suffix);
            } else {
                cw = contentWidth(e, mc);
            }
            float ch = contentHeight(e);
            pose.translate(e.x + cw * e.scale / 2f, e.y + ch * e.scale / 2f, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(e.rotation));
            pose.scale(e.scale, e.scale, 1.0F);
            pose.translate(-cw / 2f, -ch / 2f, 0);

            switch (e.type) {
                case TEXT -> {
                    g.drawString(mc.font, Component.literal(resolved), 0, 0, resolveColor(e, e.color), true);
                    int x = mc.font.width(resolved);
                    if (!info.isEmpty()) {
                        g.drawString(mc.font, Component.literal(": " + info), x, 0, resolveColor(e, e.bindColor), true);
                        x += mc.font.width(": " + info);
                        if (!suffix.isEmpty()) {
                            g.drawString(mc.font, Component.literal(" " + suffix), x, 0, resolveColor(e, e.color), true);
                        }
                    } else if (!suffix.isEmpty()) {
                        // Unbound: append the suffix directly after the text
                        g.drawString(mc.font, Component.literal(suffix), x, 0, resolveColor(e, e.color), true);
                    }
                }
                case BAR -> {
                    g.fill(0, 0, e.barWidth, e.barHeight, 0xC0000000);
                    int fillW = barFillWidth(e, mc);
                    if (fillW > 0) {
                        g.fill(1, 1, 1 + fillW, e.barHeight - 1, resolveColor(e, e.color));
                    }
                }
                case LINE -> { }
                case SHAPE -> drawShape(g, 0, 0, cw, ch, e.shape, e.filled,
                        Math.max(1, e.lineWidth), resolveColor(e, e.color));
            }
        }
        pose.popPose();
    }

    /**
     * Fill width of a BAR. The value comes from the first number parsed out of
     * the resolved text (placeholders resolve first), or, when the text uses x
     * (e.g. "x", "x*2", "x+10"), from the bound info value applied as variable x.
     * Clamped to [0, barMax].
     */
    private static int barFillWidth(HudEntry e, Minecraft mc) {
        if (e.barMax <= 0) return 0;
        String resolved = HudPlaceholders.resolve(e.text, mc);
        Double val = null;
        if (resolved != null && (resolved.indexOf('x') >= 0 || resolved.indexOf('X') >= 0)) {
            // x = the bound info value (e.g. sensor reading / NBT value)
            double base = boundInfoValue(e, mc);
            Double r = HudPlaceholders.evalMath(base, resolved);
            if (r != null && Double.isFinite(r)) val = r;
        }
        if (val == null) {
            val = parseFirstNumber(resolved);
        }
        if (val == null) return 0;
        double ratio = Mth.clamp(val / e.barMax, 0.0, 1.0);
        return (int) Math.round((e.barWidth - 2) * ratio);
    }

    /** Numeric value of the bound info (first number), or 0 when unbound. */
    private static double boundInfoValue(HudEntry e, Minecraft mc) {
        String info = HudBindings.getDisplayValue(mc.level, e);
        Double n = parseFirstNumber(info);
        return n != null ? n : 0;
    }

    private static final java.util.regex.Pattern NUMBER_PATTERN =
            java.util.regex.Pattern.compile("[-+]?\\d*\\.?\\d+");

    /** First number in a string (handles signs and decimals), or null. */
    private static Double parseFirstNumber(String s) {
        if (s == null) return null;
        java.util.regex.Matcher m = NUMBER_PATTERN.matcher(s);
        if (!m.find()) return null;
        try {
            return Double.parseDouble(m.group());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Fast line: a single rotated fill, drawn in current PoseStack space. */
    public static void drawLineFast(GuiGraphics g, float x1, float y1, float x2, float y2, int width, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1.0F) return;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x1, y1, 0);
        pose.mulPose(Axis.ZP.rotationDegrees((float) Math.toDegrees(Math.atan2(dy, dx))));
        g.fill(0, -width / 2, (int) len, width - width / 2, color);
        pose.popPose();
    }

    /**
     * Draw a shape inside the given axis-aligned box (local/screen space).
     * Performance: solid shapes use scanline fills (bounded by MAX_FILL_ROWS),
     * hollow shapes use a handful of thick line segments - never per-pixel work.
     */
    public static void drawShape(GuiGraphics g, float x1, float y1, float x2, float y2,
                                 String shape, boolean filled, int lineWidth, int color) {
        int x = (int) Math.floor(Math.min(x1, x2));
        int y = (int) Math.floor(Math.min(y1, y2));
        int w = Math.max(1, (int) Math.ceil(Math.abs(x2 - x1)));
        int h = Math.max(1, (int) Math.ceil(Math.abs(y2 - y1)));
        int lw = Math.max(1, lineWidth);
        switch (shape) {
            case "circle" -> drawEllipse(g, x, y, w, h, filled, lw, color);
            case "triangle" -> drawTriangle(g, x, y, w, h, filled, lw, color);
            case "diamond" -> drawDiamond(g, x, y, w, h, filled, lw, color);
            default -> drawRect(g, x, y, w, h, filled, lw, color);
        }
    }

    /** Rectangle: 1 fill when solid, 4 fills when hollow. */
    private static void drawRect(GuiGraphics g, int x, int y, int w, int h, boolean filled, int lw, int color) {
        if (filled) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x, y, x + w, y + lw, color);                       // top
        g.fill(x, y + h - lw, x + w, y + h, color);               // bottom
        g.fill(x, y + lw, x + lw, y + h - lw, color);             // left
        g.fill(x + w - lw, y + lw, x + w, y + h - lw, color);     // right
    }

    /** Ellipse: solid = one scanline fill per row; hollow = edge strips per row. */
    private static void drawEllipse(GuiGraphics g, int x, int y, int w, int h, boolean filled, int lw, int color) {
        double rx = Math.max(0.5, w / 2.0), ry = Math.max(0.5, h / 2.0);
        double cx = x + rx, cy = y + ry;
        int rows = Math.min(h, MAX_FILL_ROWS);
        int step = Math.max(1, (int) Math.ceil((double) h / rows));
        for (int yy = y; yy < y + h; yy += step) {
            double t = (yy + 0.5 - cy) / ry;                 // -1 .. 1
            if (t < -1 || t > 1) continue;
            double half = rx * Math.sqrt(Math.max(0, 1 - t * t));
            int x0 = (int) Math.ceil(cx - half);
            int x1 = (int) Math.floor(cx + half);
            if (x1 < x0) continue;
            int yEnd = Math.min(yy + step, y + h);
            if (filled) {
                g.fill(x0, yy, x1 + 1, yEnd, color);
            } else {
                g.fill(x0, yy, x0 + lw, yEnd, color);        // left edge strip
                g.fill(x1 - lw + 1, yy, x1 + 1, yEnd, color);// right edge strip
            }
        }
    }

    /** Triangle (apex top-center): solid scanline fill, hollow = 3 line segments. */
    private static void drawTriangle(GuiGraphics g, int x, int y, int w, int h, boolean filled, int lw, int color) {
        float apexX = x + w / 2f;
        if (!filled) {
            drawLineFast(g, apexX, y, x, y + h, lw, color);
            drawLineFast(g, x, y + h, x + w, y + h, lw, color);
            drawLineFast(g, x + w, y + h, apexX, y, lw, color);
            return;
        }
        int rows = Math.min(h, MAX_FILL_ROWS);
        int step = Math.max(1, (int) Math.ceil((double) h / rows));
        for (int yy = y; yy < y + h; yy += step) {
            double f = (yy + 0.5 - y) / h;                   // 0 at apex -> 1 at base
            double halfW = f * (w / 2.0);
            int x0 = (int) Math.ceil(apexX - halfW);
            int x1 = (int) Math.floor(apexX + halfW);
            if (x1 < x0) continue;
            g.fill(x0, yy, x1 + 1, Math.min(yy + step, y + h), color);
        }
    }

    /** Diamond (points at mid-edges): solid scanline fill, hollow = 4 line segments. */
    private static void drawDiamond(GuiGraphics g, int x, int y, int w, int h, boolean filled, int lw, int color) {
        float cx = x + w / 2f, cy = y + h / 2f;
        if (!filled) {
            drawLineFast(g, cx, y, x + w, cy, lw, color);
            drawLineFast(g, x + w, cy, cx, y + h, lw, color);
            drawLineFast(g, cx, y + h, x, cy, lw, color);
            drawLineFast(g, x, cy, cx, y, lw, color);
            return;
        }
        double hw = w / 2.0, hh = h / 2.0;
        int rows = Math.min(h, MAX_FILL_ROWS);
        int step = Math.max(1, (int) Math.ceil((double) h / rows));
        for (int yy = y; yy < y + h; yy += step) {
            double f = Math.abs((yy + 0.5 - cy) / hh);       // 0 mid -> 1 tips
            double halfW = hw * (1 - f);
            int x0 = (int) Math.ceil(cx - halfW);
            int x1 = (int) Math.floor(cx + halfW);
            if (x1 < x0) continue;
            g.fill(x0, yy, x1 + 1, Math.min(yy + step, y + h), color);
        }
    }

    /** Distance from a point to a line segment. */
    public static double distToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double len2 = dx * dx + dy * dy;
        if (len2 < 1e-6) return Math.hypot(px - x1, py - y1);
        double t = ((px - x1) * dx + (py - y1) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        double cx = x1 + t * dx, cy = y1 + t * dy;
        return Math.hypot(px - cx, py - cy);
    }

    /** Precise hit-test for LINE entries: transform the point into entry-local space, then check segment distance. */
    public static boolean containsLine(HudEntry e, Minecraft mc, double mx, double my, double threshold) {
        float cx = centerX(e, mc), cy = centerY(e, mc);
        if (e.scale < 1e-6) return false;
        // translate to segment center, inverse-rotate, inverse-scale
        double lx = mx - cx, ly = my - cy;
        double rad = Math.toRadians(-e.rotation);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double rx = (lx * cos - ly * sin) / e.scale;
        double ry = (lx * sin + ly * cos) / e.scale;
        // local segment endpoints (direction preserved)
        double lx1 = e.x - cx, ly1 = e.y - cy;
        double lx2 = e.endX - cx, ly2 = e.endY - cy;
        return distToSegment(rx, ry, lx1, ly1, lx2, ly2) <= threshold;
    }

    /** Rotation-aware selection frame (a rectangle around the entry, in rotated space). */
    public static void renderSelectionFrame(GuiGraphics g, HudEntry e, Minecraft mc, int color) {
        PoseStack pose = g.pose();
        int hw = (int) (contentWidth(e, mc) * e.scale / 2f);
        int hh = (int) (contentHeight(e) * e.scale / 2f);
        pose.pushPose();
        pose.translate(centerX(e, mc), centerY(e, mc), 0);
        pose.mulPose(Axis.ZP.rotationDegrees(e.rotation));
        g.fill(-hw, -hh, hw, -hh + 1, color);
        g.fill(-hw, hh - 1, hw, hh, color);
        g.fill(-hw, -hh, -hw + 1, hh, color);
        g.fill(hw - 1, -hh, hw, hh, color);
        pose.popPose();
    }

    public static void renderHandle(GuiGraphics g, Handle h, int color) {
        g.fill((int) h.x() - HANDLE_SIZE / 2, (int) h.y() - HANDLE_SIZE / 2,
                (int) h.x() + HANDLE_SIZE / 2 + 1, (int) h.y() + HANDLE_SIZE / 2 + 1, color);
    }
}
