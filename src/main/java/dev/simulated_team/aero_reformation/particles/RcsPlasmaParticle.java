package dev.simulated_team.aero_reformation.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import org.jetbrains.annotations.Nullable;

/**
 * RCS electric thruster plasma particle.
 * White → light blue glow that grows, shrinks, then vapourizes.
 */
public class RcsPlasmaParticle extends TextureSheetParticle {

    private static final int FRAMES = 9; // white_plasma + plasma1-8

    private final SpriteSet sprites;

    protected RcsPlasmaParticle(ClientLevel level, double x, double y, double z,
                                double vx, double vy, double vz,
                                SpriteSet sprites, float speed, float spread) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;

        this.quadSize = 0.12f + random.nextFloat() * 0.01f;
        this.lifetime = 15 + random.nextInt(6);
        this.gravity = 0.0f;
        this.friction = 0.97f;
        this.hasPhysics = false;

        this.xd = vx;
        this.yd = vy;
        this.zd = vz;

        // Remove near-zero velocity particles
        if (vx*vx + vy*vy + vz*vz < 0.005) this.remove();

        setColor(1.0f, 1.0f, 1.0f);
        this.alpha = 1.0f;
        setSprite(sprites.get(0, FRAMES));
    }

    @Override
    public void tick() {
        super.tick();
        float age = (float) this.age / this.lifetime;

        // Size: fast grow (first 25%), then gradual shrink
        float maxSize = 0.20f;
        if (age < 0.25f) {
            float t = age / 0.25f;
            quadSize = 0.12f + (maxSize - 0.12f) * t;
        } else {
            float t = (age - 0.25f) / 0.75f;
            quadSize = maxSize - (maxSize - 0.15f) * t;
        }

        // Color: white → light blue-pink
        float colorAge = Math.min(age / 0.5f, 1.0f);
        rCol = 1.0f - colorAge * 0.22f;
        gCol = 1.0f - colorAge * 0.18f;
        bCol = 1.0f;

        // Alpha: solid in first half, then gradual vaporize
        if (age > 0.5f) {
            float fade = (age - 0.5f) / 0.5f;
            alpha = 1.0f - fade * 0.85f;
        }

        // Keep sprite on first frame; color comes from vertex color
        setSprite(sprites.get(0, FRAMES));
    }

    @Override
    public void setSprite(net.minecraft.client.renderer.texture.TextureAtlasSprite pSprite) {
        super.setSprite(pSprite);
        try {
            java.lang.reflect.Field f = TextureSheetParticle.class.getDeclaredField("setSprite");
            f.setAccessible(true);
            f.setBoolean(this, false);
        } catch (Exception ignored) {}
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_LIT;
    }

    public record Factory(SpriteSet sprites) implements ParticleProvider<RcsParticleData> {
        @Override
        public @Nullable Particle createParticle(RcsParticleData data, ClientLevel level,
                                                  double x, double y, double z,
                                                  double vx, double vy, double vz) {
            return new RcsPlasmaParticle(level, x, y, z, vx, vy, vz,
                    sprites, data.speed(), data.spread());
        }
    }
}
