package com.fantasyweapons.armor;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.status.StatusType;
import com.fantasyweapons.weapon.Element;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponDefinition;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * What the armor stats do. Every piece bonus is an attribute ({@link ArmorStat}); this is where combat, the abilities,
 * statuses and the player's tick read them.
 */
public final class ArmorEffects {
    private ArmorEffects() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // which abilities are what
    // ------------------------------------------------------------------------------------------------------------

    /** Abilities that move the caster in a rush (dash distance, burn on dash). */
    static final Set<String> DASHES = Set.of("aetherlance/piercing_charge", "doomcleaver/sanguine_leap", "monolith/tectonic_slam", "voidfang/void_blink");
    /** Abilities that teleport the caster (teleport distance). */
    static final Set<String> TELEPORTS = Set.of("voidfang/void_blink");
    /** Abilities that smash the ground (ground-slam damage). */
    static final Set<String> SLAMS = Set.of("monolith/earthshatter", "monolith/seismic_fissure", "monolith/tectonic_slam", "monolith/worldbreaker",
            "starforge/gravity_slam");
    /** Abilities that call up allies (summon damage and duration). */
    static final Set<String> SUMMONS = Set.of("gravebite/legion_of_the_damned");

    private static final Set<String> REACH = Set.of("radius", "range", "length");
    private static final Set<String> DURATIONS = Set.of("duration", "mark_time", "root", "bind");

    private static String key(WeaponDefinition weapon, AbilityDefinition ability) {
        return weapon.id() + "/" + ability.id();
    }

    public static boolean isDash(WeaponDefinition weapon, AbilityDefinition ability) {
        return DASHES.contains(key(weapon, ability));
    }

    // ------------------------------------------------------------------------------------------------------------
    // abilities
    // ------------------------------------------------------------------------------------------------------------

    /** The stat that adds to all of an element's ability damage. */
    @Nullable
    static ArmorStat abilityDamageStat(Element element) {
        return switch (element) {
            case VOID -> ArmorStat.VOID_ABILITY_DAMAGE;
            case SOLAR -> ArmorStat.SOLAR_ABILITY_DAMAGE;
            case CELESTIAL -> ArmorStat.CELESTIAL_ABILITY_DAMAGE;
            case COSMIC -> ArmorStat.COSMIC_ABILITY_DAMAGE;
            case FIRE -> ArmorStat.FIRE_ABILITY_DAMAGE;
            case ICE -> ArmorStat.ICE_ABILITY_DAMAGE;
            case WATER -> ArmorStat.WATER_ABILITY_DAMAGE;
            case NATURE -> ArmorStat.NATURE_ABILITY_DAMAGE;
            case LIGHT -> ArmorStat.LIGHT_ABILITY_DAMAGE;
            default -> null;
        };
    }

    /** The stat that extends one weapon's abilities' reach. */
    @Nullable
    static ArmorStat reachStat(Element element) {
        return switch (element) {
            case ICE -> ArmorStat.ICE_ABILITY_RANGE;
            case NATURE -> ArmorStat.VINE_RANGE;
            case FIRE -> ArmorStat.CHAIN_REACH;
            case SOUL -> ArmorStat.SOUL_HARVEST_RANGE;
            default -> null;
        };
    }

    /** Multiplier on an ability's damage from the caster's armor. */
    public static float abilityDamage(LivingEntity caster, WeaponDefinition weapon, AbilityDefinition ability) {
        double bonus = 0;
        ArmorStat element = abilityDamageStat(weapon.element());
        if (element != null) bonus += element.get(caster);
        String key = key(weapon, ability);
        if (SLAMS.contains(key)) bonus += ArmorStat.SLAM_DAMAGE.get(caster);
        if (SUMMONS.contains(key)) bonus += ArmorStat.SUMMON_DAMAGE.get(caster);
        return (float) (1 + bonus) * SetAbilities.abilityDamage(caster);
    }

