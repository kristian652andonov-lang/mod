package com.terrahaul.mixin;

import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.terrain.ColumnSample;
import com.terrahaul.worldgen.terrain.TerrainModel;
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
 * In Terrahaul worlds, cave and ravine carvers do not open up next to lakes and rivers above sea
 * level. Their water is placed after carving, so a ravine cutting through a lake's rim would
 * otherwise leave the lake standing as a wall of water above the ravine.
 */
@Mixin(WorldCarver.class)
public abstract class WorldCarverMixin {
    @Inject(method = "carveBlock", at = @At("HEAD"), cancellable = true)
    private void terrahaul$keepWaterContained(CarvingContext context, CarverConfiguration config, ChunkAccess chunk,
                                              Function<BlockPos, Holder<Biome>> biomeGetter, CarvingMask mask, BlockPos.MutableBlockPos pos,
                                              BlockPos.MutableBlockPos checkPos, Aquifer aquifer, MutableBoolean reachedSurface,
                                              CallbackInfoReturnable<Boolean> cir) {
        TerrainModel model = TerrahaulGeneration.model(context.randomState());
        if (model == null || pos.getY() < model.seaLevel() - 2) {
            return;
        }
        double water = model.nearbyWaterLevel(pos.getX(), pos.getZ());
        if (water != ColumnSample.NO_WATER && pos.getY() > water - TerrainModel.WATER_GUARD) {
            cir.setReturnValue(false);
        }
    }
}
