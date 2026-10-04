package com.fantasyweapons.weapons.bloomfall;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.FieldEffect;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.MeleeContext;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusEffects;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.world.AreaEffectManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.List;

/** Server-side gameplay for every Bloomfall ability. */
public final class BloomfallAbilities {
    private BloomfallAbilities() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // Venom Bloom (passive) and poison
    // ------------------------------------------------------------------------------------------------------------

    public static void onMeleeHit(MeleeContext ctx) {
        float wd = ProgressionMath.weaponDamage(ctx.weapon(), ctx.data());
        poison(ctx.player(), ctx.weapon(), ctx.data(), ctx.target(), ctx.heavy() ? 2 : 1, wd, true);
    }

    /** Adds Nature Poison stacks. Melee only poisons once Venom Bloom is unlocked; abilities always poison. */
    static void poison(ServerPlayer owner, WeaponDefinition def, WeaponData data, LivingEntity target, int stacks, float weaponDamage, boolean melee) {
        if (!target.isAlive()) return;
        AbilityDefinition vb = def.ability(Bloomfall.VENOM_BLOOM);
        int lvl = data.abilityLevel(vb);
        if (melee && lvl <= 0) return;
        double frac = lvl > 0 ? vb.param("poison") + vb.param("poison_per_level") * (lvl - 1) : 0.025;
        StatusService.apply(target, StatusType.NATURE_POISON, 100, stacks, (float) (weaponDamage * frac), owner.getUUID());
    }

    /** Poisoned enemies killed while their poisoner wields Bloomfall with Venom Bloom burst into spores. */
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        StatusEffects.Instance poison = StatusService.get(victim, StatusType.NATURE_POISON);
        if (poison == null || poison.source == null) return;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(poison.source);
        if (p == null || p.level() != level) return;
        ItemStack held = p.getMainHandItem();
        if (!(held.getItem() instanceof FantasyWeaponItem item) || !item.definition().id().equals("bloomfall")) return;
        WeaponDefinition def = item.definition();
        WeaponData data = FantasyWeaponItem.data(held);
        AbilityDefinition vb = def.ability(Bloomfall.VENOM_BLOOM);
        if (data.abilityLevel(vb) <= 0) return;
        Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
        float wd = ProgressionMath.weaponDamage(def, data);
        for (LivingEntity e : Targeting.inRadius(level, p, at, vb.param("burst_radius"), FWDamage.Kind.ABILITY)) {
            if (e == victim) continue;
            poison(p, def, data, e, (int) vb.param("burst_stacks"), wd, false);
        }
        Kit.fx(level, FxPayload.of(FxIds.BLOOMFALL_SPORES).caster(p.getId()).pos(at).scale((float) vb.param("burst_radius"))
                .seed(level.random.nextLong()).build());
        Kit.sound(level, at, ModSounds.NATURE_GROWTH.get(), 0.8f, 1.5f);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Thorn Sweep
    // ------------------------------------------------------------------------------------------------------------

