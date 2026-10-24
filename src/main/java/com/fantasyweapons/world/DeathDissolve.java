package com.fantasyweapons.world;

import com.fantasyweapons.combat.HitTracker;
import com.fantasyweapons.network.Fx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.registry.ModAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Replaces vanilla's white death puff for mobs killed by a fantasy weapon. Vanilla removes a dead mob at
 * {@code deathTime == 20} and broadcasts entity event 60 (the puff); we remove it a moment earlier and send our own
 * elemental dissolve effect instead. Loot and XP are unaffected (they drop when the mob dies, not when it is
 * removed).
 */
public final class DeathDissolve {
    /** Death tick at which the body is removed (vanilla removes at 20). */
    public static final int REMOVE_AT = 18;

    private DeathDissolve() {
    }

    public static void tick(LivingEntity entity) {
        HitTracker tracker = entity.getExistingDataOrNull(ModAttachments.HIT_TRACKER);
        if (tracker == null || tracker.dissolveElement < 0) return;
        AABB box = entity.getBoundingBox();
        Fx.tracking(entity, FxPayload.of(FxIds.DEATH_DISSOLVE).caster(entity.getId()).pos(box.getCenter())
                .scale((float) Math.max(box.getXsize(), box.getZsize())).power((float) box.getYsize())
                .level(tracker.dissolveElement).seed(entity.getRandom().nextLong()).build());
        entity.remove(Entity.RemovalReason.KILLED);
    }
}
