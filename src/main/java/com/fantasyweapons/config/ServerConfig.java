package com.fantasyweapons.config;

import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.ability.AbilityParam;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.fantasyweapons.weapon.Weapons;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gameplay configuration ({@code serverconfig/fantasyweapons-server.toml}). SERVER configs are synced to clients on
 * join, so HUD/menu numbers always match what the server computes.
 * <p>
 * Every ability param declared in code automatically gets an entry under {@code [abilities.<weapon>.<ability>]}.
 */
public final class ServerConfig {
    /** Read-safe wrapper: returns the default until the config file is loaded (e.g. on the title screen). */
    public static final class Int {
        private final ModConfigSpec.IntValue value;
        private final int def;

        Int(ModConfigSpec.IntValue value, int def) {
            this.value = value;
            this.def = def;
        }

        public int getOrDefault() {
            return SPEC.isLoaded() ? value.get() : def;
        }
    }

    public static final class Dbl {
        private final ModConfigSpec.DoubleValue value;
        private final double def;

        Dbl(ModConfigSpec.DoubleValue value, double def) {
            this.value = value;
            this.def = def;
        }

        public double getOrDefault() {
            return SPEC.isLoaded() ? value.get() : def;
        }
    }

    public static final class Bool {
        private final ModConfigSpec.BooleanValue value;
        private final boolean def;

        Bool(ModConfigSpec.BooleanValue value, boolean def) {
            this.value = value;
            this.def = def;
        }

        public boolean getOrDefault() {
            return SPEC.isLoaded() ? value.get() : def;
        }
    }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    /**
     * The version of the ability settings this mod ships. A config file written by an older version has its ability
     * settings reset to the new defaults once when it loads (a balance overhaul would otherwise never reach it).
     */
    public static final int CURRENT_VERSION = 3;
    public static final Int CONFIG_VERSION;

    // ---- progression ----
    public static final Int MAX_LEVEL;
    public static final Dbl EXP_BASE;
    public static final Dbl EXP_LINEAR;
    public static final Dbl EXP_QUADRATIC;
    public static final Dbl EXP_CAP_PER_LEVEL;
    public static final Dbl EXP_MULTIPLIER;
    public static final Int MASTERY_POINTS_PER_LEVEL;
    public static final Int MASTERY_MILESTONE_INTERVAL;
    public static final Int MASTERY_MILESTONE_BONUS;
    public static final Int MASTERY_POINTS_PER_BOSS;

    // ---- exp sources ----
    public static final Int EXP_WEAK;
    public static final Int EXP_NORMAL;
    public static final Int EXP_STRONG;
    public static final Int EXP_ELITE;
    public static final Int EXP_BOSS;
    public static final Dbl STRONG_HEALTH;
    public static final Dbl ELITE_HEALTH;
    public static final Dbl BOSS_HEALTH;
    public static final Dbl SPAWNER_EXP_MULTIPLIER;
    public static final Bool PLAYER_KILLS_GIVE_EXP;
    public static final Int MAX_EXP_PER_KILL;
    public static final Int KILL_CREDIT_WINDOW_TICKS;

    // ---- damage ----
    public static final Dbl GLOBAL_DAMAGE_MULTIPLIER;
    public static final Dbl DAMAGE_GROWTH;
    public static final Dbl HEAVY_ATTACK_MIN_STRENGTH;
    public static final Bool ABILITIES_HURT_PLAYERS;
    public static final Bool ABILITIES_HURT_TAMED;
    public static final Bool CUSTOM_DEATH_EFFECT;
    private static final Map<String, Dbl> BASE_DAMAGE = new HashMap<>();

    // ---- abilities ----
    public static final Int MAX_HOLD_AT_FULL_CHARGE_TICKS;
    public static final Int FIZZLE_COOLDOWN_TICKS;

    // ---- world destruction (the ultimate weapons) ----
    public static final Bool DESTRUCTION;
    public static final Bool DESTRUCTION_LOADS_CHUNKS;
    public static final Int DESTRUCTION_MS_PER_TICK;
    public static final Bool DESTRUCTION_INSTANT;
    public static final Int DESTRUCTION_THREADS;
    public static final Dbl DESTRUCTION_RADIUS_SCALE;
    private static final Map<AbilityDefinition, Map<String, Dbl>> ABILITY_PARAMS = new IdentityHashMap<>();

    public static final ModConfigSpec SPEC;