    public static boolean thornSweep(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double range = ctx.scaled("range", "range_per_level");
        Vec3 origin = p.position().add(0, 1, 0);
        int stacks = (int) ctx.param("poison_stacks");
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity e : Targeting.inCone(ctx.level(), p, origin, ctx.look(), range, ctx.param("angle"), FWDamage.Kind.ABILITY)) {
            ctx.deal(e, ctx.damage(), 0);
            poison(p, ctx.weapon(), ctx.data(), e, stacks, ctx.weaponDamage(), false);
            ids.add(e.getId());
        }
        Kit.fx(ctx.level(), FxPayload.of(FxIds.BLOOMFALL_THORN_SWEEP).caster(p.getId()).pos(p.position()).dir(Targeting.flatLook(p))
                .scale((float) range).power((float) ctx.param("angle")).entities(ids).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), origin, ModSounds.WEAPON_SWING_HEAVY.get(), 1.3f, 0.9f);
        Kit.sound(ctx.level(), origin, ModSounds.NATURE_GROWTH.get(), 1.1f, 1.1f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Entangling Roots
    // ------------------------------------------------------------------------------------------------------------

    public static boolean entanglingRoots(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Kit.aimTargetOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int root = (int) Math.round(ctx.scaled("root", "root_per_level") * 20);
        int stacks = (int) ctx.param("poison_stacks");
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity e : Targeting.inRadius(ctx.level(), p, center.add(0, 1, 0), radius, FWDamage.Kind.ABILITY)) {
            ctx.deal(e, ctx.damage(), 0);
            StatusService.apply(e, StatusType.ROOTED, root, 1, 1f, p.getUUID());
            poison(p, ctx.weapon(), ctx.data(), e, stacks, ctx.weaponDamage(), false);
            ids.add(e.getId());
        }
        Kit.fx(ctx.level(), FxPayload.of(FxIds.BLOOMFALL_ROOTS).caster(p.getId()).pos(center).scale((float) radius).power(root).entities(ids)
                .seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.NATURE_GROWTH.get(), 1.6f, 0.6f);
        Kit.sound(ctx.level(), center, ModSounds.EARTH_IMPACT.get(), 1.0f, 1.3f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Overgrowth
    // ------------------------------------------------------------------------------------------------------------

    public static boolean overgrowth(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        Vec3 center = p.position();
        float pulse = (float) (ctx.damage() * ctx.param("pulse_fraction"));
        AreaEffectManager.add(new FieldEffect(ctx, center, radius, duration, 10).onPulse((o, w, f, inside) -> {
            for (LivingEntity e : inside) {
                FWDamage.deal(o, w, e, pulse, FWDamage.Kind.ABILITY, Element.NATURE, 0);
                poison(o, ctx.weapon(), ctx.data(), e, 1, ctx.weaponDamage(), false);
            }
        }));
        Kit.active(ctx, duration);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.BLOOMFALL_OVERGROWTH).caster(p.getId()).pos(center).scale((float) radius).power(duration)
                .seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.NATURE_GROWTH.get(), 2f, 0.7f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Wrath of the Wild (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean wrathOfTheWild(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Kit.aimGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int pullTime = (int) Math.round(ctx.param("pull_time") * 20);
        double pull = ctx.param("pull");
        float thorn = (float) (ctx.damage() * ctx.param("thorn_fraction"));
        int stacks = (int) ctx.param("poison_stacks");
        AreaEffectManager.add(new FieldEffect(ctx, center, radius, pullTime, 1)
                .onTick((o, w, f, inside) -> {
                    for (LivingEntity e : inside) Kit.pull(e, center.add(0, 0.5, 0), pull);
                })
                .onEnd((o, w, f) -> {
                    List<Integer> hit = new ArrayList<>();
                    for (LivingEntity e : Targeting.inRadius(o.serverLevel(), o, center.add(0, 1, 0), radius * 0.75, FWDamage.Kind.ABILITY)) {
                        FWDamage.deal(o, w, e, thorn, FWDamage.Kind.ABILITY, Element.NATURE, FWDamage.FLAG_HEAVY);
                        poison(o, ctx.weapon(), ctx.data(), e, stacks, ctx.weaponDamage(), false);
                        Kit.knock(e, center, 0.6, 1.0);
                        hit.add(e.getId());
                    }
                    Kit.fx(o.serverLevel(), FxPayload.of(FxIds.BLOOMFALL_WRATH_END).caster(o.getId()).pos(center).scale((float) radius)
                            .entities(hit).seed(o.level().random.nextLong()).build());
                    Kit.sound(o.serverLevel(), center, ModSounds.EARTH_IMPACT.get(), 2.5f, 0.6f);
                    Kit.sound(o.serverLevel(), center, ModSounds.NATURE_GROWTH.get(), 2.5f, 0.5f);
                }));
        Kit.active(ctx, pullTime);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.BLOOMFALL_WRATH).caster(p.getId()).pos(center).scale((float) radius).power(pullTime)
                .seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.NATURE_GROWTH.get(), 2.5f, 0.45f);
        return true;
    }
}
