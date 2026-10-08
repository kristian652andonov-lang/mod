package com.fantasyweapons.weapons.infernochain;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.Delayed;
import com.fantasyweapons.ability.kit.FieldEffect;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.ability.kit.ProjectileEffect;
import com.fantasyweapons.ability.kit.SweepEffect;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.MeleeContext;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.WeaponForm;
import com.fantasyweapons.world.AreaEffectManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Server-side gameplay for every Infernochain ability and the chainblade's melee behaviour. */
public final class InfernochainAbilities {
    /** Seared lasts this long per application. */
    static final int SEAR_TICKS = 80;
    /** Seared damage per stack per pulse (every half second), as a fraction of weapon damage. */
    static final float SEAR_FRACTION = 0.03f;
    /** Chainblade melee: the lash also catches enemies in this wider arc beyond the normal cleave. */
    static final float WHIP_ARC = 150f;

    private InfernochainAbilities() {
    }

    static boolean chainForm(WeaponDefinition def, WeaponData data) {
        WeaponForm form = def.form(data);
        return form != null && Infernochain.CHAINBLADE.equals(form.id());
    }

    /** Applies Seared stacks scaled to the wielder's current weapon damage. */
    static void sear(ServerPlayer owner, WeaponDefinition def, WeaponData data, LivingEntity target, int stacks) {
        if (!target.isAlive() || stacks <= 0) return;
        float perPulse = ProgressionMath.weaponDamage(def, data) * SEAR_FRACTION;
        StatusService.apply(target, StatusType.SEARED, SEAR_TICKS, stacks, perPulse, owner.getUUID());
    }