    /** An ability parameter adjusted by the caster's armor (reach, durations, projectile speed, distances, chain jumps). */
    public static double param(LivingEntity caster, WeaponDefinition weapon, AbilityDefinition ability, String name, double value) {
        String key = key(weapon, ability);
        if (name.equals("jumps")) return value + Math.round(ArmorStat.CHAIN_JUMPS.get(caster));
        double bonus = 0;
        if (REACH.contains(name) || name.endsWith("_radius") || name.endsWith("_range")) {
            bonus += ArmorStat.ABILITY_RANGE.get(caster);
            ArmorStat reach = reachStat(weapon.element());
            if (reach != null) bonus += reach.get(caster);
            if (DASHES.contains(key) && name.equals("range")) bonus += ArmorStat.DASH_DISTANCE.get(caster);
        } else if (name.equals("distance")) {
            bonus += TELEPORTS.contains(key) ? ArmorStat.TELEPORT_DISTANCE.get(caster) : DASHES.contains(key) ? ArmorStat.DASH_DISTANCE.get(caster) : 0;
        } else if (DURATIONS.contains(name) || name.endsWith("_duration")) {
            bonus += ArmorStat.ABILITY_DURATION.get(caster);
            if (SUMMONS.contains(key)) bonus += ArmorStat.SUMMON_DURATION.get(caster);
            if (name.startsWith("freeze")) bonus += ArmorStat.FREEZE_DURATION.get(caster);
        } else if (name.equals("speed")) {
            bonus += ArmorStat.PROJECTILE_SPEED.get(caster);
        }
        return bonus == 0 ? value : value * (1 + bonus);
    }

    /** Ability cooldowns are shorter (at most halved). */
    public static int cooldown(LivingEntity caster, int ticks) {
        double cdr = Math.min(0.5, ArmorStat.COOLDOWN_REDUCTION.get(caster));
        return cdr <= 0 ? ticks : (int) Math.round(ticks * (1 - cdr));
    }

    /** Abilities charge faster. */
    public static int chargeTicks(LivingEntity caster, int ticks) {
        double speed = ArmorStat.CHARGE_SPEED.get(caster);
        return speed <= 0 || ticks <= 0 ? ticks : Math.max(1, (int) Math.round(ticks / (1 + speed)));
    }

    /** Switching a weapon's mode is faster. */
    public static int formCooldown(LivingEntity caster, int ticks) {
        double faster = Math.min(0.8, ArmorStat.MODE_SWITCH_SPEED.get(caster));
        return faster <= 0 ? ticks : Math.max(1, (int) Math.round(ticks * (1 - faster)));
    }