    static {
        B.comment("Weapon level & EXP progression").push("progression");
        MAX_LEVEL = i("max_level", 100, 1, 1000, "Maximum weapon level");
        EXP_BASE = d("exp_base", 100, 1, 1e9, "EXP from level 1 to 2. Formula: base + linear*(L-1) + quadratic*(L-1)^2");
        EXP_LINEAR = d("exp_linear", 37.5, 0, 1e9, "Linear EXP growth per level (defaults give 100, 150, 225, 325, 450, ...)");
        EXP_QUADRATIC = d("exp_quadratic", 12.5, 0, 1e9, "Quadratic EXP growth per level");
        EXP_CAP_PER_LEVEL = d("exp_cap_per_level", 20000, 1, 1e12, "Upper bound for the EXP required by a single level");
        EXP_MULTIPLIER = d("exp_multiplier", 1.0, 0, 1000, "Global multiplier on all weapon EXP gained");
        MASTERY_POINTS_PER_LEVEL = i("mastery_points_per_level", 1, 0, 100, "Mastery points granted per weapon level gained");
        MASTERY_MILESTONE_INTERVAL = i("mastery_milestone_interval", 10, 0, 1000, "Every N levels grants bonus mastery points (0 = off)");
        MASTERY_MILESTONE_BONUS = i("mastery_milestone_bonus", 2, 0, 100, "Bonus mastery points at each milestone level");
        MASTERY_POINTS_PER_BOSS = i("mastery_points_per_boss_kill", 1, 0, 100, "Mastery points for killing a boss");
        B.pop();

        B.comment("Weapon EXP awarded for kills (decided on the server only)").push("exp_sources");
        EXP_WEAK = i("weak", 10, 0, 1_000_000, "Passive / weak mobs (below 10 max health)");
        EXP_NORMAL = i("normal", 25, 0, 1_000_000, "Ordinary hostile mobs");
        EXP_STRONG = i("strong", 50, 0, 1_000_000, "Mobs with max health >= strong_health");
        EXP_ELITE = i("elite", 100, 0, 1_000_000, "Mobs with max health >= elite_health or tagged #fantasyweapons:elite");
        EXP_BOSS = i("boss", 500, 0, 1_000_000, "Bosses (#c:bosses) or max health >= boss_health");
        STRONG_HEALTH = d("strong_health", 30, 1, 1e9, "Max health threshold for STRONG");
        ELITE_HEALTH = d("elite_health", 80, 1, 1e9, "Max health threshold for ELITE");
        BOSS_HEALTH = d("boss_health", 200, 1, 1e9, "Max health threshold for BOSS");
        SPAWNER_EXP_MULTIPLIER = d("spawner_multiplier", 0.25, 0, 1, "Multiplier for mobs that came from spawners (anti-farm)");
        PLAYER_KILLS_GIVE_EXP = bool("player_kills_give_exp", false, "Whether killing players awards weapon EXP");
        MAX_EXP_PER_KILL = i("max_exp_per_kill", 5000, 0, 100_000_000, "Hard cap on EXP from a single kill");
        KILL_CREDIT_WINDOW_TICKS = i("kill_credit_window_ticks", 200, 1, 72000,
                "A weapon gets kill credit if it hit the mob within this many ticks before death");
        B.pop();

        B.comment("Damage scaling, exponential in the weapon level: damage(L) = base_damage * rarity * global * growth_per_level^(L-1).",
                "Defaults: small at level 1 (close to a diamond sword), about x125 at level 100.").push("damage");
        GLOBAL_DAMAGE_MULTIPLIER = d("global_multiplier", 1.0, 0, 1e6, "Multiplies every weapon's damage");
        DAMAGE_GROWTH = d("growth_per_level", 1.05, 1.0, 2.0, "Damage multiplier gained per weapon level (1.05 = +5% per level, compounding)");
        HEAVY_ATTACK_MIN_STRENGTH = d("heavy_attack_min_strength", 0.95, 0, 1,
                "Attack-cooldown fraction required for a sneak-attack to count as a HEAVY attack");
        // renamed from abilities_hurt_players (default off) so that existing worlds pick up the new default
        ABILITIES_HURT_PLAYERS = bool("abilities_pvp", true,
                "Whether abilities (and their curses and burns) damage other players. Follows the server's own PvP rules too: PvP must be on (server.properties pvp=true), and teammates are spared when their team has friendly fire off");
        ABILITIES_HURT_TAMED = bool("abilities_hurt_tamed", false, "Whether area abilities damage tamed animals");
        CUSTOM_DEATH_EFFECT = bool("custom_death_effect", true, "Mobs killed by fantasy weapons dissolve in an elemental effect instead of the vanilla death puff");
        B.push("level1_damage");
        for (WeaponDefinition w : Weapons.all()) {
            BASE_DAMAGE.put(w.id(), d(w.id(), w.baseDamage(), 0, 1e9, w.displayName() + " level-1 melee damage"));
        }
        B.pop();
        B.pop();

        B.comment("World destruction by the two ultimate weapons (the Great Sword of Obliteration and the Spell Book of Absolute Domination)").push("destruction");
        DESTRUCTION = bool("enabled", true, "Whether their abilities really destroy the world: break, melt, freeze and rebuild blocks and whole chunks. When off, only the effects and the damage remain");
        DESTRUCTION_LOADS_CHUNKS = bool("load_chunks", true, "Load (and if needed generate) chunks out of view so the destruction is complete everywhere it reaches");
        DESTRUCTION_MS_PER_TICK = i("ms_per_tick", 18, 1, 200, "Milliseconds of each server tick spent putting rewritten chunks into the world (the rewriting itself runs on other threads). Lower keeps the server smoother, the destruction then lands more slowly");
        DESTRUCTION_INSTANT = bool("instant", true, "Each power's destruction lands all at once, the moment it strikes, instead of sweeping out over a few seconds");
        DESTRUCTION_THREADS = i("threads", 0, 0, 64, "Threads rewriting chunks for the destruction (0: every core but two)");
        DESTRUCTION_RADIUS_SCALE = d("radius_scale", 1.0, 0.05, 1.0, "Scales the destroyed area of every ability (1 = the full 64 to 256 blocks)");
        B.pop();

        CONFIG_VERSION = i("config_version", 0, 0, 1000,
                "Kept by the mod: when it is older than the mod's own, the ability settings below are reset to the mod's new defaults once");
        B.comment("Ability system").push("abilities");
        MAX_HOLD_AT_FULL_CHARGE_TICKS = i("max_hold_at_full_charge_ticks", 100, 0, 72000,
                "Abilities auto-release after being held at full charge this long (0 = never)");
        FIZZLE_COOLDOWN_TICKS = i("fizzle_cooldown_ticks", 10, 0, 1200, "Cooldown after releasing below the minimum charge");
        for (WeaponDefinition w : Weapons.all()) {
            B.push(w.id());
            for (AbilityDefinition a : w.abilities()) {
                B.comment(a.name() + " (" + a.kind().displayName() + ", unlocks at weapon level " + a.unlockLevel() + ")").push(a.id());
                Map<String, Dbl> values = new HashMap<>();
                for (AbilityParam p : a.params().values()) {
                    values.put(p.name(), d(p.name(), p.defaultValue(), p.min(), p.max(), p.comment()));
                }
                ABILITY_PARAMS.put(a, values);
                B.pop();
            }
            B.pop();
        }
        B.pop();

        SPEC = B.build();
    }

