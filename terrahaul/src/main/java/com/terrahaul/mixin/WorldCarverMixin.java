package com.terrahaul.mixin;

import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
import com.terrahaul.worldgen.water.FallPlanner;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.carver.CarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In Terrahaul worlds, cave and ravine carvers do not open up next to surface water above sea
 * level: lakes, elevated rivers, crater lakes and streams. Their water is placed after carving, so
 * a ravine cutting through a lake's rim or bed would otherwise leave the lake standing as a wall
 * of water above the ravine. Near such water a carver keeps {@link TerrainModel#WATER_GUARD}
 * blocks below its surface and eight blocks below the ground, which also keeps it out of the
 * beds of deep lakes. (The water feature seals whatever opening remains, but a sealed wall in a
 * ravine is no prettier than a breach.) Under and around waterfalls and cascades they keep as far
 * below what the plan builds ({@link FallPlanner#guardLevel}), so a pool or channel is never left
 * on a thin shelf over a cave.
 */
@Mixin(WorldCarver.class)
public abstract class WorldCarverMixin {
    /**
     * How far below sea level the plans' guard is looked at: a fall into the sea has its pool at
     * sea level and its bed a few blocks lower.
     */
    private static final int PLAN_REACH = 32;

    @Inject(method = "carveBlock", at = @At("HEAD"), cancellable = true)
    private void terrahaul$keepWaterContained(CarvingContext context, CarverConfiguration config, ChunkAccess chunk,
                                              Function<BlockPos, Holder<Biome>> biomeGetter, CarvingMask mask, BlockPos.MutableBlockPos pos,
                                              BlockPos.MutableBlockPos checkPos, Aquifer aquifer, MutableBoolean reachedSurface,
                                              CallbackInfoReturnable<Boolean> cir) {
        TerrainModel model = TerrahaulGeneration.model(context.randomState());
        if (model == null || pos.getY() < model.seaLevel() - PLAN_REACH) {
            return;
        }
        // memoised per grid corner, so this costs a few cache lookups per carved block
        int y = pos.getY();
        double plan = FallPlanner.guardLevel(model, pos.getX(), pos.getZ());
        if (plan != ColumnSample.NO_WATER && y > plan - TerrainModel.WATER_GUARD) {
            cir.setReturnValue(false);
            return;
        }
        if (y < model.seaLevel() - 2) {
            return;
        }
        double water = model.nearbyWaterLevel(pos.getX(), pos.getZ());
        if (water == ColumnSample.NO_WATER) {
            return;
        }
        if (y > water - TerrainModel.WATER_GUARD || y > model.sample(pos.getX() & ~3, pos.getZ() & ~3).height - 8.0) {
            cir.setReturnValue(false);
        }
    }
}
