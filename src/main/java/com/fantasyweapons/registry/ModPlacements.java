package com.fantasyweapons.registry;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.world.gen.FittedHeightPlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** World generation placement steps: the legendary ores' height, fitted to taller worlds (see FittedHeightPlacement). */
public final class ModPlacements {
    public static final DeferredRegister<PlacementModifierType<?>> REGISTER = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, FantasyWeapons.MOD_ID);

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<FittedHeightPlacement>> FITTED_HEIGHT =
            REGISTER.register("fitted_height", () -> () -> FittedHeightPlacement.CODEC);

    private ModPlacements() {
    }
}
