package com.fantasyweapons.weapons.umbralcodex;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.magic.Spells;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.progression.ExpService;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusEffects;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.List;

/** Server-side gameplay for every Umbral Codex spell. */
public final class UmbralCodexAbilities {
    private UmbralCodexAbilities() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // Contagion (passive): the curses of the dead leap on to the living
    // ------------------------------------------------------------------------------------------------------------

    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        StatusEffects effects = victim.getExistingDataOrNull(ModAttachments.STATUS);
        if (effects == null || effects.isEmpty()) return;
        List<StatusEffects.Instance> curses = new ArrayList<>();
        for (StatusEffects.Instance i : effects.all()) if (i.type.curse() && i.source != null) curses.add(i);
        if (curses.isEmpty()) return;
        // the curses' caster must carry an Umbral Codex that knows Contagion
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(curses.get(0).source);
        if (owner == null) return;
        ItemStack codex = ItemStack.EMPTY;
        for (StatusEffects.Instance i : curses) {
            if (i.weapon == null) continue;
            ItemStack s = ExpService.findWeapon(owner, i.weapon);
            if (s.getItem() instanceof FantasyWeaponItem w && w.definition().id().equals("umbral_codex")) codex = s;
        }
        if (codex.isEmpty()) return;
        AbilityDefinition a = ((FantasyWeaponItem) codex.getItem()).definition().ability(UmbralCodex.CONTAGION);
        int lvl = FantasyWeaponItem.data(codex).abilityLevel(a);
        if (lvl <= 0) return;
        int jumps = (int) Math.floor(Spells.scaled(a, lvl, "jumps", "jumps_per_level"));
        List<Integer> caught = new ArrayList<>();
        Vec3 at = victim.getBoundingBox().getCenter();
        for (LivingEntity e : Targeting.inRadius(level, owner, at, a.param("radius"), FWDamage.Kind.ABILITY)) {
            if (e == victim || caught.size() >= jumps) continue;
            for (StatusEffects.Instance c : curses) {
                var copy = Spells.status(owner, codex, e, c.type, Math.max(40, c.duration), c.stacks, c.potency);
                copy.extra = c.extra;
            }
            caught.add(e.getId());
        }
        if (!caught.isEmpty()) {
            Kit.fx(level, FxPayload.of(FxIds.UMBRAL_CONTAGION).caster(owner.getId()).pos(at).entities(caught).build());
            Kit.sound(level, at, ModSounds.SOUL_PROJECTILE.get(), 1.0f, 0.6f);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Hex of Leeching
    // ------------------------------------------------------------------------------------------------------------

    public static boolean hexOfLeeching(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        LivingEntity target = Spells.enemy(ctx, ctx.param("range"));
        int ticks = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        Spells.status(ctx, target, StatusType.LEECH, ticks, 1, ctx.damage());
        Kit.fx(ctx.level(), FxPayload.of(FxIds.UMBRAL_HEX).caster(p.getId()).pos(Spells.focus(p)).point(target.getBoundingBox().getCenter())
                .power(ticks).entities(target.getId()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), target.position(), ModSounds.SOUL_PROJECTILE.get(), 1.2f, 0.7f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Bane Seeds
    // ------------------------------------------------------------------------------------------------------------

    public static boolean baneSeeds(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double range = ctx.param("range");
        int seeds = (int) ctx.param("seeds");
        int grow = (int) Math.round(ctx.param("grow") * 20);
        float perPulse = ctx.damage();
        float bloom = (float) (ctx.damage() * ctx.param("bloom") * 6);
        // the enemy under the crosshair first, then those nearest the aim
        List<LivingEntity> targets = new ArrayList<>();
        LivingEntity first = Targeting.crosshair(p, range, FWDamage.Kind.ABILITY);
        if (first != null) targets.add(first);
        Vec3 aim = Targeting.aimPoint(p, range);
        for (LivingEntity e : Targeting.inRadius(ctx.level(), p, aim, 10, FWDamage.Kind.ABILITY)) {
            if (targets.size() >= seeds) break;
            if (!targets.contains(e) && p.hasLineOfSight(e)) targets.add(e);
        }
        if (targets.isEmpty()) throw AbilityContext.fail("No enemy in sight");
        Vec3 from = Spells.focus(p);
        for (int i = 0; i < seeds; i++) {
            LivingEntity t = targets.get(i % targets.size());
            int flight = 8 + i * 3 + (int) (from.distanceTo(t.position()) / 2.2);
            Kit.fx(ctx.level(), FxPayload.of(FxIds.UMBRAL_SEED).caster(p.getId()).pos(from).power(flight).entities(t.getId()).level(i)
                    .seed(Kit.seed(ctx.level())).build());
            com.fantasyweapons.magic.SpellEffect.later(ctx, t.position(), flight, (o, w, s) -> {
                if (!t.isAlive()) return;
                var bane = Spells.status(o, w, t, StatusType.BANE, grow, 1, perPulse);
                bane.extra = bloom;
                o.serverLevel().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.SCULK_BLOCK_PLACE, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 0.7f);
            });
        }
        Kit.sound(ctx.level(), from, ModSounds.SOUL_PROJECTILE.get(), 1.2f, 1.2f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Plague of Night (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean plagueOfNight(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Kit.aimTargetOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int ticks = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        float pulse = ctx.damage();
        List<Integer> sick = new ArrayList<>();
        for (LivingEntity e : Targeting.inRadius(ctx.level(), p, center, radius, FWDamage.Kind.ABILITY)) {
            StatusService.remove(e, StatusType.PLAGUE);
            Spells.status(ctx, e, StatusType.PLAGUE, ticks, 1, pulse);
            Spells.effect(e, MobEffects.WEAKNESS, ticks, 1);
            sick.add(e.getId());
        }
        Kit.active(ctx, ticks);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.UMBRAL_PLAGUE).caster(p.getId()).pos(center).scale((float) radius).power(ticks).entities(sick)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.RIFT_OPEN.get(), 2.5f, 0.5f);
        Kit.sound(ctx.level(), center, ModSounds.VOID_DIMENSION.get(), 2f, 0.8f);
        return true;
    }
}
