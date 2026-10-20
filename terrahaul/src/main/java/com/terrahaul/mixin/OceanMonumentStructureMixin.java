package com.terrahaul.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.terrahaul.worldgen.TerrahaulGeneration;
import com.terrahaul.worldgen.structure.TerrainAwareStructures;
import java.util.Optional;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ocean monuments are built at a fixed Y 39, which is the deep-ocean floor of a vanilla world.
 * Terrahaul's deep oceans are far deeper, so in Terrahaul worlds the monument is lowered onto the
 * sea floor (the lowest point under its footprint) instead of floating above it.
 * <p>
 * The monument floods its whole footprint from its base up to sea level, so on an uneven floor it
 * cuts a square pit with sheer walls into the higher parts. Its region therefore picks the
 * flattest deep candidate (TerrainAwareStructures), and a site whose floor still varies by more
 * than {@value #MAX_UNEVEN} blocks under the footprint gets no monument at all. That check is part
 * of finding the generation point, so {@code /locate} agrees with what is built.
 * <p>
 * Two details of the vanilla monument have to be followed for the move: the building keeps its
 * rooms in a list of its own, which moving the building piece does not move, so the rooms are
 * moved with it; and a monument whose structure start is loaded from disk is rebuilt from scratch
 * at Y 39, so the rebuilt monument is moved back to the height the saved one had (this keeps
 * vanilla monuments where they were, as the heights are then equal).
 */
@Mixin(OceanMonumentStructure.class)
public abstract class OceanMonumentStructureMixin {
    private static final int VANILLA_BASE_Y = 39;
    /** Largest height range of the sea floor under the footprint that still gets a monument. */
    private static final int MAX_UNEVEN = 10;

    @Inject(method = "findGenerationPoint", at = @At("RETURN"), cancellable = true)
    private void terrahaul$onlyOnFlatFloors(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        if (cir.getReturnValue().isPresent() && TerrahaulGeneration.isTerrahaul(context.chunkGenerator())) {
            int[] range = terrahaul$floorRange(context);
            if (range[1] - range[0] > MAX_UNEVEN) {
                cir.setReturnValue(Optional.empty());
            }
        }
    }

    @WrapOperation(method = "generatePieces", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePiecesBuilder;addPiece(Lnet/minecraft/world/level/levelgen/structure/StructurePiece;)V"))
    private static void terrahaul$settleOnSeaFloor(StructurePiecesBuilder builder, StructurePiece piece, Operation<Void> original,
                                                   @Local(argsOnly = true) Structure.GenerationContext context) {
        if (TerrahaulGeneration.isTerrahaul(context.chunkGenerator())) {
            int offset = terrahaul$floorRange(context)[0] - VANILLA_BASE_Y;
            if (offset < 0) {
                terrahaul$move(piece, offset);
            }
        }
        original.call(builder, piece);
    }

    /**
     * The lowest and highest sea floor on a 5 x 5 grid over the building's footprint, which starts
     * {@link TerrainAwareStructures#MONUMENT_HALF} blocks west and north of the chunk and is 58
     * blocks wide.
     */
    @Unique
    private static int[] terrahaul$floorRange(Structure.GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int x0 = chunk.getMinBlockX() - TerrainAwareStructures.MONUMENT_HALF;
        int z0 = chunk.getMinBlockZ() - TerrainAwareStructures.MONUMENT_HALF;
        int width = 2 * TerrainAwareStructures.MONUMENT_HALF - 1;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                int y = context.chunkGenerator().getFirstOccupiedHeight(x0 + i * width / 4, z0 + j * width / 4, Heightmap.Types.OCEAN_FLOOR_WG,
                    context.heightAccessor(), context.randomState());
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        }
        return new int[] {min, max};
    }

    @Inject(method = "regeneratePiecesAfterLoad", at = @At("RETURN"))
    private static void terrahaul$keepHeightAfterLoad(ChunkPos chunkPos, long seed, PiecesContainer loaded, CallbackInfoReturnable<PiecesContainer> cir) {
        PiecesContainer rebuilt = cir.getReturnValue();
        if (rebuilt == loaded || loaded.isEmpty() || rebuilt.isEmpty()) {
            return;
        }
        int dy = loaded.pieces().get(0).getBoundingBox().minY() - rebuilt.pieces().get(0).getBoundingBox().minY();
        if (dy != 0) {
            terrahaul$move(rebuilt.pieces().get(0), dy);
        }
    }

    /** Moves the monument building and its rooms up or down. */
    @Unique
    private static void terrahaul$move(StructurePiece building, int dy) {
        building.move(0, dy, 0);
        if (building instanceof MonumentBuildingAccessor accessor) {
            for (Object room : accessor.terrahaul$childPieces()) {
                ((StructurePiece) room).move(0, dy, 0);
            }
        }
    }
}
