package com.fantasyweapons.weapons.soulreaper;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityRuntime;
import com.fantasyweapons.ability.kit.Delayed;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.MeleeContext;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.entity.ThrownWeaponEntity;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Server-side gameplay for every Soulreaper ability. */
public final class SoulreaperAbilities {
    private SoulreaperAbilities() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // Soul Siphon (passive)
    // ------------------------------------------------------------------------------------------------------------

    public static void onMeleeHit(MeleeContext ctx) {
        AbilityDefinition ss = ctx.weapon().ability(Soulreaper.SOUL_SIPHON);
        int lvl = ctx.data().abilityLevel(ss);
        if (lvl <= 0) return;
        FWDamage.lifesteal(ctx.player(), ctx.damageDealt(), (float) (ss.param("steal") + ss.param("steal_per_level") * (lvl - 1)));
        if (ctx.target().isAlive() && StatusService.stacks(ctx.target(), StatusType.SOUL_DRAIN) > 0) {
            FWDamage.deal(ctx.player(), ctx.stack(), ctx.target(), (float) (ctx.damageDealt() * ss.param("torn_bonus")), FWDamage.Kind.ABILITY,
                    Element.SOUL, 0);
        }
    }

    /** Throws the actual weapon: marks it as thrown for the owner and spawns the entity. */
    static ThrownWeaponEntity throwWeapon(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        AbilityRuntime rt = p.getData(ModAttachments.ABILITY_RUNTIME);
        rt.setThrown(ctx.data().idOrNil());
        p.syncData(ModAttachments.ABILITY_RUNTIME);
        Vec3 start = p.getEyePosition().subtract(0, 0.45, 0).add(p.getLookAngle().scale(0.8));
        ThrownWeaponEntity e = ThrownWeaponEntity.create(p, ctx.stack(), ctx.data().idOrNil(), start);
        ItemStack weapon = ctx.stack();
        e.onCaught(() -> {
            // the scythe lands back in the hand with a short recoil rather than spinning on
            com.fantasyweapons.weapon.WeaponAnimations.trigger(p, weapon, "impact");
            Kit.fx(p.serverLevel(), FxPayload.of(FxIds.SOULREAPER_CATCH).caster(p.getId()).pos(p.position()).build());
            Kit.sound(p.serverLevel(), p.position(), ModSounds.SCYTHE_RETURN.get(), 1f, 1f);
        });
        return e;
    }

    /** Throws travel mostly level so a slightly downward look doesn't bury the scythe in the ground. */
    static Vec3 levelled(Vec3 look) {
        return new Vec3(look.x, Math.max(-0.05, Math.min(0.4, look.y)), look.z).normalize();
    }

