package com.terrahaul.mixin;

import com.terrahaul.worldgen.TerrahaulGeneration;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ocean monuments are built at a fixed Y 39, which is the deep-ocean floor of a vanilla world.
 * Terrahaul's deep oceans are far deeper, so in Terrahaul worlds the monument is lowered onto the
 * sea floor (the lowest of its centre and corners) instead of floating above it.
 */
@Mixin(OceanMonumentStructure.class)
public abstract class OceanMonumentStructureMixin {
    private static final int VANILLA_BASE_Y = 39;
    private static final int HALF_SIZE = 29;

    @Inject(method = "generatePieces", at = @At("TAIL"))
    private static void terrahaul$settleOnSeaFloor(StructurePiecesBuilder builder, Structure.GenerationContext context, CallbackInfo ci) {
        if (!TerrahaulGeneration.isTerrahaul(context.chunkGenerator())) {
            return;
        }
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getBlockX(9);
        int cz = chunk.getBlockZ(9);
        int floor = Integer.MAX_VALUE;
        int[][] points = {{0, 0}, {-HALF_SIZE, -HALF_SIZE}, {HALF_SIZE, -HALF_SIZE}, {-HALF_SIZE, HALF_SIZE}, {HALF_SIZE, HALF_SIZE}};
        for (int[] p : points) {
            floor = Math.min(floor, context.chunkGenerator().getFirstOccupiedHeight(cx + p[0], cz + p[1], Heightmap.Types.OCEAN_FLOOR_WG,
                context.heightAccessor(), context.randomState()));
        }
        int offset = floor - VANILLA_BASE_Y;
        if (offset < 0) {
            builder.offsetPiecesVertically(offset);
        }
    }
}
