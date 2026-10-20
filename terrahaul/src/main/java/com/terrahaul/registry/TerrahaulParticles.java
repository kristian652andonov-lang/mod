package com.terrahaul.registry;

import com.terrahaul.Terrahaul;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Ambient particle types. They are only ever spawned on the client (by the ambient spawner, the
 * water effects or the {@code /particle} command); registering the types on both sides keeps the
 * registries in sync.
 */
public final class TerrahaulParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Terrahaul.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FALLING_LEAF = PARTICLES.register("falling_leaf", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> NEEDLE = PARTICLES.register("needle", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PETAL = PARTICLES.register("petal", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DUST = PARTICLES.register("dust", () -> new SimpleParticleType(false));
    // water effects around falls and rapids
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPRAY = PARTICLES.register("spray", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MIST = PARTICLES.register("mist", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FOAM = PARTICLES.register("foam", () -> new SimpleParticleType(false));

    private TerrahaulParticles() {
    }

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
