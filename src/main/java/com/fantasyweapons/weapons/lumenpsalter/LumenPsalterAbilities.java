package com.fantasyweapons.weapons.lumenpsalter;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.magic.Allies;
import com.fantasyweapons.magic.Heals;
import com.fantasyweapons.magic.Spells;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.status.StatusType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Server-side gameplay for every Lumen Psalter spell. */
public final class LumenPsalterAbilities {
    private LumenPsalterAbilities() {
    }

    /** Chorus (passive): each other ally blessed adds a voice to the caster's chorus. */
    private static void chorus(AbilityContext ctx, int others) {
        AbilityDefinition a = ctx.weapon().ability(LumenPsalter.CHORUS);
        int lvl = ctx.data().abilityLevel(a);
        if (lvl <= 0 || others <= 0) return;
        int ticks = (int) Math.round(Spells.scaled(a, lvl, "duration", "duration_per_level") * 20);
        Spells.status(ctx, ctx.player(), StatusType.CHOIR, ticks, others, 0);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Hymn of Swiftness
    // ------------------------------------------------------------------------------------------------------------

    public static boolean hymnOfSwiftness(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        LivingEntity target = Allies.targetOrSelf(p, ctx.param("range"));
        int ticks = (int) Math.round(ctx.param("duration") * 20);
        float speed = (float) ctx.scaled("attack_speed", "attack_speed_per_level");
        List<Integer> blessed = new ArrayList<>();
        for (LivingEntity ally : Allies.inRadius(p, target.position(), ctx.param("radius"))) {
            Spells.effect(ally, MobEffects.MOVEMENT_SPEED, ticks, 1);
            Spells.effect(ally, MobEffects.DIG_SPEED, ticks, 1);
            Spells.effect(ally, MobEffects.JUMP, ticks, 0);
            Spells.status(ctx, ally, StatusType.SWIFT, ticks, 1, speed);
            blessed.add(ally.getId());
        }
        int others = (int) blessed.stream().filter(id -> id != p.getId()).count();
        Heals.blessed(p, ctx.stack(), others, blessed.contains(p.getId()));
        chorus(ctx, others);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.LUMEN_HYMN).caster(p.getId()).pos(Spells.focus(p)).point(target.getBoundingBox().getCenter())
                .scale((float) ctx.param("radius")).entities(blessed).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), target.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6f, 1.6f);
        Kit.sound(ctx.level(), target.position(), SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2f, 1.2f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Halo of Fortitude
    // ------------------------------------------------------------------------------------------------------------

    public static boolean haloOfFortitude(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        LivingEntity ally = Allies.targetOrSelf(p, ctx.param("range"));
        int ticks = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        Spells.status(ctx, ally, StatusType.HALO, ticks, 1, 0);
        Spells.effect(ally, MobEffects.DAMAGE_RESISTANCE, ticks, 1);
        Heals.barrier(p, ally, (float) ctx.param("shield"), Heals.power(ctx), ticks);
        Heals.blessed(p, ctx.stack(), ally == p ? 0 : 1, ally == p);
        chorus(ctx, ally == p ? 0 : 1);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.MAGE_BLESS).caster(p.getId()).pos(Spells.focus(p)).point(ally.getBoundingBox().getCenter())
                .level(Spells.BLESS_HALO).entities(ally.getId()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), ally.position(), SoundEvents.BEACON_POWER_SELECT, 1.4f, 1.8f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Ascension (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean ascension(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Spells.aimAllyOrGround(p, ctx.param("range"));
        int ticks = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        float ray = ctx.damage();
        List<Integer> blessed = new ArrayList<>();
        for (LivingEntity ally : Allies.inRadius(p, center, ctx.param("radius"))) {
            Spells.status(ctx, ally, StatusType.ASCENDED, ticks, 1, ray);
            Spells.effect(ally, MobEffects.SLOW_FALLING, ticks, 0);
            Spells.effect(ally, MobEffects.MOVEMENT_SPEED, ticks, 0);
            blessed.add(ally.getId());
        }
        if (!blessed.contains(p.getId())) {
            Spells.status(ctx, p, StatusType.ASCENDED, ticks, 1, ray);
            blessed.add(p.getId());
        }
        int others = blessed.size() - 1;
        Heals.blessed(p, ctx.stack(), others, true);
        chorus(ctx, others);
        Kit.active(ctx, ticks);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.LUMEN_ASCENSION).caster(p.getId()).pos(center).scale((float) ctx.param("radius")).power(ticks)
                .entities(blessed).level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, SoundEvents.BEACON_ACTIVATE, 3f, 1.5f);
        Kit.sound(ctx.level(), center, SoundEvents.AMETHYST_BLOCK_RESONATE, 3f, 0.8f);
        return true;
    }
}
