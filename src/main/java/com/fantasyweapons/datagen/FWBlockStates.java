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
        // the machines' six blocks are all drawn by the core's model
        machine(ModBlocks.ARCANE_REFINERY.get(), ModBlocks.REFINERY_PART.get(), "arcane_refinery");
        machine(ModBlocks.LEGENDARY_CRAFTING_TABLE.get(), ModBlocks.LEGENDARY_CRAFTING_TABLE_PART.get(), "legendary_crafting_table");
    }

    private void machine(net.minecraft.world.level.block.Block core, net.minecraft.world.level.block.Block part, String name) {
        ModelFile model = models().getBuilder(name).texture("particle", modLoc("block/" + name));
        for (var block : new net.minecraft.world.level.block.Block[]{core, part}) {
            getVariantBuilder(block).forAllStates(s -> net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder().modelFile(model).build());
        }
    }
}
