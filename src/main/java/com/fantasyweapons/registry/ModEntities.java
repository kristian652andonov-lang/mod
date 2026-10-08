package com.fantasyweapons.registry;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.entity.ThrownWeaponEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER = DeferredRegister.create(Registries.ENTITY_TYPE, FantasyWeapons.MOD_ID);

    /** A fantasy weapon in flight (thrown scythes). Never saved to disk. */
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownWeaponEntity>> THROWN_WEAPON = REGISTER.register("thrown_weapon",
            () -> EntityType.Builder.<ThrownWeaponEntity>of(ThrownWeaponEntity::new, MobCategory.MISC)
                    .sized(1.0f, 0.5f).clientTrackingRange(8).updateInterval(1).noSave().noSummon().build("thrown_weapon"));

    private ModEntities() {
    }
}
