package com.terrahaul.client.particle;

import com.terrahaul.registry.TerrahaulParticles;
import net.minecraft.client.particle.SpriteSet;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Registers the particle providers and keeps the sprite sets so the ambient spawner can create
 * particles directly with a chosen sprite and tint.
 */
public final class TerrahaulParticleProviders {
    static SpriteSet leafSprites;
    static SpriteSet needleSprites;
    static SpriteSet petalSprites;
    static SpriteSet dustSprites;

    private TerrahaulParticleProviders() {
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
    }
}
