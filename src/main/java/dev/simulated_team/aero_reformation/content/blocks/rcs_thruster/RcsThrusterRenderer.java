package dev.simulated_team.aero_reformation.content.blocks.rcs_thruster;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Custom BER: dual-cone exhaust + millisecond-based trail particles.
 */
public class RcsThrusterRenderer implements BlockEntityRenderer<RcsThrusterBlockEntity> {

    private static final int SEG = 10;
    private static final long TRAIL_MS = 800;

    private final List<TrailParticle> trail = new ArrayList<>();

    public RcsThrusterRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(RcsThrusterBlockEntity be, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light, int overlay) {
        if (be.getActiveMask() == 0) return;

        boolean electric = be.isElectricMode();
        Direction facing = be.getRcsFacing();
        Vec3 center = Vec3.atCenterOf(be.getBlockPos());
        long now = System.currentTimeMillis();
        float thrust = be.getThrustNorm();

        for (int nozzle = 0; nozzle < 5; nozzle++) {
            if ((be.getActiveMask() & (1 << nozzle)) == 0) continue;

            Vector3f dir = nozzleDir(nozzle, facing);
            if (dir.length() < 0.001f) continue;
            dir.normalize();

            float len = (nozzle == 0 ? 0.5f : 0.3f) * (0.6f + thrust * 0.4f);
            float baseR = (nozzle == 0 ? 0.07f : 0.05f);

            // Inner cone (bright core)
            float[] c1s = electric ? new float[]{0.8f, 0.9f, 1.0f, 0.9f} : new float[]{1.0f, 0.95f, 0.6f, 0.9f};
            float[] c1e = electric ? new float[]{0.3f, 0.5f, 1.0f, 0.0f} : new float[]{1.0f, 0.6f, 0.1f, 0.0f};
            drawCone(ps, buffer, center, dir, len, baseR, 0.01f, c1s, c1e);

            // Outer cone (glow sheath)
            float[] c2s = electric ? new float[]{0.3f, 0.5f, 1.0f, 0.3f} : new float[]{1.0f, 0.5f, 0.1f, 0.3f};
            float[] c2e = electric ? new float[]{0.2f, 0.3f, 0.8f, 0.0f} : new float[]{0.8f, 0.3f, 0.0f, 0.0f};
            drawCone(ps, buffer, center, dir, len * 1.4f, baseR * 2.0f, 0.01f, c2s, c2e);

            // Trail spawn
            if (be.getLevel() != null && be.getLevel().getGameTime() % 2 == 0) {
                Vector3f spawn = new Vector3f(dir);
                spawn.mul(0.25f);
                spawn.add((float)center.x, (float)center.y, (float)center.z);
                trail.add(new TrailParticle(spawn, dir, electric ? 0.06f : 0.08f, now));
            }
        }
        drawTrail(ps, buffer, now);
    }

