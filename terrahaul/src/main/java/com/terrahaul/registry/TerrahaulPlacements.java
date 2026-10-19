package com.terrahaul.registry;

import com.terrahaul.Terrahaul;
import com.terrahaul.worldgen.placement.DensityCountPlacement;
import com.terrahaul.worldgen.placement.TerrahaulOnlyFilter;
import com.terrahaul.worldgen.placement.TreeLineFilter;
import com.terrahaul.worldgen.placement.VolcanoZoneFilter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Placement modifier types. */
public final class TerrahaulPlacements {
    public static final DeferredRegister<PlacementModifierType<?>> TYPES = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Terrahaul.MOD_ID);

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<DensityCountPlacement>> DENSITY_COUNT =
        TYPES.register("density_count", () -> () -> DensityCountPlacement.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<TreeLineFilter>> TREE_LINE =
        TYPES.register("tree_line", () -> () -> TreeLineFilter.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<TerrahaulOnlyFilter>> TERRAHAUL_ONLY =
        TYPES.register("terrahaul_only", () -> () -> TerrahaulOnlyFilter.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<VolcanoZoneFilter>> VOLCANO_ZONE =
        TYPES.register("volcano_zone", () -> () -> VolcanoZoneFilter.CODEC);

    private TerrahaulPlacements() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
