package com.fantasyweapons.weapons.frostrend;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.Delayed;
import com.fantasyweapons.ability.kit.FieldEffect;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.ability.kit.SweepEffect;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.MeleeContext;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.progression.ExpTier;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusEffects;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.world.AreaEffectManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Server-side gameplay for every Frostrend ability. */
public final class FrostrendAbilities {
    private FrostrendAbilities() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // Frostbite (passive) and chilling
    // ------------------------------------------------------------------------------------------------------------

    public static void onMeleeHit(MeleeContext ctx) {
        chill(ctx.player(), ctx.weapon(), ctx.data(), ctx.target(), ctx.heavy() ? 2 : 1);
    }

    /**
     * Adds Frostbite stacks. Without the passive this is a weak slow; with it, full stacks freeze the target (bosses
     * are never frozen, only slowed).
     */
    static void chill(ServerPlayer owner, WeaponDefinition def, WeaponData data, LivingEntity target, int stacks) {
        if (!target.isAlive() || StatusService.stacks(target, StatusType.FROZEN) > 0) return;
        AbilityDefinition fb = def.ability(Frostrend.FROSTBITE);
        int lvl = data.abilityLevel(fb);
        float slow = lvl > 0 ? (float) (fb.param("slow_per_stack") + fb.param("slow_per_stack_per_level") * (lvl - 1)) : 0.05f;
        int duration = lvl > 0 ? (int) Math.round(fb.param("duration") * 20) : 60;
        StatusEffects.Instance inst = StatusService.apply(target, StatusType.FROSTBITE, duration, stacks, slow, owner.getUUID());
        if (lvl > 0 && inst.stacks >= StatusType.FROSTBITE.maxStacks() && ExpTier.classify(target) != ExpTier.BOSS) {
            StatusService.remove(target, StatusType.FROSTBITE);
            int freeze = (int) Math.round((fb.param("freeze_duration") + fb.param("freeze_duration_per_level") * (lvl - 1)) * 20);
            freeze(owner, target, freeze);
        }
    }

