package com.fantasyweapons.weapons.ultimate;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.network.Fx;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.weapon.Element;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.List;

/** What the two ultimate weapons' abilities share: annihilating damage, far-reaching effects and where a cast lands. */
public final class Cataclysm {
    /** How far away players still see (and hear) a cataclysm. */
    public static final double SEEN = 720;

    private Cataclysm() {
    }

    /** Sends an effect to every player in the level within {@link #SEEN} of it, and always to the caster. */
    public static void fx(ServerLevel level, ServerPlayer caster, FxPayload payload) {
        double r2 = SEEN * SEEN;
        for (ServerPlayer p : level.players()) {
            if (p == caster || p.position().distanceToSqr(payload.pos()) <= r2) Fx.only(p, payload);
        }
    }

    /**
     * Kills: deals the ability's damage, or the target's whole health several times over if that is more. Bosses lose a
     * third of their health per blow instead. Players are still spared when PvP is off.
     */
    public static float annihilate(ServerPlayer owner, ItemStack weapon, LivingEntity target, float damage, Element element) {
        float whole = target.getMaxHealth() + target.getAbsorptionAmount();
        boolean boss = target.getType().is(Tags.EntityTypes.BOSSES);
        float amount = Math.max(damage, boss ? whole * 0.34f : whole * 4f);
        target.invulnerableTime = 0;
        return FWDamage.deal(owner, weapon, target, amount, FWDamage.Kind.ABILITY, element, FWDamage.FLAG_EXECUTE);
    }

    public static float annihilate(AbilityContext ctx, LivingEntity target, Element element) {
        return annihilate(ctx.player(), ctx.stack(), target, ctx.damage(), element);
    }

