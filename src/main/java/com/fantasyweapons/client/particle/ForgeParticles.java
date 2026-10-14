package com.fantasyweapons.client.particle;

import com.fantasyweapons.registry.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * The Legendary Crafting Table's own particles, all glowing (drawn at full light) and tinted in the forge's gold:
 * <ul>
 *   <li>runes: drawn in from where they appear (the totems' crowns) to the work, turning and fading as they go</li>
 *   <li>sparks: flung off by the hammer's strikes, falling and cooling from white to orange</li>
 *   <li>embers: rising slowly off the grid, drifting and flickering out</li>
 *   <li>stars: twinkling as they rise about the work</li>
 *   <li>the flare: a star of light that swells, turns and fades when the work is done</li>
 * </ul>
 */
public final class ForgeParticles {
    private ForgeParticles() {
    }

    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.FORGE_RUNE.get(), sprites -> provider((level, x, y, z, dx, dy, dz) -> new Rune(level, x, y, z, dx, dy, dz, sprites)));
        event.registerSpriteSet(ModParticles.FORGE_SPARK.get(), sprites -> provider((level, x, y, z, dx, dy, dz) -> new Spark(level, x, y, z, dx, dy, dz, sprites)));
        event.registerSpriteSet(ModParticles.FORGE_EMBER.get(), sprites -> provider((level, x, y, z, dx, dy, dz) -> new Ember(level, x, y, z, dx, dy, dz, sprites)));
        event.registerSpriteSet(ModParticles.FORGE_STAR.get(), sprites -> provider((level, x, y, z, dx, dy, dz) -> new Star(level, x, y, z, dx, dy, dz, sprites)));
        event.registerSpriteSet(ModParticles.FORGE_FLARE.get(), sprites -> provider((level, x, y, z, dx, dy, dz) -> new Flare(level, x, y, z, dx, dy, dz, sprites)));
    }

    private interface Factory {
        Particle make(ClientLevel level, double x, double y, double z, double dx, double dy, double dz);
    }

    private static ParticleProvider<SimpleParticleType> provider(Factory factory) {
        return (type, level, x, y, z, dx, dy, dz) -> factory.make(level, x, y, z, dx, dy, dz);
    }

    /** A glowing particle: full light, see-through, fading in and out. */
    private abstract static class Glowing extends TextureSheetParticle {
        protected final SpriteSet sprites;
        protected float baseSize, baseAlpha = 1;

        Glowing(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z);
            this.sprites = sprites;
            this.hasPhysics = false;
        }

        protected void tint(int rgb) {
            setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
        }

        /** 0 to 1 over the particle's life. */
        protected float life(float partialTick) {
            return Mth.clamp((age + partialTick) / lifetime, 0, 1);
        }

        @Override
        public int getLightColor(float partialTick) {
            return 0xF000F0;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /** Drawn in from (x+dx, y+dy, z+dz) to (x, y, z), curling round the line as it comes. */
    static final class Rune extends Glowing {
        private final double tx, ty, tz, sx, sy, sz;
        private final float curl;

        Rune(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x + dx, y + dy, z + dz, sprites);
            tx = x;
            ty = y;
            tz = z;
            sx = x + dx;
            sy = y + dy;
            sz = z + dz;
            lifetime = 26 + random.nextInt(12);
            baseSize = quadSize = 0.07f + random.nextFloat() * 0.04f;
            curl = (random.nextFloat() - 0.5f) * 0.6f;
            pickSprite(sprites);
            tint(random.nextInt(3) == 0 ? 0xD9A8FF : 0xFFD884);
            roll = oRoll = (random.nextFloat() - 0.5f) * 0.6f;
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
            float t = age / (float) lifetime, e = t * t;
            double side = Math.sin(t * Math.PI) * curl;
            x = sx + (tx - sx) * e + side * (tz - sz) * 0.15;
            y = sy + (ty - sy) * e + Math.sin(t * Math.PI) * 0.25;
            z = sz + (tz - sz) * e - side * (tx - sx) * 0.15;
            roll += 0.04f;
            alpha = Math.min(1, t * 5) * Math.min(1, (1 - t) * 4);
            quadSize = baseSize * (1 - 0.5f * t);
        }
    }

    /** Flung out, falling, cooling from white through gold to orange. */
    static final class Spark extends Glowing {
        Spark(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, sprites);
            xd = dx;
            yd = dy;
            zd = dz;
            lifetime = 10 + random.nextInt(10);
            baseSize = quadSize = 0.035f + random.nextFloat() * 0.025f;
            gravity = 0.9f;
            friction = 0.9f;
            hasPhysics = true;
            pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            float t = age / (float) lifetime;
            int c = t < 0.4f ? lerp(0xFFFFF0, 0xFFD25A, t / 0.4f) : lerp(0xFFD25A, 0xFF6A1E, (t - 0.4f) / 0.6f);
            tint(c);
            quadSize = baseSize * (1 - 0.6f * t);
            alpha = Math.min(1, (1 - t) * 3);
        }
    }

    /** Rising slowly, drifting, flickering out. */
    static final class Ember extends Glowing {
        private final float phase;

        Ember(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, sprites);
            xd = dx;
            yd = dy;
            zd = dz;
            lifetime = 30 + random.nextInt(25);
            baseSize = quadSize = 0.035f + random.nextFloat() * 0.03f;
            friction = 0.97f;
            phase = random.nextFloat() * 6.28f;
            tint(random.nextBoolean() ? 0xFF8A2A : 0xFFB848);
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            float t = age / (float) lifetime;
            xd += Math.sin(age * 0.2f + phase) * 0.0015;
            zd += Math.cos(age * 0.17f + phase) * 0.0015;
            setSpriteFromAge(sprites);
            float flicker = 0.75f + 0.25f * Mth.sin(age * 0.9f + phase);
            alpha = flicker * Math.min(1, t * 6) * Math.min(1, (1 - t) * 2.5f);
        }
    }

    /** A pale gold star twinkling as it rises. */
    static final class Star extends Glowing {
        private final float phase;

        Star(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, sprites);
            xd = dx;
            yd = dy;
            zd = dz;
            lifetime = 34 + random.nextInt(14);
            baseSize = quadSize = 0.07f + random.nextFloat() * 0.03f;
            friction = 0.96f;
            phase = random.nextFloat() * 6.28f;
            tint(0xFFF0B8);
            pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            float t = age / (float) lifetime;
            quadSize = baseSize * (0.7f + 0.3f * Mth.sin(age * 0.6f + phase));
            alpha = Math.min(1, t * 6) * Math.min(1, (1 - t) * 3);
        }
    }

    /** A star of light swelling and turning, then fading. */
    static final class Flare extends Glowing {
        private final float spin;

        Flare(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, sprites);
            xd = dx;
            yd = dy;
            zd = dz;
            lifetime = 16 + random.nextInt(10);
            baseSize = 0.12f + random.nextFloat() * 0.12f;
            quadSize = 0.01f;
            friction = 0.88f;
            spin = (random.nextFloat() - 0.5f) * 0.3f;
            tint(random.nextInt(3) == 0 ? 0xFFFFFF : 0xFFE08A);
            pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            oRoll = roll;
            roll += spin;
            float t = age / (float) lifetime;
            quadSize = baseSize * Mth.sin((float) Math.PI * Math.min(1, t * 1.3f));
            alpha = Math.min(1, (1 - t) * 2.5f);
        }
    }

    private static int lerp(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return r << 16 | g << 8 | bl;
    }
}
