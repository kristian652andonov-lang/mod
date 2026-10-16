package com.fantasyweapons.weapons.domination;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapons.ultimate.Cataclysm;
import com.fantasyweapons.world.AreaEffect;
import com.fantasyweapons.world.AreaEffectManager;
import com.fantasyweapons.world.wreck.WorldWreck;
import com.fantasyweapons.world.wreck.WreckShapes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Server side of the Spell Book of Absolute Domination's six spells, all cast through the targeting map. */
public final class DominationAbilities {
    private DominationAbilities() {
    }

    /** How far a spell can land when it is cast without the map (looking at the spot). */
    private static final double LOOK_RANGE = 512;

    // ------------------------------------------------------------------------------------------------------------
    // The Beam of Grant Execution
    // ------------------------------------------------------------------------------------------------------------

    public static boolean grantExecution(AbilityContext ctx) {
        Vec3 at = Cataclysm.landing(ctx, LOOK_RANGE);
        double radius = ctx.param("radius");
        int delay = Math.max(20, (int) Math.round(ctx.param("delay") * 20));
        int beam = Math.max(20, (int) Math.round(ctx.param("duration") * 20));
        long seed = Kit.seed(ctx.level());
        Cataclysm.fx(ctx.level(), ctx.player(), FxPayload.of(FxIds.GRANT_EXECUTION).caster(ctx.player().getId()).pos(at)
                .scale((float) radius).power(delay).level(beam).seed(seed).build());
        AreaEffectManager.add(new GrantExecution(ctx, at, radius, delay, beam, seed));
        Kit.active(ctx, delay + beam);
        return true;
    }

    /** The circle opens; after the delay the beam falls, erases the column and everything in it, and keeps burning. */
    private static final class GrantExecution extends AreaEffect {
        private final double radius;
        private final int delay, beam;
        private final long seed;
        private final float damage;

