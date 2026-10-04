package com.fantasyweapons.ability;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.config.ServerConfig;
import com.fantasyweapons.progression.ProgressionMath;
import com.fantasyweapons.weapon.WeaponDefinition;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Immutable description of one ability in a weapon's tree. The same definition serves every ability level:
 * level-dependent numbers come from params ({@code damage}, {@code damage_per_level}, ...) so upgrades never need a
 * separate implementation.
 */
public final class AbilityDefinition {
    /** Standard params shared by all castable abilities. */
    public static final String DAMAGE = "damage";
    public static final String DAMAGE_PER_LEVEL = "damage_per_level";
    public static final String COOLDOWN = "cooldown";
    public static final String COOLDOWN_PER_LEVEL = "cooldown_reduction_per_level";
    public static final String CHARGE = "charge_time";
    public static final String MIN_CHARGE = "min_charge";
    public static final String FULL_CHARGE_BONUS = "full_charge_bonus";

    /** A line of numbers shown in the progression menu for the ability at a given level. */
    public record StatLine(String label, Function<StatContext, String> value) {
    }

    /** Values available when computing display numbers. */
    public record StatContext(AbilityDefinition ability, int abilityLevel, int weaponLevel, float weaponDamage) {
        public double param(String name) {
            return ability.param(name);
        }

        /** Ability damage at this level and a full charge (what the menu advertises). */
        public float fullChargeDamage() {
            return ability.damageAt(weaponDamage, abilityLevel, 1.0f);
        }
    }

    private final String id;
    private final String name;
    private final String description;
    private final AbilityKind kind;
    private final int unlockLevel;
    private final int maxLevel;
    private final int levelStep;
    private final int baseCost;
    private final int costStep;
    private final int nodeX;
    private final int nodeY;
    private final List<String> parents;
    private final Map<String, AbilityParam> params;
    private final List<StatLine> stats;
    private final Function<Integer, List<String>> upgradeLines;
    @Nullable
    private final AbilityExecutor executor;
    private final List<String> castAnimations;
    @Nullable
    private final String chargeAnimation;
    @Nullable
    private final String requiredForm;
    private WeaponDefinition weapon;

