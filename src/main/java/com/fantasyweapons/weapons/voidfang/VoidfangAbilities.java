package com.fantasyweapons.weapons.voidfang;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.MeleeContext;
import com.fantasyweapons.combat.MeleeHandler;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.network.Fx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.progression.ExpTier;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.registry.ModAttachments;
import com.fantasyweapons.sound.ModSounds;
import com.fantasyweapons.status.StatusEffects;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.world.AreaEffect;
import com.fantasyweapons.world.AreaEffectManager;
import com.fantasyweapons.world.SafeTeleport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Server-side gameplay for every Voidfang ability. Visuals are requested from clients via {@link FxPayload}s.
 */
public final class VoidfangAbilities {
    private VoidfangAbilities() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // Void Mark (passive)
    // ------------------------------------------------------------------------------------------------------------

    public static void onMeleeHit(MeleeContext ctx) {
        applyMark(ctx.player(), ctx.weapon(), ctx.data(), ctx.target(), ctx.heavy() ? 2 : 1);
    }

    /** Adds Void Mark stacks if the passive is unlocked on this weapon. */
    static void applyMark(ServerPlayer player, WeaponDefinition def, WeaponData data, LivingEntity target, int stacks) {
        AbilityDefinition mark = def.ability(Voidfang.VOID_MARK);
        if (mark == null || !target.isAlive()) return;
        int lvl = data.abilityLevel(mark);
        if (lvl <= 0) return;
        int max = (int) Math.round(mark.param("max_stacks") + mark.param("max_stacks_per_level") * (lvl - 1));
        float potency = (float) (mark.param("bonus_per_stack") + mark.param("bonus_per_stack_per_level") * (lvl - 1));
        int duration = (int) Math.round(mark.param("duration") * 20);
        int current = StatusService.stacks(target, StatusType.VOID_MARK);
        int add = Math.max(0, Math.min(stacks, max - current));
        StatusService.apply(target, StatusType.VOID_MARK, duration, Math.max(add, current == 0 ? 1 : 0), potency, player.getUUID());
    }

    /** Detonates all Void Marks on a target. @return stacks consumed */
    static int detonateMarks(ServerPlayer player, ItemStack weapon, WeaponDefinition def, WeaponData data, LivingEntity target, float weaponDamage) {
        int stacks = StatusService.stacks(target, StatusType.VOID_MARK);
        if (stacks <= 0) return 0;
        AbilityDefinition mark = def.ability(Voidfang.VOID_MARK);
        StatusService.remove(target, StatusType.VOID_MARK);
        if (mark != null) {
            float dmg = (float) (weaponDamage * mark.param("detonate_damage") * stacks);
            FWDamage.deal(player, weapon, target, dmg, FWDamage.Kind.ABILITY, Element.VOID, 0);
        }
        Fx.near(player.serverLevel(), FxPayload.of(FxIds.VOID_MARK_POP).caster(player.getId()).pos(target.getBoundingBox().getCenter())
                .level(stacks).scale(target.getBbHeight()).build());
        return stacks;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Void Slash
    // ------------------------------------------------------------------------------------------------------------

    public static boolean voidSlash(AbilityContext ctx) {
        double range = ctx.scaled("range", "range_per_level");
        double width = ctx.scaled("width", "width_per_level");
        double speed = ctx.param("speed");
        Vec3 start = ctx.eye().subtract(0, 0.35, 0);
        Vec3 dir = ctx.look().normalize();
        Vec3 end = start.add(dir.scale(range));
        HitResult wall = ctx.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.player()));
        if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation();
        double dist = Math.max(1.0, end.distanceTo(start));

