package com.fantasyweapons.magic;

import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.network.Fx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.progression.ExpService;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.status.StatusEffects;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.world.AreaEffectManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the mage's statuses do from tick to tick and blow to blow: healing over time, the plague spreading, a bubble
 * bursting, wards and armor of rime turning damage aside, blessings strengthening blows, curses that forbid healing,
 * Spring Eternal saving its bearer from death once.
 */
public final class MagicStatus {
    /** When each ascended fighter last called down a ray (game time), so a flurry of blows calls one every half second. */
    private static final Map<Integer, Long> lastRay = new java.util.HashMap<>();

    private MagicStatus() {
    }

    @Nullable
    private static ServerPlayer caster(LivingEntity e, StatusEffects.Instance inst) {
        if (inst.source == null || !(e.level() instanceof ServerLevel level)) return null;
        return level.getServer().getPlayerList().getPlayer(inst.source);
    }

    private static ItemStack weapon(@Nullable ServerPlayer caster, StatusEffects.Instance inst) {
        return caster == null || inst.weapon == null ? ItemStack.EMPTY : ExpService.findWeapon(caster, inst.weapon);
    }

    // ------------------------------------------------------------------------------------------------------------
    // ticking
    // ------------------------------------------------------------------------------------------------------------

    /** Every tick, for each of an entity's statuses still running. */
    public static void tick(LivingEntity e, StatusEffects.Instance inst) {
        switch (inst.type) {
            case SPRING -> {
                if (e.tickCount % 20 == 0) {
                    ServerPlayer c = caster(e, inst);
                    if (c != null) Heals.healFlat(c, weapon(c, inst), e, inst.potency);
                    else e.heal(inst.potency);
                }
            }
            case MENDING -> {
                if (e.tickCount % 10 == 0) {
                    ServerPlayer c = caster(e, inst);
                    if (c != null) Heals.healFlat(c, weapon(c, inst), e, inst.potency);
                    else e.heal(inst.potency);
                }
            }
            case PLAGUE -> {
                if (e.tickCount % 20 == 0) spreadPlague(e, inst);
            }
            case BUBBLE -> {
                // struck through: the bubble bursts, washing its bearer with half its strength
                if (e.getAbsorptionAmount() <= 0.01f && inst.duration < inst.maxDuration - 2) {
                    inst.duration = 0;
                    burst(e, inst, inst.extra * 0.5f, true);
                }
            }
            case GLACIAL_WARD -> {
                if (e.getAbsorptionAmount() <= 0.01f && inst.duration < inst.maxDuration - 2) {
                    inst.duration = 1;
                    Fx.near((ServerLevel) e.level(), FxPayload.of(FxIds.MAGE_WARD_SHATTER).caster(e.getId()).pos(e.position()).build());
                    e.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.9f, 1.3f);
                }
            }
            default -> {
            }
        }
    }

    /** Once, as a status runs out (or is ended by {@link #tick}). */
    public static void expired(LivingEntity e, StatusEffects.Instance inst) {
        switch (inst.type) {
            case BUBBLE -> {
                // faded unbroken: what the bubble still held becomes healing
                if (inst.extra > 0) burst(e, inst, Math.min(e.getAbsorptionAmount(), inst.extra), false);
            }
            case BANE -> {
                ServerPlayer c = caster(e, inst);
                if (c != null && e.isAlive()) {
                    FWDamage.deal(c, weapon(c, inst), e, inst.extra * inst.stacks, FWDamage.Kind.ABILITY, Element.VOID, 0);
                    Fx.near((ServerLevel) e.level(), FxPayload.of(FxIds.MAGE_BANE_BLOOM).caster(c.getId()).pos(e.getBoundingBox().getCenter())
                            .level(inst.stacks).entities(e.getId()).build());
                }
            }
            default -> {
            }
        }
    }

    /** A bubble bursting or fading: heals its bearer and is drawn popping. */
    private static void burst(LivingEntity e, StatusEffects.Instance inst, float heal, boolean broken) {
        inst.extra = 0;
        ServerPlayer c = caster(e, inst);
        if (heal > 0) {
            if (c != null) Heals.healFlat(c, ItemStack.EMPTY, e, heal);
            else e.heal(heal);
        }
        Fx.near((ServerLevel) e.level(), FxPayload.of(FxIds.MAGE_BUBBLE_POP).caster(e.getId()).pos(e.position()).level(broken ? 1 : 0).build());
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.PLAYERS, 1.2f, 0.8f);
        e.extinguishFire();
    }

    /** The plague leaps to every enemy of its caster close to the infected. */
    private static void spreadPlague(LivingEntity e, StatusEffects.Instance inst) {
        ServerPlayer c = caster(e, inst);
        if (c == null) return;
        AABB box = e.getBoundingBox().inflate(3);
        List<Integer> caught = new ArrayList<>();
        for (LivingEntity other : e.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (other == e || StatusService.stacks(other, StatusType.PLAGUE) > 0 || !FWDamage.canHarm(c, other, FWDamage.Kind.ABILITY)) continue;
            StatusEffects.Instance i = StatusService.apply(other, StatusType.PLAGUE, Math.max(40, inst.duration), 1, inst.potency, c.getUUID());
            i.weapon = inst.weapon;
            caught.add(other.getId());
        }
        if (!caught.isEmpty()) {
            Fx.near((ServerLevel) e.level(), FxPayload.of(FxIds.MAGE_PLAGUE_SPREAD).caster(e.getId()).pos(e.getBoundingBox().getCenter())
                    .entities(caught).build());
        }
    }

    /** Extra slowness (fraction of speed) from the mage's statuses. */
    public static double slow(StatusEffects effects) {
        double slow = 0;
        if (effects.has(StatusType.SOAKED)) slow = Math.max(slow, 0.2);
        if (effects.has(StatusType.WITHERING)) slow = Math.max(slow, 0.2);
        if (effects.has(StatusType.RUIN)) slow = Math.max(slow, 0.35);
        return slow;
    }

    // ------------------------------------------------------------------------------------------------------------
    // blows
    // ------------------------------------------------------------------------------------------------------------

    /** Damage about to be taken: shatterguard, wards, rime, decay, and the blessings of whoever strikes it. */
    public static void incoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        Entity src = event.getSource().getEntity();
        LivingEntity attacker = src instanceof LivingEntity le && le != target ? le : null;
        StatusEffects te = target.getExistingDataOrNull(ModAttachments.STATUS);
        float mult = 1;
        if (te != null && !te.isEmpty()) {
            if (attacker != null) {
                StatusEffects.Instance guard = te.get(StatusType.SHATTERGUARD);
                if (guard != null && guard.stacks > 0) {
                    // a shard turns the blow aside, then flies at the attacker and freezes it
                    event.setCanceled(true);
                    StatusService.consume(target, StatusType.SHATTERGUARD);
                    StatusService.apply(attacker, StatusType.FROZEN, 25, 1, 0, guard.source);
                    Fx.near((ServerLevel) target.level(), FxPayload.of(FxIds.MAGE_SHARD_BLOCK).caster(target.getId())
                            .pos(target.getBoundingBox().getCenter()).point(attacker.getBoundingBox().getCenter()).entities(attacker.getId()).build());
                    target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.2f, 1.5f);
                    return;
                }
                StatusEffects.Instance ward = te.get(StatusType.GLACIAL_WARD);
                if (ward != null) StatusService.apply(attacker, StatusType.FROSTBITE, 60, 1, 0.12f, ward.source);
            }
            StatusEffects.Instance i;
            if ((i = te.get(StatusType.FROST_WARD)) != null) mult *= 1 - Math.min(0.8f, i.potency);
            if ((i = te.get(StatusType.PERMAFROST)) != null) {
                mult *= 1 - Math.min(0.5f, i.potency);
                if (attacker != null && StatusService.stacks(attacker, StatusType.FROSTBITE) > 0) mult *= 0.9f;
            }
            if ((i = te.get(StatusType.RIME_ARMOR)) != null) mult *= 1 - 0.06f * i.stacks;
            if ((i = te.get(StatusType.DECAY)) != null) mult *= 1 + i.potency;
        }
        if (attacker != null) {
            StatusEffects ae = attacker.getExistingDataOrNull(ModAttachments.STATUS);
            if (ae != null && !ae.isEmpty()) {
                float bonus = 0;
                StatusEffects.Instance i;
                if ((i = ae.get(StatusType.VALOR)) != null) bonus += i.potency;
                if ((i = ae.get(StatusType.RADIANCE)) != null) bonus += i.potency;
                if ((i = ae.get(StatusType.DAWN)) != null) bonus += i.potency;
                if ((i = ae.get(StatusType.CHOIR)) != null) bonus += 0.04f * i.stacks;
                mult *= 1 + bonus;
                StatusEffects.Instance asc = ae.get(StatusType.ASCENDED);
                if (asc != null && !event.getSource().is(FWDamage.ABILITY)) ray(attacker, target, asc);
            }
        }
        if (mult != 1) event.setAmount(event.getAmount() * mult);
    }

    /** An ascended fighter's blow calls a ray of light down on its target (a moment later, as its own hit). */
    private static void ray(LivingEntity attacker, LivingEntity target, StatusEffects.Instance asc) {
        long now = attacker.level().getGameTime();
        Long last = lastRay.get(attacker.getId());
        if (last != null && now - last < 10) return;
        lastRay.put(attacker.getId(), now);
        ServerPlayer c = caster(attacker, asc);
        if (c == null) return;
        UUID weaponId = asc.weapon == null ? new UUID(0, 0) : asc.weapon;
        float damage = asc.potency;
        AreaEffectManager.add(new SpellEffect((ServerLevel) target.level(), c, weaponId, target.position(), 2).onEnd((o, w, s) -> {
            if (!target.isAlive()) return;
            FWDamage.deal(o, w, target, damage, FWDamage.Kind.ABILITY, Element.LIGHT, 0);
            Fx.near((ServerLevel) target.level(), FxPayload.of(FxIds.MAGE_ASCENSION_RAY).caster(o.getId()).pos(target.position())
                    .entities(target.getId()).build());
        }));
        if (lastRay.size() > 512) lastRay.clear();
    }

    /** Healing: none at all under Ruin, half under Decay. */
    public static void onHeal(LivingHealEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide) return;
        StatusEffects effects = e.getExistingDataOrNull(ModAttachments.STATUS);
        if (effects == null || effects.isEmpty()) return;
        if (effects.has(StatusType.RUIN)) event.setCanceled(true);
        else if (effects.has(StatusType.DECAY)) event.setAmount(event.getAmount() * 0.5f);
    }

    /** Spring Eternal: the first death of its bearer is turned away (runs before anything else hears of the death). */
    public static void saveFromDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide) return;
        StatusEffects.Instance spring = StatusService.get(e, StatusType.SPRING);
        if (spring == null) return;
        event.setCanceled(true);
        StatusService.remove(e, StatusType.SPRING);
        e.setHealth(Math.max(1, e.getMaxHealth() * spring.extra));
        e.removeAllEffects();
        e.extinguishFire();
        e.invulnerableTime = 40;
        Fx.near((ServerLevel) e.level(), FxPayload.of(FxIds.MAGE_SPRING_SAVE).caster(e.getId()).pos(e.position()).build());
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    /** Whether an entity carries any of the void mages' curses laid by {@code caster}. */
    public static boolean cursedBy(LivingEntity e, UUID caster) {
        StatusEffects effects = e.getExistingDataOrNull(ModAttachments.STATUS);
        if (effects == null) return false;
        for (StatusEffects.Instance i : effects.all()) if (i.type.curse() && caster.equals(i.source)) return true;
        return false;
    }

    /** A weapon's instance id, for statuses that remember which weapon laid them. */
    @Nullable
    public static UUID weaponId(ItemStack stack) {
        return stack.getItem() instanceof FantasyWeaponItem ? FantasyWeaponItem.data(stack).idOrNil() : null;
    }
}