    static void freeze(ServerPlayer owner, LivingEntity target, int ticks) {
        StatusService.apply(target, StatusType.FROZEN, ticks, 1, 1f, owner.getUUID());
        Kit.fx(owner.serverLevel(), FxPayload.of(FxIds.FROSTREND_FREEZE).caster(owner.getId()).pos(target.position())
                .scale(target.getBbWidth()).power(target.getBbHeight()).seed(owner.level().random.nextLong()).build());
        Kit.sound(owner.serverLevel(), target.position(), ModSounds.ICE_BURST.get(), 0.8f, 1.4f);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Frost Slash
    // ------------------------------------------------------------------------------------------------------------

    public static boolean frostSlash(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double range = ctx.scaled("range", "range_per_level");
        Vec3 start = ctx.eye().subtract(0, 0.4, 0);
        Vec3 dir = ctx.look().normalize();
        Vec3 end = start.add(dir.scale(range));
        HitResult wall = ctx.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation();
        double dist = Math.max(1.0, end.distanceTo(start));
        float damage = ctx.damage();
        int stacks = (int) ctx.param("frost_stacks");
        AreaEffectManager.add(new SweepEffect(ctx, start, dir, dist, ctx.param("width"), ctx.param("speed"), (o, w, e, s) -> {
            FWDamage.deal(o, w, e, damage, FWDamage.Kind.ABILITY, Element.ICE, 0);
            chill(o, ctx.weapon(), ctx.data(), e, stacks);
            e.push(s.dir().x * 0.4, 0.1, s.dir().z * 0.4);
            e.hurtMarked = true;
        }));
        Kit.fx(ctx.level(), FxPayload.of(FxIds.FROSTREND_FROST_SLASH).caster(p.getId()).pos(start).dir(dir).power((float) ctx.param("speed"))
                .scale((float) ctx.param("width")).point(end).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), start, ModSounds.ICE_BURST.get(), 1.1f, 1.3f);
        Kit.sound(ctx.level(), start, ModSounds.WEAPON_SWING_HEAVY.get(), 1f, 1.2f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Ice Spikes
    // ------------------------------------------------------------------------------------------------------------

    public static boolean iceSpikes(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 flat = Targeting.flatLook(p);
        Vec3 start = p.position().add(flat.scale(1.2));
        double length = ctx.scaled("length", "length_per_level");
        float damage = ctx.damage();
        int stacks = (int) ctx.param("frost_stacks");
        double launch = ctx.param("launch");
        AreaEffectManager.add(new SweepEffect(ctx, start, flat, length, ctx.param("width"), ctx.param("speed"), (o, w, e, s) -> {
            FWDamage.deal(o, w, e, damage, FWDamage.Kind.ABILITY, Element.ICE, 0);
            chill(o, ctx.weapon(), ctx.data(), e, stacks);
            e.push(0, launch, 0);
            e.hurtMarked = true;
        }).followGround());
        Kit.fx(ctx.level(), FxPayload.of(FxIds.FROSTREND_ICE_SPIKES).caster(p.getId()).pos(start).dir(flat).scale((float) length)
                .power((float) ctx.param("speed")).level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), start, ModSounds.ICE_BURST.get(), 1.5f, 0.8f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Glacial Domain
    // ------------------------------------------------------------------------------------------------------------

    public static boolean glacialDomain(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        Vec3 center = Kit.feet(p);
        float pulse = (float) (ctx.damage() * ctx.param("pulse_fraction"));
        AreaEffectManager.add(new FieldEffect(ctx, center, radius, duration, 10).onPulse((o, w, f, inside) -> {
            for (LivingEntity e : inside) {
                FWDamage.deal(o, w, e, pulse, FWDamage.Kind.ABILITY, Element.ICE, 0);
                chill(o, ctx.weapon(), ctx.data(), e, 1);
            }
        }));
        Kit.active(ctx, duration);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.FROSTREND_GLACIAL_DOMAIN).caster(p.getId()).pos(center).scale((float) radius).power(duration)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.ICE_BURST.get(), 1.8f, 0.6f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Absolute Zero (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean absoluteZero(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double radius = ctx.scaled("radius", "radius_per_level");
        int freeze = (int) Math.round(ctx.scaled("freeze", "freeze_per_level") * 20);
        Vec3 center = Kit.feet(p);
        float total = ctx.damage();
        float flash = (float) (total * ctx.param("flash_fraction"));
        float shatter = (float) (total * ctx.param("shatter_fraction"));
        List<LivingEntity> victims = Targeting.inRadius(ctx.level(), p, center.add(0, 1, 0), radius, FWDamage.Kind.ABILITY);
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity e : victims) {
            ctx.deal(e, flash, 0);
            if (ExpTier.classify(e) == ExpTier.BOSS) {
                StatusService.apply(e, StatusType.FROSTBITE, freeze, 5, 0.12f, p.getUUID());
            } else {
                StatusService.apply(e, StatusType.FROZEN, freeze + 2, 1, 1f, p.getUUID());
            }
            ids.add(e.getId());
        }
        Delayed.schedule(ctx, freeze, (o, w) -> {
            List<Integer> shattered = new ArrayList<>();
            for (LivingEntity e : victims) {
                if (!e.isAlive()) continue;
                boolean frozen = StatusService.stacks(e, StatusType.FROZEN) > 0;
                StatusService.remove(e, StatusType.FROZEN);
                FWDamage.deal(o, w, e, shatter * (frozen ? 1.25f : 1f), FWDamage.Kind.ABILITY, Element.ICE, FWDamage.FLAG_HEAVY);
                shattered.add(e.getId());
                Kit.fx(o.serverLevel(), FxPayload.of(FxIds.FROSTREND_SHATTER).caster(o.getId()).pos(e.position()).scale(e.getBbWidth())
                        .power(e.getBbHeight()).seed(o.level().random.nextLong()).build());
            }
            Kit.fx(o.serverLevel(), FxPayload.of(FxIds.FROSTREND_ABSOLUTE_ZERO_END).caster(o.getId()).pos(center).scale((float) radius)
                    .entities(shattered).seed(o.level().random.nextLong()).build());
            Kit.sound(o.serverLevel(), center, ModSounds.ICE_BURST.get(), 3f, 0.5f);
            Kit.sound(o.serverLevel(), center, ModSounds.EXPLOSION.get(), 1.5f, 1.4f);
        });
        Kit.active(ctx, freeze);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.FROSTREND_ABSOLUTE_ZERO).caster(p.getId()).pos(center).scale((float) radius).power(freeze)
                .entities(ids).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.ICE_BURST.get(), 3f, 0.4f);
        return true;
    }
}
