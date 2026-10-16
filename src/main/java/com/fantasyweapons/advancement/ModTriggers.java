package com.fantasyweapons.advancement;

import com.fantasyweapons.FantasyWeapons;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The criteria the mod's deeds (advancements) are earned by, beyond vanilla's. */
public final class ModTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> REGISTER = DeferredRegister.create(Registries.TRIGGER_TYPE, FantasyWeapons.MOD_ID);

    /** A legendary weapon's level, kills or boss kills changed (by weapon id). */
    public static final DeferredHolder<CriterionTrigger<?>, WeaponMilestoneTrigger> WEAPON_MILESTONE = REGISTER.register("weapon_milestone", WeaponMilestoneTrigger::new);
    /**
     * An ability was cast (by kind: "active" or "ultimate"; also "apocalyptic" for a power of an ultimate weapon, and
     * "from_afar" for one cast through the targeting map).
     */
    public static final DeferredHolder<CriterionTrigger<?>, NamedTrigger> ABILITY_CAST = REGISTER.register("ability_cast", NamedTrigger::new);
    /** A full armor set is worn (by set id). */
    public static final DeferredHolder<CriterionTrigger<?>, NamedTrigger> FULL_SET = REGISTER.register("full_set", NamedTrigger::new);
    /** A forging was started at the Legendary Crafting Table (by the item's id). */
    public static final DeferredHolder<CriterionTrigger<?>, NamedTrigger> FORGE_STARTED = REGISTER.register("forge_started", NamedTrigger::new);
    /** A weapon was infused with refined cores (by weapon id). */
    public static final DeferredHolder<CriterionTrigger<?>, NamedTrigger> INFUSED = REGISTER.register("infused", NamedTrigger::new);
    /** The Wayfarer's Codex was opened. */
    public static final DeferredHolder<CriterionTrigger<?>, NamedTrigger> CODEX_OPENED = REGISTER.register("codex_opened", NamedTrigger::new);

    private ModTriggers() {
    }
}