        AreaEffectManager.add(new SlashWave(ctx, start, dir, dist, width, speed, ctx.damage()));
        Fx.near(ctx.level(), FxPayload.of(FxIds.VOID_SLASH).caster(ctx.player().getId()).pos(start).dir(dir)
                .power((float) speed).scale((float) width).level(ctx.abilityLevel()).point(end)
                .seed(ctx.level().random.nextLong()).build());
        sound(ctx.level(), start, ModSounds.VOID_SLASH.get(), 1.2f, 0.9f + ctx.charge() * 0.2f);
        return true;
    }

    /** A crescent that travels forward and damages each entity it passes once. */
    static final class SlashWave extends AreaEffect {
        private final Vec3 start;
        private final Vec3 dir;
        private final double dist;
        private final double width;
        private final double speed;
        private final float damage;
        private final WeaponDefinition def;
        private final WeaponData data;

        SlashWave(AbilityContext ctx, Vec3 start, Vec3 dir, double dist, double width, double speed, float damage) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), start, (int) Math.ceil(dist / speed) + 1);
            this.start = start;
            this.dir = dir;
            this.dist = dist;
            this.width = width;
            this.speed = speed;
            this.damage = damage;
            this.def = ctx.weapon();
            this.data = ctx.data();
        }

        @Override
        protected void tick(ServerPlayer owner) {
            double a = Math.min(dist, speed * (age - 1));
            double b = Math.min(dist, speed * age);
            Vec3 from = start.add(dir.scale(a));
            Vec3 to = start.add(dir.scale(b));
            ItemStack weapon = weapon(owner);
            for (LivingEntity e : Targeting.alongPath(level, owner, from, to, width, FWDamage.Kind.ABILITY)) {
                if (!hitOnce.add(e.getId())) continue;
                FWDamage.deal(owner, weapon, e, damage, FWDamage.Kind.ABILITY, Element.VOID, 0);
                applyMark(owner, def, data, e, 1);
                e.push(dir.x * 0.6, 0.15, dir.z * 0.6);
                e.hurtMarked = true;
            }
            if (b >= dist) finished = true;
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Void Blink
    // ------------------------------------------------------------------------------------------------------------

    public static boolean voidBlink(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        if (p.isPassenger() || p.isSleeping()) throw AbilityContext.fail("Cannot blink right now");
        double distance = ctx.scaled("distance", "distance_per_level") * (0.5 + 0.5 * Math.min(1f, ctx.charge()));
        Vec3 look = ctx.look();
        // keep blinks mostly horizontal: clamp pitch to +-35 degrees
        double pitch = Mth.clamp(Math.asin(look.y), Math.toRadians(-35), Math.toRadians(35));
        Vec3 flat = Targeting.flatLook(p);
        Vec3 dir = new Vec3(flat.x * Math.cos(pitch), Math.sin(pitch), flat.z * Math.cos(pitch)).normalize();

        Vec3 start = p.position();
        Vec3 dest = SafeTeleport.along(p, dir, distance);
        if (dest == null) throw AbilityContext.fail("No room to blink");

        float damage = ctx.damage();
        List<Integer> hit = new ArrayList<>();
        Vec3 mid = new Vec3(0, p.getBbHeight() * 0.5, 0);
        for (LivingEntity e : Targeting.alongPath(ctx.level(), p, start.add(mid), dest.add(mid), 1.3, FWDamage.Kind.ABILITY)) {
            ctx.deal(e, (float) (damage * ctx.param("path_damage")), 0);
            applyMark(p, ctx.weapon(), ctx.data(), e, 1);
            hit.add(e.getId());
        }

        SafeTeleport.teleport(p, dest);

        double radius = ctx.scaled("slash_radius", "slash_radius_per_level");
        for (LivingEntity e : Targeting.inRadius(ctx.level(), p, dest.add(mid), radius, FWDamage.Kind.ABILITY)) {
            ctx.deal(e, damage, 0);
            applyMark(p, ctx.weapon(), ctx.data(), e, 1);
            MeleeHandler.applyKnockback(p, e, 0.6f);
            if (!hit.contains(e.getId())) hit.add(e.getId());
        }

        double riftSeconds = ctx.scaled("rift_duration", "rift_duration_per_level");
        boolean enhanced = ctx.abilityLevel() >= ctx.param("enhanced_level");
        Vec3 riftPos = start.add(0, p.getBbHeight() * 0.55, 0);
        if (enhanced) {
            AreaEffectManager.add(new LingeringRift(ctx, riftPos, (int) Math.round(riftSeconds * 20), 2.6,
                    (float) (damage * ctx.param("rift_damage"))));
        }
        Fx.near(ctx.level(), FxPayload.of(FxIds.VOID_BLINK).caster(p.getId()).pos(start).dir(dest).power(ctx.chargeMultiplier())
                .scale((float) riftSeconds).level(enhanced ? 1 : 0).entities(hit).point(riftPos)
                .seed(ctx.level().random.nextLong()).build());
        sound(ctx.level(), start, ModSounds.TELEPORT.get(), 1.0f, 1.0f);
        sound(ctx.level(), riftPos, ModSounds.RIFT_OPEN.get(), 0.9f, 1.1f);
        sound(ctx.level(), dest, ModSounds.VOID_SLASH.get(), 1.1f, 0.8f);
        return true;
    }

    /** Enhanced Void Blink: the rift left behind keeps cutting enemies close to it. */
    static final class LingeringRift extends AreaEffect {
        private final double radius;
        private final float pulseDamage;
        private final WeaponDefinition def;
        private final WeaponData data;

        LingeringRift(AbilityContext ctx, Vec3 pos, int lifetime, double radius, float pulseDamage) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), pos, lifetime);
            this.radius = radius;
            this.pulseDamage = pulseDamage;
            this.def = ctx.weapon();
            this.data = ctx.data();
        }

        @Override
        protected void tick(ServerPlayer owner) {
            if (age % 10 != 0) return;
            ItemStack weapon = weapon(owner);
            for (LivingEntity e : Targeting.inRadius(level, owner, pos, radius, FWDamage.Kind.ABILITY)) {
                FWDamage.deal(owner, weapon, e, pulseDamage, FWDamage.Kind.ABILITY, Element.VOID, 0);
                applyMark(owner, def, data, e, 1);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Rift Tear
    // ------------------------------------------------------------------------------------------------------------

    public static boolean riftTear(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double range = ctx.param("range");
        Vec3 look = ctx.look();
        LivingEntity aimed = Targeting.crosshair(p, range, FWDamage.Kind.ABILITY);
        // the rift hangs a body's height over the ground: at the enemy aimed at, else where the aim meets the ground
        // (aiming at the sky or into a wall, the ground nearest along the line of sight) - never up in the air
        Vec3 center;
        if (aimed != null && aimed.onGround()) {
            center = aimed.getBoundingBox().getCenter();
        } else {
            center = com.fantasyweapons.ability.kit.Kit.aimTargetOrGround(p, range).add(0, 1.0, 0);
        }
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        float total = ctx.damage();
        RiftTearEffect effect = new RiftTearEffect(ctx, center, duration, radius, total);
        AreaEffectManager.add(effect);
        p.getData(ModAttachments.ABILITY_RUNTIME).setActive(ctx.ability().id(), ctx.now() + duration);
        Fx.near(ctx.level(), FxPayload.of(FxIds.VOID_RIFT).caster(p.getId()).pos(center).dir(look).scale((float) radius)
                .power(duration).level(ctx.abilityLevel()).seed(ctx.level().random.nextLong()).build());
        sound(ctx.level(), center, ModSounds.RIFT_OPEN.get(), 1.6f, 0.7f);
        return true;
    }

    static final class RiftTearEffect extends AreaEffect {
        private final double radius;
        private final float pulseDamage;
        private final float collapseDamage;
        private final double pull;
        private final boolean advanced;
        private final WeaponDefinition def;
        private final WeaponData data;
        private final float weaponDamage;

        RiftTearEffect(AbilityContext ctx, Vec3 pos, int lifetime, double radius, float total) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), pos, lifetime);
            this.radius = radius;
            double collapseShare = ctx.param("collapse_share");
            int pulses = Math.max(1, lifetime / 5);
            this.pulseDamage = (float) (total * (1 - collapseShare) / pulses);
            this.collapseDamage = (float) (total * collapseShare);
            this.pull = ctx.param("pull") + 0.05 * (ctx.abilityLevel() - 1);
            this.advanced = ctx.abilityLevel() >= ctx.param("advanced_level");
            this.def = ctx.weapon();
            this.data = ctx.data();
            this.weaponDamage = ctx.weaponDamage();
        }

        @Override
        protected void tick(ServerPlayer owner) {
            for (LivingEntity e : Targeting.inRadius(level, owner, pos, radius * 1.8, FWDamage.Kind.ABILITY)) {
                Vec3 to = pos.subtract(e.getBoundingBox().getCenter());
                double d = to.length();
                if (d > 0.4) {
                    Vec3 v = to.normalize().scale(Math.min(pull, d * 0.2));
                    e.setDeltaMovement(e.getDeltaMovement().scale(0.7).add(v));
                    e.hurtMarked = true;
                    e.resetFallDistance();
                }
            }
            if (age % 5 == 0) {
                ItemStack weapon = weapon(owner);
                for (LivingEntity e : Targeting.inRadius(level, owner, pos, radius, FWDamage.Kind.ABILITY)) {
                    FWDamage.deal(owner, weapon, e, pulseDamage, FWDamage.Kind.ABILITY, Element.VOID, 0);
                    if (age % 20 == 0) applyMark(owner, def, data, e, 1);
                }
            }
        }

        @Override
        protected void onEnd(@Nullable ServerPlayer owner) {
            if (owner == null) return;
            ItemStack weapon = weapon(owner);
            List<Integer> hit = new ArrayList<>();
            for (LivingEntity e : Targeting.inRadius(level, owner, pos, radius * 1.25, FWDamage.Kind.ABILITY)) {
                FWDamage.deal(owner, weapon, e, collapseDamage, FWDamage.Kind.ABILITY, Element.VOID, 0);
                detonateMarks(owner, weapon, def, data, e, weaponDamage);
                Vec3 away = e.position().subtract(pos).normalize();
                e.push(away.x * 1.2, 0.6, away.z * 1.2);
                e.hurtMarked = true;
                hit.add(e.getId());
            }
            if (advanced) {
                for (LivingEntity e : Targeting.inRadius(level, owner, pos, 14, FWDamage.Kind.ABILITY)) {
                    if (StatusService.stacks(e, StatusType.VOID_MARK) > 0) detonateMarks(owner, weapon, def, data, e, weaponDamage);
                }
            }
            Fx.near(level, FxPayload.of(FxIds.VOID_RIFT_COLLAPSE).caster(owner.getId()).pos(pos).scale((float) radius)
                    .level(advanced ? 1 : 0).entities(hit).build());
            sound(level, pos, ModSounds.RIFT_CLOSE.get(), 1.8f, 0.8f);
            sound(level, pos, ModSounds.EXPLOSION.get(), 1.0f, 1.3f);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Void Execution
    // ------------------------------------------------------------------------------------------------------------

    public static boolean voidExecution(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double range = ctx.scaled("range", "range_per_level");
        LivingEntity target = Targeting.crosshair(p, range, FWDamage.Kind.ABILITY);
        if (target == null) throw AbilityContext.fail("No target in sight");

        Vec3 start = p.position();
        Vec3 toTarget = target.position().subtract(start);
        Vec3 flat = new Vec3(toTarget.x, 0, toTarget.z);
        flat = flat.lengthSqr() < 1e-4 ? Targeting.flatLook(p) : flat.normalize();
        double behind = target.getBbWidth() * 0.5 + 1.1;
        Vec3 dest = SafeTeleport.near(p, target.position().add(flat.scale(behind)));
        if (dest == null) dest = SafeTeleport.near(p, target.position().subtract(flat.scale(behind)));
        if (dest == null) dest = SafeTeleport.along(p, flat, Math.max(1.5, toTarget.length() - behind));
        if (dest != null) {
            SafeTeleport.teleport(p, dest);
            Vec3 face = target.position().subtract(dest);
            float yaw = (float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90f;
            p.connection.teleport(dest.x, dest.y, dest.z, yaw, p.getXRot());
        }

        ItemStack weapon = ctx.stack();
        int marks = detonateMarks(p, weapon, ctx.weapon(), ctx.data(), target, ctx.weaponDamage());
        float damage = ctx.damage() * (1f + (float) ctx.param("mark_bonus") * marks);
        boolean boss = ExpTier.classify(target) == ExpTier.BOSS;
        double threshold = ctx.scaled("execute_threshold", "execute_threshold_per_level");
        boolean executed = false;
        if (boss) {
            damage *= (float) ctx.param("boss_multiplier");
        } else if (target.getHealth() / Math.max(1f, target.getMaxHealth()) <= threshold || target.getHealth() <= damage) {
            damage = Math.max(damage, (target.getHealth() + target.getAbsorptionAmount()) * 4f + 1f);
            executed = true;
        }
        ctx.deal(target, damage, executed ? FWDamage.FLAG_EXECUTE : FWDamage.FLAG_CRIT);

        double cleave = ctx.param("cleave_radius");
        List<Integer> hit = new ArrayList<>(Collections.singletonList(target.getId()));
        for (LivingEntity e : Targeting.inRadius(ctx.level(), p, target.getBoundingBox().getCenter(), cleave, FWDamage.Kind.ABILITY)) {
            if (e == target) continue;
            ctx.deal(e, (float) (ctx.damage() * ctx.param("cleave_fraction")), 0);
            applyMark(p, ctx.weapon(), ctx.data(), e, 1);
            hit.add(e.getId());
        }
        Fx.near(ctx.level(), FxPayload.of(FxIds.VOID_EXECUTION).caster(p.getId()).pos(target.getBoundingBox().getCenter())
                .dir(start).point(dest != null ? dest : start).power(executed ? 1 : 0).level(ctx.abilityLevel()).entities(hit)
                .scale(target.getBbHeight()).seed(ctx.level().random.nextLong()).build());
        sound(ctx.level(), start, ModSounds.TELEPORT.get(), 1f, 0.8f);
        sound(ctx.level(), target.position(), ModSounds.VOID_EXECUTION.get(), 1.5f, 1f);
        return true;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Void Dimension (ultimate)
    // ------------------------------------------------------------------------------------------------------------

    public static boolean voidDimension(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double radius = ctx.scaled("radius", "radius_per_level");
        int duration = (int) Math.round(ctx.scaled("duration", "duration_per_level") * 20);
        int volleys = (int) Math.round(ctx.scaled("strikes", "strikes_per_level"));
        Vec3 center = com.fantasyweapons.ability.kit.Kit.feet(p).add(0, 1.0, 0);
        AreaEffectManager.add(new VoidDimensionEffect(ctx, center, duration, radius, volleys));
        p.getData(ModAttachments.ABILITY_RUNTIME).setActive(ctx.ability().id(), ctx.now() + duration);
        Fx.near(ctx.level(), FxPayload.of(FxIds.VOID_DIMENSION).caster(p.getId()).pos(center).scale((float) radius).power(duration)
                .level(ctx.abilityLevel()).seed(ctx.level().random.nextLong()).build());
        sound(ctx.level(), center, ModSounds.VOID_DIMENSION.get(), 2.0f, 1f);
        return true;
    }

    static final class VoidDimensionEffect extends AreaEffect {
        private final double radius;
        private final int volleys;
        private final int interval;
        private final float strikeDamage;
        private final float collapseDamage;
        private final WeaponDefinition def;
        private final WeaponData data;
        private final float weaponDamage;

        VoidDimensionEffect(AbilityContext ctx, Vec3 pos, int lifetime, double radius, int volleys) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), pos, lifetime);
            this.radius = radius;
            this.volleys = Math.max(1, volleys);
            this.interval = Math.max(2, lifetime / (this.volleys + 1));
            float total = ctx.damage();
            this.strikeDamage = (float) (total * ctx.param("strike_fraction"));
            this.collapseDamage = (float) (total * ctx.param("collapse_fraction"));
            this.def = ctx.weapon();
            this.data = ctx.data();
            this.weaponDamage = ctx.weaponDamage();
        }

        @Override
        protected void tick(ServerPlayer owner) {
            List<LivingEntity> inside = Targeting.inRadius(level, owner, pos, radius, FWDamage.Kind.ABILITY);
            for (LivingEntity e : inside) {
                StatusEffects.Instance g = StatusService.apply(e, StatusType.GRAVITY_BOUND, 10, 1, 0.08f, owner.getUUID());
                g.px = pos.x;
                g.py = pos.y;
                g.pz = pos.z;
            }
            if (age % interval == 0 && age / interval <= volleys) {
                ItemStack weapon = weapon(owner);
                for (LivingEntity e : inside) {
                    FWDamage.deal(owner, weapon, e, strikeDamage, FWDamage.Kind.ABILITY, Element.VOID, 0);
                    applyMark(owner, def, data, e, 1);
                    Fx.near(level, FxPayload.of(FxIds.VOID_DIMENSION_STRIKE).caster(owner.getId()).pos(e.getBoundingBox().getCenter())
                            .scale(e.getBbHeight()).seed(level.random.nextLong()).build());
                }
            }
        }

        @Override
        protected void onEnd(@Nullable ServerPlayer owner) {
            if (owner == null) return;
            ItemStack weapon = weapon(owner);
            List<Integer> hit = new ArrayList<>();
            for (LivingEntity e : Targeting.inRadius(level, owner, pos, radius, FWDamage.Kind.ABILITY)) {
                FWDamage.deal(owner, weapon, e, collapseDamage, FWDamage.Kind.ABILITY, Element.VOID, FWDamage.FLAG_HEAVY);
                detonateMarks(owner, weapon, def, data, e, weaponDamage);
                hit.add(e.getId());
            }
            Fx.near(level, FxPayload.of(FxIds.VOID_DIMENSION_COLLAPSE).caster(owner.getId()).pos(pos).scale((float) radius)
                    .entities(hit).build());
            sound(level, pos, ModSounds.EXPLOSION.get(), 2.5f, 0.6f);
            sound(level, pos, ModSounds.RIFT_CLOSE.get(), 2.0f, 0.5f);
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }
}
