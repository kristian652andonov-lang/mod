package com.fantasyweapons.progression;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.config.ServerConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.common.Tags;

/**
 * Kill tiers for weapon EXP. Tags let modpacks reclassify any mob without touching code:
 * {@code #fantasyweapons:weak}, {@code #fantasyweapons:strong}, {@code #fantasyweapons:elite}, {@code #fantasyweapons:boss}.
 */
public enum ExpTier {
    WEAK, NORMAL, STRONG, ELITE, BOSS;

    public static final TagKey<EntityType<?>> TAG_WEAK = tag("weak");
    public static final TagKey<EntityType<?>> TAG_STRONG = tag("strong");
    public static final TagKey<EntityType<?>> TAG_ELITE = tag("elite");
    public static final TagKey<EntityType<?>> TAG_BOSS = tag("boss");

    private static TagKey<EntityType<?>> tag(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, FantasyWeapons.id(name));
    }

    public int exp() {
        return switch (this) {
            case WEAK -> ServerConfig.EXP_WEAK.getOrDefault();
            case NORMAL -> ServerConfig.EXP_NORMAL.getOrDefault();
            case STRONG -> ServerConfig.EXP_STRONG.getOrDefault();
            case ELITE -> ServerConfig.EXP_ELITE.getOrDefault();
            case BOSS -> ServerConfig.EXP_BOSS.getOrDefault();
        };
    }

    public static ExpTier classify(LivingEntity e) {
        EntityType<?> type = e.getType();
        float hp = e.getMaxHealth();
        if (type.is(TAG_BOSS) || type.is(Tags.EntityTypes.BOSSES) || hp >= ServerConfig.BOSS_HEALTH.getOrDefault()) return BOSS;
        if (type.is(TAG_ELITE) || hp >= ServerConfig.ELITE_HEALTH.getOrDefault()) return ELITE;
        if (type.is(TAG_WEAK)) return WEAK;
        if (type.is(TAG_STRONG) || hp >= ServerConfig.STRONG_HEALTH.getOrDefault()) return STRONG;
        if (e instanceof Enemy) return NORMAL;
        return WEAK;
    }
}
