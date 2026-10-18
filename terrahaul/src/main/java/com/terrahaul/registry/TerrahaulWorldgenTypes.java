package com.terrahaul.registry;

import com.mojang.serialization.MapCodec;
import com.terrahaul.Terrahaul;
import com.terrahaul.worldgen.density.ColumnBoundedDensity;
import com.terrahaul.worldgen.density.TerrainDensityFunction;
import com.terrahaul.worldgen.surface.TerrainSurfaceRule;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Codec registrations for Terrahaul's data-driven world generation types. */
public final class TerrahaulWorldgenTypes {
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
        DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Terrahaul.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends SurfaceRules.RuleSource>> MATERIAL_RULES =
        DeferredRegister.create(Registries.MATERIAL_RULE, Terrahaul.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<TerrainDensityFunction>> TERRAIN =
        DENSITY_FUNCTION_TYPES.register("terrain", () -> TerrainDensityFunction.MAP_CODEC);
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<ColumnBoundedDensity>> COLUMN_BOUNDED =
        DENSITY_FUNCTION_TYPES.register("column_bounded", () -> ColumnBoundedDensity.MAP_CODEC);
    public static final DeferredHolder<MapCodec<? extends SurfaceRules.RuleSource>, MapCodec<TerrainSurfaceRule>> TERRAIN_SURFACE =
        MATERIAL_RULES.register("terrain_surface", () -> TerrainSurfaceRule.MAP_CODEC);

    private TerrahaulWorldgenTypes() {
    }

    public static void register(IEventBus modBus) {
        DENSITY_FUNCTION_TYPES.register(modBus);
        MATERIAL_RULES.register(modBus);
    }
}
