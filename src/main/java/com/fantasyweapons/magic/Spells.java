package com.fantasyweapons.magic;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.status.StatusEffects;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Small pieces every mage spell is built from: where spells leave the focus, finding a target, laying statuses. */
public final class Spells {
    /** What a {@link com.fantasyweapons.network.FxIds#MAGE_BLESS} carries to an ally (its level). */
    public static final int BLESS_WARD = 1, BLESS_SHATTERGUARD = 2, BLESS_BUBBLE = 3, BLESS_VALOR = 4, BLESS_HALO = 5;

    private Spells() {
    }

    /** Where a spell leaves its caster: the staff's head or the book over the palm, a little ahead and to the right. */
    public static Vec3 focus(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        boolean staff = p.getMainHandItem().getItem() instanceof FantasyWeaponItem w
                && w.definition().weaponClass() == com.fantasyweapons.weapon.WeaponClass.STAFF;
        return p.getEyePosition().add(look.scale(0.6)).add(right.scale(0.35)).add(0, staff ? 0.15 : -0.3, 0);
    }

    /** Where a healing or blessing is laid: at the feet of the ally in the crosshair, or on the ground pointed at. */
    public static Vec3 aimAllyOrGround(ServerPlayer p, double range) {
        LivingEntity ally = Allies.crosshair(p, range);
        if (ally != null) {
            Vec3 g = com.fantasyweapons.ability.kit.Kit.groundOrNull(p.serverLevel(), ally.position(), 48);
            if (g != null) return g;
        }
        return com.fantasyweapons.ability.kit.Kit.aimGround(p, range);
    }

    /** The enemy under the crosshair within range; the cast fails without one. */
    public static LivingEntity enemy(AbilityContext ctx, double range) {
        LivingEntity e = Targeting.crosshair(ctx.player(), range, FWDamage.Kind.ABILITY);
        if (e == null) throw AbilityContext.fail("No enemy in sight");
        return e;
    }

    /** Lays a status as this cast's: its caster and the weapon remembered (for healing credit, contagion...). */
    public static StatusEffects.Instance status(AbilityContext ctx, LivingEntity target, StatusType type, int ticks, int stacks, float potency) {
        return status(ctx.player(), ctx.stack(), target, type, ticks, stacks, potency);
    }

    public static StatusEffects.Instance status(ServerPlayer caster, ItemStack weapon, LivingEntity target, StatusType type, int ticks, int stacks,
                                                float potency) {
        StatusEffects.Instance inst = StatusService.apply(target, type, ticks, stacks, potency, caster.getUUID());
        inst.weapon = MagicStatus.weaponId(weapon);
        return inst;
    }

    /** A vanilla effect without the swirl of potion particles (its icon still shows in the inventory and the HUD). */
    public static void effect(LivingEntity e, Holder<MobEffect> effect, int ticks, int amplifier) {
        MobEffectInstance had = e.getEffect(effect);
        if (had != null && (had.getAmplifier() > amplifier || had.getAmplifier() == amplifier && had.getDuration() > ticks)) return;
        e.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, false, true));
    }

    /** Washes away fire, harmful effects and every harmful status. */
    public static void cleanse(LivingEntity e) {
        e.extinguishFire();
        List<Holder<MobEffect>> bad = new ArrayList<>();
        for (MobEffectInstance i : e.getActiveEffects()) if (i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) bad.add(i.getEffect());
        for (Holder<MobEffect> h : bad) e.removeEffect(h);
        StatusEffects effects = e.getExistingDataOrNull(ModAttachments.STATUS);
        if (effects == null) return;
        List<StatusType> harmful = new ArrayList<>();
        for (StatusEffects.Instance i : effects.all()) if (i.type.harmful()) harmful.add(i.type);
        for (StatusType t : harmful) StatusService.remove(e, t);
    }

    /** {@code base + perLevel * (level - 1)} of two ability params, for passives read outside a cast. */
    public static double scaled(com.fantasyweapons.ability.AbilityDefinition a, int level, String base, String perLevel) {
        return a.param(base) + a.param(perLevel) * Math.max(0, level - 1);
    }
}
