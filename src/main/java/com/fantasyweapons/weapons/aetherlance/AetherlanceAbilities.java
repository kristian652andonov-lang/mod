package com.fantasyweapons.weapons.aetherlance;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.FieldEffect;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.ability.kit.Kit2;
import com.fantasyweapons.ability.kit.ProjectileEffect;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.MeleeContext;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.world.AreaEffectManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-side gameplay for every Aetherlance ability, including the piercing thrust on normal attacks. */
public final class AetherlanceAbilities {
    private static final Map<UUID, Integer> THRUSTS = new HashMap<>();
    /** Enemies a plain thrust pierces behind its target (before Aether Resonance). */
    private static final int BASE_PIERCE = 3;

    private AetherlanceAbilities() {
    }

    public static void forget(UUID player) {
        THRUSTS.remove(player);
    }

    /** The lance tip: well in front of the player at chest height. */
    static Vec3 tip(ServerPlayer p) {
        return p.getEyePosition().subtract(0, 0.35, 0).add(p.getLookAngle().scale(2.2));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Piercing thrust (every normal attack) + Aether Resonance (passive)
    // ------------------------------------------------------------------------------------------------------------

    public static void onMeleeHit(MeleeContext ctx) {
        if (!ctx.primary()) return;
        ServerPlayer p = ctx.player();
        AbilityDefinition res = ctx.weapon().ability(Aetherlance.RESONANCE);
        int lvl = ctx.data().abilityLevel(res);
        int pierce = BASE_PIERCE + (lvl > 0 ? (int) Math.round(res.param("extra_pierce") + res.param("extra_pierce_per_level") * (lvl - 1)) : 0);
        Vec3 from = ctx.target().getBoundingBox().getCenter();
        Vec3 dir = p.getLookAngle();
        int n = 0;
        for (LivingEntity e : Targeting.alongPath(p.serverLevel(), p, from, from.add(dir.scale(5)), 0.9, FWDamage.Kind.MELEE)) {
            if (e == ctx.target()) continue;
            if (n++ >= pierce) break;
            FWDamage.deal(p, ctx.stack(), e, ctx.damageDealt() * 0.7f, FWDamage.Kind.MELEE, Element.ENERGY, 0);
        }
        Kit.fx(p.serverLevel(), FxPayload.of(FxIds.AETHERLANCE_THRUST).caster(p.getId()).pos(from).dir(dir).power(n).build());
        if (lvl <= 0) return;
        int t = THRUSTS.merge(p.getUUID(), 1, Integer::sum);
        if (t < (int) res.param("thrusts")) return;
        THRUSTS.remove(p.getUUID());
        float wd = ProgressionMath.weaponDamage(ctx.weapon(), ctx.data());
        float dmg = (float) (wd * (res.param("bolt") + res.param("bolt_per_level") * (lvl - 1)));
        bolt(p, ctx.stack(), tip(p), dir.scale(2.4), 28, 0.7, dmg, 0.6f);
    }

    /** Fires one piercing bolt (server simulation + client payload). */
    static void bolt(ServerPlayer p, ItemStack weapon, Vec3 start, Vec3 vel, double range, double width, float damage, float scale) {
        long seed = p.level().random.nextLong();
        ServerLevel level = p.serverLevel();
        ProjectileEffect proj = new ProjectileEffect(level, p, com.fantasyweapons.weapon.FantasyWeaponItem.data(weapon).idOrNil(), start, vel, width, range,
                -1, (o, e, pr) -> {
            FWDamage.deal(o, pr.weaponOf(o), e, damage, FWDamage.Kind.ABILITY, Element.ENERGY, 0);
            Kit.fx(o.serverLevel(), FxPayload.of(FxIds.AETHERLANCE_PIERCE).caster(o.getId()).pos(e.getBoundingBox().getCenter()).dir(pr.velocity())
                    .seed(seed).build());
            return false;
        }).onEnd((o, pos, any, pr) -> Kit.fx(o.serverLevel(), FxPayload.of(FxIds.AETHERLANCE_BOLT_END).caster(o.getId()).pos(pos).scale(scale)
                .seed(seed).build()));
        AreaEffectManager.add(proj);
        Kit.fx(level, FxPayload.of(FxIds.AETHERLANCE_BOLT).caster(p.getId()).pos(start).dir(vel).power((float) range).scale(scale).seed(seed).build());
        Kit.sound(level, start, ModSounds.ENERGY_FIRE.get(), 1.2f * scale + 0.4f, 1.3f - scale * 0.3f);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Aether Bolt
    // ------------------------------------------------------------------------------------------------------------

    public static boolean aetherBolt(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        bolt(p, ctx.stack(), tip(p), ctx.look().scale(ctx.param("speed")), ctx.param("range"), ctx.param("width"), ctx.damage(), 1f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Piercing Charge
    // ------------------------------------------------------------------------------------------------------------

    public static boolean piercingCharge(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        if (p.isPassenger()) throw AbilityContext.fail("Cannot charge right now");
        Vec3 flat = Targeting.flatLook(p);
        double distance = ctx.scaled("distance", "distance_per_level");
        Vec3 dest = com.fantasyweapons.world.SafeTeleport.along(p, flat, distance);
        if (dest == null) throw AbilityContext.fail("No room to charge");
        double dist = Math.max(1, new Vec3(dest.x - p.getX(), 0, dest.z - p.getZ()).length());
        int ticks = 8;
        float damage = ctx.damage();
        double width = ctx.param("width");
        // hits follow the (wall-clipped) charge path on the server; the motion below makes the player visibly surge along it
        AreaEffectManager.add(new com.fantasyweapons.ability.kit.SweepEffect(ctx, p.position().add(0, 1, 0), flat, dist + 1, width, (dist + 1) / ticks,
                (o, w, e, sw) -> {
                    FWDamage.deal(o, w, e, damage, FWDamage.Kind.ABILITY, Element.ENERGY, FWDamage.FLAG_HEAVY);
                    Vec3 side = new Vec3(-flat.z, 0, flat.x);
                    double s = Math.signum(e.position().subtract(o.position()).dot(side));
                    e.push(side.x * s * 0.8 + flat.x * 0.3, 0.35, side.z * s * 0.8 + flat.z * 0.3);
                    e.hurtMarked = true;
                    Kit.fx(o.serverLevel(), FxPayload.of(FxIds.AETHERLANCE_PIERCE).caster(o.getId()).pos(e.getBoundingBox().getCenter()).dir(flat)
                            .seed(o.level().random.nextLong()).build());
                }));
        Vec3 vel = flat.scale(dist / ticks).add(0, 0.05, 0);
        AreaEffectManager.add(new FieldEffect(ctx, p.position(), 1, ticks, 1).follow(p, Vec3.ZERO).onTick((o, w, f, inside) -> {
            o.resetFallDistance();
            o.setDeltaMovement(vel.x, Math.max(o.getDeltaMovement().y, 0.02), vel.z);
            o.hurtMarked = true;
        }));
        Kit.fx(ctx.level(), FxPayload.of(FxIds.AETHERLANCE_CHARGE).caster(p.getId()).pos(p.position()).dir(flat).power(ticks + 2)
                .seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), p.position(), ModSounds.ENERGY_FIRE.get(), 1.5f, 0.7f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Celestial Barrage
    // ------------------------------------------------------------------------------------------------------------

    public static boolean barrage(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        int n = (int) Math.round(ctx.scaled("bolts", "bolts_per_level"));
        double spread = Math.toRadians(ctx.param("spread"));
        float per = ctx.damage() / n * 1.6f; // each bolt pierces, so the fan's damage is concentrated on what it crosses
        Vec3 look = ctx.look();
        Vec3[] basis = Kit2.basis(look);
        Vec3 start = tip(p);
        for (int i = 0; i < n; i++) {
            double a = n == 1 ? 0 : -spread / 2 + spread * i / (n - 1);
            Vec3 dir = look.scale(Math.cos(a)).add(basis[0].scale(Math.sin(a))).normalize();
            bolt(p, ctx.stack(), start, dir.scale(2.2), ctx.param("range"), 0.8, per, 0.8f);
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Judgement Ray (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean judgementRay(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        int rehit = Math.max(1, (int) ctx.param("rehit"));
        int hitsPerEnemy = Math.max(1, duration / rehit);
        float per = ctx.damage() / hitsPerEnemy;
        double range = ctx.param("range");
        double radius = ctx.param("radius");
        Map<Integer, Integer> last = new HashMap<>();
        AreaEffectManager.add(new FieldEffect(ctx, p.position(), 1, duration, 1).follow(p, Vec3.ZERO).onTick((o, w, f, inside) -> {
            Vec3 start = tip(o);
            Vec3 end = start.add(o.getLookAngle().scale(range));
            HitResult hit = o.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, o));
            if (hit.getType() != HitResult.Type.MISS) end = hit.getLocation();
            List<LivingEntity> targets = Targeting.alongPath(o.serverLevel(), o, start, end, radius, FWDamage.Kind.ABILITY);
            for (LivingEntity e : targets) {
                Integer prev = last.get(e.getId());
                if (prev != null && f.age() - prev < rehit) continue;
                last.put(e.getId(), f.age());
                FWDamage.deal(o, w, e, per, FWDamage.Kind.ABILITY, Element.ENERGY, 0);
            }
            if (f.age() % 10 == 0) Kit.sound(o.serverLevel(), end, ModSounds.ENERGY_FIRE.get(), 0.8f, 0.5f + o.level().random.nextFloat() * 0.2f);
        }));
        Kit.active(ctx, duration);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.AETHERLANCE_RAY).caster(p.getId()).pos(p.position()).power(duration).scale((float) radius)
                .level((int) range).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), p.position(), ModSounds.ENERGY_FIRE.get(), 2.5f, 0.4f);
        return true;
    }
}
