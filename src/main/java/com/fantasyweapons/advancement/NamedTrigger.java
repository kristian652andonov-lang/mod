package com.fantasyweapons.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** Something happened to a player, with a name (a weapon, a set, a kind of ability) a criterion may ask for. */
public class NamedTrigger extends SimpleCriterionTrigger<NamedTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, String name) {
        trigger(player, instance -> instance.name().map(name::equals).orElse(true));
    }

    /** A criterion met by any such event. */
    public Criterion<Instance> any() {
        return createCriterion(new Instance(Optional.empty(), Optional.empty()));
    }

    /** A criterion met by the event named {@code name}. */
    public Criterion<Instance> named(String name) {
        return createCriterion(new Instance(Optional.empty(), Optional.of(name)));
    }

    public record Instance(Optional<ContextAwarePredicate> player, Optional<String> name) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Codec.STRING.optionalFieldOf("name").forGetter(Instance::name)
        ).apply(i, Instance::new));
    }
}
