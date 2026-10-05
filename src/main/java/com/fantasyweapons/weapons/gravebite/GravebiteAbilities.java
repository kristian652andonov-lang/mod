package com.fantasyweapons.weapons.gravebite;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.FieldEffect;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.ability.kit.ProjectileEffect;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.MeleeContext;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.world.AreaEffectManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-side gameplay for every Gravebite ability. */
public final class GravebiteAbilities {
    /** Souls stored in the skull by Soul Harvest, per player (transient). */
    private static final Map<UUID, Integer> STORED = new HashMap<>();

    private GravebiteAbilities() {
    }

    /** Where souls leave the skull: in front of the player at axe-head height. */
    static Vec3 mouth(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        return p.position().add(0, p.getBbHeight() * 0.85, 0).add(look.scale(1.3));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Soul Harvest (passive)
    // ------------------------------------------------------------------------------------------------------------

    public static void onMeleeHit(MeleeContext ctx) {
        if (!ctx.target().isAlive()) harvest(ctx.player(), ctx.weapon(), ctx.data(), ctx.target());
    }

    static void harvest(ServerPlayer p, WeaponDefinition def, WeaponData data, LivingEntity victim) {
        AbilityDefinition sh = def.ability(Gravebite.SOUL_HARVEST);
        int lvl = data.abilityLevel(sh);
        if (lvl <= 0) return;
        p.heal((float) (p.getMaxHealth() * (sh.param("heal") + sh.param("heal_per_level") * (lvl - 1))));
        int max = (int) Math.round(sh.param("max_souls") + sh.param("max_souls_per_level") * (lvl - 1));
        STORED.merge(p.getUUID(), 1, (a, b) -> Math.min(max, a + b));
        Kit.fx(p.serverLevel(), FxPayload.of(FxIds.GRAVEBITE_HARVEST).caster(p.getId()).pos(victim.position().add(0, victim.getBbHeight() * 0.6, 0))
                .seed(p.level().random.nextLong()).build());
    }

    public static void forget(UUID player) {
        STORED.remove(player);
    }

    static void hitSoul(ServerPlayer o, ProjectileEffect pr, LivingEntity e, float damage, WeaponDefinition def, WeaponData data) {
        FWDamage.deal(o, pr.weaponOf(o), e, damage, FWDamage.Kind.ABILITY, Element.NECROMANCY, 0);
        if (!e.isAlive()) harvest(o, def, data, e);
    }

    /** Launches one homing soul (server simulation) and returns its seed for the client. */
    static long launchSoul(AbilityContext ctx, Vec3 start, Vec3 vel, @Nullable LivingEntity target, double range, float damage, long seed) {
        ProjectileEffect soul = new ProjectileEffect(ctx, start, vel, 0.7, range, 0, (o, e, pr) -> {
            hitSoul(o, pr, e, damage, ctx.weapon(), ctx.data());
            return true;
        }).homing(target, 0.22).onEnd((o, pos, any, pr) -> Kit.fx(o.serverLevel(), FxPayload.of(FxIds.GRAVEBITE_SOUL_HIT).caster(o.getId())
                .pos(pos).level(any ? 1 : 0).seed(seed).build()));
        AreaEffectManager.add(soul);
        return seed;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Soul Volley
    // ------------------------------------------------------------------------------------------------------------

    public static boolean soulVolley(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        int extra = STORED.getOrDefault(p.getUUID(), 0);
        STORED.remove(p.getUUID());
        int souls = (int) Math.round(ctx.scaled("souls", "souls_per_level")) + extra;
        double range = ctx.param("range");
        double speed = ctx.param("speed");
        float per = ctx.damage() / Math.max(1, souls - extra);
        List<LivingEntity> targets = Targeting.inCone(ctx.level(), p, p.getEyePosition(), ctx.look(), range, 70, FWDamage.Kind.ABILITY);
        LivingEntity aimed = Targeting.crosshair(p, range, FWDamage.Kind.ABILITY);
        if (aimed != null) {
            targets.remove(aimed);
            targets.add(0, aimed);
        }
        Vec3 start = mouth(p);
        Vec3 look = ctx.look();
        Vec3[] basis = com.fantasyweapons.ability.kit.Kit2.basis(look);
        List<Integer> targetIds = new ArrayList<>();
        List<Vec3> launchDirs = new ArrayList<>();
        long base = Kit.seed(ctx.level());
        for (int i = 0; i < souls; i++) {
            LivingEntity t = targets.isEmpty() ? null : targets.get(i % targets.size());
            double a = i * Math.PI * 2 / souls;
            Vec3 spread = basis[0].scale(Math.cos(a) * 0.55).add(basis[1].scale(Math.sin(a) * 0.55 + 0.25));
            Vec3 vel = look.add(spread).normalize().scale(speed);
            launchSoul(ctx, start, vel, t, range, per, base + i * 7919L);
            targetIds.add(t == null ? -1 : t.getId());
            launchDirs.add(vel);
        }
        Kit.fx(ctx.level(), FxPayload.of(FxIds.GRAVEBITE_SOUL_VOLLEY).caster(p.getId()).pos(start).dir(look).power((float) speed)
                .scale((float) range).entities(targetIds).points(launchDirs).seed(base).level(souls).build());
        Kit.sound(ctx.level(), start, ModSounds.SOUL_PROJECTILE.get(), 1.4f, 0.8f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Grave Chains
    // ------------------------------------------------------------------------------------------------------------

    public static boolean graveChains(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Kit.aimTargetOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int bind = (int) Math.round(ctx.scaled("bind", "bind_per_level") * 20);
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity e : Targeting.inRadius(ctx.level(), p, center.add(0, 1, 0), radius, FWDamage.Kind.ABILITY)) {
            ctx.deal(e, ctx.damage(), 0);
            StatusService.apply(e, StatusType.ROOTED, bind, 1, 1f, p.getUUID());
            if (!e.isAlive()) harvest(p, ctx.weapon(), ctx.data(), e);
            ids.add(e.getId());
        }
        Kit.fx(ctx.level(), FxPayload.of(FxIds.GRAVEBITE_CHAINS).caster(p.getId()).pos(center).scale((float) radius).power(bind).entities(ids)
                .seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.SOUL_PROJECTILE.get(), 1.6f, 0.5f);
        Kit.sound(ctx.level(), center, ModSounds.CHAIN_ATTACK.get(), 1.2f, 0.7f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Death's Maw
    // ------------------------------------------------------------------------------------------------------------

    public static boolean deathsMaw(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double range = ctx.scaled("range", "range_per_level");
        Vec3 origin = p.position().add(0, 1, 0);
        Vec3 look = Targeting.flatLook(p);
        List<Integer> ids = new ArrayList<>();
        float drain = (float) (ctx.weaponDamage() * ctx.param("drain"));
        int drainTicks = (int) Math.round(ctx.param("drain_duration") * 20);
        for (LivingEntity e : Targeting.inCone(ctx.level(), p, origin, look, range, ctx.param("angle"), FWDamage.Kind.ABILITY)) {
            ctx.deal(e, ctx.damage(), FWDamage.FLAG_HEAVY);
            if (e.isAlive()) StatusService.apply(e, StatusType.SOUL_DRAIN, drainTicks, 1, drain, p.getUUID());
            else harvest(p, ctx.weapon(), ctx.data(), e);
            Kit.pull(e, origin, 0.5);
            ids.add(e.getId());
        }
        Kit.fx(ctx.level(), FxPayload.of(FxIds.GRAVEBITE_MAW).caster(p.getId()).pos(origin).dir(look).scale((float) range).entities(ids)
                .seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), origin, ModSounds.SOUL_PROJECTILE.get(), 1.8f, 0.4f);
        Kit.sound(ctx.level(), origin, ModSounds.HEAVY_IMPACT.get(), 1.2f, 0.6f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Legion of the Damned (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean legion(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        int souls = (int) Math.round(ctx.scaled("souls", "souls_per_level"));
        int duration = (int) Math.round(ctx.param("duration") * 20);
        int interval = Math.max(1, (int) ctx.param("interval"));
        double range = ctx.param("range");
        float per = ctx.damage() / souls;
        int[] left = {souls};
        Vec3 center = Kit.feet(p); // the host gathers where it was summoned
        AreaEffectManager.add(new FieldEffect(ctx, center, range, duration, interval)
                .onPulse((o, w, f, inside) -> {
                    if (f.age() < 10 || left[0] <= 0 || inside.isEmpty()) return;
                    ServerLevel level = o.serverLevel();
                    LivingEntity t = inside.get(level.random.nextInt(inside.size()));
                    double a = level.random.nextDouble() * Math.PI * 2;
                    Vec3 start = center.add(Math.cos(a) * 2.2, 1.6 + level.random.nextDouble(), Math.sin(a) * 2.2);
                    Vec3 vel = t.getBoundingBox().getCenter().subtract(start).normalize().add(0, 0.4, 0).normalize().scale(0.95);
                    long seed = launchSoul(ctx, start, vel, t, range + 4, per, level.random.nextLong());
                    left[0]--;
                    Kit.fx(level, FxPayload.of(FxIds.GRAVEBITE_LEGION_LAUNCH).caster(o.getId()).pos(start).dir(vel).entities(t.getId())
                            .seed(seed).build());
                    if (level.random.nextInt(3) == 0) Kit.sound(level, start, ModSounds.SOUL_PROJECTILE.get(), 0.8f, 1.2f + level.random.nextFloat() * 0.4f);
                })
                .onEnd((o, w, f) -> Kit.fx(o.serverLevel(), FxPayload.of(FxIds.GRAVEBITE_LEGION_END).caster(o.getId()).pos(center).build())));
        Kit.active(ctx, duration);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.GRAVEBITE_LEGION).caster(p.getId()).pos(center).power(duration).level(souls)
                .seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), p.position(), ModSounds.SOUL_PROJECTILE.get(), 2.5f, 0.4f);
        return true;
    }
}
