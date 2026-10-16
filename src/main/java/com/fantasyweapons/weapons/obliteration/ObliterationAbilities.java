package com.fantasyweapons.weapons.obliteration;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/** Server side of the Great Sword of Obliteration's six abilities. */
public final class ObliterationAbilities {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private ObliterationAbilities() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // The Great Slash
    // ------------------------------------------------------------------------------------------------------------

    public static boolean greatSlash(AbilityContext ctx) {
        Vec3 eye = ctx.eye(), look = ctx.look().normalize();
        Vec3 right = look.cross(UP);
        if (right.lengthSqr() < 1e-4) right = Vec3.directionFromRotation(0, ctx.player().getYRot() + 90);
        right = right.normalize();
        Vec3 up = right.cross(look).normalize();
        // a towering stroke, almost straight down from high on the left: it splits whatever stands ahead in two
        double tilt = Math.toRadians(-72);
        Vec3 blade = right.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt))).normalize();
        double length = ctx.param("length"), half = ctx.param("width") / 2, thickness = ctx.param("thickness"), speed = Math.max(1, ctx.param("speed"));
        long seed = Kit.seed(ctx.level());
        Cataclysm.fx(ctx.level(), ctx.player(), FxPayload.of(FxIds.GREAT_SLASH).caster(ctx.player().getId()).pos(eye).dir(look).point(blade)
                .scale((float) length).power((float) half).level((int) Math.round(speed * 100)).seed(seed).build());
        // the cut runs out through the world as the slash flies
        double r = WorldWreck.radius(length);
        var shaper = WreckShapes.slab(eye, look, blade, r, WorldWreck.radius(half), thickness);
        int reach = (int) Math.ceil(r + half);
        for (int cx = (int) Math.floor(eye.x - reach) >> 4; cx <= (int) Math.floor(eye.x + reach) >> 4; cx++) {
            for (int cz = (int) Math.floor(eye.z - reach) >> 4; cz <= (int) Math.floor(eye.z + reach) >> 4; cz++) {
                Vec3 mid = new Vec3(cx * 16 + 8, eye.y, cz * 16 + 8).subtract(eye);
                double f = mid.x * look.x + mid.z * look.z;
                if (f < -16 || f > r + 16 || mid.horizontalDistance() > r + half + 16) continue;
                WorldWreck.submit(ctx.level(), cx, cz, (int) Math.max(0, f / speed), seed, shaper);
            }
        }
        AreaEffectManager.add(new GreatSlash(ctx, eye, look, blade, length, half, thickness, speed));
        ctx.level().playSound(null, eye.x, eye.y, eye.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 6f, 0.5f);
        return true;
    }

    /** The slash flies out, cutting in two everything it passes through. */
    private static final class GreatSlash extends AreaEffect {
        private final Vec3 look, blade, normal;
        private final double length, half, thickness, speed;
        private final float damage;
        private final Set<Integer> cut = new HashSet<>();

        GreatSlash(AbilityContext ctx, Vec3 eye, Vec3 look, Vec3 blade, double length, double half, double thickness, double speed) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), eye, (int) (length / speed) + 2);
            this.look = look;
            this.blade = blade;
            this.normal = look.cross(blade).normalize();
            this.length = length;
            this.half = half;
            this.thickness = thickness;
            this.speed = speed;
            this.damage = ctx.damage();
        }

        @Override
        protected void tick(ServerPlayer owner) {
            double front = age * speed;
            if (age == lifetime - 1) Cataclysm.frames(level, owner, pos.add(look.scale(length * 0.5)), length, true);
            for (LivingEntity e : Cataclysm.victims(level, owner, pos.add(look.scale(front)), half + speed + 4)) {
                if (cut.contains(e.getId())) continue;
                Vec3 d = e.getBoundingBox().getCenter().subtract(pos);
                double f = d.dot(look), l = Math.abs(d.dot(blade)), n = Math.abs(d.dot(normal));
                double size = Math.max(e.getBbWidth(), e.getBbHeight()) / 2;
                if (f < 1 || f > front + 2 || f > length || n > thickness + size || l > half * Math.sqrt(Math.min(1, f / (length * 0.35))) + size) continue;
                cut.add(e.getId());
                Cataclysm.fx(level, owner, FxPayload.of(FxIds.GREAT_SLASH_CUT).caster(owner.getId()).pos(e.getBoundingBox().getCenter()).dir(normal)
                        .scale((float) Math.max(e.getBbWidth(), e.getBbHeight())).build());
                Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.LIGHT);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // still to come
    // ------------------------------------------------------------------------------------------------------------

    public static boolean fourthDimension(AbilityContext ctx) {
        Vec3 at = Cataclysm.landing(ctx, 512);
        double radius = WorldWreck.radius(ctx.param("radius"));
        int delay = Math.max(20, (int) Math.round(ctx.param("delay") * 20));
        long seed = Kit.seed(ctx.level());
        var level = ctx.level();
        int minY = level.getMinBuildHeight(), height = level.getHeight();
        var plan = new com.fantasyweapons.weapons.ultimate.ShufflePlan(at, radius, seed, minY, height);
        // the region is loaded now so every chunk can be read at the same instant when space breaks
        for (var m : plan.moves()) WorldWreck.hold(level, m.dest().x, m.dest().z);
        Cataclysm.fx(level, ctx.player(), FxPayload.of(FxIds.FOURTH_DIMENSION).caster(ctx.player().getId()).pos(at).scale((float) radius)
                .power(delay).level(minY).entities(java.util.List.of(height)).seed(seed).build());
        AreaEffectManager.add(new FourthDimension(ctx, at, radius, delay, plan, seed));
        Kit.active(ctx, 30);
        return true;
    }

    /**
     * Space over the region is lifted out of its three dimensions: everything in it floats as the tesseract forms, then
     * every chunk is read at once and rewritten from another, shuffled, turned and tipped, and what was inside dies.
     */
    private static final class FourthDimension extends AreaEffect {
        private final double radius;
        private final int delay;
        private final com.fantasyweapons.weapons.ultimate.ShufflePlan plan;
        private final java.util.List<net.minecraft.world.level.ChunkPos> region;
        private final long seed;
        private final float damage;

        FourthDimension(AbilityContext ctx, Vec3 at, double radius, int delay, com.fantasyweapons.weapons.ultimate.ShufflePlan plan, long seed) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), at, delay + 40);
            this.radius = radius;
            this.delay = delay;
            this.plan = plan;
            this.region = plan.moves().stream().map(com.fantasyweapons.weapons.ultimate.ShufflePlan.Move::dest).toList();
            this.seed = seed;
            this.damage = ctx.damage();
        }

        private java.util.List<LivingEntity> inside(ServerPlayer owner) {
            return Cataclysm.victims(level, owner, pos, radius * 1.3).stream()
                    .filter(e -> com.fantasyweapons.weapons.ultimate.ShufflePlan.inside(region, e.getX(), e.getZ())).toList();
        }

        @Override
        protected void tick(ServerPlayer owner) {
            if (age < delay && age % 10 == 0) {
                for (LivingEntity e : inside(owner)) e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.LEVITATION, 14, 0, false, false));
            }
            if (age == delay) shatter(owner);
            if (age == delay + 14) {
                for (LivingEntity e : inside(owner)) Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.VOID);
            }
        }

        private void shatter(ServerPlayer owner) {
            // every chunk is read before any is written, so each can be refilled from any other
            java.util.Map<Long, net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.world.level.block.state.BlockState>[]> snaps = new java.util.HashMap<>();
            for (var m : plan.moves()) {
                var chunk = level.getChunkSource().getChunkNow(m.dest().x, m.dest().z);
                if (chunk != null) snaps.put(m.dest().toLong(), com.fantasyweapons.world.wreck.WreckChunk.snapshot(chunk));
            }
            for (var m : plan.moves()) WorldWreck.submit(level, m.dest().x, m.dest().z, m.lag(), seed, WreckShapes.shuffle(m, snaps));
            for (LivingEntity e : inside(owner)) Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.VOID);
            Cataclysm.frames(level, owner, pos, radius * 3, true);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 40f, 0.4f);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // The Arch Dragon of Hell
    // ------------------------------------------------------------------------------------------------------------

    /** Ticks from the strike until the split ground has run out to its edge. */
    private static final int SPLIT = 40;

    /** The cracks the dragon's strike splits the ground with (the client draws the same ones). */
    public static com.fantasyweapons.weapons.ultimate.CrackNetwork dragonCracks(long seed, double radius) {
        return com.fantasyweapons.weapons.ultimate.CrackNetwork.radial(seed ^ 0xD4A60L, 14, radius, 4.5, 22);
    }

    /** Where the Arch Dragon bursts out of the ground: behind the caster. */
    public static Vec3 dragonRift(net.minecraft.server.level.ServerLevel level, ServerPlayer p, Vec3 target) {
        Vec3 f = new Vec3(target.x - p.getX(), 0, target.z - p.getZ());
        if (f.lengthSqr() < 1) f = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        if (f.lengthSqr() < 1e-4) f = new Vec3(0, 0, 1);
        Vec3 at = Kit.feet(p).subtract(f.normalize().scale(48));
        return Cataclysm.surface(level, at);
    }

    public static boolean archDragon(AbilityContext ctx) {
        Vec3 target = Cataclysm.landing(ctx, ctx.param("range"));
        double radius = ctx.param("radius"), length = ctx.param("length");
        int flight = Math.max(40, (int) Math.round(ctx.param("flight") * 20));
        long seed = Kit.seed(ctx.level());
        Vec3 caster = Kit.feet(ctx.player());
        Vec3 rift = dragonRift(ctx.level(), ctx.player(), target);
        Cataclysm.fx(ctx.level(), ctx.player(), FxPayload.of(FxIds.ARCH_DRAGON).caster(ctx.player().getId()).pos(target)
                .points(java.util.List.of(caster, rift)).scale((float) length).power((float) radius).level(flight).seed(seed).build());
        AreaEffectManager.add(new ArchDragon(ctx, caster, rift, target, radius, length, flight, seed));
        Kit.active(ctx, flight + SPLIT + 20);
        return true;
    }

    /**
     * The dragon flies its path, burning and devouring what it passes; it dives into the target, the ground there
     * explodes into a crater and splits open for the whole radius, and everything on it burns to coal, magma and lava.
     */
    private static final class ArchDragon extends AreaEffect {
        private final com.fantasyweapons.weapons.ultimate.DragonPath path;
        private final double radius, length;
        private final int flight;
        private final long seed;
        private final float damage;

        ArchDragon(AbilityContext ctx, Vec3 caster, Vec3 rift, Vec3 target, double radius, double length, int flight, long seed) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), target, flight + SPLIT + 30);
            this.path = new com.fantasyweapons.weapons.ultimate.DragonPath(caster, rift, target, flight, seed);
            this.radius = radius;
            this.length = length;
            this.flight = flight;
            this.seed = seed;
            this.damage = ctx.damage();
        }

        @Override
        protected void tick(ServerPlayer owner) {
            Vec3 rift = path.rift();
            if (age == 2) {
                // the hole it came out of goes down to the bottom of the world
                WorldWreck.disc(level, rift, 14, seed, d -> 0, WreckShapes.shaft(rift, 10, 4));
                Cataclysm.frames(level, owner, rift, 160, false);
                level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 30f, 0.5f);
            }
            if (age <= flight) devour(owner);
            if (age == flight) strike(owner);
            if (age > flight && age % 10 == 0) {
                for (LivingEntity e : Cataclysm.victims(level, owner, pos, radius)) e.igniteForSeconds(10);
            }
            if (age == flight + SPLIT + 5) {
                // nothing on the split ground lives
                for (LivingEntity e : Cataclysm.victims(level, owner, pos, radius)) Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.FIRE);
            }
        }

        /** What the head and the body pass through dies; what is near them catches fire. */
        private void devour(ServerPlayer owner) {
            double head = path.head(age);
            Vec3 h = path.at(head);
            for (LivingEntity e : Cataclysm.victims(level, owner, h, 30)) {
                if (e.distanceToSqr(h) < 16 * 16) Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.FIRE);
                else e.igniteForSeconds(8);
            }
            for (double s = head - 20; s > head - length && s > 0; s -= 20) {
                for (LivingEntity e : Cataclysm.victims(level, owner, path.at(s), 10)) Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.FIRE);
            }
        }

        private void strike(ServerPlayer owner) {
            double r = WorldWreck.radius(radius);
            double crater = r * 0.3;
            var cracks = dragonCracks(seed, r);
            WorldWreck.disc(level, pos, crater + 10, seed, d -> (int) (d / 9), WreckShapes.eruption(pos, crater, 20, cracks));
            WorldWreck.disc(level, pos, r + 4, seed + 1, d -> 2 + (int) (d / r * SPLIT), WreckShapes.all(WreckShapes.scorch(pos, r), WreckShapes.hellSplit(pos, cracks)));
            for (LivingEntity e : Cataclysm.victims(level, owner, pos.add(0, crater * 0.3, 0), crater * 1.3)) {
                e.push(0, 2.4, 0);
                e.hurtMarked = true;
                Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.FIRE);
            }
            for (LivingEntity e : Cataclysm.victims(level, owner, pos, radius * 1.4)) {
                if (e.distanceToSqr(pos) < crater * crater) continue;
                Kit.knock(e, pos, 3, 1.2);
                e.igniteForSeconds(12);
            }
            Cataclysm.frames(level, owner, pos, radius * 2.5, true);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 40f, 0.35f);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // The Grand Earthquake
    // ------------------------------------------------------------------------------------------------------------

    /** The earthquake's radial cracks (the client builds the same ones from the seed). */
    public static com.fantasyweapons.weapons.ultimate.CrackNetwork quakeCracks(long seed, double radius) {
        return com.fantasyweapons.weapons.ultimate.CrackNetwork.radial(seed ^ 0x51AB, 16, radius, 4, 26);
    }

    public static boolean grandEarthquake(AbilityContext ctx) {
        Vec3 at = Kit.feet(ctx.player());
        double radius = ctx.param("radius"), safe = ctx.param("safe_radius");
        int duration = Math.max(60, (int) Math.round(ctx.param("duration") * 20));
        long seed = Kit.seed(ctx.level());
        Cataclysm.fx(ctx.level(), ctx.player(), FxPayload.of(FxIds.GRAND_EARTHQUAKE).caster(ctx.player().getId()).pos(at)
                .scale((float) radius).power((float) safe).level(duration).seed(seed).build());
        AreaEffectManager.add(new GrandEarthquake(ctx, at, radius, safe, duration, seed));
        Kit.active(ctx, duration);
        return true;
    }

    /** The earth breaks outward from the blow in plates, fissures and cracks; everything on it is torn apart. */
    private static final class GrandEarthquake extends AreaEffect {
        private final double radius, safe;
        private final int duration;
        private final long seed;
        private final float damage;

        GrandEarthquake(AbilityContext ctx, Vec3 at, double radius, double safe, int duration, long seed) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), at, duration + 2);
            this.radius = radius;
            this.safe = safe;
            this.duration = duration;
            this.seed = seed;
            this.damage = ctx.damage();
        }

        private double front() {
            return safe + (radius - safe) * Math.min(1, age / (duration * 0.85));
        }

        @Override
        protected void tick(ServerPlayer owner) {
            if (age == 1) {
                double r = WorldWreck.radius(radius);
                net.minecraft.util.RandomSource rand = net.minecraft.util.RandomSource.create(seed);
                int n = 30;
                double[][] plates = new double[n][2];
                int[] shifts = new int[n];
                for (int i = 0; i < n; i++) {
                    double a = rand.nextDouble() * Math.PI * 2, d = Math.sqrt(rand.nextDouble()) * r;
                    plates[i][0] = Math.cos(a) * d;
                    plates[i][1] = Math.sin(a) * d;
                    shifts[i] = rand.nextInt(19) - 10;
                }
                var shaper = WreckShapes.quake(pos, r, safe, plates, shifts, quakeCracks(seed, r));
                WorldWreck.disc(level, pos, r, seed, d -> (int) (Math.max(0, d - safe) / Math.max(1, r - safe) * duration * 0.85), shaper);
                Cataclysm.frames(level, owner, pos, 80, true);
            }
            double front = front();
            if (age % 5 == 0) {
                for (LivingEntity e : Cataclysm.victims(level, owner, pos, Math.min(radius, front + 10))) {
                    double d = Math.hypot(e.getX() - pos.x, e.getZ() - pos.z);
                    if (d < safe + 1) continue;
                    // the ground bucks under everything; where the quake breaks through, it is torn apart
                    e.push((level.random.nextDouble() - 0.5) * 0.6, 0.35 + level.random.nextDouble() * 0.4, (level.random.nextDouble() - 0.5) * 0.6);
                    e.hurtMarked = true;
                    if (Math.abs(d - front) < 14) Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.EARTH);
                }
            }
            if (age == duration) {
                for (LivingEntity e : Cataclysm.victims(level, owner, pos, radius)) {
                    if (Math.hypot(e.getX() - pos.x, e.getZ() - pos.z) >= safe + 1) Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.EARTH);
                }
                Cataclysm.frames(level, owner, pos, radius, false);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Thornspire Cataclysm
    // ------------------------------------------------------------------------------------------------------------

    /** Ticks the spiral of thorns takes to run out to its end. */
    private static final int SPIRAL = 90;

    public static boolean thornspire(AbilityContext ctx) {
        Vec3 at = Kit.feet(ctx.player());
        double radius = WorldWreck.radius(ctx.param("radius")), safe = ctx.param("safe_radius");
        int count = Math.max(6, Math.min(160, (int) Math.round(ctx.param("spires"))));
        long seed = Kit.seed(ctx.level());
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(seed);
        java.util.List<Vec3> pts = new java.util.ArrayList<>();
        java.util.List<Integer> codes = new java.util.ArrayList<>();
        java.util.List<double[]> spires = new java.util.ArrayList<>();
        double turn = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double k = i / (double) (count - 1);
            double d = safe + 6 + (radius - safe - 6) * Math.pow(k, 0.8) + (r.nextDouble() - 0.5) * 6;
            double a = turn + k * Math.PI * 2 * 3.2 + (r.nextDouble() - 0.5) * 0.3;
            double x = at.x + Math.cos(a) * d, z = at.z + Math.sin(a) * d;
            double g = groundY(ctx.level(), x, z, at.y);
            double height = 18 + 30 * Math.sqrt(k) + r.nextDouble() * 14;
            double lean = Math.toRadians(12 + r.nextDouble() * 22);
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 foot = new Vec3(x, g - 3, z);
            Vec3 tip = foot.add(out.scale(Math.sin(lean) * height)).add(0, Math.cos(lean) * height, 0);
            double rad = 2.4 + 2.6 * Math.sqrt(k) + r.nextDouble() * 1.4;
            int tick = 8 + (int) (k * SPIRAL);
            pts.add(foot);
            pts.add(tip);
            codes.add(tick * 1000 + (int) Math.round(rad * 10));
            spires.add(new double[]{tick, rad});
        }
        Cataclysm.fx(ctx.level(), ctx.player(), FxPayload.of(FxIds.THORNSPIRE).caster(ctx.player().getId()).pos(at).scale((float) radius)
                .power((float) safe).points(pts).entities(codes).seed(seed).build());
        AreaEffectManager.add(new Thornspire(ctx, at, radius, safe, pts, spires, seed));
        Kit.active(ctx, SPIRAL + 20);
        return true;
    }

    /** The thorns burst up one after another along the spiral, impaling what stands where they rise; they stay. */
    private static final class Thornspire extends AreaEffect {
        private final double radius, safe;
        private final java.util.List<Vec3> pts;
        private final java.util.List<double[]> spires;
        private final long seed;
        private final float damage;

        Thornspire(AbilityContext ctx, Vec3 at, double radius, double safe, java.util.List<Vec3> pts, java.util.List<double[]> spires, long seed) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), at, SPIRAL + 30);
            this.radius = radius;
            this.safe = safe;
            this.pts = pts;
            this.spires = spires;
            this.seed = seed;
            this.damage = ctx.damage();
        }

        @Override
        protected void tick(ServerPlayer owner) {
            for (int i = 0; i < spires.size(); i++) {
                if ((int) spires.get(i)[0] != age) continue;
                Vec3 foot = pts.get(i * 2), tip = pts.get(i * 2 + 1);
                double rad = spires.get(i)[1];
                var shaper = WreckShapes.thornSpire(foot, tip, rad);
                int reach = (int) Math.ceil(rad + 5);
                for (int cx = (int) Math.floor(Math.min(foot.x, tip.x) - reach) >> 4; cx <= (int) Math.floor(Math.max(foot.x, tip.x) + reach) >> 4; cx++) {
                    for (int cz = (int) Math.floor(Math.min(foot.z, tip.z) - reach) >> 4; cz <= (int) Math.floor(Math.max(foot.z, tip.z) + reach) >> 4; cz++) {
                        WorldWreck.submit(level, cx, cz, 0, seed + i, shaper);
                    }
                }
                for (LivingEntity e : Cataclysm.victims(level, owner, foot.add(0, 4, 0), rad + 6)) {
                    e.push(0, 1.6, 0);
                    e.hurtMarked = true;
                    Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.NATURE);
                }
            }
            if (age == 10) Cataclysm.frames(level, owner, pos, radius, true);
            if (age == SPIRAL + 12) {
                // nothing in the forest of thorns survives
                for (LivingEntity e : Cataclysm.victims(level, owner, pos, radius)) {
                    if (Math.hypot(e.getX() - pos.x, e.getZ() - pos.z) >= safe) Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.NATURE);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Ice Rain
    // ------------------------------------------------------------------------------------------------------------

    /** Ticks the cloud gathers before the first spike falls, and ticks a spike falls for. */
    private static final int GATHER = 30, FALL = 16;

    public static boolean iceRain(AbilityContext ctx) {
        Vec3 at = Kit.feet(ctx.player());
        double radius = ctx.param("radius"), safe = ctx.param("safe_radius");
        int duration = Math.max(GATHER + 40, (int) Math.round(ctx.param("duration") * 20));
        int count = Math.max(4, Math.min(250, (int) Math.round(ctx.param("spikes"))));
        long seed = Kit.seed(ctx.level());
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(seed);
        java.util.List<Vec3> strikes = new java.util.ArrayList<>();
        java.util.List<Integer> times = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = safe + 4 + Math.sqrt(r.nextDouble()) * (radius - safe - 4);
            double x = at.x + Math.cos(a) * d, z = at.z + Math.sin(a) * d;
            strikes.add(new Vec3(x, groundY(ctx.level(), x, z, at.y), z));
            times.add(GATHER + FALL + (int) (r.nextDouble() * (duration - GATHER - FALL - 10)));
        }
        Cataclysm.fx(ctx.level(), ctx.player(), FxPayload.of(FxIds.ICE_RAIN).caster(ctx.player().getId()).pos(at).scale((float) radius)
                .power(duration).points(strikes).entities(times).seed(seed).build());
        AreaEffectManager.add(new IceRain(ctx, at, radius, safe, duration, strikes, times, seed));
        Kit.active(ctx, duration);
        return true;
    }

    private static double groundY(net.minecraft.server.level.ServerLevel level, double x, double z, double fallback) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (level.getChunkSource().getChunkNow(bx >> 4, bz >> 4) == null) return fallback;
        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
    }

    /** A storm cloud rains giant spikes of ice: each strike blasts a crater and kills, and in the end the land freezes. */
    private static final class IceRain extends AreaEffect {
        private final double radius, safe;
        private final int duration;
        private final java.util.List<Vec3> strikes;
        private final java.util.List<Integer> times;
        private final long seed;
        private final float damage;

        IceRain(AbilityContext ctx, Vec3 at, double radius, double safe, int duration, java.util.List<Vec3> strikes, java.util.List<Integer> times, long seed) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), at, duration + 2);
            this.radius = radius;
            this.safe = safe;
            this.duration = duration;
            this.strikes = strikes;
            this.times = times;
            this.seed = seed;
            this.damage = ctx.damage();
        }

        @Override
        protected void tick(ServerPlayer owner) {
            for (int i = 0; i < strikes.size(); i++) {
                if (times.get(i) != age) continue;
                Vec3 p = strikes.get(i);
                net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(seed + i * 31L);
                double length = 12 + r.nextDouble() * 14, base = 2.2 + r.nextDouble() * 1.6, crater = 3 + r.nextDouble() * 2.5;
                Vec3 dir = new Vec3((r.nextDouble() - 0.5) * 0.5, -1, (r.nextDouble() - 0.5) * 0.5);
                var shaper = WreckShapes.iceSpike(p, dir, length, base, crater);
                int reach = (int) Math.ceil(crater + 8);
                for (int cx = (int) Math.floor(p.x - reach) >> 4; cx <= (int) Math.floor(p.x + reach) >> 4; cx++) {
                    for (int cz = (int) Math.floor(p.z - reach) >> 4; cz <= (int) Math.floor(p.z + reach) >> 4; cz++) {
                        WorldWreck.submit(level, cx, cz, 0, seed + i, shaper);
                    }
                }
                for (LivingEntity e : Cataclysm.victims(level, owner, p, 7)) Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.ICE);
                for (LivingEntity e : Cataclysm.victims(level, owner, p, 16)) Kit.knock(e, p, 1.2, 0.5);
            }
            if (age == GATHER + FALL) Cataclysm.frames(level, owner, pos, radius, true);
            if (age == duration - 10) {
                // what the ice did not strike, the cold takes
                double r = WorldWreck.radius(radius);
                WorldWreck.disc(level, pos, r, seed, d -> (int) (d / 12), WreckShapes.freeze(pos, r, safe));
                for (LivingEntity e : Cataclysm.victims(level, owner, pos, radius)) {
                    if (Math.hypot(e.getX() - pos.x, e.getZ() - pos.z) >= safe) Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.ICE);
                }
            }
        }

    }
}
