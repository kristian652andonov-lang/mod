package com.fantasyweapons.weapons.domination;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapons.ultimate.Cataclysm;
import com.fantasyweapons.world.AreaEffect;
import com.fantasyweapons.world.AreaEffectManager;
import com.fantasyweapons.world.wreck.ChunkShaper;
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
            // the land it erases, and the cracks it splits round the hole, are worked out while the circle opens, so each
            // lands all at once: the hole the moment the beam bites, the cracks as it burns
            if (age == 1) {
                double r = WorldWreck.radius(radius);
                WorldWreck.disc(level, pos, r + Math.min(8, r * 0.06) + 12, seed, d -> delay - 1, WreckShapes.shaft(pos, r));
            }
            if (age == 2) {
                double r = WorldWreck.radius(radius);
                var ring = com.fantasyweapons.weapons.ultimate.CrackNetwork.radial(seed ^ 0xBEA4L, 12, r + 2, r * 2.2, 6, 120);
                WorldWreck.disc(level, pos, r * 2.2 + 12, seed + 3, d -> delay + beam / 2 - 2, WreckShapes.cracks(pos, ring));
            }
            if (age == delay) {
                Cataclysm.frames(level, owner, pos, radius * 3, 0xFFB43A, 0xFFF6D6);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 30f, 0.5f);
            }
            if (age >= delay && age <= delay + beam && (age - delay) % 4 == 0) {
                Cataclysm.eraseColumn(owner, weapon(owner), level, pos, radius, damage, Element.LIGHT);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // still to come
    // ------------------------------------------------------------------------------------------------------------

    // ------------------------------------------------------------------------------------------------------------
    // The Ultramassive Blackhole
    // ------------------------------------------------------------------------------------------------------------

    /** Radius of the black sphere itself, and how high above the ground it opens. */
    public static final float HORIZON = 40, HOLE_HEIGHT = 110;
    private static final int FORM = 70;

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
            // the land it swallows, and the cracks its collapse splits round the bowl, are worked out from the start, so
            // each lands all at once: the bowl the moment it tears open, the cracks as it collapses
            if (age == 1) {
                double r = WorldWreck.radius(reach);
                WorldWreck.disc(level, pos, r + WreckShapes.ragReach(r) + 14, seed, d -> FORM - 1, WreckShapes.sphere(pos, r));
            }
            if (age == 2) {
                double r = WorldWreck.radius(reach);
                var ring = com.fantasyweapons.weapons.ultimate.CrackNetwork.radial(seed ^ 0xB1AC4L, 14, r * 0.97, r * 1.7, 7, 120);
                WorldWreck.disc(level, pos, r * 1.7 + 12, seed + 5, d -> collapse - 2, WreckShapes.cracks(pos, ring));
            }
            if (age == FORM) {
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 30f, 0.4f);
                // the one impact frame: the moment it tears open
                Cataclysm.frames(level, owner, pos, reach * 2, 0xFF9636, 0xFFF8EE);
            }
            if (age >= FORM && age < collapse) feed(owner, (age - FORM) / (float) (collapse - FORM));
            if (age == collapse) {
                // the collapse takes everything still in reach
                for (net.minecraft.world.entity.Entity e : inReach()) swallow(owner, e);
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

    // ------------------------------------------------------------------------------------------------------------
    // The Earthsplitting Eruption
    // ------------------------------------------------------------------------------------------------------------

    /** The eruption's crack network (the client builds the same one from the seed). */
    public static com.fantasyweapons.weapons.ultimate.CrackNetwork eruptionCracks(long seed, double radius) {
        return com.fantasyweapons.weapons.ultimate.CrackNetwork.radial(seed, 11, radius * 1.1, 5.5, 120);
    }

    public static boolean eruption(AbilityContext ctx) {
        Vec3 at = Cataclysm.landing(ctx, LOOK_RANGE);
        double radius = ctx.param("radius");
        int delay = Math.max(20, (int) Math.round(ctx.param("delay") * 20));
        double depth = ctx.param("depth");
        long seed = Kit.seed(ctx.level());
        Cataclysm.fx(ctx.level(), ctx.player(), FxPayload.of(FxIds.ERUPTION).caster(ctx.player().getId()).pos(at)
                .scale((float) radius).power(delay).level((int) depth).seed(seed).build());
        AreaEffectManager.add(new Eruption(ctx, at, radius, delay, depth, seed));
        Kit.active(ctx, delay + 40);
        return true;
    }

    /** The ground splits open in spreading cracks; then the earth explodes and the land is left lava, magma and coal. */
    private static final class Eruption extends AreaEffect {
        private final double radius, depth;
        private final int delay;
        private final long seed;
        private final float damage;
        private final com.fantasyweapons.weapons.ultimate.CrackNetwork cracks;

        Eruption(AbilityContext ctx, Vec3 at, double radius, int delay, double depth, long seed) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), at, delay + 60);
            this.radius = radius;
            this.delay = delay;
            this.depth = depth;
            this.seed = seed;
            this.damage = ctx.damage();
            this.cracks = eruptionCracks(seed, WorldWreck.radius(radius));
        }

        @Override
        protected void tick(ServerPlayer owner) {
            if (age == 1) tearCracks();
            if (age == 2) {
                // the crater is worked out while the ground shakes, so it blows out all at once the moment it erupts
                double r = WorldWreck.radius(radius);
                WorldWreck.disc(level, pos, r + 10, seed, d -> delay - 2, WreckShapes.eruption(pos, r, depth, cracks));
            }
            if (age < delay && age % 10 == 0) tremor(owner);
            if (age == delay) erupt(owner);
            if (age > delay && age % 10 == 0) {
                for (var e : Cataclysm.victims(level, owner, pos, radius)) e.igniteForSeconds(8);
            }
        }

        /** The cracks open outward over the delay, chunk by chunk as they reach it. */
        private void tearCracks() {
            java.util.Map<Long, Integer> due = new java.util.HashMap<>();
            double grow = cracks.reach();
            for (var s : cracks.segments()) {
                int steps = Math.max(1, (int) (s.length() / 4));
                for (int i = 0; i <= steps; i++) {
                    double t = i / (double) steps;
                    double x = pos.x + s.x0() + (s.x1() - s.x0()) * t, z = pos.z + s.z0() + (s.z1() - s.z0()) * t;
                    int when = (int) ((s.from() + s.length() * t) / grow * delay * 0.85);
                    due.merge(net.minecraft.world.level.ChunkPos.asLong((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4), when, Math::min);
                }
            }
            var shaper = WreckShapes.cracks(pos, cracks);
            due.forEach((key, when) -> WorldWreck.submit(level, net.minecraft.world.level.ChunkPos.getX(key), net.minecraft.world.level.ChunkPos.getZ(key), when, seed, shaper));
        }

        /** The ground shudders: everything on it is jolted and set burning near the cracks. */
        private void tremor(ServerPlayer owner) {
            for (var e : Cataclysm.victims(level, owner, pos, radius)) {
                e.push(0, 0.25 + level.random.nextDouble() * 0.2, 0);
                e.hurtMarked = true;
                if (cracks.at(e.getX() - pos.x, e.getZ() - pos.z, Double.MAX_VALUE, null) != null) e.igniteForSeconds(4);
            }
        }

        private void erupt(ServerPlayer owner) {
            for (var e : Cataclysm.victims(level, owner, pos.add(0, radius * 0.25, 0), radius * 1.05)) {
                e.push(0, 2.2, 0);
                e.hurtMarked = true;
                Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.FIRE);
            }
            // the blast throws back and burns what stands beyond the crater
            for (var e : Cataclysm.victims(level, owner, pos, radius * 1.7)) {
                if (e.distanceToSqr(pos) < radius * radius) continue;
                Kit.knock(e, pos, 3.5, 1.4);
                e.igniteForSeconds(10);
            }
            Cataclysm.frames(level, owner, pos, radius * 2.5, 0xFF7A1A, 0xFFD050);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 40f, 0.4f);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Tectonic Breakage
    // ------------------------------------------------------------------------------------------------------------

    /** How many times the canyon widens on its way to full width. */
    private static final int TECTONIC_STAGES = 5;

    public static boolean tectonic(AbilityContext ctx) {
        Vec3 at = Cataclysm.landing(ctx, LOOK_RANGE);
        // the canyon runs square to the caster's line of sight, so it tears across the view
        Vec3 sight = new Vec3(at.x - ctx.player().getX(), 0, at.z - ctx.player().getZ());
        if (sight.lengthSqr() < 1) sight = new Vec3(ctx.look().x, 0, ctx.look().z);
        if (sight.lengthSqr() < 1e-4) sight = new Vec3(1, 0, 0);
        sight = sight.normalize();
        Vec3 dir = new Vec3(-sight.z, 0, sight.x);
        double length = ctx.param("length"), width = ctx.param("width");
        int duration = Math.max(60, (int) Math.round(ctx.param("duration") * 20));
        long seed = Kit.seed(ctx.level());
        Cataclysm.fx(ctx.level(), ctx.player(), FxPayload.of(FxIds.TECTONIC).caster(ctx.player().getId()).pos(at).dir(dir)
                .scale((float) length).power((float) width).level(duration).seed(seed).build());
        AreaEffectManager.add(new Tectonic(ctx, at, dir, length, width, duration, seed));
        Kit.active(ctx, duration);
        return true;
    }

    /** The land splits along a line and a canyon slowly opens down to bedrock, filling with lava from below. */
    private static final class Tectonic extends AreaEffect {
        private final Vec3 dir;
        private final double length, width;
        private final int duration;
        private final long seed;
        private final float damage;

        Tectonic(AbilityContext ctx, Vec3 at, Vec3 dir, double length, double width, int duration, long seed) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), at, duration + 20);
            this.dir = dir;
            this.length = length;
            this.width = width;
            this.duration = duration;
            this.seed = seed;
            this.damage = ctx.damage();
        }

        /** How wide the canyon is open at {@code tick} (its half-width), from a crack to full width. */
        private double halfWidthAt(int tick) {
            return width / 2 * Math.min(1, Math.max(0.08, tick / (double) duration));
        }

        @Override
        protected void tick(ServerPlayer owner) {
            for (int stage = 1; stage <= TECTONIC_STAGES; stage++) {
                int when = 10 + (duration - 20) * (stage - 1) / (TECTONIC_STAGES - 1);
                if (age == when) open(stage / (double) TECTONIC_STAGES);
            }
            if (age % 4 == 0 && age <= duration) swallow(owner);
            if (age == 10) Cataclysm.frames(level, owner, pos, length * 0.75);
        }

        /** Widens the canyon to {@code fraction} of its width, the tear running from the middle out to its ends. */
        private void open(double fraction) {
            double len = WorldWreck.radius(length), half = WorldWreck.radius(width) / 2 * Math.max(0.12, fraction);
            int lavaY = level.getMinBuildHeight() + 8 + (int) Math.round(18 * fraction);
            ChunkShaper shaper = WreckShapes.canyon(pos, dir, len, half, lavaY);
            double reach = half + 16;
            int c0x = (int) Math.floor(pos.x - len / 2 - reach) >> 4, c1x = (int) Math.floor(pos.x + len / 2 + reach) >> 4;
            int c0z = (int) Math.floor(pos.z - len / 2 - reach) >> 4, c1z = (int) Math.floor(pos.z + len / 2 + reach) >> 4;
            for (int cx = c0x; cx <= c1x; cx++) {
                for (int cz = c0z; cz <= c1z; cz++) {
                    double rx = cx * 16 + 8 - pos.x, rz = cz * 16 + 8 - pos.z;
                    double along = rx * dir.x + rz * dir.z, across = Math.abs(-rx * dir.z + rz * dir.x);
                    if (Math.abs(along) > len / 2 + 12 || across > reach) continue;
                    WorldWreck.submit(level, cx, cz, (int) (Math.abs(along) / 10), seed, shaper);
                }
            }
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 30f, 0.4f);
        }

        /** Whatever stands where the land has opened falls into the abyss; those near its lips are thrown about. */
        private void swallow(ServerPlayer owner) {
            double half = halfWidthAt(age) + 2, len = length / 2;
            for (var e : Cataclysm.victims(level, owner, pos, len + width)) {
                double rx = e.getX() - pos.x, rz = e.getZ() - pos.z;
                double along = rx * dir.x + rz * dir.z, across = Math.abs(-rx * dir.z + rz * dir.x);
                if (Math.abs(along) > len) continue;
                if (across <= half) {
                    e.setDeltaMovement(e.getDeltaMovement().multiply(0.3, 1, 0.3).add(0, -0.8, 0));
                    e.hurtMarked = true;
                    Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.EARTH);
                } else if (across <= half + 24) {
                    e.push((level.random.nextDouble() - 0.5) * 0.4, 0.2 + level.random.nextDouble() * 0.25, (level.random.nextDouble() - 0.5) * 0.4);
                    e.hurtMarked = true;
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // The Roots of the Great Tree
    // ------------------------------------------------------------------------------------------------------------

    /** When the roots have broken out and seize what is in reach. */
    private static final int GRAB = 40;

    public static boolean greatTree(AbilityContext ctx) {
        Vec3 at = Cataclysm.landing(ctx, LOOK_RANGE);
        double radius = ctx.param("radius");
        int count = Math.max(4, (int) Math.round(ctx.param("roots")));
        int duration = Math.max(GRAB + 40, (int) Math.round(ctx.param("duration") * 20) + GRAB);
        long seed = Kit.seed(ctx.level());
        java.util.List<com.fantasyweapons.weapons.ultimate.RootSpline> roots = growRoots(ctx.level(), at, WorldWreck.radius(radius), count, seed);
        FxPayload.Builder fx = FxPayload.of(FxIds.GREAT_TREE).caster(ctx.player().getId()).pos(at).scale((float) radius).power(duration).seed(seed);
        java.util.List<Integer> thick = new java.util.ArrayList<>();
        for (var r : roots) {
            fx.point(r.p0()).point(r.p1()).point(r.p2()).point(r.p3());
            thick.add(Math.round(r.thickness() * 10));
        }
        Cataclysm.fx(ctx.level(), ctx.player(), fx.entities(thick).build());
        AreaEffectManager.add(new GreatTree(ctx, at, radius, duration, roots, seed));
        Kit.active(ctx, duration);
        return true;
    }

    /** The ground height at a column, if its chunk is loaded (else the target's). */
    private static double groundAt(net.minecraft.server.level.ServerLevel level, double x, double z, double fallback) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (level.getChunkSource().getChunkNow(bx >> 4, bz >> 4) == null) return fallback;
        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, bx, bz);
    }

    /**
     * The roots: a crown of colossal ones heaving out of the middle and arching outward, and many more breaking out all
     * over the land, each arching up out of the ground and plunging back in.
     */
    private static java.util.List<com.fantasyweapons.weapons.ultimate.RootSpline> growRoots(net.minecraft.server.level.ServerLevel level, Vec3 at,
                                                                                         double radius, int count, long seed) {
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(seed);
        java.util.List<com.fantasyweapons.weapons.ultimate.RootSpline> out = new java.util.ArrayList<>();
        int crown = Math.max(3, count / 6);
        for (int i = 0; i < count; i++) {
            boolean big = i < crown;
            double ang = big ? (i + r.nextDouble() * 0.5) * Math.PI * 2 / crown : r.nextDouble() * Math.PI * 2;
            double start = big ? r.nextDouble() * 6 : 12 + r.nextDouble() * radius * 0.8;
            double len = big ? radius * (0.5 + r.nextDouble() * 0.25) : 26 + r.nextDouble() * 60;
            double arch = big ? 55 + r.nextDouble() * 35 : 14 + r.nextDouble() * 34 + len * 0.15;
            float thick = big ? 8f + r.nextFloat() * 3 : 2.5f + r.nextFloat() * 3.5f;
            // most roots run outward from the middle, the rest every which way
            double heading = big || r.nextFloat() < 0.65f ? ang + (r.nextDouble() - 0.5) * 0.7 : r.nextDouble() * Math.PI * 2;
            double sx = at.x + Math.cos(ang) * start, sz = at.z + Math.sin(ang) * start;
            double ex = sx + Math.cos(heading) * len, ez = sz + Math.sin(heading) * len;
            double gs = groundAt(level, sx, sz, at.y), ge = groundAt(level, ex, ez, at.y);
            Vec3 p0 = new Vec3(sx, gs - thick * 1.6, sz), p3 = new Vec3(ex, ge - thick * 1.2, ez);
            Vec3 along = new Vec3(Math.cos(heading), 0, Math.sin(heading));
            Vec3 p1 = p0.add(along.scale(len * 0.12)).add(0, arch * 1.25, 0);
            Vec3 p2 = p3.add(along.scale(-len * 0.3)).add(0, arch * (0.7 + r.nextDouble() * 0.4), 0);
            out.add(new com.fantasyweapons.weapons.ultimate.RootSpline(p0, p1, p2, p3, thick));
        }
        return out;
    }

    /** The roots break out, seize and crush everything in reach; when it ends they are built into the world for good. */
    private static final class GreatTree extends AreaEffect {
        private final double radius;
        private final int duration;
        private final java.util.List<com.fantasyweapons.weapons.ultimate.RootSpline> roots;
        private final long seed;
        private final float damage;
        private final java.util.Map<Integer, Vec3> held = new java.util.HashMap<>();

        GreatTree(AbilityContext ctx, Vec3 at, double radius, int duration, java.util.List<com.fantasyweapons.weapons.ultimate.RootSpline> roots, long seed) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), at, duration + 2);
            this.radius = radius;
            this.duration = duration;
            this.roots = roots;
            this.seed = seed;
            this.damage = ctx.damage();
        }

        @Override
        protected void tick(ServerPlayer owner) {
            if (age == GRAB) seize(owner);
            if (age > GRAB && age < duration) hold(owner);
            if (age == duration) {
                for (var e : Cataclysm.victims(level, owner, pos, radius)) Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.NATURE);
                // the roots stay: built into the world, nearest first
                ChunkShaper shaper = WreckShapes.roots(roots);
                java.util.Set<Long> chunks = new java.util.HashSet<>();
                for (var r : roots) {
                    for (int cx = (int) Math.floor(r.minX()) >> 4; cx <= (int) Math.floor(r.maxX()) >> 4; cx++) {
                        for (int cz = (int) Math.floor(r.minZ()) >> 4; cz <= (int) Math.floor(r.maxZ()) >> 4; cz++) {
                            chunks.add(net.minecraft.world.level.ChunkPos.asLong(cx, cz));
                        }
                    }
                }
                for (long key : chunks) {
                    int cx = net.minecraft.world.level.ChunkPos.getX(key), cz = net.minecraft.world.level.ChunkPos.getZ(key);
                    double d = Math.hypot(cx * 16 + 8 - pos.x, cz * 16 + 8 - pos.z);
                    WorldWreck.submit(level, cx, cz, (int) (d / 20), seed, shaper);
                }
                Cataclysm.frames(level, owner, pos, radius * 2);
            }
        }

        private void seize(ServerPlayer owner) {
            java.util.List<Integer> ids = new java.util.ArrayList<>();
            for (var e : Cataclysm.victims(level, owner, pos, radius)) {
                if (ids.size() >= 250) break;
                held.put(e.getId(), e.position().add(0, 1.5, 0));
                ids.add(e.getId());
            }
            Cataclysm.fx(level, owner, FxPayload.of(FxIds.GREAT_TREE_GRAB).caster(owner.getId()).pos(pos).power(duration - GRAB).entities(ids).build());
        }

        /** Held fast, lifted a little off the ground, crushed again and again. */
        private void hold(ServerPlayer owner) {
            for (var it = held.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                var e = level.getEntity(entry.getKey());
                if (!(e instanceof net.minecraft.world.entity.LivingEntity le) || !le.isAlive()) {
                    it.remove();
                    continue;
                }
                Vec3 to = entry.getValue().subtract(le.position());
                le.setDeltaMovement(to.scale(0.3));
                le.hurtMarked = true;
                le.resetFallDistance();
                if ((age - GRAB) % 20 == 0) {
                    le.invulnerableTime = 0;
                    com.fantasyweapons.combat.FWDamage.deal(owner, weapon(owner), le, damage * 0.1f, com.fantasyweapons.combat.FWDamage.Kind.ABILITY, Element.NATURE, 0);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // The Ice Age
    // ------------------------------------------------------------------------------------------------------------

    /** Ticks the sea takes to rise at the target before the wave rolls out. */
    public static final int SURGE = 40;

    public static boolean iceAge(AbilityContext ctx) {
        Vec3 at = Cataclysm.landing(ctx, LOOK_RANGE);
        double radius = ctx.param("radius"), height = ctx.param("height"), speed = Math.max(0.3, ctx.param("speed"));
        long seed = Kit.seed(ctx.level());
        Cataclysm.fx(ctx.level(), ctx.player(), FxPayload.of(FxIds.ICE_AGE).caster(ctx.player().getId()).pos(at)
                .scale((float) radius).power((float) height).level((int) Math.round(speed * 100)).seed(seed).build());
        AreaEffectManager.add(new IceAge(ctx, at, radius, speed, seed));
        Kit.active(ctx, SURGE + (int) (radius / speed));
        return true;
    }

    /** A tsunami rolls out in every direction, destroying and killing what it hits; behind it everything is ice. */
    private static final class IceAge extends AreaEffect {
        private final double radius, speed;
        private final long seed;
        private final float damage;

        IceAge(AbilityContext ctx, Vec3 at, double radius, double speed, long seed) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), at, SURGE + (int) (radius / speed) + 30);
            this.radius = radius;
            this.speed = speed;
            this.seed = seed;
            this.damage = ctx.damage();
        }

        @Override
        protected void tick(ServerPlayer owner) {
            if (age == SURGE) {
                double r = WorldWreck.radius(radius);
                // the land freezes right behind the wave
                WorldWreck.disc(level, pos, r, seed, d -> SURGE + 4 + (int) (d / speed), WreckShapes.freeze(pos, r));
                Cataclysm.frames(level, owner, pos, radius * 1.5);
            }
            if (age < SURGE) return;
            double wave = (age - SURGE) * speed;
            if (wave > radius + 8) return;
            for (var e : Cataclysm.victims(level, owner, pos, Math.min(radius, wave + 6))) {
                double d = Math.hypot(e.getX() - pos.x, e.getZ() - pos.z);
                if (d < wave - 10) continue;
                // swept up by the wave and frozen
                Kit.knock(e, pos, 2.4, 0.9);
                Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.ICE);
            }
        }
    }
}