    static float reap(ServerPlayer o, net.minecraft.world.item.ItemStack weapon, LivingEntity e, float damage, double drain) {
        float dealt = FWDamage.deal(o, weapon, e, damage, FWDamage.Kind.ABILITY, Element.SOUL, 0);
        FWDamage.lifesteal(o, dealt, (float) drain);
        if (dealt > 0) {
            Kit.fx(o.serverLevel(), FxPayload.of(FxIds.SOULREAPER_REAP).caster(o.getId()).pos(e.position().add(0, e.getBbHeight() * 0.6, 0))
                    .entities(e.getId()).seed(o.level().random.nextLong()).build());
        }
        return dealt;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Reaper's Throw
    // ------------------------------------------------------------------------------------------------------------

    public static boolean reapersThrow(AbilityContext ctx) {
        float damage = ctx.damage();
        double drain = ctx.param("drain");
        ThrownWeaponEntity e = throwWeapon(ctx).out(levelled(ctx.look()), ctx.param("speed"), ctx.scaled("range", "range_per_level"))
                .hits(1.4, 1000, (o, w, t, th) -> reap(o, w, t, damage, drain));
        ctx.level().addFreshEntity(e);
        Kit.sound(ctx.level(), e.position(), ModSounds.SCYTHE_THROW.get(), 1.3f, 1f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Soul Rend
    // ------------------------------------------------------------------------------------------------------------

    public static boolean soulRend(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double range = ctx.scaled("range", "range_per_level");
        Vec3 origin = p.position().add(0, 1, 0);
        float drain = (float) (ctx.weaponDamage() * ctx.param("drain"));
        int drainTicks = (int) Math.round(ctx.param("drain_duration") * 20);
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity e : Targeting.inCone(ctx.level(), p, origin, ctx.look(), range, ctx.param("angle"), FWDamage.Kind.ABILITY)) {
            ctx.deal(e, ctx.damage(), 0);
            if (e.isAlive()) StatusService.apply(e, StatusType.SOUL_DRAIN, drainTicks, 1, drain, p.getUUID());
            ids.add(e.getId());
        }
        Kit.fx(ctx.level(), FxPayload.of(FxIds.SOULREAPER_REND).caster(p.getId()).pos(origin).dir(Targeting.flatLook(p)).scale((float) range)
                .power((float) ctx.param("angle")).entities(ids).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), origin, ModSounds.WEAPON_SWING_HEAVY.get(), 1.4f, 0.7f);
        Kit.sound(ctx.level(), origin, ModSounds.SOUL_PROJECTILE.get(), 1.2f, 0.6f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Reaping Whirl
    // ------------------------------------------------------------------------------------------------------------

    public static boolean reapingWhirl(AbilityContext ctx) {
        int ticks = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        float per = (float) (ctx.damage() * ctx.param("hit_fraction"));
        double drain = ctx.param("drain");
        ThrownWeaponEntity e = throwWeapon(ctx).orbit(ticks, ctx.param("radius"), 1.1)
                .hits(1.5, (int) ctx.param("rehit"), (o, w, t, th) -> reap(o, w, t, per, drain));
        ctx.level().addFreshEntity(e);
        Kit.active(ctx, ticks);
        Kit.sound(ctx.level(), e.position(), ModSounds.SCYTHE_THROW.get(), 1.4f, 0.8f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Death's Toll (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean deathsToll(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double radius = ctx.scaled("radius", "radius_per_level");
        int max = (int) Math.round(ctx.scaled("max_targets", "max_targets_per_level"));
        List<LivingEntity> marked = new ArrayList<>(Targeting.inRadius(ctx.level(), p, Kit.feet(p).add(0, 1, 0), radius, FWDamage.Kind.ABILITY));
        if (marked.isEmpty()) throw AbilityContext.fail("No souls to claim");
        if (marked.size() > max) marked = new ArrayList<>(marked.subList(0, max));
        int delay = (int) Math.round(ctx.param("mark_time") * 20);
        for (LivingEntity e : marked) StatusService.apply(e, StatusType.SOUL_DRAIN, delay + 60, 1, 0f, p.getUUID());
        List<Integer> ids = marked.stream().map(LivingEntity::getId).toList();
        Kit.fx(ctx.level(), FxPayload.of(FxIds.SOULREAPER_TOLL).caster(p.getId()).pos(p.position()).power(delay).entities(ids)
                .seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), p.position(), ModSounds.SOUL_PROJECTILE.get(), 2.5f, 0.35f);
        float per = (float) (ctx.damage() * ctx.param("hit_fraction"));
        double drain = ctx.param("drain");
        List<LivingEntity> hunt = marked;
        Kit.active(ctx, delay + 20 * hunt.size() / 3 + 40);
        Delayed.schedule(ctx, delay, (o, w) -> {
            ThrownWeaponEntity e = throwWeapon(ctx).hunt(hunt, 1.9).hits(1.6, 1000, (oo, ww, t, th) -> reap(oo, ww, t, per, drain));
            o.serverLevel().addFreshEntity(e);
            Kit.sound(o.serverLevel(), o.position(), ModSounds.SCYTHE_THROW.get(), 1.8f, 0.7f);
        });
        return true;
    }
}
