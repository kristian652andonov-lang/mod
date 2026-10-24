package com.fantasyweapons.weapons.rimeheart;

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
import com.fantasyweapons.weapon.FantasyWeaponItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Server-side gameplay for every Rimeheart spell. */
public final class RimeheartAbilities {
    private RimeheartAbilities() {
    }

    /** Destroys every projectile inside {@code box} (and {@code keep}) not loosed by the caster's side; returns where they broke. */
    static List<Vec3> shatterProjectiles(ServerPlayer caster, AABB box, java.util.function.Predicate<Vec3> keep) {
        List<Vec3> broke = new ArrayList<>();
        for (Projectile proj : caster.serverLevel().getEntitiesOfClass(Projectile.class, box)) {
            Entity owner = proj.getOwner();
            if (owner != null && Allies.is(caster, owner)) continue;
            if (!keep.test(proj.position())) continue;
            broke.add(proj.position());
            proj.discard();
        }
        return broke;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Glacial Ward
    // ------------------------------------------------------------------------------------------------------------

    public static boolean glacialWard(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        LivingEntity ally = Allies.targetOrSelf(p, ctx.param("range"));
        int ticks = (int) Math.round(ctx.param("duration") * 20);
        Heals.barrier(p, ally, (float) ctx.param("shield"), Heals.power(ctx), ticks);
        Spells.status(ctx, ally, StatusType.GLACIAL_WARD, ticks, 1, 0);
        Heals.blessed(p, ctx.stack(), ally == p ? 0 : 1, ally == p);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.MAGE_BLESS).caster(p.getId()).pos(Spells.focus(p)).point(ally.getBoundingBox().getCenter())
                .level(Spells.BLESS_WARD).entities(ally.getId()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), ally.position(), ModSounds.ICE_BURST.get(), 1.0f, 1.3f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Permafrost (passive): an aura about the Rimeheart's bearer
    // ------------------------------------------------------------------------------------------------------------

    /** Every second while held: allies around the bearer take less damage for a little longer. */
    public static void aura(ServerPlayer p, ItemStack stack) {
        AbilityDefinition a = ((FantasyWeaponItem) stack.getItem()).definition().ability(Rimeheart.PERMAFROST);
        int lvl = FantasyWeaponItem.data(stack).abilityLevel(a);
        if (lvl <= 0) return;
        float reduction = (float) Spells.scaled(a, lvl, "reduction", "reduction_per_level");
        for (LivingEntity ally : Allies.inRadius(p, p.position(), a.param("radius"))) {
            Spells.status(p, stack, ally, StatusType.PERMAFROST, 30, 1, reduction);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Frost Rampart
    // ------------------------------------------------------------------------------------------------------------

    public static boolean frostRampart(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Kit.aimTargetOrGround(p, ctx.param("range"));
        double width = ctx.scaled("width", "width_per_level");
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        float damage = ctx.damage();
        Vec3 facing = Targeting.flatLook(p);
        Vec3 across = facing.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 a = center.add(across.scale(-width / 2)), b = center.add(across.scale(width / 2));
        AABB box = new AABB(a, b).inflate(1.4).expandTowards(0, 4, 0);
        new SpellEffect(ctx, center, duration).onTick((o, w, s) -> {
            ServerLevel level = o.serverLevel();
            for (LivingEntity e : Targeting.alongPath(level, o, a.add(0, 1, 0), b.add(0, 1, 0), 1.1, FWDamage.Kind.ABILITY)) {
                if (e.getY() > center.y + 3.6) continue;
                if (s.once(e.getId())) {
                    FWDamage.deal(o, w, e, damage, FWDamage.Kind.ABILITY, Element.ICE, 0);
                    Spells.status(o, w, e, StatusType.FROSTBITE, 80, 2, 0.12f);
                }
                // held at the wall's face: pushed back to its own side and stopped
                double side = Math.signum(e.position().subtract(center).dot(facing));
                if (side == 0) side = -1;
                Vec3 v = e.getDeltaMovement();
                double into = v.dot(facing) * side;
                if (into < 0) v = v.subtract(facing.scale(into * side));
                e.setDeltaMovement(v.add(facing.scale(side * 0.25)));
                e.hurtMarked = true;
            }
            List<Vec3> broke = shatterProjectiles(o, box, pos -> Targeting.distanceToSegmentSqr(new Vec3(pos.x, center.y + 1, pos.z), a.add(0, 1, 0), b.add(0, 1, 0)) < 1.6);
            for (Vec3 at : broke) {
                Kit.fx(level, FxPayload.of(FxIds.MAGE_WARD_SHATTER).caster(o.getId()).pos(at).level(1).build());
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.6f, 1.6f);
            }
        }).start();
        Kit.fx(ctx.level(), FxPayload.of(FxIds.RIME_RAMPART).caster(p.getId()).pos(center).dir(across).scale((float) width).power(duration)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.ICE_BURST.get(), 1.8f, 0.7f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Everfrost Bastion (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean everfrostBastion(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 center = Spells.aimAllyOrGround(p, ctx.param("range"));
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        float ward = (float) ctx.param("ward");
        float shield = (float) ctx.param("shield");
        float power = Heals.power(ctx);
        float pulse = (float) (ctx.damage() * ctx.param("pulse_fraction"));
        AABB box = new AABB(center, center).inflate(radius + 1, 0, radius + 1).expandTowards(0, 10, 0);
        new SpellEffect(ctx, center, duration).onTick((o, w, s) -> {
            if (s.age() < 10) return; // the spires are still rising
            ServerLevel level = o.serverLevel();
            if (s.age() % 10 == 0) {
                for (LivingEntity ally : Allies.inRadius(o, center, radius)) {
                    Spells.status(o, w, ally, StatusType.FROST_WARD, 25, 1, ward);
                    if (s.age() % 40 == 0) Heals.barrier(o, ally, shield, power, 60);
                }
            }
            if (s.age() % 20 == 0) {
                for (LivingEntity e : Targeting.inRadius(level, o, center, radius, FWDamage.Kind.ABILITY)) {
                    FWDamage.deal(o, w, e, pulse, FWDamage.Kind.ABILITY, Element.ICE, 0);
                    var frost = Spells.status(o, w, e, StatusType.FROSTBITE, 60, 1, 0.12f);
                    if (frost.stacks >= StatusType.FROSTBITE.maxStacks() && StatusService.stacks(e, StatusType.FROZEN) == 0) {
                        Spells.status(o, w, e, StatusType.FROZEN, 30, 1, 0);
                    }
                }
            }
            for (Vec3 at : shatterProjectiles(o, box, pos -> pos.subtract(center).horizontalDistance() < radius + 0.5)) {
                Kit.fx(level, FxPayload.of(FxIds.MAGE_WARD_SHATTER).caster(o.getId()).pos(at).level(1).build());
            }
        }).start();
        Kit.active(ctx, duration);
        Kit.fx(ctx.level(), FxPayload.of(FxIds.RIME_BASTION).caster(p.getId()).pos(center).scale((float) radius).power(duration)
                .level(ctx.abilityLevel()).seed(Kit.seed(ctx.level())).build());
        Kit.sound(ctx.level(), center, ModSounds.ICE_BURST.get(), 3f, 0.5f);
        return true;
    }
}
