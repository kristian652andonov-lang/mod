package com.fantasyweapons.status;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.weapon.Element;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.UUID;

/**
 * Server logic for the custom status system: application, stacking, ticking (damage over time, crowd control),
 * attribute modifiers, and damage amplification queries used by {@link FWDamage}.
 */
public final class StatusService {
    private static final ResourceLocation SLOW_ID = FantasyWeapons.id("status_slow");
    private static final ResourceLocation BERSERK_SPEED_ID = FantasyWeapons.id("status_berserk_speed");
    private static final ResourceLocation BERSERK_KB_ID = FantasyWeapons.id("status_berserk_kb");
    private static final ResourceLocation OVERHEAT_SPEED_ID = FantasyWeapons.id("status_overheat_speed");

    private StatusService() {
    }

    /**
     * Applies or refreshes a status. Duration is refreshed to the max of old/new, stacks add up to the type's cap,
     * potency takes the stronger value.
     */
    public static StatusEffects.Instance apply(LivingEntity target, StatusType type, int duration, int addStacks, float potency, @Nullable UUID source) {
        StatusEffects effects = target.getData(ModAttachments.STATUS);
        StatusEffects.Instance inst = effects.get(type);
        if (inst == null) {
            inst = new StatusEffects.Instance(type, Math.min(type.maxStacks(), Math.max(1, addStacks)), duration, potency);
            effects.put(inst);
        } else {
            inst.stacks = Math.min(type.maxStacks(), inst.stacks + addStacks);
            inst.duration = Math.max(inst.duration, duration);
            inst.maxDuration = Math.max(inst.maxDuration, inst.duration);
            inst.potency = Math.max(inst.potency, potency);
        }
        if (source != null) inst.source = source;
        onChanged(target, effects);
        return inst;
    }

    public static void remove(LivingEntity target, StatusType type) {
        StatusEffects effects = target.getExistingDataOrNull(ModAttachments.STATUS);
        if (effects != null && effects.remove(type) != null) onChanged(target, effects);
    }

    public static int stacks(LivingEntity target, StatusType type) {
        StatusEffects effects = target.getExistingDataOrNull(ModAttachments.STATUS);
        return effects == null ? 0 : effects.stacks(type);
    }

    @Nullable
    public static StatusEffects.Instance get(LivingEntity target, StatusType type) {
        StatusEffects effects = target.getExistingDataOrNull(ModAttachments.STATUS);
        return effects == null ? null : effects.get(type);
    }

    /** Damage multiplier for damage of {@code element} taken by {@code target}. */
    public static float incomingMultiplier(LivingEntity target, @Nullable Element element) {
        StatusEffects effects = target.getExistingDataOrNull(ModAttachments.STATUS);
        if (effects == null || effects.isEmpty()) return 1f;
        float mult = 1f;
        StatusEffects.Instance mark = effects.get(StatusType.VOID_MARK);
        if (mark != null && element == Element.VOID) mult += mark.potency * mark.stacks;
        if (effects.has(StatusType.FROZEN)) mult += 0.15f;
        StatusEffects.Instance light = effects.get(StatusType.ECLIPSE_LIGHT);
        StatusEffects.Instance dark = effects.get(StatusType.ECLIPSE_DARKNESS);
        if (light != null && dark != null) mult += 0.25f; // both marks: eclipse resonance
        if (effects.has(StatusType.GRAVITY_BOUND) && element == Element.COSMIC) mult += 0.2f;
        if (effects.has(StatusType.SOLAR_BURN) && element == Element.SOLAR) mult += 0.15f;
        return mult;
    }

    /** Multiplier for damage dealt by {@code attacker}. */
    public static float outgoingMultiplier(LivingEntity attacker) {
        StatusEffects effects = attacker.getExistingDataOrNull(ModAttachments.STATUS);
        if (effects == null || effects.isEmpty()) return 1f;
        float mult = 1f;
        StatusEffects.Instance berserk = effects.get(StatusType.BERSERKER);
        if (berserk != null) mult += berserk.potency;
        StatusEffects.Instance heat = effects.get(StatusType.INFERNO_OVERHEAT);
        if (heat != null) mult += 0.06f * heat.stacks;
        return mult;
    }

    public static float lifestealMultiplier(LivingEntity attacker) {
        return stacks(attacker, StatusType.BERSERKER) > 0 ? 2.5f : 1f;
    }