    private void drawCone(PoseStack ps, MultiBufferSource buf, Vec3 center, Vector3f dir,
                          float len, float baseR, float tipR, float[] cS, float[] cE) {
        VertexConsumer vc = buf.getBuffer(RenderType.translucent());
        Matrix4f m = ps.last().pose();
        float cx = (float)center.x, cy = (float)center.y, cz = (float)center.z;

        // Build orthogonal basis: up = arbitrary axis perpendicular to dir
        Vector3f up = Math.abs(dir.y()) < 0.9f
                ? new Vector3f(0, 1, 0).cross(dir)
                : new Vector3f(1, 0, 0).cross(dir);
        up.normalize();
        Vector3f right = new Vector3f(dir).cross(up).normalize();

        for (int i = 0; i < SEG; i++) {
            float a0 = (float) i / SEG * Mth.TWO_PI;
            float a1 = (float) (i + 1) / SEG * Mth.TWO_PI;
            float cos0 = Mth.cos(a0), sin0 = Mth.sin(a0);
            float cos1 = Mth.cos(a1), sin1 = Mth.sin(a1);

            // Base vertices
            float bx0 = cx + (right.x() * cos0 + up.x() * sin0) * baseR;
            float by0 = cy + (right.y() * cos0 + up.y() * sin0) * baseR;
            float bz0 = cz + (right.z() * cos0 + up.z() * sin0) * baseR;
            float bx1 = cx + (right.x() * cos1 + up.x() * sin1) * baseR;
            float by1 = cy + (right.y() * cos1 + up.y() * sin1) * baseR;
            float bz1 = cz + (right.z() * cos1 + up.z() * sin1) * baseR;

            // Tip vertices
            float tx = cx + dir.x() * len;
            float ty = cy + dir.y() * len;
            float tz = cz + dir.z() * len;

            // Triangle 1: base edge -> tip
            vertex(vc, m, bx0, by0, bz0, cS[0], cS[1], cS[2], cS[3]);
            vertex(vc, m, bx1, by1, bz1, cS[0], cS[1], cS[2], cS[3]);
            vertex(vc, m, tx, ty, tz, cE[0], cE[1], cE[2], cE[3]);
        }
    }

    private void drawTrail(PoseStack ps, MultiBufferSource buf, long now) {
        VertexConsumer vc = buf.getBuffer(RenderType.translucent());
        Matrix4f m = ps.last().pose();
        Iterator<TrailParticle> it = trail.iterator();
        while (it.hasNext()) {
            TrailParticle p = it.next();
            float age = p.age(now);
            if (age >= 1) { it.remove(); continue; }

            float alpha = (1 - age) * 0.4f;
            float px = p.x + p.vx * age * 50, py = p.y + p.vy * age * 50, pz = p.z + p.vz * age * 50;
            float s = 0.03f + age * 0.04f;
            float r = p.electric ? 0.5f : 1.0f;
            float g = p.electric ? 0.6f : 0.6f;
            float b = p.electric ? 1.0f : 0.1f;

            vertex(vc, m, px - s, py - s, pz - s, r, g, b, alpha);
            vertex(vc, m, px + s, py - s, pz - s, r, g, b, alpha);
            vertex(vc, m, px + s, py + s, pz - s, r, g, b, alpha);
            vertex(vc, m, px - s, py + s, pz - s, r, g, b, alpha);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z,
                                float r, float g, float b, float a) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a);
    }

    private static Vector3f nozzleDir(int idx, Direction facing) {
        Vector3f local = switch (idx) {
            case 0 -> new Vector3f(0, 0, 1);
            case 1 -> new Vector3f(-0.707f, 0, 0.707f);
            case 2 -> new Vector3f(0.707f, 0, 0.707f);
            case 3 -> new Vector3f(0, -0.707f, 0.707f);
            case 4 -> new Vector3f(0, 0.707f, 0.707f);
            default -> new Vector3f(0, 0, 1);
        };
        return rotateByFacing(local, facing);
    }

    private static Vector3f rotateByFacing(Vector3f v, Direction facing) {
        return switch (facing) {
            case NORTH -> v;
            case SOUTH -> new Vector3f(-v.x(), v.y(), -v.z());
            case EAST -> new Vector3f(v.z(), v.y(), -v.x());
            case WEST -> new Vector3f(-v.z(), v.y(), v.x());
            case UP -> new Vector3f(v.x(), -v.z(), v.y());
            case DOWN -> new Vector3f(v.x(), v.z(), -v.y());
        };
    }

    record TrailParticle(float x, float y, float z, float vx, float vy, float vz,
                          boolean electric, long birthMs) {
        TrailParticle(Vector3f pos, Vector3f dir, float speed, long now) {
            this(pos.x(), pos.y(), pos.z(), dir.x() * speed, dir.y() * speed, dir.z() * speed,
                    false, now);
        }
        float age(long now) { return Math.min((now - birthMs) / (float)TRAIL_MS, 1); }
    }
}
