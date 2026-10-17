package com.fantasyweapons.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** A legendary weapon reached a level, a number of kills or of boss kills. */
public class WeaponMilestoneTrigger extends SimpleCriterionTrigger<WeaponMilestoneTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, String weapon, int level, int kills, int bossKills) {
        trigger(player, i -> i.weapon().map(weapon::equals).orElse(true) && level >= i.level() && kills >= i.kills() && bossKills >= i.bossKills());
    }

    public Criterion<Instance> level(int level) {
        return createCriterion(new Instance(Optional.empty(), Optional.empty(), level, 0, 0));
    }

    public Criterion<Instance> kills(int kills) {
        return createCriterion(new Instance(Optional.empty(), Optional.empty(), 0, kills, 0));
    }

    public Criterion<Instance> bossKills(int bossKills) {
        return createCriterion(new Instance(Optional.empty(), Optional.empty(), 0, 0, bossKills));
    }

    public record Instance(Optional<ContextAwarePredicate> player, Optional<String> weapon, int level, int kills, int bossKills)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Codec.STRING.optionalFieldOf("weapon").forGetter(Instance::weapon),
                Codec.INT.optionalFieldOf("level", 0).forGetter(Instance::level),
                Codec.INT.optionalFieldOf("kills", 0).forGetter(Instance::kills),
                Codec.INT.optionalFieldOf("boss_kills", 0).forGetter(Instance::bossKills)
        ).apply(i, Instance::new));
    }
}
