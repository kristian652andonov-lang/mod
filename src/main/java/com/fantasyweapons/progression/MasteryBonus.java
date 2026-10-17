package com.fantasyweapons.progression;

import java.util.List;

/**
 * Mastery milestones. Mastery is a percentage derived from weapon level and purchased upgrades; crossing a milestone
 * grants a passive bonus. The list is the single extension point for future cosmetic/animation unlocks.
 */
public final class MasteryBonus {
    public enum Kind { DAMAGE, COOLDOWN, CHARGE_SPEED, AWAKENED }

    public record Milestone(float threshold, Kind kind, float amount, String description) {
    }

    public static final List<Milestone> MILESTONES = List.of(
            new Milestone(0.25f, Kind.DAMAGE, 0.05f, "+5% weapon damage"),
            new Milestone(0.50f, Kind.COOLDOWN, 0.10f, "-10% ability cooldowns"),
            new Milestone(0.75f, Kind.CHARGE_SPEED, 0.15f, "+15% ability charge speed"),
            new Milestone(1.00f, Kind.AWAKENED, 0.10f, "Awakened: +10% damage and an awakened aura")
    );

    private MasteryBonus() {
    }

    private static float sum(float mastery, Kind kind) {
        float total = 0;
        for (Milestone m : MILESTONES) {
            if (mastery + 1e-4f >= m.threshold() && (m.kind() == kind || (kind == Kind.DAMAGE && m.kind() == Kind.AWAKENED))) {
                total += m.amount();
            }
        }
        return total;
    }

    public static float damageBonus(float mastery) {
        return sum(mastery, Kind.DAMAGE);
    }

    public static float cooldownReduction(float mastery) {
        return sum(mastery, Kind.COOLDOWN);
    }

    public static float chargeSpeedBonus(float mastery) {
        return sum(mastery, Kind.CHARGE_SPEED);
    }

    public static boolean awakened(float mastery) {
        return mastery >= 0.9999f;
    }
}
