package com.fantasyweapons.datagen;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.material.Material;
import com.fantasyweapons.registry.ModBlocks;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Block states for blocks drawn by their GeckoLib model: the baked model is empty and only names the particle texture
 * (the first frame of the block's own animated texture) for the breaking and landing particles.
 */
public class FWBlockStates extends BlockStateProvider {
    public FWBlockStates(PackOutput output, ExistingFileHelper files) {
        super(output, FantasyWeapons.MOD_ID, files);
    }

    @Override
    protected void registerStatesAndModels() {
        for (Material m : Material.values()) {
            ModelFile model = models().getBuilder(m.oreId()).texture("particle", modLoc("block/" + m.oreAsset()));
            simpleBlock(ModBlocks.ORES.get(m).get(), model);
        }
    }
}
