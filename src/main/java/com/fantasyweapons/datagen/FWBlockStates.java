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
        // the refinery's six blocks are all drawn by the core's model
        ModelFile refinery = models().getBuilder("arcane_refinery").texture("particle", modLoc("block/arcane_refinery"));
        getVariantBuilder(ModBlocks.ARCANE_REFINERY.get()).forAllStates(s -> net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                .modelFile(refinery).build());
        getVariantBuilder(ModBlocks.REFINERY_PART.get()).forAllStates(s -> net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                .modelFile(refinery).build());
    }
}
