package com.fantasyweapons.registry;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.ability.AbilityRuntime;
import com.fantasyweapons.combat.HitTracker;
import com.fantasyweapons.status.StatusEffects;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, FantasyWeapons.MOD_ID);

    /**
     * Per-player ability runtime: charge state, cooldowns, thrown weapon. Cooldowns are persisted (so relogging can't
     * reset them) and the whole state is synced to players tracking the owner (they need it for charge VFX).
     */
    public static final Supplier<AttachmentType<AbilityRuntime>> ABILITY_RUNTIME = REGISTER.register("ability_runtime",
            () -> AttachmentType.builder(AbilityRuntime::new)
                    .serialize(AbilityRuntime.CODEC)
                    .copyOnDeath()
                    .sync(AbilityRuntime.STREAM_CODEC)
                    .build());

    /** Custom status effects (Void Mark, Frostbite, ...). Synced to trackers for HUD/VFX; not saved. */
    public static final Supplier<AttachmentType<StatusEffects>> STATUS = REGISTER.register("status",
            () -> AttachmentType.builder(StatusEffects::new)
                    .sync(StatusEffects.STREAM_CODEC)
                    .build());

    /** Who last hit a mob with which weapon; used for kill credit. Server only, transient. */
    public static final Supplier<AttachmentType<HitTracker>> HIT_TRACKER = REGISTER.register("hit_tracker",
            () -> AttachmentType.builder(HitTracker::new).build());

    private ModAttachments() {
    }
}
