package com.terrahaul.client.particle;

import com.terrahaul.registry.TerrahaulParticles;
import net.minecraft.client.particle.SpriteSet;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Registers the particle providers and keeps the sprite sets so the ambient spawner and the water
 * effects can create particles directly with a chosen sprite and tint.
 */
public final class TerrahaulParticleProviders {
    static SpriteSet leafSprites;
    static SpriteSet needleSprites;
    static SpriteSet petalSprites;
    static SpriteSet dustSprites;
    static SpriteSet spraySprites;
    static SpriteSet mistSprites;
    static SpriteSet foamSprites;

    private TerrahaulParticleProviders() {
    }

    /** Whether the water effect sprites are loaded (they are once the particle atlas has been built). */
    public static boolean waterReady() {
        return spraySprites != null && mistSprites != null && foamSprites != null;
    }

    public static SpriteSet spraySprites() {
        return spraySprites;
    }

    public static SpriteSet mistSprites() {
        return mistSprites;
    }

    public static SpriteSet foamSprites() {
        return foamSprites;
    }

    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(TerrahaulParticles.FALLING_LEAF.get(), sprites -> {
            leafSprites = sprites;
            return (type, level, x, y, z, dx, dy, dz) -> {
                TumblingParticle particle = new TumblingParticle(level, x, y, z, TumblingParticle.Kind.LEAF);
                particle.pickSprite(sprites);
                particle.setColor(0.42F, 0.62F, 0.22F);
                return particle;
            };
        });
        event.registerSpriteSet(TerrahaulParticles.NEEDLE.get(), sprites -> {
            needleSprites = sprites;
            return (type, level, x, y, z, dx, dy, dz) -> {
                TumblingParticle particle = new TumblingParticle(level, x, y, z, TumblingParticle.Kind.NEEDLE);
                particle.pickSprite(sprites);
                particle.setColor(0.38F, 0.6F, 0.38F);
                return particle;
            };
        });
        event.registerSpriteSet(TerrahaulParticles.PETAL.get(), sprites -> {
            petalSprites = sprites;
            return (type, level, x, y, z, dx, dy, dz) -> {
                TumblingParticle particle = new TumblingParticle(level, x, y, z, TumblingParticle.Kind.PETAL);
                particle.pickSprite(sprites);
                return particle;
            };
        });
        event.registerSpriteSet(TerrahaulParticles.DUST.get(), sprites -> {
            dustSprites = sprites;
            return (type, level, x, y, z, dx, dy, dz) -> {
                DustMoteParticle particle = new DustMoteParticle(level, x, y, z);
                particle.pickSprite(sprites);
                return particle;
            };
        });
        // the water effects create their particles directly; these providers serve /particle
        event.registerSpriteSet(TerrahaulParticles.SPRAY.get(), sprites -> {
            spraySprites = sprites;
            return (type, level, x, y, z, dx, dy, dz) -> {
                SprayParticle particle = new SprayParticle(level, x, y, z, dx, dy, dz, SprayParticle.Kind.SPRAY, null);
                particle.pickSprite(sprites);
                return particle;
            };
        });
        event.registerSpriteSet(TerrahaulParticles.MIST.get(), sprites -> {
            mistSprites = sprites;
            return (type, level, x, y, z, dx, dy, dz) -> {
                MistParticle particle = new MistParticle(level, x, y, z, dx, dy, dz, 0.5F, 1.6F, 100, 0.4F, 0.012F, false, null);
                particle.pickSprite(sprites);
                return particle;
            };
        });
        event.registerSpriteSet(TerrahaulParticles.FOAM.get(), sprites -> {
            foamSprites = sprites;
            return (type, level, x, y, z, dx, dy, dz) -> {
                FoamParticle particle = new FoamParticle(level, x, y, z, dx, dz, 0.3F, 120, null);
                particle.pickSprite(sprites);
                return particle;
            };
        });
    }
}
