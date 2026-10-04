package com.fantasyweapons.weapons.starforge;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.Delayed;
import com.fantasyweapons.ability.kit.FieldEffect;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.MeleeContext;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusEffects;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.world.AreaEffectManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Server-side gameplay for every Starforge ability. */
public final class StarforgeAbilities {
    private StarforgeAbilities() {
    }

    /** Binds an entity to a gravity point for some ticks. */
    static void bind(ServerPlayer owner, LivingEntity e, Vec3 point, int ticks, float strength) {
        if (!e.isAlive()) return;
        StatusEffects.Instance g = StatusService.apply(e, StatusType.GRAVITY_BOUND, ticks, 1, strength, owner.getUUID());
        g.px = point.x;
        g.py = point.y;
        g.pz = point.z;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Gravity Well (passive)
    // ------------------------------------------------------------------------------------------------------------

    public static void onMeleeHit(MeleeContext ctx) {
        if (!ctx.heavy() || !ctx.primary()) return;
        AbilityDefinition gw = ctx.weapon().ability(Starforge.GRAVITY_WELL);
        int lvl = ctx.data().abilityLevel(gw);
        if (lvl <= 0) return;
        ServerPlayer p = ctx.player();
        Vec3 point = ctx.target().position();
        int ticks = (int) Math.round((gw.param("duration") + gw.param("duration_per_level") * (lvl - 1)) * 20);
        for (LivingEntity e : Targeting.inRadius(p.serverLevel(), p, point.add(0, 1, 0), gw.param("radius"), FWDamage.Kind.ABILITY)) {
            bind(p, e, point, ticks, 0.12f);
        }
        Kit.fx(p.serverLevel(), FxPayload.of(FxIds.STARFORGE_WELL).caster(p.getId()).pos(point).scale((float) gw.param("radius")).power(ticks)
                .seed(p.level().random.nextLong()).build());
        Kit.sound(p.serverLevel(), point, ModSounds.GRAVITY.get(), 0.9f, 1.3f);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Gravity Slam
    // ------------------------------------------------------------------------------------------------------------

    public static boolean gravitySlam(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double radius = ctx.scaled("radius", "radius_per_level");
        Vec3 c = p.position();
        for (LivingEntity e : Targeting.inRadius(ctx.level(), p, c.add(0, 1, 0), radius * 1.4, FWDamage.Kind.ABILITY)) Kit.pull(e, c, ctx.param("pull"));
        float damage = ctx.damage();
        Delayed.schedule(ctx, 4, (o, w) -> {
            List<Integer> hit = Kit.falloffBurst(o, w, c.add(0, 1, 0), radius, damage, 0.6f, Element.COSMIC, FWDamage.FLAG_HEAVY, 0.2, 0.55,
                    e -> bind(o, e, c, 30, 0.1f));
            Kit.fx(o.serverLevel(), FxPayload.of(FxIds.STARFORGE_SLAM_IMPACT).caster(o.getId()).pos(c).scale((float) radius).entities(hit)
                    .seed(o.level().random.nextLong()).build());
            Kit.sound(o.serverLevel(), c, ModSounds.HEAVY_IMPACT.get(), 1.8f, 0.6f);
            Kit.sound(o.serverLevel(), c, ModSounds.GRAVITY.get(), 1.4f, 0.8f);
        });
        Kit.fx(ctx.level(), FxPayload.of(FxIds.STARFORGE_SLAM).caster(p.getId()).pos(c).scale((float) radius).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), c, ModSounds.GRAVITY.get(), 1.4f, 1.4f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Meteor Strike
    // ------------------------------------------------------------------------------------------------------------

    /** Drops a meteor: visuals immediately, impact (damage + optional gravity field) after {@code fall} ticks. */
    static void meteor(AbilityContext ctx, Vec3 target, double radius, float damage, int fall, float scale, int fieldTicks, double fieldPull,
                       float fieldDamage, long seed) {
        Vec3 sky = target.add(-4 - scale * 2, 22 + scale * 4, 3 + scale);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.STARFORGE_METEOR).caster(ctx.player().getId()).pos(target).point(sky).power(fall).scale(scale)
                .level((int) Math.round(radius * 10)).seed(seed).build());
        Delayed.schedule(ctx, fall, (o, w) -> {
            ServerLevel level = o.serverLevel();
            List<Integer> hit = Kit.falloffBurst(o, w, target.add(0, 1, 0), radius, damage, 0.5f, Element.COSMIC, FWDamage.FLAG_HEAVY, 1.0, 0.6, null);
            Kit.fx(level, FxPayload.of(FxIds.STARFORGE_METEOR_IMPACT).caster(o.getId()).pos(target).scale((float) radius).power(fieldTicks)
                    .entities(hit).seed(seed).build());
            Kit.sound(level, target, ModSounds.METEOR_IMPACT.get(), 2.2f + scale * 0.3f, 1.1f - scale * 0.15f);
            if (fieldTicks > 0) {
                int pulses = Math.max(1, fieldTicks / 10);
                AreaEffectManager.add(new FieldEffect(ctx, target, radius * 1.5, fieldTicks, 10)
                        .onTick((oo, ww, f, inside) -> {
                            for (LivingEntity e : inside) Kit.pull(e, target.add(0, 0.5, 0), fieldPull);
                        })
                        .onPulse((oo, ww, f, inside) -> {
                            for (LivingEntity e : inside) FWDamage.deal(oo, ww, e, fieldDamage / pulses, FWDamage.Kind.ABILITY, Element.COSMIC, 0);
                        }));
            }
        });
    }

    public static boolean meteorStrike(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 target = Kit.aimTargetOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        float total = ctx.damage();
        float fieldShare = (float) ctx.param("field_fraction");
        int field = (int) Math.round(ctx.param("field") * 20);
        meteor(ctx, target, radius, total * (1 - fieldShare), (int) ctx.param("fall"), 1f, field, ctx.param("field_pull"), total * fieldShare,
                Kit.seed(ctx.level()));
        Kit.sound(ctx.level(), p.position(), ModSounds.ABILITY_ACTIVATE.get(), 1.2f, 0.7f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Event Horizon
    // ------------------------------------------------------------------------------------------------------------

    public static boolean eventHorizon(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Kit.aimTargetOrGround(p, ctx.param("range")).add(0, 1.6, 0);
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.param("duration") * 20);
        float total = ctx.damage();
        float implosion = (float) (total * ctx.param("implosion_share"));
        int pulses = Math.max(1, duration / 10);
        float perPulse = (total - implosion) / pulses;
        double pull = ctx.param("pull");
        AreaEffectManager.add(new FieldEffect(ctx, center, radius, duration, 10)
                .onTick((o, w, f, inside) -> {
                    for (LivingEntity e : inside) Kit.pull(e, center, pull);
                })
                .onPulse((o, w, f, inside) -> {
                    for (LivingEntity e : inside) {
                        if (e.distanceToSqr(center) < 9) FWDamage.deal(o, w, e, perPulse, FWDamage.Kind.ABILITY, Element.COSMIC, 0);
                    }
                })
                .onEnd((o, w, f) -> {
                    List<Integer> hit = Kit.falloffBurst(o, w, center, radius * 0.6, implosion, 0.5f, Element.COSMIC, FWDamage.FLAG_HEAVY, 1.4, 0.6, null);
                    Kit.fx(o.serverLevel(), FxPayload.of(FxIds.STARFORGE_HORIZON_END).caster(o.getId()).pos(center).scale((float) radius).entities(hit)
                            .seed(o.level().random.nextLong()).build());
                    Kit.sound(o.serverLevel(), center, ModSounds.EXPLOSION.get(), 2.2f, 0.6f);
                    Kit.sound(o.serverLevel(), center, ModSounds.GRAVITY.get(), 2f, 0.5f);
                }));
        Kit.active(ctx, duration);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.STARFORGE_HORIZON).caster(p.getId()).pos(center).scale((float) radius).power(duration)
                .seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.GRAVITY.get(), 2f, 0.5f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Starfall (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean starfall(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 c = p.position();
        double radius = ctx.scaled("radius", "radius_per_level");
        int meteors = (int) Math.round(ctx.scaled("meteors", "meteors_per_level"));
        int duration = (int) Math.round(ctx.param("duration") * 20);
        double mr = ctx.param("meteor_radius");
        float total = ctx.damage();
        float small = (float) (total * ctx.param("meteor_fraction"));
        float fin = (float) (total * ctx.param("final_fraction"));
        ServerLevel level = ctx.level();
        List<LivingEntity> targets = Targeting.inRadius(level, p, c.add(0, 1, 0), radius, FWDamage.Kind.ABILITY);
        for (int i = 0; i < meteors; i++) {
            Vec3 spot;
            if (!targets.isEmpty() && level.random.nextInt(3) > 0) {
                LivingEntity t = targets.get(level.random.nextInt(targets.size()));
                spot = t.position().add(level.random.nextGaussian() * 1.2, 0, level.random.nextGaussian() * 1.2);
            } else {
                double a = level.random.nextDouble() * Math.PI * 2, r = 2 + Math.sqrt(level.random.nextDouble()) * (radius - 2);
                spot = c.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            }
            Vec3 ground = Kit.ground(level, spot.add(0, 6, 0), 14);
            int delay = 10 + i * Math.max(1, duration / meteors);
            long seed = level.random.nextLong();
            Delayed.schedule(ctx, delay, (o, w) -> meteor(ctx, ground, mr, small, 12, 0.6f, 0, 0, 0, seed));
        }
        Delayed.schedule(ctx, duration + 10, (o, w) -> meteor(ctx, c, radius * 0.75, fin, 22, 2.6f, 40, 0.25, fin * 0.15f, level.random.nextLong()));
        Kit.active(ctx, duration + 34);
        Kit.fx(level, FxPayload.of(FxIds.STARFORGE_STARFALL).caster(p.getId()).pos(c).scale((float) radius).power(duration + 34)
                .seed(Kit.seed(level)).build());
        Kit.sound(level, c, ModSounds.GRAVITY.get(), 2.5f, 0.4f);
        return true;
    }
}
