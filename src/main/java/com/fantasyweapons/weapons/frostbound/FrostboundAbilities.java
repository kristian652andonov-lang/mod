package com.fantasyweapons.weapons.frostbound;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.magic.Allies;
import com.fantasyweapons.magic.Heals;
import com.fantasyweapons.magic.SpellEffect;
import com.fantasyweapons.magic.Spells;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Server-side gameplay for every Frostbound Grimoire spell. */
public final class FrostboundAbilities {
    private FrostboundAbilities() {
    }

    /** Rime Armor (passive): every spell cast lays another plate of rime over the caster. */
    private static void rime(AbilityContext ctx) {
        AbilityDefinition a = ctx.weapon().ability(FrostboundGrimoire.RIME_ARMOR);
        int lvl = ctx.data().abilityLevel(a);
        if (lvl <= 0) return;
        int ticks = (int) Math.round(Spells.scaled(a, lvl, "duration", "duration_per_level") * 20);
        Spells.status(ctx, ctx.player(), StatusType.RIME_ARMOR, ticks, 1, 0);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Frozen Tomb
    // ------------------------------------------------------------------------------------------------------------

    public static boolean frozenTomb(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        LivingEntity target = Spells.enemy(ctx, ctx.param("range"));
        int ticks = (int) Math.round(ctx.scaled("freeze", "freeze_per_level") * 20);
        float damage = ctx.damage();
        Spells.status(ctx, target, StatusType.FROZEN, ticks, 1, 0);
        target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.FROST_TOMB).caster(p.getId()).pos(Spells.focus(p)).point(target.getBoundingBox().getCenter())
                .power(ticks).entities(target.getId()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), target.position(), ModSounds.ICE_BURST.get(), 1.4f, 1.0f);
        new SpellEffect(ctx, target.position(), ticks).onEnd((o, w, s) -> {
            if (!target.isAlive()) return;
            FWDamage.deal(o, w, target, damage, FWDamage.Kind.ABILITY, Element.ICE, FWDamage.FLAG_HEAVY);
            Spells.status(o, w, target, StatusType.FROSTBITE, 80, 2, 0.12f);
            Kit.fx(o.serverLevel(), FxPayload.of(FxIds.FROST_TOMB_SHATTER).caster(o.getId()).pos(target.getBoundingBox().getCenter())
                    .entities(target.getId()).seed(o.getRandom().nextLong()).build());
            Kit.sound(o.serverLevel(), target.position(), net.minecraft.sounds.SoundEvents.GLASS_BREAK, 1.3f, 0.8f);
        }).start();
        rime(ctx);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Shatterguard
    // ------------------------------------------------------------------------------------------------------------

    public static boolean shatterguard(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        LivingEntity ally = Allies.targetOrSelf(p, ctx.param("range"));
        int ticks = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        StatusService.remove(ally, StatusType.SHATTERGUARD);
        Spells.status(ctx, ally, StatusType.SHATTERGUARD, ticks, (int) ctx.param("shards"), 0);
        Heals.blessed(p, ctx.stack(), ally == p ? 0 : 1, ally == p);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.MAGE_BLESS).caster(p.getId()).pos(Spells.focus(p)).point(ally.getBoundingBox().getCenter())
                .level(Spells.BLESS_SHATTERGUARD).entities(ally.getId()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), ally.position(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, 1.2f);
        rime(ctx);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Absolute Winter (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean absoluteWinter(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Kit.aimTargetOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        int freeze = (int) Math.round(ctx.param("freeze") * 20);
        float ward = (float) ctx.param("ward");
        float pulse = (float) (ctx.damage() * ctx.param("pulse_fraction"));
        float shatter = (float) (ctx.damage() * ctx.param("freeze_fraction"));
        new SpellEffect(ctx, center, duration).onTick((o, w, s) -> {
            if (s.age() < 8) return;
            ServerLevel level = o.serverLevel();
            if (s.age() % 10 == 0) for (LivingEntity ally : Allies.inRadius(o, center, radius)) Spells.status(o, w, ally, StatusType.FROST_WARD, 25, 1, ward);
            if (s.age() % 20 != 0) return;
            List<Integer> frozen = new ArrayList<>();
            for (LivingEntity e : Targeting.inRadius(level, o, center, radius, FWDamage.Kind.ABILITY)) {
                FWDamage.deal(o, w, e, pulse, FWDamage.Kind.ABILITY, Element.ICE, 0);
                if (StatusService.stacks(e, StatusType.FROZEN) > 0) continue;
                var frost = Spells.status(o, w, e, StatusType.FROSTBITE, 60, 1, 0.13f);
                if (frost.stacks >= StatusType.FROSTBITE.maxStacks()) {
                    StatusService.remove(e, StatusType.FROSTBITE);
                    Spells.status(o, w, e, StatusType.FROZEN, freeze, 1, 0);
                    FWDamage.deal(o, w, e, shatter, FWDamage.Kind.ABILITY, Element.ICE, FWDamage.FLAG_HEAVY);
                    frozen.add(e.getId());
                }
            }
            if (!frozen.isEmpty()) {
                Kit.fx(level, FxPayload.of(FxIds.FROST_WINTER_FREEZE).caster(o.getId()).pos(center).entities(frozen).power(freeze).build());
                Kit.sound(level, center, ModSounds.ICE_BURST.get(), 1.5f, 1.4f);
            }
        }).start();
        Kit.active(ctx, duration);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.FROST_WINTER).caster(p.getId()).pos(center).scale((float) radius).power(duration)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.ICE_BURST.get(), 3f, 0.45f);
        rime(ctx);
        return true;
    }
}
