package com.fantasyweapons.ability;

import com.fantasyweapons.ability.AbilityDefinition.StatContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Formatting helpers for ability stat lines and upgrade descriptions shown in the progression menu. They read the
 * same params gameplay reads, so the menu can never disagree with what the ability does.
 */
public final class AbilityText {
    private AbilityText() {
    }

    public static Function<StatContext, String> damage() {
        return c -> String.format("%,.0f", c.fullChargeDamage());
    }

    public static Function<StatContext, String> damageFraction(double fraction) {
        return c -> String.format("%,.0f", c.fullChargeDamage() * fraction);
    }

    public static Function<StatContext, String> cooldown() {
        return c -> String.format("%.1fs", c.ability().cooldownTicks(c.abilityLevel()) / 20.0);
    }

    public static Function<StatContext, String> charge() {
        return c -> c.ability().chargeTicks() <= 0 ? "Instant" : String.format("%.1fs", c.ability().chargeTicks() / 20.0);
    }

    /** {@code base + perLevel*(lvl-1)} with a unit suffix. */
    public static Function<StatContext, String> scaled(String base, String perLevel, String unit, int decimals) {
        return c -> format(c.param(base) + c.param(perLevel) * Math.max(0, c.abilityLevel() - 1), decimals) + unit;
    }

    public static Function<StatContext, String> param(String name, String unit, int decimals) {
        return c -> format(c.param(name), decimals) + unit;
    }

    public static Function<StatContext, String> percent(String base, String perLevel) {
        return c -> Math.round((c.param(base) + (perLevel == null ? 0 : c.param(perLevel) * Math.max(0, c.abilityLevel() - 1))) * 100) + "%";
    }

    private static String format(double v, int decimals) {
        return decimals <= 0 ? String.valueOf(Math.round(v)) : String.format("%." + decimals + "f", v);
    }

    /** Builder for upgrade-line lambdas: standard "+X% DAMAGE" etc. plus milestone lines. */
    public static final class Upgrades {
        private final List<Function<Integer, String>> lines = new ArrayList<>();

        public static Upgrades create() {
            return new Upgrades();
        }

        public Upgrades damagePerLevel(double fraction) {
            lines.add(l -> "+" + Math.round(fraction * 100) + "% DAMAGE");
            return this;
        }

        public Upgrades cooldownPerLevel(double fraction) {
            lines.add(l -> "-" + Math.round(fraction * 100) + "% COOLDOWN");
            return this;
        }

        public Upgrades perLevel(String label, double amount, String unit) {
            lines.add(l -> "+" + (amount == Math.floor(amount) ? String.valueOf((long) amount) : String.valueOf(amount)) + unit + " " + label);
            return this;
        }

        /** A special line that only appears when reaching a specific ability level. */
        public Upgrades at(int level, String text) {
            lines.add(l -> l == level ? text : null);
            return this;
        }

        public Function<Integer, List<String>> build() {
            return lvl -> {
                List<String> out = new ArrayList<>();
                for (Function<Integer, String> f : lines) {
                    String s = f.apply(lvl);
                    if (s != null) out.add(s);
                }
                return out;
            };
        }
    }
}
