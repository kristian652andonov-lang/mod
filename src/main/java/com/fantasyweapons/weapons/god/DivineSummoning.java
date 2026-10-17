package com.fantasyweapons.weapons.god;

import com.fantasyweapons.ability.AbilityContext;
import com.fantasyweapons.ability.AbilityExecutor;
import com.fantasyweapons.ability.kit.Kit;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.weapons.ultimate.Cataclysm;
import com.fantasyweapons.world.AreaEffect;
import com.fantasyweapons.world.AreaEffectManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * How every power of the Weapon of God begins: slowly. The caster is lifted into the air and held there, wreathed in
 * light, while the sky darkens and the power is called down - a great circle under them, the six powers' lights circling
 * them, a pillar of light up out of the blade, and over the place it will strike, the clouds turning - for ten seconds.
 * Only then is it loosed, as if it had been cast at that moment with the aim it was cast with. The caster cannot be hurt
 * while they summon, and comes down slowly after.
 */
public final class DivineSummoning {
    /** Ticks a power takes to summon. */
    public static final int TICKS = 200;
    /** How high the caster is lifted. */
    private static final double LIFT = 3.5;

    /** Each power's colour, in {@link WeaponOfGod#POWERS} order (the summoning burns in it). */
    private static final int[] COLORS = {0xFFD970, 0xA066FF, 0xFF5A1E, 0xD49A52, 0x7BDA62, 0x9EE6FF, 0xFFF2C0, 0xB57CFF, 0xFFFFFF};

    private DivineSummoning() {
    }

    public static int color(String abilityId) {
        int i = WeaponOfGod.POWERS.indexOf(abilityId);
        return i < 0 ? 0xFFFFFF : COLORS[i];
    }

    /** Begins summoning {@code power}; it is under way (and goes on cooldown) from now. */
    public static boolean begin(AbilityContext ctx, AbilityExecutor power) {
        AbilityContext held = ctx.frozen();
        var level = ctx.level();
        ServerPlayer p = ctx.player();
        Vec3 at = Cataclysm.landing(held, 1024);
        double size = held.ability().hasParam("radius") ? held.param("radius") : 160;
        int index = WeaponOfGod.POWERS.indexOf(held.ability().id());
        Cataclysm.fx(level, p, FxPayload.of(FxIds.DIVINE_SUMMON).caster(p.getId()).pos(at).point(held.feet()).scale((float) size)
                .power(TICKS).level(color(held.ability().id())).entities(java.util.List.of(Math.max(0, index))).seed(Kit.seed(level)).build());
        Kit.active(held, TICKS);
        // the land it will strike starts loading now, so it is there to be destroyed when the power comes
        double reach = Math.min(600, size + 48);
        for (var c : com.fantasyweapons.weapons.ultimate.ShufflePlan.region(at, reach)) com.fantasyweapons.world.wreck.WorldWreck.hold(level, c.x, c.z);
        AreaEffectManager.add(new Summoning(held, at, power));
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 6f, 0.5f);
        return true;
    }

    /** The caster held aloft while the power comes; then it is loosed. */
    private static final class Summoning extends AreaEffect {
        private final AbilityContext ctx;
        private final AbilityExecutor power;
        private final double startY;

        Summoning(AbilityContext ctx, Vec3 at, AbilityExecutor power) {
            super(ctx.level(), ctx.player(), ctx.data().idOrNil(), at, TICKS + 1);
            this.ctx = ctx;
            this.power = power;
            this.startY = ctx.player().getY();
        }

        @Override
        protected void tick(ServerPlayer owner) {
            if (age < TICKS) hold(owner);
            if (age % 40 == 0 && age < TICKS) {
                level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 4f, 0.5f + age / (float) TICKS);
            }
            if (age != TICKS) return;
            owner.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
            owner.removeEffect(MobEffects.LEVITATION);
            try {
                power.execute(ctx);
            } catch (RuntimeException e) {
                com.fantasyweapons.FantasyWeapons.LOGGER.error("Power {} failed after its summoning", ctx.ability().id(), e);
            }
            finished = true;
        }

        /** Lifted, then held hovering: no harm comes to them while they summon. */
        private void hold(ServerPlayer owner) {
            owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10, 4, false, false));
            owner.resetFallDistance();
            if (owner.getAbilities().flying) return;
            double target = startY + LIFT;
            if (owner.getY() < target - 0.25) {
                owner.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 6, 1, false, false));
                owner.removeEffect(MobEffects.SLOW_FALLING);
            } else {
                owner.removeEffect(MobEffects.LEVITATION);
                owner.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 6, 0, false, false));
            }
        }
    }
}
