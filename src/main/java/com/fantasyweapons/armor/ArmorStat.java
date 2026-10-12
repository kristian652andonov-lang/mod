package com.fantasyweapons.armor;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.Locale;

/**
 * The legendary armor's own stats, each a player attribute (0 without armor) that armor pieces add to and that combat
 * and the abilities read (see {@code ArmorStats}). Percent stats are fractions: 0.15 = +15%.
 */
public enum ArmorStat {
    VOID_ABILITY_DAMAGE("Void Ability Damage", Format.PERCENT),
    SOLAR_ABILITY_DAMAGE("Solar Ability Damage", Format.PERCENT),
    CELESTIAL_ABILITY_DAMAGE("Light & Dark Ability Damage", Format.PERCENT),
    COSMIC_ABILITY_DAMAGE("Meteor Ability Damage", Format.PERCENT),
    FIRE_ABILITY_DAMAGE("Fire Ability Damage", Format.PERCENT),
    VOID_DAMAGE("Void Damage", Format.PERCENT),
    LIGHTNING_DAMAGE("Lightning Damage", Format.PERCENT),
    ENERGY_DAMAGE("Energy Damage", Format.PERCENT),
    COOLDOWN_REDUCTION("Ability Cooldown Reduction", Format.PERCENT),
    TELEPORT_DISTANCE("Teleport Distance", Format.PERCENT),
    CHARGE_SPEED("Charge Speed", Format.PERCENT),
    FREEZE_DURATION("Freeze Duration", Format.PERCENT),
    FROST_RESISTANCE("Frost Resistance", Format.PERCENT),
    ICE_ABILITY_RANGE("Ice Ability Range", Format.PERCENT),
    ICE_SPEED("Movement Speed on Ice and Snow", Format.PERCENT),
    CRIT_DAMAGE("Critical Damage", Format.PERCENT),
    LIFE_STEAL("Life Steal", Format.PERCENT),
    HEAVY_ATTACK_DAMAGE("Heavy Attack Damage", Format.PERCENT),
    CHAIN_JUMPS("Chain Lightning Jump", Format.COUNT),
    SUMMON_DAMAGE("Summon Damage", Format.PERCENT),
    SOUL_DRAIN("Soul Drain", Format.PERCENT),
    SUMMON_DURATION("Summon Duration", Format.PERCENT),
    UNDEAD_RESISTANCE("Undead Resistance", Format.PERCENT),
    SOUL_HARVEST_RANGE("Soul Harvest Range", Format.PERCENT),
    LIFE_DRAIN("Life Drain", Format.PERCENT),
    ABILITY_DURATION("Ability Duration", Format.PERCENT),
    POISON_DURATION("Poison Duration", Format.PERCENT),
    REGENERATION("Regeneration", Format.REGEN),
    VINE_RANGE("Vine Ability Range", Format.PERCENT),
    MODE_SHIELD("Mode-Switch Shield", Format.SHIELD),
    MODE_SWITCH_SPEED("Faster Mode Switching", Format.PERCENT),
    DARK_SPEED("Movement Speed in Dark Mode", Format.PERCENT),
    GRAVITY_RESISTANCE("Gravity Resistance", Format.PERCENT),
    ABILITY_RANGE("Ability Range", Format.PERCENT),
    FALL_IMMUNITY("Fall Damage Immunity", Format.FLAG),
    PROJECTILE_SPEED("Projectile Speed", Format.PERCENT),
    DASH_DISTANCE("Dash Distance", Format.PERCENT),
    SLAM_DAMAGE("Ground-Slam Damage", Format.PERCENT),
    FIRE_RESISTANCE("Fire Resistance", Format.PERCENT),
    CHAIN_REACH("Chain Reach", Format.PERCENT),
    BURN_ON_DASH("Burn on Dash", Format.FLAG);

    public enum Format {
        /** "+15% Void Ability Damage" */
        PERCENT,
        /** "+1 Chain Lightning Jump" */
        COUNT,
        /** "Fall Damage Immunity" */
        FLAG,
        /** "Regeneration: 1 health every 4 seconds" */
        REGEN,
        /** "Mode-Switch Shield: 4 absorption" */
        SHIELD
    }

    private final String displayName;
    private final Format format;
    Holder<Attribute> holder;

    ArmorStat(String displayName, Format format) {
        this.displayName = displayName;
        this.format = format;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    public Format format() {
        return format;
    }

    public Holder<Attribute> holder() {
        return holder;
    }

    /** The wearer's total of this stat (0 for anything that has no such attribute). */
    public double get(LivingEntity entity) {
        if (holder == null) return 0;
        AttributeInstance a = entity.getAttribute(holder);
        return a == null ? 0 : a.getValue();
    }

    /** One tooltip line for a bonus of {@code amount} of this stat. */
    public String describe(double amount) {
        return switch (format) {
            case PERCENT -> String.format(Locale.ROOT, "+%d%% %s", Math.round(amount * 100), displayName);
            case COUNT -> String.format(Locale.ROOT, "+%d %s%s", Math.round(amount), displayName, Math.round(amount) == 1 ? "" : "s");
            case FLAG -> displayName;
            case REGEN -> String.format(Locale.ROOT, "Regeneration: %s health every 4 seconds", trim(amount));
            case SHIELD -> String.format(Locale.ROOT, "Mode-Switch Shield: %s absorption", trim(amount));
        };
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
