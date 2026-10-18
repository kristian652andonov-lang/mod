package com.terrahaul.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Data pack condition {@code terrahaul:config}: true when the named boolean option of the common
 * configuration is enabled. Lets users switch Terrahaul data (for example the replacement of vanilla
 * trees) off without editing data packs.
 */
public record ConfigCondition(String key, boolean expected) implements ICondition {
    public static final MapCodec<ConfigCondition> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.STRING.fieldOf("key").forGetter(ConfigCondition::key),
        Codec.BOOL.optionalFieldOf("value", true).forGetter(ConfigCondition::expected)
    ).apply(i, ConfigCondition::new));

    @Override
    public boolean test(IContext context) {
        return TerrahaulConfig.flag(key) == expected;
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
