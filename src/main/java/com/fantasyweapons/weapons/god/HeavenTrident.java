package com.fantasyweapons.weapons.god;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapons.ultimate.Cataclysm;
import com.fantasyweapons.weapons.ultimate.CrackNetwork;
import com.fantasyweapons.world.AreaEffect;
import com.fantasyweapons.world.AreaEffectManager;
import com.fantasyweapons.world.wreck.WorldWreck;
import com.fantasyweapons.world.wreck.WreckShapes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * TRIDENT FROM HEAVEN. The sky tears open high over the target, on the caster's side of it; a giant trident slowly
 * comes out of the opening, prongs first, then falls along a steep line onto the place chosen. Whatever is in its way
 * as it falls is destroyed, and where it strikes, everything within the radius: the land is blasted out in a bowl down
 * through the bottom of the world, the ground round it split open.
 */
public final class HeavenTrident {
    /** How high over the target the sky opens, and how steeply the trident falls from it (degrees above level). */
    public static final double HEIGHT = 320, PITCH = 66;
    /** How long the trident is, from the tips of its prongs to the end of its shaft. */
    public static final double LENGTH = 230;

    private HeavenTrident() {
    }

    /** Where the sky opens for a strike at {@code at}, the caster standing at {@code from}. */
    public static Vec3 opening(Vec3 at, Vec3 from) {
        Vec3 back = new Vec3(from.x - at.x, 0, from.z - at.z);
        if (back.lengthSqr() < 1) back = new Vec3(0, 0, -1);
        back = back.normalize();
        return at.add(back.scale(HEIGHT / Math.tan(Math.toRadians(PITCH)))).add(0, HEIGHT, 0);
    }

    public static boolean cast(AbilityContext ctx) {
        Vec3 at = Cataclysm.landing(ctx, 1024);
        Vec3 sky = opening(at, ctx.feet());
        int open = Math.max(40, (int) Math.round(ctx.param("open") * 20));
        int emerge = Math.max(40, (int) Math.round(ctx.param("emerge") * 20));
        int fall = Math.max(20, (int) Math.round(ctx.param("fall") * 20));
        int strike = open + emerge + fall;
        double radius = ctx.param("radius");
        long seed = Kit.seed(ctx.level());
        Cataclysm.fx(ctx.level(), ctx.player(), FxPayload.of(FxIds.HEAVEN_TRIDENT).caster(ctx.player().getId()).pos(at).point(sky)
                .scale((float) radius).power(strike).level(open).entities(java.util.List.of(emerge)).seed(seed).build());
        AreaEffectManager.add(new Strike(ctx, at, sky, radius, open, emerge, strike, seed));
        Kit.active(ctx, strike + 40);
        return true;
    }

    /** Where the tips of the prongs are, {@code tick} ticks in (the client works the same out). */
    public static Vec3 tips(Vec3 sky, Vec3 at, int open, int emerge, int strike, float tick) {
        Vec3 dir = at.subtract(sky).normalize();
        if (tick < open) return sky;
        if (tick < open + emerge) {
            // slowly out of the sky, until the whole trident is out
            float k = (tick - open) / emerge;
            float eased = k * k * (3 - 2 * k);
            return sky.add(dir.scale(LENGTH * eased));
        }
        // then it falls, faster and faster
        float k = Math.min(1, (tick - open - emerge) / (float) (strike - open - emerge));
        Vec3 out = sky.add(dir.scale(LENGTH));
        return out.add(at.subtract(out).scale(k * k));
    }

    private static final class Strike extends AreaEffect {
        private final Vec3 sky;
        private final double radius;
        private final int open, emerge, strike;
        private final long seed;
        private final float damage;

        Strike(AbilityContext ctx, Vec3 at, Vec3 sky, double radius, int open, int emerge, int strike, long seed) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), at, strike + 40);
            this.sky = sky;
            this.radius = radius;
            this.open = open;
            this.emerge = emerge;
            this.strike = strike;
            this.seed = seed;
            this.damage = ctx.damage();
        }

        @Override
        protected void tick(ServerPlayer owner) {
            if (age == 1) {
                // the crater, and the cracks it splits round itself, are worked out while the trident comes, so they land
                // all at once the moment it strikes
                double r = WorldWreck.radius(radius);
                WorldWreck.disc(level, pos, r + WreckShapes.ragReach(r) + 14, seed, d -> strike - 1, WreckShapes.sphere(pos, r));
            }
            if (age == 2) {
                double r = WorldWreck.radius(radius);
                var ring = CrackNetwork.radial(seed ^ 0x7121DEL, 18, r * 0.98, r * 1.6, 7, 120);
                WorldWreck.disc(level, pos, r * 1.6 + 12, seed + 7, d -> strike + 10, WreckShapes.cracks(pos, ring));
            }
            if (age == open) level.playSound(null, sky.x, sky.y, sky.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 60f, 0.5f);
            if (age > open && age < strike) {
                // whatever is in its way as it comes is destroyed
                Vec3 tips = tips(sky, pos, open, emerge, strike, age);
                Vec3 dir = pos.subtract(sky).normalize();
                for (double s = 0; s < LENGTH; s += 24) {
                    Vec3 p = tips.subtract(dir.scale(s));
                    if (p.subtract(sky).dot(dir) < 0) break;
                    for (LivingEntity e : Cataclysm.victims(level, owner, p, s < 60 ? 34 : 14)) Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.LIGHT);
                }
            }
            if (age == strike) land(owner);
        }

        private void land(ServerPlayer owner) {
            for (LivingEntity e : Cataclysm.victims(level, owner, pos, radius)) Cataclysm.annihilate(owner, weapon(owner), e, damage, Element.LIGHT);
            // the blast throws back and burns what stands beyond the crater
            for (LivingEntity e : Cataclysm.victims(level, owner, pos, radius * 1.6)) {
                if (e.distanceToSqr(pos) < radius * radius) continue;
                Kit.knock(e, pos, 4, 1.5);
                e.igniteForSeconds(10);
            }
            Cataclysm.frames(level, owner, pos, radius * 3, 0xFFE07A, 0xFFFFFF);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 60f, 0.3f);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 60f, 0.5f);
        }
    }
}
