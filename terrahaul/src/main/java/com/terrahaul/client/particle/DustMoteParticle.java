package com.terrahaul.client.particle;

import com.terrahaul.client.wind.WindSystem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * A faint wisp of dust or sand carried along the ground by the wind. It is lifted and dropped by
 * turbulence, fades in and out, and is tinted with the colour of the ground it was blown from.
 */
public class DustMoteParticle extends TextureSheetParticle {
    private final float maxAlpha;
    private final float turbulence;
    private final float phase;

    public DustMoteParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.lifetime = 40 + random.nextInt(50);
        this.quadSize = 0.06F + random.nextFloat() * 0.08F;
        this.maxAlpha = 0.35F + random.nextFloat() * 0.3F;
        this.turbulence = 0.01F + random.nextFloat() * 0.015F;
        this.phase = random.nextFloat() * Mth.TWO_PI;
        this.alpha = 0.0F;
        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        float t = (float) age / lifetime;
        alpha = maxAlpha * Math.min(1.0F, Math.min(t * 5.0F, (1.0F - t) * 3.0F));
        float wind = WindSystem.total(1.0F);
        double speed = 0.08 + wind * 0.09;
        xd += (WindSystem.dirX(1.0F) * speed - xd) * 0.2;
        zd += (WindSystem.dirZ(1.0F) * speed - zd) * 0.2;
        yd = Mth.sin(age * 0.21F + phase) * turbulence + 0.004;
        x += xd;
        y += yd;
        z += zd;
        setPos(x, y, z);
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            // blown into a wall or a dune: dissolve quickly
            lifetime = Math.min(lifetime, age + 6);
        }
    }
}
