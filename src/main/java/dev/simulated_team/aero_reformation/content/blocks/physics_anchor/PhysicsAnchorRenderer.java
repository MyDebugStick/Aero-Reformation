package dev.simulated_team.aero_reformation.content.blocks.physics_anchor;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import com.mojang.math.Axis;

public class PhysicsAnchorRenderer implements BlockEntityRenderer<PhysicsAnchorBlockEntity> {

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath("aero_reformation", "textures/block/physics_anchor.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEX);

    public PhysicsAnchorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(PhysicsAnchorBlockEntity be, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light, int overlay) {
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        float a = (be.getLevel().getGameTime() + partialTick) * 1.5f;
        ps.mulPose(Axis.YP.rotationDegrees(a));
        ps.mulPose(Axis.XP.rotationDegrees(a * 0.7f));
        ps.translate(0, (float)Math.sin((be.getLevel().getGameTime()+partialTick)*0.08f)*0.1f, 0);
        ps.translate(-0.5, -0.5, -0.5);

        RenderSystem.disableDepthTest();
        VertexConsumer vc = buffer.getBuffer(RENDER_TYPE);
        renderCube(ps, vc, 4, 12, light, overlay, 12f, 0f, 16f, 4f, 0);
        RenderSystem.enableDepthTest();

        ps.popPose();
    }

    private void renderCube(PoseStack ps, VertexConsumer vc, int from, int to, int light, int overlay,
                             float u0, float v0, float u1, float v1, int rot) {
        float f=from/16f, t=to/16f;
        float[] u=rotUV(u0/16,v0/16,u1/16,v1/16,rot);
        Matrix4f m=ps.last().pose();
        quad(m,vc,t,t,f,t,f,f,f,f,f,f,t,f,u,light,overlay,0,0,-1);
        quad(m,vc,f,t,t,f,f,t,t,f,t,t,t,t,u,light,overlay,0,0,1);
        quad(m,vc,t,t,f,t,f,f,t,f,t,t,t,t,u,light,overlay,1,0,0);
        quad(m,vc,f,t,t,f,f,t,f,f,f,f,t,f,u,light,overlay,-1,0,0);
        quad(m,vc,f,t,f,t,t,f,t,t,t,f,t,t,u,light,overlay,0,1,0);
        quad(m,vc,f,f,t,t,f,t,t,f,f,f,f,f,u,light,overlay,0,-1,0);
    }

    private void quad(Matrix4f m, VertexConsumer vc,
                       float x0,float y0,float z0, float x1,float y1,float z1,
                       float x2,float y2,float z2, float x3,float y3,float z3,
                       float u0,float v0, float u1,float v1, float u2,float v2, float u3,float v3,
                       int light, int overlay, float nx, float ny, float nz) {
        vc.addVertex(m,x0,y0,z0).setColor(1f,1f,1f,1f).setUv(u0,v0).setLight(light).setOverlay(overlay).setNormal(nx,ny,nz);
        vc.addVertex(m,x1,y1,z1).setColor(1f,1f,1f,1f).setUv(u1,v1).setLight(light).setOverlay(overlay).setNormal(nx,ny,nz);
        vc.addVertex(m,x2,y2,z2).setColor(1f,1f,1f,1f).setUv(u2,v2).setLight(light).setOverlay(overlay).setNormal(nx,ny,nz);
        vc.addVertex(m,x3,y3,z3).setColor(1f,1f,1f,1f).setUv(u3,v3).setLight(light).setOverlay(overlay).setNormal(nx,ny,nz);
    }

    private void quad(Matrix4f m, VertexConsumer vc,
                       float x0,float y0,float z0, float x1,float y1,float z1,
                       float x2,float y2,float z2, float x3,float y3,float z3,
                       float[] uv, int light, int overlay, float nx, float ny, float nz) {
        quad(m,vc,x0,y0,z0,x1,y1,z1,x2,y2,z2,x3,y3,z3,
             uv[0],uv[1],uv[2],uv[3],uv[4],uv[5],uv[6],uv[7],light,overlay,nx,ny,nz);
    }

    private float[] rotUV(float u0,float v0,float u1,float v1,int rot) {
        float[][] c={{u0,v1},{u0,v0},{u1,v0},{u1,v1}};
        float[][] r=new float[4][2]; int s=rot/90;
        for(int i=0;i<4;i++)r[(i+s)%4]=c[i];
        return new float[]{r[0][0],r[0][1],r[1][0],r[1][1],r[2][0],r[2][1],r[3][0],r[3][1]};
    }
}