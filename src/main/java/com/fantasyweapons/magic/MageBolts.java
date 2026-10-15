package com.fantasyweapons.magic;

import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.MeleeContext;
import com.fantasyweapons.combat.MeleeHandler;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.playerclass.ClassGear;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponClass;
import com.fantasyweapons.weapon.WeaponDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A staff's or spellbook's click: an elemental bolt loosed at the crosshair instead of a swing. A staff's bolt is
 * fast and hits hard; a spellbook's is slower and lighter but curves after its target. Each element leaves its mark
 * - fire sears, ice frostbites, water soaks, nature poisons, light reveals, the void rots - and the weapon's own
 * passive answers every hit as it would a blade's.
 */
public final class MageBolts {
    private static final double RANGE = 36;

    private MageBolts() {
    }

    public static boolean isFocus(ItemStack stack) {
        return stack.getItem() instanceof FantasyWeaponItem w && w.definition().weaponClass().magic();
    }

    /** The click: loosed only once the focus has recovered, like a blade's swing. */
    public static void cast(ServerPlayer p) {
        ItemStack stack = p.getMainHandItem();
        if (!isFocus(stack)) return;
        if (!ClassGear.canUse(p, stack)) {
            ClassGear.refuse(p, stack);
            return;
        }
        float strength = p.getAttackStrengthScale(0.5f);
        if (strength < MeleeHandler.MIN_STRENGTH) return;
        p.resetAttackStrengthTicker();
        FantasyWeaponItem item = (FantasyWeaponItem) stack.getItem();
        WeaponDefinition def = item.definition();
        WeaponData data = FantasyWeaponItem.data(stack);
        boolean staff = def.weaponClass() == WeaponClass.STAFF;
        boolean crit = !MeleeHandler.suppressRandomCrits && p.getRandom().nextFloat() < def.critChance();
        float damage = ProgressionMath.weaponDamage(def, data) * (staff ? 0.85f : 0.7f) * StatusService.outgoingMultiplier(p);
        if (crit) damage *= def.critMultiplier();

        ServerLevel level = p.serverLevel();
        Vec3 start = Spells.focus(p);
        Vec3 aim = Targeting.aimPoint(p, RANGE);
        Vec3 dir = aim.subtract(start);
        dir = dir.lengthSqr() < 1e-4 ? p.getLookAngle() : dir.normalize();
        double speed = staff ? 1.7 : 1.15;
        LivingEntity homing = staff ? null : Targeting.crosshair(p, RANGE, FWDamage.Kind.ABILITY);
        long seed = Kit.seed(level);
        float dmg = damage;
        int flags = crit ? FWDamage.FLAG_CRIT : 0;
        int[] state = {0};
        Vec3[] vel = {dir.scale(speed)};
        new SpellEffect(level, p, data.idOrNil(), start, (int) Math.ceil(RANGE / speed) + 2).onTick((o, w, s) -> {
            if (homing != null && homing.isAlive()) {
                Vec3 want = homing.getBoundingBox().getCenter().subtract(s.pos()).normalize().scale(speed);
                vel[0] = vel[0].add(want.subtract(vel[0]).scale(0.18));
            }
            Vec3 from = s.pos(), to = from.add(vel[0]);
            HitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, o));
            if (wall.getType() != HitResult.Type.MISS) to = wall.getLocation();
            // the water staff's Undertow: its bolts wash over allies on the way, healing them
            if (undertow(def, data) > 0) {
                for (LivingEntity ally : Allies.inRadius(o, from.lerp(to, 0.5), from.distanceTo(to) * 0.5 + 0.6)) {
                    if (ally == o || !s.once(ally.getId())) continue;
                    Heals.heal(o, w, ally, (float) undertow(def, data), Heals.power(o, def, data, 1));
                }
            }
            for (LivingEntity e : Targeting.alongPath(level, o, from, to, staff ? 0.45 : 0.4, FWDamage.Kind.MELEE)) {
                if (!s.once(e.getId())) continue;
                hit(o, w, def, data, e, dmg, flags, crit);
                s.move(Targeting.closestPoint(e.getBoundingBox(), from));
                state[0] = 1;
                s.finish();
                return;
            }
            s.move(to);
            if (wall.getType() != HitResult.Type.MISS) s.finish();
        }).onEnd((o, w, s) -> {
            Kit.fx(level, FxPayload.of(FxIds.MAGE_BOLT_HIT).caster(o.getId()).pos(s.pos()).level(def.element().ordinal()).scale(staff ? 1 : 0)
                    .power(state[0]).seed(seed).build());
        }).start();
        Kit.fx(level, FxPayload.of(FxIds.MAGE_BOLT).caster(p.getId()).pos(start).dir(vel[0]).power((float) RANGE).level(def.element().ordinal())
                .scale(staff ? 1 : 0).seed(seed).entities(homing == null ? new int[0] : new int[]{homing.getId()}).build());
        Kit.sound(level, start, ModSounds.FIRE_WHOOSH.get(), 0.5f, staff ? 1.5f : 1.8f);
        p.causeFoodExhaustion(0.05f);
    }

    /** A bolt striking an enemy: its damage, its element's mark, and the weapon's own passive. */
    private static void hit(ServerPlayer o, ItemStack w, WeaponDefinition def, WeaponData data, LivingEntity e, float damage, int flags, boolean crit) {
        float dealt = FWDamage.deal(o, w, e, damage, FWDamage.Kind.MELEE, def.element(), flags);
        Kit.knock(e, o.position(), 0.25, 0.05);
        float wd = ProgressionMath.weaponDamage(def, data);
        switch (def.element()) {
            case FIRE -> Spells.status(o, w, e, StatusType.SEARED, 60, 1, wd * 0.03f);
            case ICE -> Spells.status(o, w, e, StatusType.FROSTBITE, 60, 1, 0.1f);
            case WATER -> Spells.status(o, w, e, StatusType.SOAKED, 50, 1, 0);
            case NATURE -> Spells.status(o, w, e, StatusType.NATURE_POISON, 60, 1, wd * 0.02f);
            case LIGHT -> {
                Spells.effect(e, MobEffects.GLOWING, 60, 0);
                if (e.isInvertedHealAndHarm()) FWDamage.deal(o, w, e, damage * 0.4f, FWDamage.Kind.ABILITY, Element.LIGHT, 0);
            }
            case VOID -> Spells.status(o, w, e, StatusType.ROT, 60, 1, wd * 0.02f);
            default -> {
            }
        }
        if (def.meleeHook() != null && (dealt > 0 || e.isAlive())) def.meleeHook().onHit(new MeleeContext(o, w, def, data, e, dealt, false, crit, true));
        o.setLastHurtMob(e);
        if (e.isAlive()) e.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.4f);
    }

    /** The share of an ally's health the water staff's bolts restore (0 without Undertow). */
    private static double undertow(WeaponDefinition def, WeaponData data) {
        var a = def.ability("undertow");
        int lvl = a == null ? 0 : data.abilityLevel(a);
        return lvl <= 0 ? 0 : Spells.scaled(a, lvl, "ally_heal", "ally_heal_per_level");
    }
}