    /**
     * Erases everything inside a vertical column from the bottom of the world to the top: creatures (the caster may
     * harm) are annihilated, everything else that is not a player - items, orbs, falling blocks, carts, arrows - is
     * deleted outright.
     *
     * @return the creatures struck
     */
    public static List<LivingEntity> eraseColumn(ServerPlayer owner, ItemStack weapon, ServerLevel level, Vec3 center, double radius, float damage, Element element) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(center.x - radius, level.getMinBuildHeight() - 16, center.z - radius,
                center.x + radius, level.getMaxBuildHeight() + 64, center.z + radius);
        List<LivingEntity> hit = new java.util.ArrayList<>();
        double r2 = radius * radius;
        for (net.minecraft.world.entity.Entity e : level.getEntities((net.minecraft.world.entity.Entity) null, box, x -> true)) {
            double dx = e.getX() - center.x, dz = e.getZ() - center.z;
            if (dx * dx + dz * dz > r2) continue;
            if (e instanceof LivingEntity le) {
                if (FWDamage.canHarm(owner, le, FWDamage.Kind.ABILITY)) {
                    annihilate(owner, weapon, le, damage, element);
                    hit.add(le);
                }
            } else if (!(e instanceof net.minecraft.world.entity.player.Player) && !(e instanceof net.neoforged.neoforge.entity.PartEntity<?>)
                    && !e.isRemoved()) {
                e.discard();
            }
        }
        return hit;
    }

    /** Ticks an ultimate power gathers - the sky darkening, the ground shaking, its might drawing in - before it strikes. */
    public static final int WINDUP = 44;

    /** Where a power gathers: where it will land, round the caster, or behind them (where the Arch Dragon breaks out). */
    private enum Focus { AIM, SELF, BEHIND }

    private record Gathering(Focus focus, int color) {
    }

    /** The powers that gather before they strike (the Great Slash holds before the blade instead). */
    private static final java.util.Map<String, Gathering> GATHERINGS = java.util.Map.ofEntries(
            java.util.Map.entry("fourth_dimension_ascension", new Gathering(Focus.AIM, 0xB57CFF)),
            java.util.Map.entry("arch_dragon_of_hell", new Gathering(Focus.BEHIND, 0xFF5A1E)),
            java.util.Map.entry("the_grand_earthquake", new Gathering(Focus.SELF, 0xD49A52)),
            java.util.Map.entry("thornspire_cataclysm", new Gathering(Focus.SELF, 0x8BE05A)),
            java.util.Map.entry("ice_rain", new Gathering(Focus.SELF, 0xA8ECFF)),
            java.util.Map.entry("beam_of_grant_execution", new Gathering(Focus.AIM, 0xFFD84A)),
            java.util.Map.entry("ultramassive_blackhole", new Gathering(Focus.AIM, 0xA066FF)),
            java.util.Map.entry("earthsplitting_eruption", new Gathering(Focus.AIM, 0xFF6A1A)),
            java.util.Map.entry("tectonic_breakage", new Gathering(Focus.AIM, 0xE09A48)),
            java.util.Map.entry("roots_of_the_great_tree", new Gathering(Focus.AIM, 0x7BDA62)),
            java.util.Map.entry("the_ice_age", new Gathering(Focus.AIM, 0x9EE6FF)));

    /** Whether this power gathers before it strikes. */
    public static boolean gathers(String abilityId) {
        return GATHERINGS.containsKey(abilityId);
    }

    /**
     * Casts a power after its wind-up: the caster's aim is held as it is now, everyone near sees the power gather where
     * it will strike, and after {@link #WINDUP} ticks it takes effect just as if it had been cast then.
     *
     * @return true: the power is under way (it goes on cooldown now)
     */
    public static boolean windup(AbilityContext ctx, com.fantasyweapons.ability.AbilityExecutor power) {
        Gathering g = GATHERINGS.get(ctx.ability().id());
        AbilityContext held = ctx.frozen();
        double size = ctx.ability().hasParam("radius") ? held.param("radius") : ctx.ability().hasParam("length") ? held.param("length") * 0.4 : 80;
        size = Math.max(30, Math.min(260, size));
        Vec3 at = switch (g.focus()) {
            case SELF -> held.feet();
            case AIM -> landing(held, 512);
            case BEHIND -> com.fantasyweapons.weapons.obliteration.ObliterationAbilities.dragonRift(held,
                    landing(held, ctx.ability().hasParam("range") ? held.param("range") : 512));
        };
        if (g.focus() == Focus.BEHIND) size = 48;
        fx(ctx.level(), ctx.player(), FxPayload.of(com.fantasyweapons.network.FxIds.ULTIMATE_WINDUP).caster(ctx.player().getId()).pos(at)
                .scale((float) size).power(WINDUP).level(g.color()).point(held.feet()).build());
        com.fantasyweapons.ability.kit.Kit.active(held, WINDUP);
        com.fantasyweapons.world.AreaEffectManager.add(new Windup(held, at, power));
        return true;
    }

    /** Waits out a power's wind-up, then casts it. */
    private static final class Windup extends com.fantasyweapons.world.AreaEffect {
        private final AbilityContext ctx;
        private final com.fantasyweapons.ability.AbilityExecutor power;

        Windup(AbilityContext ctx, Vec3 at, com.fantasyweapons.ability.AbilityExecutor power) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), at, WINDUP + 1);
            this.ctx = ctx;
            this.power = power;
        }

        @Override
        protected void tick(ServerPlayer owner) {
            if (age != WINDUP) return;
            try {
                power.execute(ctx);
            } catch (RuntimeException e) {
                com.fantasyweapons.FantasyWeapons.LOGGER.error("Power {} failed after gathering", ctx.ability().key(), e);
            }
            finished = true;
        }
    }

    /**
     * Impact frames for the blow landing at {@code at}, for the caster and everyone within {@code seen} blocks of it.
     *
     * @param cataclysm the long, hard sequence, or the ordinary ultimate one
     */
    public static void frames(ServerLevel level, ServerPlayer caster, Vec3 at, double seen, boolean cataclysm) {
        FxPayload p = FxPayload.of(com.fantasyweapons.network.FxIds.CATACLYSM_FRAME).caster(caster.getId()).pos(at).scale((float) seen)
                .level(cataclysm ? 1 : 0).build();
        double r2 = seen * seen;
        for (ServerPlayer pl : level.players()) if (pl == caster || pl.position().distanceToSqr(at) <= r2) Fx.only(pl, p);
    }

    /** Every creature the caster may harm within {@code radius} of {@code center}. */
    public static List<LivingEntity> victims(ServerLevel level, ServerPlayer caster, Vec3 center, double radius) {
        return Targeting.inRadius(level, caster, center, radius, FWDamage.Kind.ABILITY);
    }

    /**
     * Where a cast lands: the point picked on the targeting map, else where the player looks (up to {@code range}),
     * always set on the ground (the highest solid block of the column when its chunk is loaded).
     */
    public static Vec3 landing(AbilityContext ctx, double range) {
        Vec3 t = ctx.target();
        if (t == null) return Kit.aimGround(ctx.player(), ctx.eye(), ctx.look(), ctx.feet(), range);
        return surface(ctx.level(), t);
    }

    /** The top of the ground at a point's column (unchanged if its chunk is not loaded). */
    public static Vec3 surface(ServerLevel level, Vec3 at) {
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z);
        if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return at;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new Vec3(at.x, y, at.z);
    }
}
