package com.terrahaul.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.terrahaul.worldgen.TerrahaulGeneration;
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
 * sea floor (the lowest of its centre and corners) instead of floating above it.
 * <p>
 * Two details of the vanilla monument have to be followed for that: the building keeps its rooms
 * in a list of its own, which moving the building piece does not move, so the rooms are moved with
 * it; and a monument whose structure start is loaded from disk is rebuilt from scratch at Y 39,
 * so the rebuilt monument is moved back to the height the saved one had (this keeps vanilla
 * monuments where they were, as the heights are then equal).
 */
@Mixin(OceanMonumentStructure.class)
public abstract class OceanMonumentStructureMixin {
    private static final int VANILLA_BASE_Y = 39;
    private static final int HALF_SIZE = 29;

    @WrapOperation(method = "generatePieces", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePiecesBuilder;addPiece(Lnet/minecraft/world/level/levelgen/structure/StructurePiece;)V"))
    private static void terrahaul$settleOnSeaFloor(StructurePiecesBuilder builder, StructurePiece piece, Operation<Void> original,
                                                   @Local(argsOnly = true) Structure.GenerationContext context) {
        if (TerrahaulGeneration.isTerrahaul(context.chunkGenerator())) {
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
                terrahaul$move(piece, offset);
            }
        }
        original.call(builder, piece);
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
