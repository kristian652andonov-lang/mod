package com.fantasyweapons.armor;

import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.network.Fx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.world.AreaEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Gravebite's Legion of the Grave: a slain enemy risen as a short-lived spectral ally. It hunts the enemies near its
 * master and strikes them with necromancy, then fades. Server side it is only a moving point; clients draw a ghost that
 * follows the positions it sends ({@code level} 0 = moving, 1 = striking, 2 = fading).
 */
final class SpectralAlly extends AreaEffect {
    private static final int RISE = 12;
    private static final double HUNT_RADIUS = 10;
    private static final double LEASH = 24;

    private final float damage;
    private final float size;
    private final long seed;
    private int targetId = -1;
    private int cooldown = RISE;

    SpectralAlly(ServerLevel level, ServerPlayer owner, UUID weaponId, Vec3 pos, int lifetime, float damage, float size, long seed) {
        super(level, owner, weaponId, pos, lifetime);
        this.damage = damage;
        this.size = size;
        this.seed = seed;
    }

    @Override
    protected void tick(ServerPlayer owner) {
        if (owner.distanceToSqr(pos) > LEASH * LEASH) {
            finished = true;
            return;
        }
        if (age <= RISE) {
            // climbs out of the corpse
            pos = pos.add(0, 0.05, 0);
            if (age == 1 || age % 3 == 0) send(0, -1);
            return;
        }
        LivingEntity target = target(owner);
        Vec3 goal;
        double speed;
        if (target != null) {
            goal = target.getBoundingBox().getCenter();
            speed = 0.42;
        } else {
            // with nothing to fight it hovers at its master's shoulder
            float yaw = (owner.getYRot() + 130 + (seed & 63)) * Mth.DEG_TO_RAD;
            goal = owner.position().add(-Mth.sin(yaw) * 1.3, 1.9 + Math.sin(age * 0.12) * 0.15, Mth.cos(yaw) * 1.3);
            speed = 0.3;
        }
        Vec3 to = goal.subtract(pos);
        double d = to.length();
        if (d > 1e-3) pos = pos.add(to.scale(Math.min(speed, target != null ? d : d * 0.25) / d));
        if (cooldown > 0) cooldown--;
        if (target != null && cooldown <= 0 && pos.distanceTo(goal) < 1.2 + target.getBbWidth() * 0.5) {
            FWDamage.deal(owner, weapon(owner), target, damage, FWDamage.Kind.ABILITY, Element.NECROMANCY, 0);
            cooldown = 20;
            send(1, target.getId());
        } else if (age % 3 == 0) {
            send(0, target == null ? -1 : target.getId());
        }
    }

    @Nullable
    private LivingEntity target(ServerPlayer owner) {
        if (targetId >= 0 && level.getEntity(targetId) instanceof LivingEntity t && t.isAlive()
                && FWDamage.canHarm(owner, t, FWDamage.Kind.ABILITY) && t.distanceToSqr(owner) < LEASH * LEASH) {
            return t;
        }
        targetId = -1;
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : Targeting.inRadius(level, owner, pos, HUNT_RADIUS, FWDamage.Kind.ABILITY)) {
            if (e instanceof net.minecraft.world.entity.player.Player) continue;
            double d = e.distanceToSqr(pos);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best != null) targetId = best.getId();
        return best;
    }

    private void send(int state, int target) {
        Fx.near(level, FxPayload.of(FxIds.SET_GRAVE_ALLY).caster(-1).pos(pos).power(lifetime - age).scale(size).level(state)
                .entities(target < 0 ? new int[0] : new int[]{target}).seed(seed).build());
    }

    @Override
    protected void onEnd(@Nullable ServerPlayer owner) {
        send(2, -1);
        // its master may have left this dimension: the ally's place is freed all the same
        SetAbilities.allyGone(owner != null ? owner : level.getServer().getPlayerList().getPlayer(ownerId));
    }
}