    static void sear(AbilityContext ctx, LivingEntity target, int stacks) {
        sear(ctx.player(), ctx.weapon(), ctx.data(), target, stacks);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Melee: chainblade lash + Overheat (passive)
    // ------------------------------------------------------------------------------------------------------------

    public static void onMeleeHit(MeleeContext ctx) {
        ServerPlayer p = ctx.player();
        boolean chain = chainForm(ctx.weapon(), ctx.data());
        if (chain) sear(p, ctx.weapon(), ctx.data(), ctx.target(), 1);
        if (!ctx.primary()) return;
        if (chain) whipArc(ctx);
        overheat(ctx);
    }

    /** The chain lash sweeps a wide arc: enemies outside the normal cleave cone but inside the whip's arc are hit too. */
    private static void whipArc(MeleeContext ctx) {
        ServerPlayer p = ctx.player();
        WeaponForm form = ctx.weapon().form(ctx.data());
        double reach = ctx.weapon().weaponClass().cleaveRange() + (form != null ? form.reachBonus() : 0);
        Vec3 origin = p.position().add(0, p.getBbHeight() * 0.5, 0);
        Vec3 look = p.getLookAngle();
        double inner = Math.cos(Math.toRadians(ctx.weapon().weaponClass().cleaveAngle() / 2));
        for (LivingEntity e : Targeting.inCone(p.serverLevel(), p, origin, look, reach, WHIP_ARC, FWDamage.Kind.MELEE)) {
            if (e == ctx.target() || e instanceof Player) continue;
            Vec3 to = e.getBoundingBox().getCenter().subtract(origin).normalize();
            if (to.dot(look) > inner) continue; // already hit by the cleave
            float d = FWDamage.deal(p, ctx.stack(), e, ctx.damageDealt() * 0.55f, FWDamage.Kind.MELEE, Element.FIRE, 0);
            if (d > 0) sear(p, ctx.weapon(), ctx.data(), e, 1);
        }
    }

    private static void overheat(MeleeContext ctx) {
        AbilityDefinition oh = ctx.weapon().ability(Infernochain.OVERHEAT);
        int lvl = ctx.data().abilityLevel(oh);
        if (lvl <= 0) return;
        ServerPlayer p = ctx.player();
        int stacks = StatusService.stacks(p, StatusType.INFERNO_OVERHEAT);
        if (stacks >= StatusType.INFERNO_OVERHEAT.maxStacks()) {
            // Meltdown: vent all the heat into a blast around the target
            StatusService.remove(p, StatusType.INFERNO_OVERHEAT);
            Vec3 c = ctx.target().getBoundingBox().getCenter();
            float damage = ctx.damageDealt() * (float) (oh.param("meltdown") + oh.param("meltdown_per_level") * (lvl - 1));
            double radius = oh.param("radius");
            List<Integer> hit = Kit.burst(p, ctx.stack(), c, radius, damage, Element.FIRE, FWDamage.FLAG_HEAVY, 0.8, 0.35,
                    e -> sear(p, ctx.weapon(), ctx.data(), e, 2));
            Kit.fx(p.serverLevel(), FxPayload.of(FxIds.INFERNO_MELTDOWN).caster(p.getId()).pos(c).scale((float) radius).entities(hit)
                    .seed(p.level().random.nextLong()).build());
            Kit.sound(p.serverLevel(), c, ModSounds.EXPLOSION.get(), 1.4f, 1.1f);
            return;
        }
        StatusService.apply(p, StatusType.INFERNO_OVERHEAT, (int) Math.round(oh.param("duration") * 20), 1, 0, p.getUUID());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Inferno Lash
    // ------------------------------------------------------------------------------------------------------------

    public static boolean infernoLash(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        boolean chain = chainForm(ctx.weapon(), ctx.data());
        double range = chain ? ctx.scaled("chain_range", "chain_range_per_level") : ctx.param("sword_range");
        double angle = chain ? ctx.param("chain_angle") : ctx.param("sword_angle");
        float damage = ctx.damage() * (chain ? 1f : (float) (1 + ctx.param("sword_bonus")));
        int sear = chain ? (int) ctx.param("sear") : 0;
        Vec3 origin = p.position().add(0, p.getBbHeight() * 0.5, 0);
        Vec3 look = Targeting.flatLook(p);
        long seed = Kit.seed(ctx.level());
        int delay = chain ? 5 : 3;
        Delayed.schedule(ctx, delay, (o, w) -> {
            List<Integer> hit = new ArrayList<>();
            for (LivingEntity e : Targeting.inCone(o.serverLevel(), o, origin, look, range, angle, FWDamage.Kind.ABILITY)) {
                FWDamage.deal(o, w, e, damage, FWDamage.Kind.ABILITY, Element.FIRE, FWDamage.FLAG_HEAVY);
                if (chain) {
                    sear(ctx, e, sear);
                    Kit.knock(e, origin, 0.35, 0.2);
                } else {
                    Kit.knock(e, origin, 1.1, 0.35);
                }
                hit.add(e.getId());
            }
            Kit.sound(o.serverLevel(), origin, chain ? ModSounds.CHAIN_ATTACK.get() : ModSounds.FIRE_WHOOSH.get(), 1.4f, chain ? 0.8f : 1f);
        });
        Kit.fx(ctx.level(), FxPayload.of(FxIds.INFERNO_LASH).caster(p.getId()).pos(origin).dir(look).scale((float) range).power((float) angle)
                .level(chain ? 1 : 0).seed(seed).build());
        Kit.sound(ctx.level(), origin, ModSounds.FIRE_WHOOSH.get(), 1.2f, 0.8f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Hellhook
    // ------------------------------------------------------------------------------------------------------------

    public static boolean hellhook(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double range = ctx.scaled("range", "range_per_level");
        Vec3 start = ctx.eye().add(0, -0.3, 0).add(ctx.look().scale(0.6));
        Vec3 vel = ctx.look().scale(ctx.param("speed"));
        float damage = ctx.damage();
        int sear = (int) ctx.param("sear");
        long seed = Kit.seed(ctx.level());
        ProjectileEffect hook = new ProjectileEffect(ctx, start, vel, 0.6, range, 0, (o, target, proj) -> {
            FWDamage.deal(o, proj.weaponOf(o), target, damage, FWDamage.Kind.ABILITY, Element.FIRE, FWDamage.FLAG_HEAVY);
            sear(ctx, target, sear);
            // drag the victim to the wielder
            Vec3 to = o.position().add(Targeting.flatLook(o).scale(1.6)).subtract(target.position());
            double resist = 1.0 - Math.min(1.0, target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            Vec3 v = to.normalize().scale(Math.min(2.4, to.length() * 0.32) * Math.max(0.3, resist)).add(0, 0.35, 0);
            target.setDeltaMovement(v);
            target.hurtMarked = true;
            Kit.fx(o.serverLevel(), FxPayload.of(FxIds.INFERNO_HOOK_END).caster(o.getId()).pos(target.getBoundingBox().getCenter()).level(1)
                    .entities(target.getId()).seed(seed).build());
            Kit.sound(o.serverLevel(), target.position(), ModSounds.CHAIN_RETRACT.get(), 1.3f, 1f);
            return true;
        }).onEnd((o, pos, hitSomething, proj) -> {
            if (hitSomething) return;
            boolean wall = pos.distanceTo(start) < range - 1 && wallAhead(o.serverLevel(), pos, proj.velocity());
            if (wall) {
                // the hook bit into terrain: haul the wielder after it
                Vec3 to = pos.subtract(o.position());
                Vec3 v = to.normalize().scale(Math.min(2.6, to.length() * 0.28)).add(0, 0.45, 0);
                o.setDeltaMovement(v);
                o.hurtMarked = true;
                o.resetFallDistance();
                Kit.sound(o.serverLevel(), pos, ModSounds.CHAIN_RETRACT.get(), 1.3f, 0.8f);
            }
            Kit.fx(o.serverLevel(), FxPayload.of(FxIds.INFERNO_HOOK_END).caster(o.getId()).pos(pos).level(wall ? 2 : 0).seed(seed).build());
        });
        AreaEffectManager.add(hook);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.INFERNO_HOOK).caster(p.getId()).pos(start).dir(vel).scale((float) range).seed(seed).build());
        Kit.sound(ctx.level(), start, ModSounds.CHAIN_ATTACK.get(), 1.3f, 1.2f);
        return true;
    }

    private static boolean wallAhead(ServerLevel level, Vec3 pos, Vec3 vel) {
        Vec3 ahead = pos.add(vel.normalize().scale(0.6));
        return level.clip(new ClipContext(pos, ahead, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty())).getType() != HitResult.Type.MISS;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Cinder Cyclone (chainblade)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean cinderCyclone(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double radius = ctx.param("radius");
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        int interval = 5;
        int pulses = Math.max(1, duration / interval);
        float perPulse = ctx.damage() / pulses;
        double pull = ctx.param("pull");
        AreaEffectManager.add(new FieldEffect(ctx, p.position(), radius, duration, interval).follow(p, new Vec3(0, 1, 0))
                .onTick((o, w, f, inside) -> {
                    for (LivingEntity e : inside) {
                        if (e.distanceToSqr(o) > radius * radius * 0.36) Kit.pull(e, o.position().add(0, 1, 0), pull);
                    }
                })
                .onPulse((o, w, f, inside) -> {
                    for (LivingEntity e : inside) {
                        FWDamage.deal(o, w, e, perPulse, FWDamage.Kind.ABILITY, Element.FIRE, 0);
                        sear(ctx, e, 1);
                    }
                    Kit.sound(o.serverLevel(), o.position(), ModSounds.FIRE_WHOOSH.get(), 0.9f, 0.8f + 0.1f * (f.age() % 3));
                }));
        Kit.active(ctx, duration);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.INFERNO_CYCLONE).caster(p.getId()).pos(p.position()).scale((float) radius).power(duration)
                .seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), p.position(), ModSounds.CHAIN_ATTACK.get(), 1.5f, 0.7f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Drake's Wrath (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean drakesWrath(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        ServerLevel level = ctx.level();
        double range = ctx.param("range");
        int rise = (int) ctx.param("rise");
        double radius = ctx.scaled("radius", "radius_per_level");
        int burnTicks = (int) Math.round(ctx.param("burn") * 20);
        float total = ctx.damage();
        float dive = (float) (total * ctx.param("dive_fraction"));
        float blast = (float) (total * ctx.param("explosion_fraction"));
        float burn = (float) (total * ctx.param("burn_fraction"));
        // the drake rears up behind and above the wielder, then dives to where they aim
        Vec3 look = Targeting.flatLook(p);
        Vec3 from = p.position().add(look.scale(-2)).add(0, 7, 0);
        Vec3 target = Kit.aimGround(p, range);
        double length = from.distanceTo(target);
        double speed = 1.6;
        int diveTicks = (int) Math.ceil(length / speed);
        long seed = Kit.seed(level);
        Kit.active(ctx, rise + diveTicks + 10);
        Delayed.schedule(ctx, rise, (o, w) -> {
            AreaEffectManager.add(new SweepEffect(ctx, from, target.subtract(from), length, 3.0, speed, (oo, ww, e, sweep) -> {
                FWDamage.deal(oo, ww, e, dive, FWDamage.Kind.ABILITY, Element.FIRE, FWDamage.FLAG_HEAVY);
                sear(ctx, e, 5);
                Kit.knock(e, e.position().subtract(sweep.dir()), 0.8, 0.7);
            }));
            Kit.sound(o.serverLevel(), from, ModSounds.FIRE_WHOOSH.get(), 2.5f, 0.5f);
        });
        Delayed.schedule(ctx, rise + diveTicks, (o, w) -> {
            ServerLevel l = o.serverLevel();
            List<Integer> hit = Kit.falloffBurst(o, w, target.add(0, 1, 0), radius, blast, 0.5f, Element.FIRE, FWDamage.FLAG_HEAVY, 1.4, 0.8,
                    e -> sear(ctx, e, 5));
            Kit.fx(l, FxPayload.of(FxIds.INFERNO_DRAKE_END).caster(o.getId()).pos(target).scale((float) radius).power(burnTicks).point(o.position())
                    .entities(hit).seed(seed).build());
            Kit.sound(l, target, ModSounds.EXPLOSION.get(), 3f, 0.6f);
            Kit.sound(l, target, ModSounds.FIRE_WHOOSH.get(), 2.5f, 0.6f);
            // the scorched path keeps burning, on the ground under the dive (however high up the wielder was)
            Vec3 mid = from.lerp(target, 0.35);
            Vec3 groundFrom = Kit.groundAt(l, new Vec3(mid.x, target.y, mid.z), target);
            int pulses = Math.max(1, burnTicks / 10);
            for (int i = 0; i <= 3; i++) {
                Vec3 at = Kit.groundAt(l, groundFrom.lerp(target, i / 3.0), target);
                AreaEffectManager.add(new FieldEffect(ctx, at, i == 3 ? radius * 0.7 : 3.0, burnTicks, 10).onPulse((oo, ww, f, inside) -> {
                    for (LivingEntity e : inside) {
                        FWDamage.deal(oo, ww, e, burn / pulses / 4f, FWDamage.Kind.ABILITY, Element.FIRE, 0);
                        sear(ctx, e, 1);
                    }
                }));
            }
        });
        Kit.fx(level, FxPayload.of(FxIds.INFERNO_DRAKE).caster(p.getId()).pos(from).point(target).power(rise).level(diveTicks).seed(seed).build());
        Kit.sound(level, p.position(), ModSounds.CHAIN_TRANSFORM.get(), 1.6f, 0.6f);
        Kit.sound(level, from, ModSounds.FIRE_WHOOSH.get(), 2f, 0.4f);
        return true;
    }
}
