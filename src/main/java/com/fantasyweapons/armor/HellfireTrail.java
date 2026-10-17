package com.fantasyweapons.armor;

import com.fantasyweapons.combat.FWDamage;
import com.fantasyweapons.combat.Targeting;
import com.fantasyweapons.status.StatusService;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.world.AreaEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Infernochain's Hellforged: a line of hellfire left on the ground by a chain attack. It sets alight whoever stands in it. */
final class HellfireTrail extends AreaEffect {
    private static final double WIDTH = 0.9;
    private final Vec3 from, to;
    private final float sear;

    HellfireTrail(ServerLevel level, ServerPlayer owner, UUID weaponId, Vec3 from, Vec3 to, int lifetime, float sear) {
        super(level, owner, weaponId, from, lifetime);
        this.from = from.add(0, 0.5, 0);
        this.to = to.add(0, 0.5, 0);
        this.sear = sear;
    }

    @Override
    protected void tick(ServerPlayer owner) {
        if (age % 10 != 1) return;
        for (LivingEntity e : Targeting.alongPath(level, owner, from, to, WIDTH, FWDamage.Kind.ABILITY)) {
            e.igniteForSeconds(4);
            StatusService.apply(e, StatusType.SEARED, 40, 1, sear, owner.getUUID());
        }
    }
}
