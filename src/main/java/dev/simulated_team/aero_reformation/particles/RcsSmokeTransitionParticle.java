package dev.simulated_team.aero_reformation.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Red-to-gray smoke transition particle.
 * Behavior matches vanilla CampfireSmokeParticle (random drift, long life, fade at end)
 * but animates through frames from red-tinted to original gray smoke.
 */
public class RcsSmokeTransitionParticle extends TextureSheetParticle {

    private static final int FADE_START = 30; // ticks before end to start fading
    private static final int FRAMES = 22;

    private final SpriteSet sprites;

    protected RcsSmokeTransitionParticle(ClientLevel level, double x, double y, double z,
                                          double vx, double vy, double vz,
                                          SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;

        this.scale(1.5F);
        this.setSize(0.25F, 0.25F);
        this.lifetime = 60 + random.nextInt(40);
        this.gravity = 3.0E-6F; // vanilla upward drift
        this.hasPhysics = false;

        this.xd = vx;
        this.yd = vy + (double)(random.nextFloat() / 500.0F); // vanilla tiny upward boost
        this.zd = vz;

        setColor(1.0f, 0.5f, 0.1f); // orange tint for early frames
        this.alpha = 0.2F; // starts transparent, grows opaque
        setSprite(sprites.get(0, 21));
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ < this.lifetime && !(this.alpha <= 0.0F)) {
            // Enhanced random horizontal drift (2x vanilla for more spread)
            this.xd = this.xd + (double)(this.random.nextFloat() / 2500.0F * (float)(this.random.nextBoolean() ? 1 : -1));
            this.zd = this.zd + (double)(this.random.nextFloat() / 2500.0F * (float)(this.random.nextBoolean() ? 1 : -1));
            this.yd = this.yd - (double)this.gravity;
            this.move(this.xd, this.yd, this.zd);
            this.xd *= 0.98;
            this.yd *= 0.98;
            this.zd *= 0.98;

            // Animate through red→gray frames over lifetime
            int frame = Mth.clamp((int)((float)this.age / this.lifetime * 21.0f), 0, 21);
            setSprite(sprites.get(frame, 21));

            // Color: orange at start → neutral white at end
            float colorAge = (float)this.age / this.lifetime;
            rCol = 1.0f;
            gCol = 0.5f + colorAge * 0.5f;
            bCol = 0.1f + colorAge * 0.9f;

            // Alpha: transparent → opaque → fade at end
            if (this.age < this.lifetime - FADE_START) {
                float growAge = (float)this.age / (this.lifetime - FADE_START);
                this.alpha = 0.2F + growAge * 0.7F; // 0.2 → 0.9
            } else if (this.alpha > 0.01F) {
                this.alpha -= 0.015F; // vanilla fade-out
            }
            if (this.age >= this.lifetime - FADE_START && this.alpha > 0.01F) {
                this.alpha -= 0.015F;
            }
        } else {
            this.remove();
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Factory(SpriteSet sprites) implements ParticleProvider<RcsSmokeTransitionData> {
        @Override
        public @Nullable Particle createParticle(RcsSmokeTransitionData data, ClientLevel level,
                                                  double x, double y, double z,
                                                  double vx, double vy, double vz) {
            return new RcsSmokeTransitionParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
