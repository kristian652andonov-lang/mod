package com.terrahaul.worldgen;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;

/**
 * Global fluid picker for Terrahaul worlds. Vanilla hard-codes the deep lava level at Y=-54,
 * which is ten blocks above the vanilla world bottom; in a world reaching down to Y=-512 that
 * would flood every cave between -512 and -54 with lava. Terrahaul keeps vanilla's intent
 * (lava only in the lowest ten blocks) by placing the lava level relative to the world bottom.
 */
public final class TerrahaulFluidPicker implements Aquifer.FluidPicker {
    private final Aquifer.FluidStatus lava;
    private final Aquifer.FluidStatus water;
    private final int lavaLevel;
    private final int seaLevel;

    public TerrahaulFluidPicker(int lavaLevel, int seaLevel, BlockState defaultFluid) {
        this.lavaLevel = lavaLevel;
        this.seaLevel = seaLevel;
        this.lava = new Aquifer.FluidStatus(lavaLevel, Blocks.LAVA.defaultBlockState());
        this.water = new Aquifer.FluidStatus(seaLevel, defaultFluid);
    }

    @Override
    public Aquifer.FluidStatus computeFluid(int x, int y, int z) {
        return y < Math.min(lavaLevel, seaLevel) ? lava : water;
    }

    public int seaLevel() {
        return seaLevel;
    }

    public net.minecraft.world.level.block.state.BlockState seaWater() {
        return water.at(seaLevel - 1);
    }
}
