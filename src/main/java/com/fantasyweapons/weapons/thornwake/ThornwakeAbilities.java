package com.fantasyweapons.weapons.thornwake;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.MeleeContext;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.magic.Allies;
import com.fantasyweapons.magic.Heals;
import com.fantasyweapons.magic.SpellEffect;
import com.fantasyweapons.magic.Spells;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-side gameplay for every Thornwake spell. */
public final class ThornwakeAbilities {
    /** When each caster's Verdant Pulse last fired (game time): at most twice a second. */
    private static final Map<UUID, Long> lastPulse = new HashMap<>();

    private ThornwakeAbilities() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // Verdant Pulse (passive): each bolt landed heals the most wounded ally near
    // ------------------------------------------------------------------------------------------------------------

    public static void onHit(MeleeContext ctx) {
        AbilityDefinition a = ctx.weapon().ability(Thornwake.VERDANT_PULSE);
        int lvl = ctx.data().abilityLevel(a);
        if (lvl <= 0) return;
        ServerPlayer p = ctx.player();
        long now = p.level().getGameTime();
        Long last = lastPulse.get(p.getUUID());
        if (last != null && now - last < 10) return;
        lastPulse.put(p.getUUID(), now);
        LivingEntity ally = Allies.mostWounded(p, p.position(), a.param("radius"), Set.of());
        if (ally == null || ally.getHealth() >= ally.getMaxHealth()) return;
        Heals.heal(p, ctx.stack(), ally, (float) Spells.scaled(a, lvl, "heal", "heal_per_level"), Heals.power(p, ctx.weapon(), ctx.data(), 1));
        Kit.fx(p.serverLevel(), FxPayload.of(FxIds.THORN_PULSE).caster(p.getId()).pos(ctx.target().getBoundingBox().getCenter())
                .point(ally.getBoundingBox().getCenter()).level(0).entities(ally.getId()).build());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Lifebloom
    // ------------------------------------------------------------------------------------------------------------

    public static boolean lifebloom(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Spells.aimAllyOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.param("duration") * 20);
        float heal = (float) ctx.param("heal");
        float power = Heals.power(ctx);
        new SpellEffect(ctx, center, duration).onTick((o, w, s) -> {
            if (s.age() < 12 || s.age() % 20 != 12) return; // the flower opens first
            List<Integer> healed = new ArrayList<>();
            for (LivingEntity ally : Allies.inRadius(o, center, radius)) {
                Heals.heal(o, w, ally, heal, power);
                healed.add(ally.getId());
            }
            Kit.fx(o.serverLevel(), FxPayload.of(FxIds.THORN_PULSE).caster(o.getId()).pos(center.add(0, 1.2, 0)).level(2).scale((float) radius)
                    .entities(healed).build());
        }).start();
        Kit.fx(ctx.level(), FxPayload.of(FxIds.THORN_LIFEBLOOM).caster(p.getId()).pos(center).point(Spells.focus(p)).scale((float) radius).power(duration)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.NATURE_GROWTH.get(), 1.4f, 1.2f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Grasping Grove
    // ------------------------------------------------------------------------------------------------------------

    public static boolean graspingGrove(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Kit.aimTargetOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.param("duration") * 20);
        int root = (int) Math.round(ctx.param("root") * 20);
        float damage = ctx.damage();
        float poison = (float) (ctx.weaponDamage() * 0.05);
        new SpellEffect(ctx, center, duration).onTick((o, w, s) -> {
            if (s.age() < 6) return;
            for (LivingEntity e : Targeting.inRadius(o.serverLevel(), o, center.add(0, 0.5, 0), radius, FWDamage.Kind.ABILITY)) {
                if (s.once(e.getId())) {
                    FWDamage.deal(o, w, e, damage, FWDamage.Kind.ABILITY, Element.NATURE, 0);
                    Spells.status(o, w, e, StatusType.ROOTED, root, 1, 0);
                }
                if (s.age() % 20 == 0) Spells.status(o, w, e, StatusType.NATURE_POISON, 60, 1, poison);
            }
            if (s.age() % 20 == 6) {
                for (LivingEntity ally : Allies.inRadius(o, center.add(0, 0.5, 0), radius)) Spells.effect(ally, MobEffects.REGENERATION, 50, 1);
            }
        }).start();
        Kit.fx(ctx.level(), FxPayload.of(FxIds.THORN_GROVE).caster(p.getId()).pos(center).scale((float) radius).power(duration)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.NATURE_GROWTH.get(), 1.8f, 0.7f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Worldtree's Boon (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean worldtreesBoon(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Spells.aimAllyOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        float heal = (float) ctx.param("heal");
        float power = Heals.power(ctx);
        new SpellEffect(ctx, center, duration).onTick((o, w, s) -> {
            if (s.age() < 20 || s.age() % 20 != 0) return; // the tree grows for a second first
            List<Integer> healed = new ArrayList<>();
            for (LivingEntity ally : Allies.inRadius(o, center, radius)) {
                Spells.cleanse(ally);
                Heals.heal(o, w, ally, heal, power);
                healed.add(ally.getId());
            }
            if (!healed.isEmpty()) Kit.fx(o.serverLevel(), FxPayload.of(FxIds.THORN_PULSE).caster(o.getId()).pos(center).level(1).entities(healed).build());
        }).start();
        Kit.active(ctx, duration);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.THORN_WORLDTREE).caster(p.getId()).pos(center).scale((float) radius).power(duration)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.NATURE_GROWTH.get(), 3f, 0.5f);
        Kit.sound(ctx.level(), center, SoundEvents.AZALEA_LEAVES_PLACE, 2f, 0.6f);
        return true;
    }
}
