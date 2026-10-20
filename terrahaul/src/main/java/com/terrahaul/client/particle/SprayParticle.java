package com.terrahaul.client.particle;

import com.terrahaul.client.wind.WindSystem;
import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Water in the air around falling water: fine spray drifting off a curtain, drops flung from the
 * lip, and the white burst where the water lands.
 * <p>
 * The particles do not collide like vanilla ones (a collision test per particle per tick would cost
 * far more than these short lives are worth); instead each tick reads the one block they are in.
 * A drop that comes down into water is swallowed by it, spray that drifts into rock is gone. The
 * sprites are white and tinted a cold blue-white, and the world's light falls on them like on
 * any particle, so they glitter in the sun and fade into the shade under a cliff.
 */
public class SprayParticle extends TextureSheetParticle {
    public enum Kind {
        // sizes are half widths: about vanilla's splash for spray and drops, so they still show as
        // white water, not single pixels, from twenty blocks away

        /** Fine spray off a curtain: nearly weightless, slowed by the air and carried by the wind. */
        SPRAY(0.07F, 0.14F, 14, 18, 0.05F, 0.92F, 1.0F),
        /** Drops flung from the lip or thrown up by the landing water: fall back in an arc. */
        DROPLET(0.06F, 0.12F, 22, 20, 0.8F, 0.98F, 0.15F),
        /** The burst of white water where a fall lands: big, fast and short-lived. */
        SPLASH(0.12F, 0.26F, 8, 12, 0.6F, 0.95F, 0.25F);

        final float minSize;
        final float maxSize;
        final int minLife;
        final int extraLife;
        final float gravity;
        final float friction;
        final float windResponse;

        Kind(float minSize, float maxSize, int minLife, int extraLife, float gravity, float friction, float windResponse) {
            this.minSize = minSize;
            this.maxSize = maxSize;
            this.minLife = minLife;
            this.extraLife = extraLife;
            this.gravity = gravity;
            this.friction = friction;
            this.windResponse = windResponse;
        }
    }

    private final Kind kind;
    private final ParticleGroup group;
    private final float maxAlpha;
    private final BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();

    public SprayParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, Kind kind, ParticleGroup group) {
        super(level, x, y, z);
        this.kind = kind;
        this.group = group;
        this.hasPhysics = false;
        this.gravity = kind.gravity;
        this.friction = kind.friction;
        this.quadSize = kind.minSize + random.nextFloat() * (kind.maxSize - kind.minSize);
        this.lifetime = kind.minLife + random.nextInt(kind.extraLife + 1);
        this.maxAlpha = kind == Kind.SPRAY ? 0.55F + random.nextFloat() * 0.3F : 0.8F + random.nextFloat() * 0.2F;
        this.alpha = kind == Kind.SPRAY ? 0.0F : maxAlpha;
        float shade = 0.94F + random.nextFloat() * 0.06F;
        setColor(0.9F * shade, 0.95F * shade, shade);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
    }

    /** Uses a specific sprite of the set. */
    public SprayParticle withSprite(TextureAtlasSprite sprite) {
        setSprite(sprite);
        return this;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public Optional<ParticleGroup> getParticleGroup() {
        return Optional.ofNullable(group);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        float t = (float) age / lifetime;
        // spray fades in as it leaves the curtain; everything fades out over the last part of its life
        float in = kind == Kind.SPRAY ? Math.min(1.0F, age / 3.0F) : 1.0F;
        alpha = maxAlpha * in * Math.min(1.0F, (1.0F - t) / 0.4F);
        if (kind == Kind.SPLASH) {
            quadSize *= 1.015F;
        }

        // the wind takes fine spray along; heavy drops hardly notice it
        float wind = WindSystem.total(1.0F) * kind.windResponse;
        xd += (WindSystem.dirX(1.0F) * wind * 0.06 - xd) * 0.05 * kind.windResponse;
        zd += (WindSystem.dirZ(1.0F) * wind * 0.06 - zd) * 0.05 * kind.windResponse;
        yd -= 0.04 * gravity;
        x += xd;
        y += yd;
        z += zd;
        xd *= friction;
        yd *= friction;
        zd *= friction;
        setPos(x, y, z);

        cell.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        BlockState state = level.getBlockState(cell);
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            // swallowed by the water it comes down into
            if (yd < 0.0 && fluid.is(FluidTags.WATER) && y < cell.getY() + fluid.getOwnHeight()) {
                remove();
            }
        } else if (state.blocksMotion()) {
            remove();
        }
    }
}
