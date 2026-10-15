package com.fantasyweapons.weapons.verdantalmanac;

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
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Server-side gameplay for every Verdant Almanac spell. */
public final class VerdantAlmanacAbilities {
    private VerdantAlmanacAbilities() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // Photosynthesis (passive): mending in the sun while the Almanac is held
    // ------------------------------------------------------------------------------------------------------------

    /** Every second while held: every other second in daylight under the open sky, the bearer mends a little. */
    public static void sunlight(ServerPlayer p, ItemStack stack) {
        if (p.tickCount % 40 != 0) return;
        AbilityDefinition a = ((FantasyWeaponItem) stack.getItem()).definition().ability(VerdantAlmanac.PHOTOSYNTHESIS);
        if (FantasyWeaponItem.data(stack).abilityLevel(a) <= 0) return;
        if (!p.level().isDay() || !p.level().canSeeSky(p.blockPosition().above()) || p.getHealth() >= p.getMaxHealth()) return;
        p.heal(p.getMaxHealth() * (float) a.param("regen"));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Mending Vines
    // ------------------------------------------------------------------------------------------------------------

    public static boolean mendingVines(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        LivingEntity ally = Allies.targetOrSelf(p, ctx.param("range"));
        int ticks = (int) Math.round(ctx.param("duration") * 20);
        float perPulse = ally.getMaxHealth() * (float) ctx.param("heal") * Heals.power(ctx);
        Spells.status(ctx, ally, StatusType.MENDING, ticks, 1, perPulse);
        Heals.blessed(p, ctx.stack(), ally == p ? 0 : 1, ally == p);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.VERDANT_VINES).caster(p.getId()).pos(Spells.focus(p)).point(ally.getBoundingBox().getCenter())
                .power(ticks).entities(ally.getId()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), ally.position(), ModSounds.NATURE_GROWTH.get(), 1.2f, 1.4f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Pollen Bloom
    // ------------------------------------------------------------------------------------------------------------

    public static boolean pollenBloom(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Kit.aimTargetOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.param("duration") * 20);
        float poison = ctx.damage() / 2; // two pulses a second
        new SpellEffect(ctx, center, duration).onTick((o, w, s) -> {
            if (s.age() % 20 != 4) return;
            Vec3 c = center.add(0, 1, 0);
            int blessed = 0;
            for (LivingEntity ally : Allies.inRadius(o, c, radius)) {
                Spells.effect(ally, MobEffects.REGENERATION, 50, 1);
                Spells.effect(ally, MobEffects.DIG_SPEED, 50, 1);
                if (ally != o && s.once(ally.getId())) blessed++;
            }
            if (blessed > 0) Heals.blessed(o, w, blessed, false);
            for (LivingEntity e : Targeting.inRadius(o.serverLevel(), o, c, radius, FWDamage.Kind.ABILITY)) {
                Spells.status(o, w, e, StatusType.NATURE_POISON, 50, 1, poison);
            }
        }).start();
        Kit.fx(ctx.level(), FxPayload.of(FxIds.VERDANT_POLLEN).caster(p.getId()).pos(center).scale((float) radius).power(duration)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, SoundEvents.AZALEA_LEAVES_BREAK, 1.6f, 1.2f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Spring Eternal (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean springEternal(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Spells.aimAllyOrGround(p, ctx.param("range"));
        double radius = ctx.param("radius");
        int ticks = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        float power = Heals.power(ctx);
        List<Integer> blessed = new ArrayList<>();
        for (LivingEntity ally : Allies.inRadius(p, center, radius)) {
            var spring = Spells.status(ctx, ally, StatusType.SPRING, ticks, 1, ally.getMaxHealth() * (float) ctx.param("heal") * power);
            spring.extra = (float) ctx.param("save");
            Heals.heal(p, ctx.stack(), ally, (float) ctx.param("heal") * 3, power);
            blessed.add(ally.getId());
        }
        if (!blessed.contains(p.getId())) {
            // the caster always shares in the spring they call
            var spring = Spells.status(ctx, p, StatusType.SPRING, ticks, 1, p.getMaxHealth() * (float) ctx.param("heal") * power);
            spring.extra = (float) ctx.param("save");
            blessed.add(p.getId());
        }
        Heals.blessed(p, ctx.stack(), blessed.size() - 1, true);
        Kit.active(ctx, ticks);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.VERDANT_SPRING).caster(p.getId()).pos(center).scale((float) radius).power(ticks).entities(blessed)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.NATURE_GROWTH.get(), 3f, 0.8f);
        Kit.sound(ctx.level(), center, SoundEvents.AMETHYST_BLOCK_RESONATE, 2f, 1.4f);
        return true;
    }
}
