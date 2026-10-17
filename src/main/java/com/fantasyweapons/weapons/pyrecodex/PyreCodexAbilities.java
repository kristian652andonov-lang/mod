package com.fantasyweapons.weapons.pyrecodex;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.HitTracker;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.magic.SpellEffect;
import com.fantasyweapons.magic.Spells;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.progression.ExpService;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-side gameplay for every Pyre Codex spell. */
public final class PyreCodexAbilities {
    /** How fast the phoenix wheels round (radians a tick) and how high it flies; shared with its visuals. */
    public static final double PHOENIX_TURN = 0.11, PHOENIX_HEIGHT = 4.5;

    private PyreCodexAbilities() {
    }

    private static void sear(ServerPlayer o, ItemStack w, LivingEntity e, float perPulse, int seconds) {
        Spells.status(o, w, e, StatusType.SEARED, seconds * 20, 1, perPulse);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Searing Chain
    // ------------------------------------------------------------------------------------------------------------

    public static boolean searingChain(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        LivingEntity first = Spells.enemy(ctx, ctx.param("range"));
        int jumps = (int) Math.floor(ctx.scaled("jumps", "jumps_per_level"));
        double reach = ctx.param("jump_range");
        float damage = ctx.damage();
        float burn = (float) (ctx.weaponDamage() * ctx.param("sear"));
        List<Vec3> points = new ArrayList<>();
        List<Integer> hit = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        points.add(Spells.focus(p));
        LivingEntity current = first;
        for (int i = 0; i <= jumps && current != null; i++) {
            seen.add(current.getId());
            points.add(current.getBoundingBox().getCenter());
            hit.add(current.getId());
            ctx.deal(current, damage, 0);
            sear(p, ctx.stack(), current, burn, 4);
            damage *= (float) ctx.param("falloff");
            LivingEntity next = null;
            for (LivingEntity e : Targeting.inRadius(ctx.level(), p, current.getBoundingBox().getCenter(), reach, FWDamage.Kind.ABILITY)) {
                if (!seen.contains(e.getId())) {
                    next = e;
                    break;
                }
            }
            current = next;
        }
        Kit.fx(ctx.level(), FxPayload.of(FxIds.PYRE_CHAIN).caster(p.getId()).pos(points.get(0)).points(points).entities(hit)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), points.get(1), ModSounds.FIRE_WHOOSH.get(), 1.4f, 1.1f);
        Kit.sound(ctx.level(), points.get(points.size() - 1), ModSounds.FIRE_WHOOSH.get(), 1.0f, 1.4f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Wildfire (passive): a burning foe slain bursts into flame
    // ------------------------------------------------------------------------------------------------------------

    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        if (StatusService.stacks(victim, StatusType.SEARED) <= 0 && !victim.isOnFire()) return;
        HitTracker t = victim.getExistingDataOrNull(ModAttachments.HIT_TRACKER);
        if (t == null || t.player == null) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(t.player);
        if (owner == null) return;
        ItemStack stack = ExpService.findWeapon(owner, t.weapon);
        if (!(stack.getItem() instanceof FantasyWeaponItem item) || !item.definition().id().equals("pyre_codex")) return;
        AbilityDefinition wild = item.definition().ability(PyreCodex.WILDFIRE);
        int lvl = FantasyWeaponItem.data(stack).abilityLevel(wild);
        if (lvl <= 0) return;
        float weaponDamage = ProgressionMath.weaponDamage(item.definition(), FantasyWeaponItem.data(stack));
        double radius = Spells.scaled(wild, lvl, "radius", "radius_per_level");
        Vec3 at = victim.getBoundingBox().getCenter();
        List<Integer> hit = Kit.burst(owner, stack, at, radius, (float) (weaponDamage * wild.param("burst")), Element.FIRE, 0, 0.4, 0.25,
                e -> sear(owner, stack, e, (float) (weaponDamage * wild.param("sear")), 4));
        Kit.fx(level, FxPayload.of(FxIds.PYRE_WILDFIRE).caster(owner.getId()).pos(at).scale((float) radius).entities(hit).seed(Kit.seed(level)).build());
        Kit.sound(level, at, ModSounds.FIRE_WHOOSH.get(), 1.2f, 0.8f);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Wall of Pyres
    // ------------------------------------------------------------------------------------------------------------

    public static boolean wallOfPyres(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Kit.aimTargetOrGround(p, ctx.param("range"));
        double length = ctx.scaled("length", "length_per_level");
        int duration = (int) Math.round(ctx.param("duration") * 20);
        double knock = ctx.param("knockback");
        float damage = ctx.damage();
        float burn = (float) (ctx.weaponDamage() * 0.05);
        Vec3 facing = Targeting.flatLook(p);
        Vec3 across = facing.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 a = center.add(across.scale(-length / 2)).add(0, 1, 0), b = center.add(across.scale(length / 2)).add(0, 1, 0);
        Map<Integer, Integer> lastBurn = new HashMap<>();
        new SpellEffect(ctx, center, duration).onTick((o, w, s) -> {
            if (s.age() < 4) return; // the flames are still rising
            for (LivingEntity e : Targeting.alongPath(o.serverLevel(), o, a, b, 0.9, FWDamage.Kind.ABILITY)) {
                Integer last = lastBurn.get(e.getId());
                if (last != null && s.age() - last < 10) continue;
                lastBurn.put(e.getId(), s.age());
                FWDamage.deal(o, w, e, damage, FWDamage.Kind.ABILITY, Element.FIRE, 0);
                sear(o, w, e, burn, 4);
                // thrown back the way it came
                double side = Math.signum(e.position().subtract(center).dot(facing));
                if (side == 0) side = -1;
                e.push(facing.x * side * knock, 0.3, facing.z * side * knock);
                e.hurtMarked = true;
            }
        }).start();
        Kit.fx(ctx.level(), FxPayload.of(FxIds.PYRE_WALL).caster(p.getId()).pos(center).dir(across).scale((float) length).power(duration)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.FIRE_WHOOSH.get(), 1.8f, 0.5f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Phoenix (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    /** Where the phoenix is {@code age} ticks into its flight: wheeling round, swooping low twice a circuit. */
    public static Vec3 phoenixAt(Vec3 center, double radius, double age) {
        double t = age * PHOENIX_TURN;
        double dive = Math.cos(t * 2);
        double r = radius * (0.75 + 0.25 * dive);
        double h = PHOENIX_HEIGHT * (0.55 + 0.45 * dive) + 0.6;
        return center.add(Math.cos(t) * r, h, Math.sin(t) * r);
    }

    public static boolean phoenix(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Kit.aimTargetOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        float total = ctx.damage();
        float pass = (float) (total * ctx.param("pass_fraction"));
        float crash = (float) (total * ctx.param("crash_fraction"));
        double crashRadius = ctx.param("crash_radius");
        float burn = (float) (ctx.weaponDamage() * 0.08);
        Map<Integer, Integer> lastPass = new HashMap<>();
        new SpellEffect(ctx, center, duration).onTick((o, w, s) -> {
            if (s.age() < 10) return; // rising from its ember
            Vec3 at = phoenixAt(center, radius, s.age());
            s.move(at);
            for (LivingEntity e : Targeting.inRadius(o.serverLevel(), o, at, 2.6, FWDamage.Kind.ABILITY)) {
                Integer last = lastPass.get(e.getId());
                if (last != null && s.age() - last < 10) continue;
                lastPass.put(e.getId(), s.age());
                FWDamage.deal(o, w, e, pass, FWDamage.Kind.ABILITY, Element.FIRE, 0);
                sear(o, w, e, burn, 5);
                Kit.knock(e, at, 0.4, 0.3);
            }
            if (s.age() % 16 == 0) Kit.sound(o.serverLevel(), at, ModSounds.FIRE_WHOOSH.get(), 1.4f, 0.9f + o.getRandom().nextFloat() * 0.3f);
        }).onEnd((o, w, s) -> {
            List<Integer> hit = Kit.falloffBurst(o, w, center.add(0, 1, 0), crashRadius, crash, 0.45f, Element.FIRE, FWDamage.FLAG_HEAVY, 1.4, 0.8,
                    e -> sear(o, w, e, burn * 1.5f, 6));
            Kit.fx(o.serverLevel(), FxPayload.of(FxIds.PYRE_PHOENIX_CRASH).caster(o.getId()).pos(center).point(s.pos()).scale((float) crashRadius)
                    .entities(hit).seed(o.getRandom().nextLong()).build());
            Kit.sound(o.serverLevel(), center, ModSounds.EXPLOSION.get(), 2.6f, 0.6f);
            Kit.sound(o.serverLevel(), center, ModSounds.SOLAR_BURST.get(), 2.2f, 0.7f);
        }).start();
        Kit.active(ctx, duration);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.PYRE_PHOENIX).caster(p.getId()).pos(center).scale((float) radius).power(duration)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.SOLAR_BURST.get(), 2.0f, 1.3f);
        return true;
    }
}