    private AbilityDefinition(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.description = b.description;
        this.kind = b.kind;
        this.unlockLevel = b.unlockLevel;
        this.maxLevel = b.maxLevel;
        this.levelStep = b.levelStep;
        this.baseCost = b.baseCost;
        this.costStep = b.costStep;
        this.nodeX = b.nodeX;
        this.nodeY = b.nodeY;
        this.parents = List.copyOf(b.parents);
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(b.params));
        this.stats = List.copyOf(b.stats);
        this.upgradeLines = b.upgradeLines;
        this.executor = b.executor;
        this.castAnimations = List.copyOf(b.castAnimations);
        this.chargeAnimation = b.chargeAnimation;
        this.requiredForm = b.requiredForm;
    }

    public static Builder builder(String id, AbilityKind kind) {
        return new Builder(id, kind);
    }

    /** Called once by {@link WeaponDefinition} when the weapon is built. */
    public void bind(WeaponDefinition weapon) {
        if (this.weapon != null) throw new IllegalStateException("Ability " + id + " already bound");
        this.weapon = weapon;
    }

    public String id() {
        return id;
    }

    /** Globally unique key, e.g. {@code fantasyweapons:voidfang/void_blink}. */
    public ResourceLocation key() {
        return ResourceLocation.fromNamespaceAndPath(FantasyWeapons.MOD_ID, weapon.id() + "/" + id);
    }

    public ResourceLocation icon() {
        return ResourceLocation.fromNamespaceAndPath(FantasyWeapons.MOD_ID, "textures/gui/ability/" + weapon.id() + "/" + id + ".png");
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public AbilityKind kind() {
        return kind;
    }

    public int unlockLevel() {
        return unlockLevel;
    }

    public int maxLevel() {
        return maxLevel;
    }

    /** Weapon level needed to reach the given ability level (level 1 = unlock level). */
    public int weaponLevelFor(int abilityLevel) {
        return unlockLevel + Math.max(0, abilityLevel - 1) * levelStep;
    }

    /** Mastery point cost to go from {@code abilityLevel - 1} to {@code abilityLevel}. */
    public int costFor(int abilityLevel) {
        return baseCost + Math.max(0, abilityLevel - 2) * costStep;
    }

    public int nodeX() {
        return nodeX;
    }

    public int nodeY() {
        return nodeY;
    }

    public List<String> parents() {
        return parents;
    }

    public Map<String, AbilityParam> params() {
        return params;
    }

    public List<StatLine> stats() {
        return stats;
    }

    public List<String> upgradeLines(int toLevel) {
        return upgradeLines.apply(toLevel);
    }

    @Nullable
    public AbilityExecutor executor() {
        return executor;
    }

    public List<String> castAnimations() {
        return castAnimations;
    }

    @Nullable
    public String chargeAnimation() {
        return chargeAnimation;
    }

    /** Form id this ability requires, or null if usable in any form. */
    @Nullable
    public String requiredForm() {
        return requiredForm;
    }

    public WeaponDefinition weapon() {
        return weapon;
    }

    /** Reads a param from the (synced) server config, falling back to its default before configs load. */
    public double param(String paramName) {
        AbilityParam p = params.get(paramName);
        if (p == null) throw new IllegalArgumentException("Unknown param '" + paramName + "' on " + key());
        return ServerConfig.abilityParam(this, p);
    }

    public boolean hasParam(String paramName) {
        return params.containsKey(paramName);
    }

    public int chargeTicks() {
        return hasParam(CHARGE) ? (int) Math.round(param(CHARGE) * 20.0) : 0;
    }

    public float minCharge() {
        return hasParam(MIN_CHARGE) ? (float) param(MIN_CHARGE) : 0.25f;
    }

    /** Cooldown in ticks at the given ability level (before mastery modifiers). */
    public int cooldownTicks(int abilityLevel) {
        if (!hasParam(COOLDOWN)) return 0;
        double reduction = hasParam(COOLDOWN_PER_LEVEL) ? param(COOLDOWN_PER_LEVEL) * Math.max(0, abilityLevel - 1) : 0;
        return (int) Math.round(param(COOLDOWN) * Math.max(0.25, 1.0 - reduction) * 20.0);
    }

    /**
     * Multiplier applied for how long the ability was charged. Mirrors the design brief:
     * 25% → 50%, 50% → 75%, 75% → 100%, full charge → {@code full_charge_bonus} (150% by default).
     */
    public float chargeMultiplier(float chargeFraction) {
        if (chargeTicks() <= 0) return 1.0f;
        if (chargeFraction >= 0.999f) {
            return hasParam(FULL_CHARGE_BONUS) ? (float) param(FULL_CHARGE_BONUS) : 1.5f;
        }
        return Math.max(0.25f, chargeFraction + 0.25f);
    }

    /** Ability damage for a weapon damage value, ability level and charge. */
    public float damageAt(float weaponDamage, int abilityLevel, float chargeFraction) {
        if (!hasParam(DAMAGE)) return 0;
        double perLevel = hasParam(DAMAGE_PER_LEVEL) ? param(DAMAGE_PER_LEVEL) : 0.15;
        return ProgressionMath.abilityDamage(weaponDamage, param(DAMAGE), perLevel, abilityLevel, chargeMultiplier(chargeFraction));
    }

    public static final class Builder {
        private final String id;
        private final AbilityKind kind;
        private String name;
        private String description = "";
        private int unlockLevel = 1;
        private int maxLevel = 5;
        private int levelStep = 8;
        private int baseCost = 1;
        private int costStep = 1;
        private int nodeX;
        private int nodeY;
        private final List<String> parents = new ArrayList<>();
        private final Map<String, AbilityParam> params = new LinkedHashMap<>();
        private final List<StatLine> stats = new ArrayList<>();
        private Function<Integer, List<String>> upgradeLines = lvl -> List.of();
        private AbilityExecutor executor;
        private final List<String> castAnimations = new ArrayList<>();
        private String chargeAnimation;
        private String requiredForm;

        private Builder(String id, AbilityKind kind) {
            this.id = id;
            this.kind = kind;
            this.name = id;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder unlock(int weaponLevel) {
            this.unlockLevel = weaponLevel;
            return this;
        }

        /** @param maxLevel ability max level; @param levelStep weapon levels between ability levels */
        public Builder levels(int maxLevel, int levelStep) {
            this.maxLevel = maxLevel;
            this.levelStep = levelStep;
            return this;
        }

        public Builder cost(int baseCost, int costStep) {
            this.baseCost = baseCost;
            this.costStep = costStep;
            return this;
        }

        public Builder node(int x, int y, String... parents) {
            this.nodeX = x;
            this.nodeY = y;
            this.parents.addAll(List.of(parents));
            return this;
        }

        public Builder param(String name, double def, String comment) {
            params.put(name, AbilityParam.of(name, def, comment));
            return this;
        }

        public Builder fraction(String name, double def, String comment) {
            params.put(name, AbilityParam.fraction(name, def, comment));
            return this;
        }

        /** Convenience for the standard damage / charge / cooldown params. */
        public Builder standard(double damageMultiplier, double chargeSeconds, double cooldownSeconds) {
            param(DAMAGE, damageMultiplier, "Damage as a multiple of the weapon's current damage");
            fraction(DAMAGE_PER_LEVEL, 0.18, "Extra damage per ability level (0.18 = +18% per level)");
            param(CHARGE, chargeSeconds, "Seconds to reach full charge (0 = instant)");
            fraction(MIN_CHARGE, chargeSeconds > 0 ? 0.25 : 0.0, "Minimum charge fraction needed to release");
            param(FULL_CHARGE_BONUS, 1.5, "Damage multiplier when released at full charge");
            param(COOLDOWN, cooldownSeconds, "Cooldown in seconds");
            fraction(COOLDOWN_PER_LEVEL, 0.05, "Cooldown reduction per ability level");
            return this;
        }

        public Builder stat(String label, Function<StatContext, String> value) {
            stats.add(new StatLine(label, value));
            return this;
        }

        public Builder upgrades(Function<Integer, List<String>> lines) {
            this.upgradeLines = lines;
            return this;
        }

        public Builder executor(AbilityExecutor executor) {
            this.executor = executor;
            return this;
        }

        public Builder animations(String chargeAnimation, String... castAnimations) {
            this.chargeAnimation = chargeAnimation;
            this.castAnimations.clear();
            this.castAnimations.addAll(List.of(castAnimations));
            return this;
        }

        public Builder requiresForm(String formId) {
            this.requiredForm = formId;
            return this;
        }

        public AbilityDefinition build() {
            return new AbilityDefinition(this);
        }
    }
}