        GrantExecution(AbilityContext ctx, Vec3 at, double radius, int delay, int beam, long seed) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), at, delay + beam + 2);
            this.radius = radius;
            this.delay = delay;
            this.beam = beam;
            this.seed = seed;
            this.damage = ctx.damage();
        }

        @Override
        protected void tick(ServerPlayer owner) {
            if (age == delay) {
                double r = WorldWreck.radius(radius);
                // the land is erased from the middle out as the beam bites
                WorldWreck.disc(level, pos, r + 3, seed, d -> (int) (d / 6), WreckShapes.shaft(pos, r, 4));
                Cataclysm.frames(level, owner, pos, radius * 3, true);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 30f, 0.5f);
            }
            if (age >= delay && age <= delay + beam && (age - delay) % 4 == 0) {
                Cataclysm.eraseColumn(owner, weapon(owner), level, pos, radius, damage, Element.LIGHT);
            }
            if (age == delay + beam / 2) Cataclysm.frames(level, owner, pos, radius * 3, false);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // still to come
    // ------------------------------------------------------------------------------------------------------------

    // ------------------------------------------------------------------------------------------------------------
    // The Ultramassive Blackhole
    // ------------------------------------------------------------------------------------------------------------

    /** Radius of the black sphere itself, and how high above the ground it opens. */
    private static final float HORIZON = 28, HOLE_HEIGHT = 95;
    private static final int FORM = 40;

    public static boolean blackhole(AbilityContext ctx) {
        Vec3 ground = Cataclysm.landing(ctx, LOOK_RANGE);
        Vec3 hole = ground.add(0, HOLE_HEIGHT, 0);
        double reach = ctx.param("radius");
        int life = Math.max(FORM + 80, (int) Math.round(ctx.param("duration") * 20));
        int collapse = life - 30;
        long seed = Kit.seed(ctx.level());
        Cataclysm.fx(ctx.level(), ctx.player(), FxPayload.of(FxIds.BLACKHOLE).caster(ctx.player().getId()).pos(hole).dir(ground)
                .scale((float) reach).power(collapse).level(FORM).seed(seed).build());
        AreaEffectManager.add(new Blackhole(ctx, hole, reach, collapse, (float) ctx.param("pull"), seed));
        Kit.active(ctx, life);
        return true;
    }

    /**
     * Opens, then drags in everything within its reach, faster and faster, spiralling: what crosses the horizon is
     * deleted. The land is eaten in a growing sphere. At the end it collapses, taking whatever is still in reach.
     */
    private static final class Blackhole extends AreaEffect {
        private final double reach;
        private final int collapse;
        private final float pull, damage;
        private final long seed;

        Blackhole(AbilityContext ctx, Vec3 hole, double reach, int collapse, float pull, long seed) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), hole, collapse + 2);
            this.reach = reach;
            this.collapse = collapse;
            this.pull = pull;
            this.seed = seed;
            this.damage = ctx.damage();
        }

        @Override
        protected void tick(ServerPlayer owner) {
            if (age == FORM) {
                double r = WorldWreck.radius(reach);
                int span = collapse - FORM - 10;
                WorldWreck.disc(level, pos, r + 3, seed, d -> FORM + (int) (Math.min(1, d / r) * span), WreckShapes.sphere(pos, r));
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 30f, 0.4f);
            }
            if (age >= FORM && age < collapse) feed(owner, (age - FORM) / (float) (collapse - FORM));
            if (age == collapse) {
                // the collapse takes everything still in reach
                for (net.minecraft.world.entity.Entity e : inReach()) swallow(owner, e);
                Cataclysm.frames(level, owner, pos, reach * 2, true);
            }
        }

        private java.util.List<net.minecraft.world.entity.Entity> inReach() {
            java.util.List<net.minecraft.world.entity.Entity> out = new java.util.ArrayList<>();
            double r2 = reach * reach;
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e == null || e.isRemoved()) continue;
                double dx = e.getX() - pos.x, dz = e.getZ() - pos.z;
                if (dx * dx + dz * dz > r2 || e.getY() > pos.y + reach) continue;
                out.add(e);
            }
            return out;
        }

        private boolean takes(ServerPlayer owner, net.minecraft.world.entity.Entity e) {
            if (e instanceof net.minecraft.world.entity.player.Player p) {
                return p != owner && !p.isCreative() && !p.isSpectator()
                        && com.fantasyweapons.combat.FWDamage.canHarm(owner, p, com.fantasyweapons.combat.FWDamage.Kind.ABILITY);
            }
            if (e instanceof net.minecraft.world.entity.LivingEntity le) {
                return com.fantasyweapons.combat.FWDamage.canHarm(owner, le, com.fantasyweapons.combat.FWDamage.Kind.ABILITY);
            }
            return !(e instanceof net.neoforged.neoforge.entity.PartEntity<?>);
        }

        private void swallow(ServerPlayer owner, net.minecraft.world.entity.Entity e) {
            if (!takes(owner, e)) return;
            if (e instanceof net.minecraft.world.entity.LivingEntity le) Cataclysm.annihilate(owner, weapon(owner), le, damage, Element.VOID);
            else e.discard();
        }

        /** Drags things in along a tightening spiral; what reaches the horizon is gone. */
        private void feed(ServerPlayer owner, float progress) {
            if (age % 2 != 0) return;
            for (net.minecraft.world.entity.Entity e : inReach()) {
                if (!takes(owner, e)) continue;
                Vec3 to = pos.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
                double d = to.length();
                if (d < HORIZON + 3) {
                    swallow(owner, e);
                    continue;
                }
                Vec3 in = to.scale(1 / d);
                Vec3 around = new Vec3(-in.z, 0, in.x);
                double speed = pull * (0.18 + 1.9 * progress) * (0.35 + 0.65 * (1 - Math.min(1, d / reach)));
                Vec3 v = in.scale(speed).add(around.scale(speed * 0.45)).add(0, 0.04, 0);
                e.setDeltaMovement(e.getDeltaMovement().scale(0.5).add(v));
                e.hurtMarked = true;
                e.resetFallDistance();
                if (e instanceof net.minecraft.world.entity.Mob mob) mob.getNavigation().stop();
            }
        }
    }

    public static boolean eruption(AbilityContext ctx) {
        throw AbilityContext.fail("The Earthsplitting Eruption is still being written");
    }

    public static boolean tectonic(AbilityContext ctx) {
        throw AbilityContext.fail("Tectonic Breakage is still being written");
    }

    public static boolean greatTree(AbilityContext ctx) {
        throw AbilityContext.fail("The Roots of the Great Tree is still being written");
    }

    public static boolean iceAge(AbilityContext ctx) {
        throw AbilityContext.fail("The Ice Age is still being written");
    }
}
