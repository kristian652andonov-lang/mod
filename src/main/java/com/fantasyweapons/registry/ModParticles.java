package com.fantasyweapons.registry;

import com.fantasyweapons.FantasyWeapons;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Legendary Crafting Table's particles: runes drawn from its totems into the work, sparks off the hammer, embers off
 * the grid, stars circling the work, and the flare when it is done.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> REGISTER = DeferredRegister.create(Registries.PARTICLE_TYPE, FantasyWeapons.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FORGE_RUNE = REGISTER.register("forge_rune", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FORGE_SPARK = REGISTER.register("forge_spark", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FORGE_EMBER = REGISTER.register("forge_ember", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FORGE_STAR = REGISTER.register("forge_star", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FORGE_FLARE = REGISTER.register("forge_flare", () -> new SimpleParticleType(false));

    private ModParticles() {
    }
}