    private ServerConfig() {
    }

    private static Int i(String name, int def, int min, int max, String comment) {
        return new Int(B.comment(comment).defineInRange(name, def, min, max), def);
    }

    private static Dbl d(String name, double def, double min, double max, String comment) {
        return new Dbl(B.comment(comment).defineInRange(name, def, min, max), def);
    }

    private static Bool bool(String name, boolean def, String comment) {
        return new Bool(B.comment(comment).define(name, def), def);
    }

    public static double baseDamage(WeaponDefinition def) {
        Dbl v = BASE_DAMAGE.get(def.id());
        return v == null ? def.baseDamage() : v.getOrDefault();
    }

    public static double abilityParam(AbilityDefinition ability, AbilityParam param) {
        Map<String, Dbl> values = ABILITY_PARAMS.get(ability);
        if (values == null) return param.defaultValue();
        Dbl v = values.get(param.name());
        return v == null ? param.defaultValue() : v.getOrDefault();
    }

    /** Resets the ability settings of a config file written by an older version of the mod to its new defaults. */
    public static void migrate() {
        if (!SPEC.isLoaded() || CONFIG_VERSION.value.get() >= CURRENT_VERSION) return;
        for (var e : ABILITY_PARAMS.entrySet()) {
            for (AbilityParam p : e.getKey().params().values()) {
                Dbl v = e.getValue().get(p.name());
                if (v != null) v.value.set(p.defaultValue());
            }
        }
        CONFIG_VERSION.value.set(CURRENT_VERSION);
        SPEC.save();
        com.fantasyweapons.FantasyWeapons.LOGGER.info("Ability settings reset to the defaults of config version {}", CURRENT_VERSION);
    }

    /** Forces class init so the spec exists before registration. */
    public static List<String> touch() {
        return List.of(SPEC.toString());
    }
}
