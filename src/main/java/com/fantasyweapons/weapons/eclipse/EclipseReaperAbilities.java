package com.fantasyweapons.weapons.eclipse;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityRuntime;
import com.fantasyweapons.ability.kit.FieldEffect;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.MeleeContext;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.entity.ThrownWeaponEntity;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.registry.ModAttachments;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-side gameplay for every Eclipse Reaper ability. */
public final class EclipseReaperAbilities {
    /** Players who switched modes recently: game time of the switch (Equilibrium bonus). */
    private static final Map<UUID, Long> SWITCHED = new HashMap<>();

    private EclipseReaperAbilities() {
    }

    static boolean dark(WeaponDefinition def, WeaponData data) {
        WeaponForm f = def.form(data);
        return f != null && EclipseReaper.DARK.equals(f.id());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Equilibrium (passive): marks, resonance and the switch bonus
    // ------------------------------------------------------------------------------------------------------------

    public static void onFormSwitch(ServerPlayer p, ItemStack stack, WeaponForm form) {
        SWITCHED.put(p.getUUID(), p.serverLevel().getServer().overworld().getGameTime());
    }

    public static void forget(UUID player) {
        SWITCHED.remove(player);
    }

    /** Consumes the switch bonus if Equilibrium is unlocked and the switch was recent. */
    static float switchBonus(ServerPlayer p, WeaponDefinition def, WeaponData data) {
        AbilityDefinition eq = def.ability(EclipseReaper.EQUILIBRIUM);
        if (data.abilityLevel(eq) <= 0) return 1f;
        Long at = SWITCHED.get(p.getUUID());
        long now = p.serverLevel().getServer().overworld().getGameTime();
        if (at == null || now - at > eq.param("switch_window") * 20) return 1f;
        SWITCHED.remove(p.getUUID());
        return 1f + (float) eq.param("switch_bonus");
    }

    static void brand(ServerPlayer p, LivingEntity e, boolean darkBrand, int stacks, float darkDot) {
        if (!e.isAlive()) return;
        if (darkBrand) StatusService.apply(e, StatusType.ECLIPSE_DARKNESS, 100, stacks, darkDot, p.getUUID());
        else StatusService.apply(e, StatusType.ECLIPSE_LIGHT, 100, stacks, 0f, p.getUUID());
    }

    /** If the target carries both brands and Equilibrium is unlocked: detonate them in an eclipse. */
    static void resonate(ServerPlayer p, ItemStack weapon, WeaponDefinition def, WeaponData data, LivingEntity e) {
        AbilityDefinition eq = def.ability(EclipseReaper.EQUILIBRIUM);
        int lvl = data.abilityLevel(eq);
        if (lvl <= 0 || !e.isAlive()) return;
        if (StatusService.stacks(e, StatusType.ECLIPSE_LIGHT) <= 0 || StatusService.stacks(e, StatusType.ECLIPSE_DARKNESS) <= 0) return;
        StatusService.remove(e, StatusType.ECLIPSE_LIGHT);
        StatusService.remove(e, StatusType.ECLIPSE_DARKNESS);
        float wd = ProgressionMath.weaponDamage(def, data);
        float dmg = (float) (wd * (eq.param("resonance") + eq.param("resonance_per_level") * (lvl - 1)));
        FWDamage.deal(p, weapon, e, dmg, FWDamage.Kind.ABILITY, Element.CELESTIAL, FWDamage.FLAG_CRIT);
        Kit.fx(p.serverLevel(), FxPayload.of(FxIds.ECLIPSE_RESONANCE).caster(p.getId()).pos(e.getBoundingBox().getCenter()).scale(e.getBbHeight())
                .seed(p.level().random.nextLong()).build());
        Kit.sound(p.serverLevel(), e.position(), ModSounds.SOLAR_BURST.get(), 1.2f, 1.5f);
    }

    public static void onMeleeHit(MeleeContext ctx) {
        boolean dark = dark(ctx.weapon(), ctx.data());
        float bonus = switchBonus(ctx.player(), ctx.weapon(), ctx.data());
        if (bonus > 1f) {
            FWDamage.deal(ctx.player(), ctx.stack(), ctx.target(), ctx.damageDealt() * (bonus - 1f), FWDamage.Kind.ABILITY, Element.CELESTIAL, 0);
        }
        if (ctx.data().abilityLevel(ctx.weapon().ability(EclipseReaper.EQUILIBRIUM)) > 0) {
            float wd = ProgressionMath.weaponDamage(ctx.weapon(), ctx.data());
            brand(ctx.player(), ctx.target(), dark, 1, wd * 0.03f);
            resonate(ctx.player(), ctx.stack(), ctx.weapon(), ctx.data(), ctx.target());
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Eclipse Disc
    // ------------------------------------------------------------------------------------------------------------

    public static boolean eclipseDisc(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        boolean dark = dark(ctx.weapon(), ctx.data());
        float damage = ctx.damage() * (dark ? 1f : 1f + (float) ctx.param("light_bonus")) * switchBonus(p, ctx.weapon(), ctx.data());
        float dot = (float) (ctx.weaponDamage() * ctx.param("dark_dot"));
        int dotTicks = (int) Math.round(ctx.param("dark_duration") * 20);
        AbilityRuntime rt = p.getData(ModAttachments.ABILITY_RUNTIME);
        rt.setThrown(ctx.data().idOrNil());
        p.syncData(ModAttachments.ABILITY_RUNTIME);
        Vec3 look = ctx.look();
        Vec3 dir = new Vec3(look.x, Math.max(-0.05, Math.min(0.4, look.y)), look.z).normalize();
        Vec3 start = p.getEyePosition().subtract(0, 0.45, 0).add(look.scale(0.8));
        ThrownWeaponEntity e = ThrownWeaponEntity.create(p, ctx.stack(), ctx.data().idOrNil(), start)
                .out(dir, ctx.param("speed"), ctx.scaled("range", "range_per_level"))
                .hits(1.4, 1000, (o, w, t, th) -> {
                    float dealt = FWDamage.deal(o, w, t, damage, FWDamage.Kind.ABILITY, Element.CELESTIAL, dark ? 0 : FWDamage.FLAG_CRIT);
                    if (dark) StatusService.apply(t, StatusType.ECLIPSE_DARKNESS, dotTicks, 1, dot, o.getUUID());
                    else brand(o, t, false, 1, 0);
                    resonate(o, w, ctx.weapon(), ctx.data(), t);
                    Kit.fx(o.serverLevel(), FxPayload.of(FxIds.ECLIPSE_DISC_HIT).caster(o.getId()).pos(t.getBoundingBox().getCenter())
                            .level(dark ? 1 : 0).seed(o.level().random.nextLong()).build());
                    return dealt;
                })
                .onCaught(() -> Kit.sound(p.serverLevel(), p.position(), ModSounds.SCYTHE_RETURN.get(), 1f, dark ? 0.8f : 1.2f));
        ctx.level().addFreshEntity(e);
        Kit.sound(ctx.level(), start, ModSounds.SCYTHE_THROW.get(), 1.3f, dark ? 0.8f : 1.2f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Solar Flare (light)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean solarFlare(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double radius = ctx.scaled("radius", "radius_per_level");
        int marks = (int) ctx.param("marks");
        float damage = ctx.damage() * switchBonus(p, ctx.weapon(), ctx.data());
        List<Integer> hit = Kit.falloffBurst(p, ctx.stack(), p.position().add(0, 1, 0), radius, damage, 0.6f, Element.CELESTIAL, FWDamage.FLAG_CRIT,
                0.9, 0.35, e -> {
                    brand(p, e, false, marks, 0);
                    resonate(p, ctx.stack(), ctx.weapon(), ctx.data(), e);
                });
        Kit.fx(ctx.level(), FxPayload.of(FxIds.ECLIPSE_SOLAR_FLARE).caster(p.getId()).pos(p.position()).scale((float) radius).entities(hit)
                .seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), p.position(), ModSounds.SOLAR_BURST.get(), 1.8f, 1.3f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Umbral Vortex (dark)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean umbralVortex(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Kit.aimTargetOrGround(p, ctx.param("range")).add(0, 1, 0);
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.param("duration") * 20);
        int pulses = Math.max(1, duration / 10);
        float per = ctx.damage() * switchBonus(p, ctx.weapon(), ctx.data()) / pulses;
        double pull = ctx.param("pull");
        float dot = ctx.weaponDamage() * 0.04f;
        AreaEffectManager.add(new FieldEffect(ctx, center, radius, duration, 10)
                .onTick((o, w, f, inside) -> {
                    for (LivingEntity e : inside) Kit.pull(e, center, pull);
                })
                .onPulse((o, w, f, inside) -> {
                    for (LivingEntity e : inside) {
                        FWDamage.deal(o, w, e, per, FWDamage.Kind.ABILITY, Element.CELESTIAL, 0);
                        brand(o, e, true, 1, dot);
                        resonate(o, w, ctx.weapon(), ctx.data(), e);
                    }
                }));
        Kit.active(ctx, duration);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.ECLIPSE_UMBRAL_VORTEX).caster(p.getId()).pos(center).scale((float) radius).power(duration)
                .seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.RIFT_OPEN.get(), 1.6f, 0.6f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Total Eclipse (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean totalEclipse(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        int interval = Math.max(2, (int) ctx.param("interval"));
        float total = ctx.damage();
        float beam = (float) (total * ctx.param("beam_fraction"));
        float corona = (float) (total * ctx.param("corona_fraction"));
        float dot = ctx.weaponDamage() * 0.05f;
        Vec3 center = p.position();
        Vec3 sun = center.add(0, 13, 0);
        int[] count = {0};
        AreaEffectManager.add(new FieldEffect(ctx, center, radius, duration, interval)
                .onPulse((o, w, f, inside) -> {
                    if (f.age() < 20 || inside.isEmpty()) return;
                    ServerLevel level = o.serverLevel();
                    boolean darkBeam = (count[0]++ & 1) == 1;
                    LivingEntity t = inside.get(level.random.nextInt(inside.size()));
                    FWDamage.deal(o, w, t, beam, FWDamage.Kind.ABILITY, Element.CELESTIAL, darkBeam ? 0 : FWDamage.FLAG_CRIT);
                    brand(o, t, darkBeam, 1, dot);
                    resonate(o, w, ctx.weapon(), ctx.data(), t);
                    Kit.fx(level, FxPayload.of(FxIds.ECLIPSE_BEAM).caster(o.getId()).pos(t.position()).point(sun).level(darkBeam ? 1 : 0)
                            .seed(level.random.nextLong()).build());
                })
                .onEnd((o, w, f) -> {
                    List<Integer> hit = Kit.falloffBurst(o, w, center.add(0, 1, 0), radius, corona, 0.5f, Element.CELESTIAL, FWDamage.FLAG_HEAVY, 1.2, 0.6,
                            e -> resonate(o, w, ctx.weapon(), ctx.data(), e));
                    Kit.fx(o.serverLevel(), FxPayload.of(FxIds.ECLIPSE_TOTAL_END).caster(o.getId()).pos(center).point(sun).scale((float) radius)
                            .entities(hit).seed(o.level().random.nextLong()).build());
                    Kit.sound(o.serverLevel(), center, ModSounds.SOLAR_BURST.get(), 3f, 0.6f);
                    Kit.sound(o.serverLevel(), center, ModSounds.EXPLOSION.get(), 2f, 0.8f);
                }));
        Kit.active(ctx, duration);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.ECLIPSE_TOTAL).caster(p.getId()).pos(center).point(sun).scale((float) radius).power(duration)
                .seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.MODE_TRANSFORM.get(), 2.5f, 0.5f);
        return true;
    }
}