    /** Called every server tick for living entities carrying the status attachment. */
    public static void tick(LivingEntity entity) {
        StatusEffects effects = entity.getExistingDataOrNull(ModAttachments.STATUS);
        if (effects == null || effects.isEmpty()) return;
        boolean changed = false;
        boolean pulse = entity.tickCount % 10 == 0;
        Iterator<StatusEffects.Instance> it = effects.all().iterator();
        while (it.hasNext()) {
            StatusEffects.Instance inst = it.next();
            if (--inst.duration <= 0) {
                it.remove();
                changed = true;
                continue;
            }
            switch (inst.type) {
                case SOLAR_BURN, NATURE_POISON, SOUL_DRAIN -> {
                    if (pulse) dot(entity, inst);
                }
                case FROZEN, ROOTED -> entity.setDeltaMovement(0, Math.min(0, entity.getDeltaMovement().y), 0);
                case GRAVITY_BOUND -> {
                    Vec3 to = new Vec3(inst.px, inst.py, inst.pz).subtract(entity.position());
                    double dist = to.length();
                    if (dist > 0.6) {
                        Vec3 pull = to.normalize().scale(Math.min(inst.potency, dist * 0.25));
                        entity.setDeltaMovement(entity.getDeltaMovement().scale(0.6).add(pull));
                        entity.hurtMarked = true;
                    }
                }
                default -> {
                }
            }
        }
        if (changed) onChanged(entity, effects);
        else updateAttributes(entity, effects);
    }

    private static void dot(LivingEntity entity, StatusEffects.Instance inst) {
        if (!(entity.level() instanceof ServerLevel level) || inst.source == null) return;
        ServerPlayer source = level.getServer().getPlayerList().getPlayer(inst.source);
        if (source == null) return;
        float amount = inst.potency * inst.stacks;
        float dealt = FWDamage.deal(source, ItemStack.EMPTY, entity, amount, FWDamage.Kind.DOT, null, FWDamage.FLAG_DOT);
        // keep the weapon's kill credit alive while its damage-over-time ticks
        com.fantasyweapons.combat.HitTracker tracker = entity.getExistingDataOrNull(ModAttachments.HIT_TRACKER);
        if (dealt > 0 && tracker != null && source.getUUID().equals(tracker.player)) tracker.gameTime = level.getServer().overworld().getGameTime();
        if (inst.type == StatusType.SOUL_DRAIN) FWDamage.lifesteal(source, dealt, 0.5f);
    }

    private static void onChanged(LivingEntity entity, StatusEffects effects) {
        updateAttributes(entity, effects);
        if (!entity.level().isClientSide) entity.syncData(ModAttachments.STATUS);
    }

    private static void updateAttributes(LivingEntity entity, StatusEffects effects) {
        double slow = 0;
        if (effects.has(StatusType.FROZEN) || effects.has(StatusType.ROOTED)) {
            slow = 1.0;
        } else {
            StatusEffects.Instance frost = effects.get(StatusType.FROSTBITE);
            if (frost != null) slow = Math.max(slow, frost.potency * frost.stacks);
            StatusEffects.Instance poison = effects.get(StatusType.NATURE_POISON);
            if (poison != null) slow = Math.max(slow, 0.25);
        }
        setModifier(entity, Attributes.MOVEMENT_SPEED, SLOW_ID, slow > 0 ? -Math.min(1.0, slow) : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        StatusEffects.Instance berserk = effects.get(StatusType.BERSERKER);
        setModifier(entity, Attributes.ATTACK_SPEED, BERSERK_SPEED_ID, berserk != null ? 0.5 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(entity, Attributes.KNOCKBACK_RESISTANCE, BERSERK_KB_ID, berserk != null ? 1.0 : 0, AttributeModifier.Operation.ADD_VALUE);

        StatusEffects.Instance heat = effects.get(StatusType.INFERNO_OVERHEAT);
        setModifier(entity, Attributes.ATTACK_SPEED, OVERHEAT_SPEED_ID, heat != null ? 0.05 * heat.stacks : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void setModifier(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = entity.getAttribute(attribute);
        if (inst == null) return;
        if (amount == 0) {
            if (inst.getModifier(id) != null) inst.removeModifier(id);
            return;
        }
        AttributeModifier existing = inst.getModifier(id);
        if (existing == null || existing.amount() != amount) {
            inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
        }
    }
}