    /** After switching a weapon's mode: the mode-switch shield (absorption for 5 seconds). */
    public static void onFormSwitch(ServerPlayer player) {
        double shield = ArmorStat.MODE_SHIELD.get(player);
        if (shield > 0) {
            int amplifier = Math.max(0, (int) Math.round(shield / 4) - 1);
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, amplifier, false, false, true));
        }
        SetAbilities.onFormSwitch(player);
    }

    /** After an ability is cast: dashes leave a trail that burns enemies for a moment. */
    public static void onAbilityCast(ServerPlayer player, WeaponDefinition weapon, AbilityDefinition ability) {
        if (isDash(weapon, ability) && ArmorStat.BURN_ON_DASH.get(player) > 0) {
            player.getPersistentData().putLong(BURN_DASH_KEY, player.serverLevel().getGameTime() + 30);
        }
    }

    private static final String BURN_DASH_KEY = FantasyWeapons.MOD_ID + ":burn_dash_until";

    // ------------------------------------------------------------------------------------------------------------
    // damage dealt
    // ------------------------------------------------------------------------------------------------------------

    /** Multiplier on all of an attacker's damage of an element (void, lightning, energy damage). */
    public static float elementDamage(LivingEntity attacker, @Nullable Element element) {
        if (element == null) return 1f;
        ArmorStat stat = switch (element) {
            case VOID -> ArmorStat.VOID_DAMAGE;
            case LIGHTNING -> ArmorStat.LIGHTNING_DAMAGE;
            case ENERGY -> ArmorStat.ENERGY_DAMAGE;
            default -> null;
        };
        return stat == null ? 1f : (float) (1 + stat.get(attacker));
    }

    /** Multiplier on what a staff or spellbook deals (its bolts and its spells). */
    public static float spellDamage(LivingEntity attacker, net.minecraft.world.item.ItemStack weapon) {
        if (!(weapon.getItem() instanceof com.fantasyweapons.weapon.FantasyWeaponItem w) || !w.definition().weaponClass().magic()) return 1f;
        return (float) (1 + ArmorStat.SPELL_DAMAGE.get(attacker));
    }

    /** Multiplier on the heals and shields {@code caster} casts. */
    public static float healingPower(LivingEntity caster) {
        return (float) (1 + ArmorStat.HEALING_POWER.get(caster));
    }

    /** After damage lands: soul drain (necromancy) and life drain (soul) heal the attacker; a burning dash ignites. */
    public static void afterDamage(ServerPlayer attacker, LivingEntity target, float dealt, @Nullable Element element) {
        if (dealt > 0 && element == Element.NECROMANCY) heal(attacker, dealt * ArmorStat.SOUL_DRAIN.get(attacker));
        if (dealt > 0 && element == Element.SOUL) heal(attacker, dealt * ArmorStat.LIFE_DRAIN.get(attacker));
        if (burningDash(attacker)) target.igniteForSeconds(4);
    }

    /** The melee multiplier for critical and heavy hits. */
    public static float meleeMultiplier(LivingEntity attacker, boolean crit, boolean heavy) {
        double m = 1;
        if (crit) m *= 1 + ArmorStat.CRIT_DAMAGE.get(attacker);
        if (heavy) m *= 1 + ArmorStat.HEAVY_ATTACK_DAMAGE.get(attacker);
        return (float) m;
    }

    /** Melee life steal from armor (and the Doomcleaver set's blood frenzy). */
    public static float lifeSteal(LivingEntity attacker) {
        return (float) ArmorStat.LIFE_STEAL.get(attacker) + SetAbilities.lifeSteal(attacker);
    }

    private static void heal(LivingEntity e, double amount) {
        if (amount > 0 && e.isAlive()) e.heal((float) amount);
    }

    // ------------------------------------------------------------------------------------------------------------
    // statuses
    // ------------------------------------------------------------------------------------------------------------

    /** A status's duration adjusted by the armor of whoever applied it and of whoever receives it. */
    public static int statusDuration(LivingEntity target, StatusType type, int duration, @Nullable UUID source) {
        double mult = 1;
        Player applier = source == null ? null : target.level().getPlayerByUUID(source);
        boolean frost = type == StatusType.FROSTBITE || type == StatusType.FROZEN;
        if (applier != null && applier != target) {
            if (frost) mult += ArmorStat.FREEZE_DURATION.get(applier);
            if (type == StatusType.NATURE_POISON) mult += ArmorStat.POISON_DURATION.get(applier);
        }
        if (frost) mult *= 1 - Math.min(0.9, ArmorStat.FROST_RESISTANCE.get(target));
        return mult == 1 ? duration : Math.max(1, (int) Math.round(duration * mult));
    }

    // ------------------------------------------------------------------------------------------------------------
    // damage taken
    // ------------------------------------------------------------------------------------------------------------

    /** Multiplier on damage {@code victim} takes from {@code source} (frost, fire and undead resistance). */
    public static float incoming(LivingEntity victim, DamageSource source) {
        double mult = 1;
        if (source.is(DamageTypeTags.IS_FREEZING)) mult *= 1 - Math.min(0.9, ArmorStat.FROST_RESISTANCE.get(victim));
        if (source.is(DamageTypeTags.IS_FIRE)) mult *= 1 - Math.min(0.9, ArmorStat.FIRE_RESISTANCE.get(victim));
        if (source.getEntity() instanceof LivingEntity attacker && attacker.getType().is(EntityTypeTags.UNDEAD)) {
            mult *= 1 - Math.min(0.9, ArmorStat.UNDEAD_RESISTANCE.get(victim));
        }
        return (float) mult;
    }

    /** Multiplier on a weapon's element damage {@code victim} takes (frost and fire resistance against ice and fire weapons). */
    public static float incomingElement(LivingEntity victim, @Nullable Element element) {
        if (element == Element.ICE) return (float) (1 - Math.min(0.9, ArmorStat.FROST_RESISTANCE.get(victim)));
        if (element == Element.FIRE || element == Element.SOLAR) return (float) (1 - Math.min(0.9, ArmorStat.FIRE_RESISTANCE.get(victim)));
        return 1f;
    }

    /** Multiplier on fall damage (0 with fall-damage immunity). */
    public static float fallDamage(LivingEntity victim) {
        if (ArmorStat.FALL_IMMUNITY.get(victim) > 0) return 0f;
        return (float) (1 - Math.min(0.9, ArmorStat.GRAVITY_RESISTANCE.get(victim)));
    }

    // ------------------------------------------------------------------------------------------------------------
    // every tick
    // ------------------------------------------------------------------------------------------------------------

    private static final ResourceLocation CONDITION_SPEED = FantasyWeapons.id("armor_condition_speed");

    public static void tick(ServerPlayer player) {
        // regeneration: so much health every 4 seconds
        double regen = ArmorStat.REGENERATION.get(player);
        if (regen > 0 && player.tickCount % 80 == 0 && player.getHealth() < player.getMaxHealth()) player.heal((float) regen);

        // speed on ice and snow, and in the Eclipse Reaper's dark mode
        double speed = 0;
        if (onIceOrSnow(player)) speed += ArmorStat.ICE_SPEED.get(player);
        // in the Eclipse set's totality both modes' bonuses apply
        if (darkMode(player) || SetAbilities.totality(player)) speed += ArmorStat.DARK_SPEED.get(player);
        AttributeInstance move = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (move != null) {
            AttributeModifier current = move.getModifier(CONDITION_SPEED);
            double have = current == null ? 0 : current.amount();
            if (Math.abs(have - speed) > 1e-6) {
                move.removeModifier(CONDITION_SPEED);
                if (speed > 0) move.addTransientModifier(new AttributeModifier(CONDITION_SPEED, speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }

        // a burning dash leaves fire in its wake and sets alight whatever it brushes past
        if (burningDash(player)) {
            ServerLevel level = player.serverLevel();
            level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.3, player.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
            level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.2, player.getZ(), 1, 0.2, 0.1, 0.2, 0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(1.2),
                    e -> e != player && e.isAlive() && !(e instanceof Player p && !player.canHarmPlayer(p)))) {
                e.igniteForSeconds(4);
            }
        }

        SetAbilities.tick(player);
    }

    static boolean burningDash(ServerPlayer player) {
        return player.getPersistentData().getLong(BURN_DASH_KEY) > player.serverLevel().getGameTime();
    }

    static boolean onIceOrSnow(LivingEntity e) {
        BlockState below = e.level().getBlockState(e.getOnPos());
        BlockState at = e.level().getBlockState(e.blockPosition());
        return below.is(BlockTags.ICE) || below.is(Blocks.SNOW_BLOCK) || below.is(Blocks.POWDER_SNOW) || at.is(Blocks.SNOW) || at.is(Blocks.POWDER_SNOW);
    }

    /** Whether {@code e} holds the Eclipse Reaper in its dark form. */
    public static boolean darkMode(LivingEntity e) {
        var held = e.getMainHandItem();
        if (!(held.getItem() instanceof FantasyWeaponItem weapon) || !weapon.definition().id().equals("eclipse_reaper")) return false;
        var form = weapon.definition().form(FantasyWeaponItem.data(held));
        return form != null && form.id().contains("dark");
    }
}
