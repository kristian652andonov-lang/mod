package com.terrahaul.mixin;

import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skips the structure-terrain adaptation ("beard") where it is zero anyway.
 * <p>
 * Vanilla's beardifier visits every structure piece and jigsaw junction near the chunk for every
 * density sample. Each contribution vanishes more than {@value Beardifier#BEARD_KERNEL_RADIUS}
 * blocks above or below a piece's vertical range (or a junction's ground level), but the loop still
 * runs. In a Terrahaul world the noise fill covers a column several times as tall as vanilla's, so
 * this loop was about a fifth of chunk generation time near villages and other jigsaw structures.
 * The vertical range of everything the chunk's beardifier holds is computed once, and samples
 * outside it return 0 at once. The result is identical to vanilla's in every world.
 */
@Mixin(Beardifier.class)
public abstract class BeardifierMixin {
    @Shadow
    @Final
    private ObjectListIterator<Beardifier.Rigid> pieceIterator;
    @Shadow
    @Final
    private ObjectListIterator<JigsawJunction> junctionIterator;

    @Unique
    private boolean terrahaul$rangeKnown;
    @Unique
    private int terrahaul$minY;
    @Unique
    private int terrahaul$maxY;

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void terrahaul$outsideRange(DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
        if (!this.terrahaul$rangeKnown) {
            this.terrahaul$computeRange();
        }
        int y = context.blockY();
        if (y < this.terrahaul$minY || y > this.terrahaul$maxY) {
            cir.setReturnValue(0.0);
        }
    }

    @Unique
    private void terrahaul$computeRange() {
        int radius = Beardifier.BEARD_KERNEL_RADIUS;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        while (this.pieceIterator.hasNext()) {
            Beardifier.Rigid rigid = this.pieceIterator.next();
            BoundingBox box = rigid.box();
            int ground = box.minY() + rigid.groundLevelDelta();
            // BURY and BEARD_THIN act within the kernel radius of the ground level, BEARD_BOX and
            // ENCAPSULATE within it of the span between the ground level / bottom and the top
            min = Math.min(min, Math.min(ground, box.minY()) - radius);
            max = Math.max(max, Math.max(ground, box.maxY()) + radius);
        }
        this.pieceIterator.back(Integer.MAX_VALUE);
        while (this.junctionIterator.hasNext()) {
            JigsawJunction junction = this.junctionIterator.next();
            min = Math.min(min, junction.getSourceGroundY() - radius);
            max = Math.max(max, junction.getSourceGroundY() + radius);
        }
        this.junctionIterator.back(Integer.MAX_VALUE);
        this.terrahaul$minY = min;
        this.terrahaul$maxY = max;
        this.terrahaul$rangeKnown = true;
    }
}
