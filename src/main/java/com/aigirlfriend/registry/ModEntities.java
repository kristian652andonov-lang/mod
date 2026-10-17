package com.aigirlfriend.registry;

import com.aigirlfriend.AIGirlfriend;
import com.aigirlfriend.entity.GirlfriendEntity;
import com.aigirlfriend.entity.SeatEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, AIGirlfriend.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<GirlfriendEntity>> GIRLFRIEND = ENTITIES.register("girlfriend",
            () -> EntityType.Builder.of(GirlfriendEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.75f)
                    .eyeHeight(1.58f)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(AIGirlfriend.MOD_ID + ":girlfriend"));

    public static final DeferredHolder<EntityType<?>, EntityType<SeatEntity>> SEAT = ENTITIES.register("seat",
            () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .noSummon()
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(AIGirlfriend.MOD_ID + ":seat"));

    private ModEntities() {}
}
