package com.fantasyweapons.combat;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityKind;
import com.fantasyweapons.network.Fx;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Impact frames belong to the blow landing, not to the cast: casting an ability that deals damage arms one for the
 * caster, and the first hit that ability lands fires it, where it lands - an ultimate's full sequence, any other
 * ability's quick flash - for the caster and for the players within 20 to 50 blocks (by the ability) of the hit or the
 * caster. Heals, shields and blessings (no damage) never arm one, a cast that fizzles is disarmed, and an
 * armed frame that never lands (a miss) lapses after a few seconds. Swings, bolts and damage over time do not fire it.
 */
public final class ImpactHits {
    /** How long an armed frame waits for its hit (ticks): ultimates are often slow to land (a meteor, a dive). */
    private static final int ACTIVE_WINDOW = 120, ULTIMATE_WINDOW = 260;

    /**
     * How far other players see it (blocks), by the ability: 20 to 35 for an ordinary one, 35 to 50 for an ultimate,
     * the wider its area the further.
     */
    private static final float ACTIVE_SEEN = 20, ACTIVE_SEEN_MAX = 35, ULTIMATE_SEEN = 35, ULTIMATE_SEEN_MAX = 50;

    private record Armed(boolean ultimate, long expires, float seen) {
    }

    /** Server thread only. */
    private static final Map<UUID, Armed> ARMED = new HashMap<>();

    private ImpactHits() {
    }

    /** Just before an ability runs: arms its impact frame if it deals damage (so an instant hit inside the cast fires it). */
    public static void arm(ServerPlayer caster, AbilityDefinition ability) {
        if (!ability.hasParam(AbilityDefinition.DAMAGE) || ability.param(AbilityDefinition.DAMAGE) <= 0) {
            ARMED.remove(caster.getUUID());
            return;
        }
        boolean ultimate = ability.kind() == AbilityKind.ULTIMATE;
        long now = caster.serverLevel().getGameTime();
        ARMED.put(caster.getUUID(), new Armed(ultimate, now + (ultimate ? ULTIMATE_WINDOW : ACTIVE_WINDOW), seenFrom(ability, ultimate)));
    }

    /** How far away other players see this ability's impact frames. */
    public static float seenFrom(AbilityDefinition ability, boolean ultimate) {
        float area = ability.hasParam("radius") ? (float) ability.param("radius") : 0;
        float base = ultimate ? ULTIMATE_SEEN : ACTIVE_SEEN, max = ultimate ? ULTIMATE_SEEN_MAX : ACTIVE_SEEN_MAX;
        return Math.min(max, base + area * 2);
    }

    /** The cast failed: nothing will land. */
    public static void disarm(ServerPlayer caster) {
        ARMED.remove(caster.getUUID());
    }

    /** Every ability hit: the first one after a cast fires its impact frame at the target. */
    public static void onHit(ServerPlayer attacker, LivingEntity target) {
        Armed armed = ARMED.remove(attacker.getUUID());
        if (armed == null || attacker.serverLevel().getGameTime() > armed.expires()) return;
        FxPayload frame = FxPayload.of(FxIds.IMPACT_FRAME).caster(attacker.getId())
                .pos(target.position().add(0, target.getBbHeight() * 0.5, 0)).level(armed.ultimate() ? 1 : 0).scale(armed.seen()).build();
        // everyone near the blow or the caster (their clients show it within the ability's radius), and the caster wherever they stand
        Fx.near(attacker.serverLevel(), frame);
        if (attacker.position().distanceTo(frame.pos()) > Fx.VIEW_RADIUS) Fx.only(attacker, frame);
    }

    /** Whether the player has an impact frame waiting for its hit (tests). */
    public static boolean armed(ServerPlayer player) {
        return ARMED.containsKey(player.getUUID());
    }
}
