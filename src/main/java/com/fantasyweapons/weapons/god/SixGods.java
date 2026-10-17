package com.fantasyweapons.weapons.god;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapons.ultimate.Cataclysm;
import com.fantasyweapons.world.AreaEffect;
import com.fantasyweapons.world.AreaEffectManager;
import com.fantasyweapons.world.wreck.ChunkShaper;
import com.fantasyweapons.world.wreck.DivineShapes;
import com.fantasyweapons.world.wreck.WorldWreck;
import com.fantasyweapons.world.wreck.WreckShapes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * COMBINED POWER OF THE 6 GODS. An enormous magic circle in the colours of the fourth dimension opens in the sky, far
 * larger than Grant Execution's; six circles each as large as that one turn round it, one in the colour of each power,
 * tilted in toward it. Each pours its power into the middle, and a beam of every colour at once - the six mixed, as the
 * tesseract's edges are - falls from it, erasing everything within the radius to the bottom of the world.
 */
public final class SixGods {
    /** Radius of the ground left standing under the caster, if they are inside the radius. */
    private static final double SAFE = 12;

    private SixGods() {
    }

    private static int delay(AbilityContext ctx) {
        return Math.max(60, (int) Math.round(ctx.param("delay") * 20));
    }

    /**
     * Sets going the land the beam erases (all of it, to the bottom of the world, but the caster's own ground if they stand
     * inside), to be gone the moment the beam bites: {@code lead} ticks from now and the circles' filling after that.
     */
    static void prepare(AbilityContext ctx, Vec3 at, long seed, int lead) {
        int delay = delay(ctx);
        double r = WorldWreck.radius(ctx.param("radius"));
        Vec3 feet = ctx.feet();
        ChunkShaper shaft = WreckShapes.shaft(at, r, com.fantasyweapons.world.wreck.Wall.SHATTERED);
        if (Math.hypot(feet.x - at.x, feet.z - at.z) < r + SAFE + 16) shaft = DivineShapes.sparing(shaft, feet, SAFE);
        WorldWreck.disc(ctx.level(), at, r + 20, seed, d -> lead + delay - 1, shaft);
    }

    public static boolean cast(AbilityContext ctx) {
        DivineSummoning.Prepared pre = DivineSummoning.prepared(ctx);
        Vec3 at = pre != null ? pre.at() : Cataclysm.landing(ctx, 1024);
        double radius = ctx.param("radius");
        int delay = delay(ctx);
        int beam = Math.max(40, (int) Math.round(ctx.param("duration") * 20));
        long seed = pre != null ? pre.seed() : Kit.seed(ctx.level());
        if (pre == null) prepare(ctx, at, seed, 0);
        Cataclysm.fx(ctx.level(), ctx.player(), FxPayload.of(FxIds.SIX_GODS).caster(ctx.player().getId()).pos(at)
                .scale((float) radius).power(delay).level(beam).seed(seed).build());
        AreaEffectManager.add(new Beam(ctx, at, radius, delay, beam));
        Kit.active(ctx, delay + beam);
        return true;
    }

    private static final class Beam extends AreaEffect {
        private final double radius;
        private final int delay, beam;
        private final float damage;

        Beam(AbilityContext ctx, Vec3 at, double radius, int delay, int beam) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), at, delay + beam + 2);
            this.radius = radius;
            this.delay = delay;
            this.beam = beam;
            this.damage = ctx.damage();
        }

        @Override
        protected void tick(ServerPlayer owner) {
            if (age == delay) {
                Cataclysm.frames(level, owner, pos, radius * 3, 0xC79CFF, 0xFFFFFF);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 80f, 0.3f);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 80f, 0.5f);
            }
            if (age >= delay && age <= delay + beam && (age - delay) % 4 == 0) {
                Cataclysm.eraseColumn(owner, weapon(owner), level, pos, radius, damage, Element.LIGHT);
            }
        }
    }
}
