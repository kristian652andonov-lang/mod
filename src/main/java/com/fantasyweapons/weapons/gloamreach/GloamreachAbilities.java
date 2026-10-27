package com.fantasyweapons.weapons.gloamreach;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.magic.SpellEffect;
import com.fantasyweapons.magic.Spells;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Server-side gameplay for every Gloamreach spell. */
public final class GloamreachAbilities {
    private GloamreachAbilities() {
    }

    /** Decay (passive): every Gloamreach curse also leaves its victim rotting, open to harm and slow to heal. */
    private static void decay(AbilityContext ctx, LivingEntity e, int ticks) {
        AbilityDefinition a = ctx.weapon().ability(Gloamreach.DECAY);
        int lvl = ctx.data().abilityLevel(a);
        if (lvl <= 0) return;
        Spells.status(ctx, e, StatusType.DECAY, ticks, 1, (float) Spells.scaled(a, lvl, "vulnerability", "vulnerability_per_level"));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Withering Curse
    // ------------------------------------------------------------------------------------------------------------

    public static boolean witheringCurse(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        LivingEntity target = Spells.enemy(ctx, ctx.param("range"));
        int ticks = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        float bite = ctx.damage();
        var curse = Spells.status(ctx, target, StatusType.WITHERING, ticks, 1, bite);
        curse.potency = bite;
        curse.extra = bite;
        decay(ctx, target, ticks);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.GLOAM_WITHER).caster(p.getId()).pos(Spells.focus(p)).point(target.getBoundingBox().getCenter())
                .power(ticks).entities(target.getId()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), target.position(), ModSounds.VOID_SLASH.get(), 1.0f, 0.6f);
        Kit.sound(ctx.level(), target.position(), SoundEvents.WITHER_AMBIENT, 0.5f, 1.6f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Miasma
    // ------------------------------------------------------------------------------------------------------------

    public static boolean miasma(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Kit.aimTargetOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.param("duration") * 20);
        int rot = (int) Math.round(ctx.param("rot_duration") * 20);
        float perLayer = ctx.damage();
        new SpellEffect(ctx, center, duration).onTick((o, w, s) -> {
            if (s.age() % 20 != 5) return;
            for (LivingEntity e : Targeting.inRadius(o.serverLevel(), o, center.add(0, 1, 0), radius, FWDamage.Kind.ABILITY)) {
                Spells.status(o, w, e, StatusType.ROT, rot, 1, perLayer);
                Spells.effect(e, MobEffects.WEAKNESS, rot, 0);
                decay(ctx, e, rot);
            }
        }).start();
        Kit.fx(ctx.level(), FxPayload.of(FxIds.GLOAM_MIASMA).caster(p.getId()).pos(center).scale((float) radius).power(duration)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.RIFT_OPEN.get(), 1.2f, 0.6f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Dirge of Ruin (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean dirgeOfRuin(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Kit.aimTargetOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int ticks = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        float pulse = ctx.damage();
        List<Integer> marked = new ArrayList<>();
        for (LivingEntity e : Targeting.inRadius(ctx.level(), p, center, radius, FWDamage.Kind.ABILITY)) {
            Spells.status(ctx, e, StatusType.RUIN, ticks, 1, pulse);
            Spells.effect(e, MobEffects.WEAKNESS, ticks, 1);
            decay(ctx, e, ticks);
            marked.add(e.getId());
        }
        Kit.active(ctx, ticks);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.GLOAM_DIRGE).caster(p.getId()).pos(center).scale((float) radius).power(ticks).entities(marked)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.VOID_DIMENSION.get(), 2.5f, 0.6f);
        Kit.sound(ctx.level(), center, SoundEvents.BELL_RESONATE, 2.5f, 0.5f);
        return true;
    }
}
